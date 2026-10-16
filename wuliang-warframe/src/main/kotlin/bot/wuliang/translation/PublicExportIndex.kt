package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.moudles.Nodes
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Path
import java.util.*

/** 结合导出对象与本地化字典解析游戏标识；对象路径需要先解析元数据，不能一律当作字典键。 */
class PublicExportIndex private constructor(
    paths: Set<String>,
    readDocument: (String) -> JsonNode,
    readDictionary: (String, MutableMap<String, String>) -> Unit,
    aliases: Map<String, String>,
    previous: PublicExportIndex? = null,
    unchangedFiles: Set<String> = emptySet(),
    ownsDocuments: Boolean = false,
) {
    constructor(documents: Map<String, JsonNode>, aliases: Map<String, String> = emptyMap()) : this(
        documents.keys,
        documents::getValue,
        { file, result ->
            val root = documents.getValue(file)
            require(root.isObject && root.size() > 0) { "$file 字典为空或格式错误" }
            mergeExportDictionary(file, result) { accept ->
                root.fields().forEach { (key, value) ->
                    require(value.isTextual) { "$file 字典包含非文本值：$key" }
                    accept(key, value.asText())
                }
            }
        },
        aliases,
    )

    private val aliases = aliases.mapKeys { normalize(it.key) }
    private val sourcePaths = paths.toSet()
    private val objectFiles = paths.filter { it.startsWith("Export") || PublicExportStore.acceptsOfficial(it) }.toSet()
    private val dictionaries: Map<String, Map<String, String>>

    private data class OfficialReference(val id: String, val field: String)

    /** 构建期间拥有节点；发布后只读。未变的目录可被新旧快照共同引用。 */
    private class ObjectCatalog {
        val objects = linkedMapOf<String, JsonNode>()
        val supplementalObjects = linkedMapOf<String, JsonNode>()
        val officialIds = mutableSetOf<String>()
        val officialObjects = mapOf("en" to linkedMapOf(), "zh" to linkedMapOf<String, JsonNode>())
        val officialReferences = linkedMapOf<String, MutableList<OfficialReference>>()
        val identities = linkedMapOf<String, String>()
        val recipes = mutableSetOf<String>()
        val relics = mutableSetOf<String>()
    }

    private val catalog: ObjectCatalog = previous?.takeIf {
        it.aliases == this.aliases && it.objectFiles == objectFiles && unchangedFiles.containsAll(objectFiles)
    }?.catalog ?: ObjectCatalog()
    private val objects = catalog.objects
    private val supplementalObjects = catalog.supplementalObjects
    private val officialIds = catalog.officialIds
    private val officialObjects = catalog.officialObjects
    private val officialReferences = catalog.officialReferences
    private val identities = catalog.identities
    private val recipes = catalog.recipes
    private val relics = catalog.relics
    private val byEnglish: ExportNameIndex
    private val byChinese: ExportNameIndex
    private val byOfficialEnglish: ExportNameIndex
    private val byOfficialChinese: ExportNameIndex
    private val missionTypesByIndex: Map<Int, String>
    private val factionsByIndex: Map<Int, String>
    var onMissing: (String, String, String) -> Unit = { _, _, _ -> }

    init {
        // 只在构建期间引用上一个索引，不能把整份旧索引保存在新索引中形成版本引用链。
        val reusable = previous?.takeIf { it.aliases == this.aliases }
        val canonicalKeys = HashMap<String, String>()
        reusable?.dictionaries?.values?.forEach { dictionary ->
            dictionary.keys.forEach { canonicalKeys.putIfAbsent(it, it) }
        }
        /** 规范化标识并复用构建期字符串池中的键，减少中英文目录重复字符串。 */
        fun canonicalId(value: String): String = normalize(value).let { canonicalKeys.getOrPut(it) { it } }
        require(paths.containsAll(PublicExportStore.requiredFiles)) { "词库缺少必要文件" }
        PublicExportStore.validateOfficialFiles(paths)
        val languageFiles = paths.intersect(PublicExportStore.languageFiles)
        require(languageFiles.isEmpty() || languageFiles == PublicExportStore.languageFiles) { "完整语言表必须同时包含中文和英文" }
        dictionaries = PublicExportPolicy.LOCALES.associateWith { locale ->
            val files = setOf("dict.$locale.json", "languages.$locale.json").intersect(paths)
            val oldFiles = reusable?.sourcePaths?.intersect(setOf("dict.$locale.json", "languages.$locale.json"))
            if (reusable != null && oldFiles == files && unchangedFiles.containsAll(files)) {
                reusable.dictionaries.getValue(locale)
            } else linkedMapOf<String, String>().also { result ->
                val canonical = CanonicalExportDictionary(result, canonicalKeys)
                // 同语言内 Plus 的非空值覆盖完整语言表；中文合并完成以后才回退英文。
                "languages.$locale.json".takeIf { it in paths }?.let { readDictionary(it, canonical) }
                readDictionary("dict.$locale.json", canonical)
            }
        }
        val catalogReused = catalog === reusable?.catalog
        if (!catalogReused) {
            paths.filter { it.startsWith("Export") }.sorted().forEach { file ->
                val root = readDocument(file)
                require(root.isContainerNode) { "$file 必须是对象或数组" }
                if (file in PublicExportStore.requiredFiles) {
                    require(root.isObject && root.size() > 0) { "$file 目录为空或格式错误" }
                }
                /** 递归收集 Plus 对象、节点和奖励池，校验大小写冲突并建立物品元数据目录。 */
                fun collect(node: JsonNode) {
                    if (node.isObject) node.fields().forEach { (key, value) ->
                        val regionEntry = file == "ExportRegions.json" && node === root
                        // 节点目录完整收录；奖励池保留整个嵌套数组。
                        val objectEntry = value.isObject || (file == "ExportRewards.json" && value.isArray)
                        val identityKey = regionEntry || key.startsWith("/") || REGION_KEY_PATTERN.matches(key)
                        if (objectEntry && identityKey) {
                            val normalized = canonicalId(key)
                            val old = objects[normalized]
                            require(identities[normalized] == null || identities[normalized] == key || old == value) {
                                "导出对象存在大小写冲突：${identities[normalized]} / $key"
                            }
                            identities.putIfAbsent(normalized, key)
                            // 文件读取产生的树转移给目录，父子对象共享同一子树；外部传入的树仍隔离复制。
                            if (old == null) objects[normalized] = if (ownsDocuments) value else value.deepCopy()
                            else if (old is ObjectNode) objects[normalized] = mergeObject(old, value, ownsDocuments) {
                                !old.has(it)
                            }
                            if (file == "ExportRecipes.json") recipes.add(normalized)
                            if (file == "ExportRelics.json") relics.add(normalized)
                        }
                        collect(value)
                    } else if (node.isArray) node.forEach(::collect)
                }
                collect(root)
            }
            supplementalObjects.putAll(objects)
            paths.filter(PublicExportStore::acceptsOfficial).sorted().forEach { file ->
                val root = readDocument(file)
                val locale = if (file.endsWith(".zh.json")) "zh" else "en"
                val category = file.removePrefix("official.").removeSuffix(".$locale.json")
                require(root.isObject && root.path(category).isArray && root.path(category).size() > 0) {
                    "$file 官方对象目录为空或格式错误"
                }
                /** 递归收集官方对象及其 uniqueName，合并缺失字段并建立分语言的官方目录。 */
                fun collectOfficial(node: JsonNode) {
                    if (node.isObject) {
                        val key = node["uniqueName"]?.takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)
                        if (key != null) {
                            val normalized = canonicalId(key)
                            officialIds.add(normalized)
                            val old = officialObjects.getValue(locale)[normalized]
                            if (old == null) officialObjects.getValue(locale)[normalized] =
                                if (ownsDocuments) node else node.deepCopy()
                            else if (old is ObjectNode) officialObjects.getValue(locale)[normalized] =
                                mergeObject(old, node, ownsDocuments) { missingField(old[it]) }
                            identities.putIfAbsent(normalized, key)
                            if (category == "ExportRecipes") recipes.add(normalized)
                        }
                        // 电波、奖励和技能可位于嵌套对象，不能只读取顶层数组。
                        node.elements().forEachRemaining(::collectOfficial)
                    } else if (node.isArray) node.forEach(::collectOfficial)
                }
                collectOfficial(root)
            }
            supplementalObjects.forEach { (id, node) ->
                if (id in officialIds) {
                    listOf("name", "description", "systemName").forEach { field ->
                        node[field]?.takeIf(JsonNode::isTextual)?.asText()
                            ?.takeIf { it.startsWith("/Lotus/Language/", ignoreCase = true) }?.let { key ->
                                officialReferences.getOrPut(canonicalId(key)) { mutableListOf() }
                                    .add(OfficialReference(id, field))
                            }
                    }
                }
            }
            // 保持现有元数据优先顺序，补缺只修改新的顶层容器。
            (officialObjects.getValue("zh") + officialObjects.getValue("en")).forEach { (key, node) ->
                val primary = if (node is ObjectNode) node.objectNode().also { it.setAll<JsonNode>(node) } else node
                if (primary is ObjectNode) objects[key]?.fields()?.forEach { (field, value) ->
                    if (missingField(primary[field])) primary.set<JsonNode>(field, value)
                }
                objects[key] = primary
            }
        }
        /** 按标识前缀 [prefix] 构建协议数字索引到目录标识的映射。 */
        fun catalogIndices(prefix: String): Map<Int, String> = linkedMapOf<Int, String>().also { entries ->
            identities.keys.forEach { key ->
                if (key.startsWith(prefix)) {
                    objects[key]?.get("index")?.takeIf(JsonNode::isNumber)?.asInt()
                        ?.let { entries.putIfAbsent(it, key) }
                }
            }
        }
        missionTypesByIndex = if (catalogReused) reusable.missionTypesByIndex else catalogIndices("mt_")
        factionsByIndex = if (catalogReused) reusable.factionsByIndex else catalogIndices("fc_")
        byEnglish = if (catalogReused && dictionaries.getValue("en") === reusable.dictionaries.getValue("en")) {
            reusable.byEnglish
        } else ExportNameIndex.Builder().also { english ->
            dictionaries.getValue("en").forEach { (key, value) ->
                if (value.length in 1..160) english.add(normalize(clean(value)), key)
            }
            identities.keys.forEach { key ->
                resolve(key, "en", "name", mutableSetOf())?.let { english.add(normalize(clean(it)), key) }
            }
        }.build()
        byChinese = if (catalogReused && byEnglish === reusable.byEnglish &&
            dictionaries.getValue("zh") === reusable.dictionaries.getValue("zh")
        ) {
            reusable.byChinese
        } else ExportNameIndex.Builder().also { chinese ->
            val seen = HashSet<String>()
            byEnglish.forEachId { key ->
                if (seen.add(key)) {
                    // 纯语言键直接走同一字典回退规则，不为每条文本分配递归路径集合。
                    val text =
                        if (key.startsWith("/lotus/language/") && key !in aliases) dictionary(key, "zh") else null
                    (text ?: resolve(key, "zh", "name", mutableSetOf()))?.let {
                        chinese.add(normalize(clean(it)), key)
                    }
                }
            }
        }.build()
        /** 从指定语言的官方对象建立名称反查索引，名称规范化后保留所有候选标识。 */
        fun officialNames(locale: String): ExportNameIndex = ExportNameIndex.Builder().also { names ->
            officialObjects.getValue(locale).keys.forEach { id ->
                officialField(id, "name", locale)?.let { names.add(normalize(it), id) }
            }
        }.build()
        byOfficialEnglish = if (catalogReused) reusable.byOfficialEnglish else officialNames("en")
        byOfficialChinese = if (catalogReused) reusable.byOfficialChinese else officialNames("zh")
    }

    /**
     * 按原始标识取得导出对象元数据，同时兼容可转换的 StoreItems 路径。
     * 返回节点只供读取；索引发布后调用方不得修改节点及其子节点。
     */
    fun metadata(key: String): JsonNode? = objects[objectKey(key)]

    /** 只读取官方同语言字段，中文缺失时交给补充源，不能提前回退官方英文。 */
    fun officialField(key: String, field: String, locale: String = "zh"): String? {
        require(locale == "zh" || locale == "en")
        officialObjectField(key, field, locale)?.let { return it }
        if (field != "name") return null
        val values = officialReferences[normalize(key)].orEmpty()
            .mapNotNull { officialObjectField(it.id, it.field, locale) }.distinct()
        return values.singleOrNull()
    }

    /** 读取同语言官方对象字段，处理商店路径与任务数量占位符，空值或未解析路径返回 null。 */
    private fun officialObjectField(key: String, field: String, locale: String): String? {
        val dictionary = officialObjects.getValue(locale)
        val normalized = normalize(key)
        val item = dictionary[normalized] ?: dictionary[normalized.replaceFirst("/lotus/storeitems/", "/lotus/")]
        ?: return null
        val value = item[field]
        val text = when {
            value?.isTextual == true -> value.asText()
            value?.isArray == true -> value.filter(JsonNode::isTextual).joinToString("\n") { it.asText() }
            else -> null
        }?.takeIf { it.isNotBlank() && !it.startsWith("/") } ?: return null
        val count = item["required"] ?: item["requiredCount"]
        return clean(if (count?.isNumber == true) text.replace("|COUNT|", count.asText()) else text)
            .takeIf(String::isNotBlank)
    }

    /**
     * 把 Market 武器 slug 对应到进化适配器对象路径；只有唯一匹配时才返回结果。
     */
    fun incarnonAdapter(slug: String): String? {
        val basename = slug.replace("_", "") + "IncarnonUnlocker"
        return identities.values.singleOrNull {
            it.startsWith("/Lotus/Types/Items/MiscItems/IncarnonAdapters/") &&
                    it.substringAfterLast('/').equals(basename, ignoreCase = true)
        }
    }

    /** 解析指定语言的名称，无法解析时记录缺失项并返回原始标识。 */
    fun name(key: String, locale: String = "zh"): String =
        field(key, "name", locale) ?: key.also { onMissing(key, locale, "name") }

    /** 解析指定语言的说明文本，缺失时返回 null 并沿用统一字段查询的缺失记录策略。 */
    fun description(key: String, locale: String = "zh"): String? = field(key, "description", locale)

    /**
     * 优先按标识解析字段；名称还允许按英文完整名称反查，但只接受唯一译文。
     * 无法解析时返回 null 并记录缺失，由调用方决定展示回退值。
     */
    fun field(key: String, field: String, locale: String = "zh"): String? {
        require(locale == "zh" || locale == "en")
        val resolved = resolve(key, locale, field, mutableSetOf())
        if (resolved != null) return clean(resolved)
        if (field == "name") {
            val officialValues = byOfficialEnglish[normalize(clean(key))]
                .mapNotNull { officialField(it, field, locale) }.distinct()
            if (officialValues.size == 1) return officialValues.single()
            val candidates = byEnglish[normalize(clean(key))]
            val values = candidates.mapNotNull { resolve(it, locale, field, mutableSetOf())?.let(::clean) }.distinct()
            if (values.size == 1) return values.single()
        }
        onMissing(key, locale, field)
        return null
    }

    /**
     * 组合节点、星球、默认任务类型及派系；未知节点保留原始标识，避免世界状态解析失败。
     * 双派系节点和特殊防御节点单独采用交战标签。
     */
    fun node(id: String, factionName: (String) -> String = { name(it) }): Nodes {
        val node = metadata(id) ?: return Nodes(name = name(id), faction = null, type = null)
        val label = name(id)
        val system = officialField(id, "systemName") ?: node["systemName"]?.asText()?.let(::name)
        /** 读取节点中的数字字段 [field]，按 [catalog] 转换为目录标识，缺失或无法匹配时返回 null。 */
        fun catalogByIndex(catalog: Map<Int, String>, field: String): String? =
            node[field]?.takeIf(JsonNode::isNumber)?.asInt()?.let(catalog::get)
        return Nodes(
            name = if (system.isNullOrBlank()) label else "$label ($system)",
            faction = if (id.equals("SolNode450", ignoreCase = true)) {
                factionName("/Lotus/Language/Missions/DualDefenseCompare")
            } else if (node.has("secondaryFaction") || node.has("secondaryFactionIndex")) {
                factionName("/Lotus/Language/Missions/MissionName_Crossfire")
            } else (catalogByIndex(factionsByIndex, "factionIndex") ?: node["faction"]?.asText())?.let(factionName),
            type = catalogByIndex(missionTypesByIndex, "missionIndex")?.let(::name)
                ?: node["missionName"]?.asText()?.let(::name) ?: node["missionType"]?.asText()?.let(::name)
        )
    }

    /** 按完整中英文名称反查，返回去重后的候选译名，由调用方处理多个结果。 */
    fun translateQuery(query: String): List<String> {
        val normalized = normalize(clean(query))
        val officialEnMatches = byOfficialEnglish[normalized].map { name(it) }.distinct()
        if (officialEnMatches.isNotEmpty()) return officialEnMatches
        val officialZhMatches = byOfficialChinese[normalized].map { name(it, "en") }.distinct()
        if (officialZhMatches.isNotEmpty()) return officialZhMatches
        val enMatches = byEnglish[normalized].map { name(it) }.distinct()
        if (enMatches.isNotEmpty()) return enMatches
        return byChinese[normalized].map { name(it, "en") }.distinct()
    }

    /**
     * 递归解析字典键、对象字段及父对象，使用 seen 防止循环引用。
     * 蓝图和遗物按模板拼接名称，挑战说明按 required 或 requiredCount 替换数量占位符。
     */
    private fun resolve(key: String, locale: String, field: String, seen: MutableSet<String>): String? {
        officialField(key, field, locale)?.let { return it }
        // 只补缺失对象的语言键关联；上游已有对象元数据时继续使用上游。
        if (objects[objectKey(key)] == null) {
            val alias = aliases[objectKey(key)] ?: aliases[normalize(key)]
            if (alias != null) {
                if (!seen.add("alias:${normalize(key)}:$field")) return null
                return resolve(alias, locale, field, seen)
            }
        }
        val normalized = objectKey(key)
        if (!seen.add("$normalized:$field")) return null
        if (field == "name") dictionary(key, locale)?.let { return it }
        val item = supplementalObjects[normalized] ?: objects[normalized] ?: return null
        if (field == "name" && normalized in recipes) {
            val result = objects[normalized]?.get("resultType")?.asText() ?: return null
            val resultName = resolve(result, locale, "name", seen.toMutableSet()) ?: result
            return (dictionary("/Lotus/Language/Items/BlueprintAndItem", locale)
                ?: "|ITEM| Blueprint").replace("|ITEM|", resultName)
        }
        if (field == "name" && normalized in relics) {
            val era = objects[normalized]?.get("era")?.asText() ?: return officialField(key, field, "en")
            val category = objects[normalized]?.get("category")?.asText() ?: return officialField(key, field, "en")
            val eraName = dictionary("/Lotus/Language/Relics/Era_${era.uppercase(Locale.ROOT)}", locale) ?: era
            return (dictionary("/Lotus/Language/Relics/VoidProjectionName", locale) ?: "|ERA| |CATEGORY| Relic")
                .replace("|ERA|", eraName).replace("|CATEGORY|", category)
        }
        val value = item[field]
        val text = when {
            value?.isTextual == true -> value.asText()
            value?.isArray == true -> value.mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }.joinToString("\n")
            else -> null
        }
        if (!text.isNullOrBlank()) {
            if (field == "name" && text.equals("/Lotus/Language/Relics/VoidProjectionName", ignoreCase = true)) {
                onMissing(key, locale, "relic-name")
                return null
            }
            val localized = dictionary(text, locale) ?: if (text.startsWith("/")) {
                resolve(text, locale, "name", seen.toMutableSet()) ?: text.also { onMissing(it, locale, field) }
            } else text
            val required = item["required"] ?: item["requiredCount"]
            return if (required?.isNumber == true) localized.replace("|COUNT|", required.asText()) else localized
        }
        return item["parentName"]?.asText()?.let { resolve(it, locale, field, seen) }
            ?: if (locale == "zh") officialField(key, field, "en") else null
    }

    /**
     * 查询合并后的同语言字典；两个来源都缺中文时才回退英文并记录缺失。
     */
    private fun dictionary(key: String, locale: String): String? {
        val normalized = normalize(key)
        officialField(key, "name", locale)?.let { return it }
        dictionaries.getValue(locale)[normalized]?.takeIf { it.isNotBlank() }?.let { return it }
        val officialEnglish = if (normalized in officialReferences) officialField(key, "name", "en") else null
        if (locale == "zh") (officialEnglish ?: dictionaries.getValue("en")[normalized])?.let {
            onMissing(key, locale, "dictionary")
            return it
        }
        return null
    }

    /**
     * 先匹配原始对象路径，仅在未命中时移除 StoreItems 前缀以兼容商店物品路径。
     */
    private fun objectKey(key: String): String {
        val normalized = normalize(key)
        if (normalized in officialIds) return normalized
        val canonical = normalized.replaceFirst("/lotus/storeitems/", "/lotus/")
        if (canonical in officialIds) return canonical
        if (objects.containsKey(normalized)) return normalized
        return if (normalized.startsWith("/lotus/storeitems/")) normalized.replaceFirst(
            "/lotus/storeitems/",
            "/lotus/"
        ) else normalized
    }

    companion object {
        /** 从已经校验的快照文件构建只读词库索引，允许复用上一版本中内容未变的目录。 */
        internal fun fromFiles(
            directory: Path,
            files: List<ExportFile>,
            aliases: Map<String, String>,
            previous: PublicExportIndex? = null,
            unchangedFiles: Set<String> = emptySet(),
        ): PublicExportIndex {
            val reader = ExportDocumentReader(directory, files)
            return PublicExportIndex(
                files.map { it.path }.toSet(), reader::document, reader::dictionary,
                aliases, previous, unchangedFiles, ownsDocuments = true,
            )
        }

        private val REGION_KEY_PATTERN = Regex("(?:SolNode|SettlementNode|ClanNode|MT_|FC_).*", RegexOption.IGNORE_CASE)
        private val MARKUP_PATTERN = Regex("<[^>]+>")
        /** 判断元数据字段是否缺失、为 null、为空文本或为空容器。 */
        private fun missingField(value: JsonNode?): Boolean = value == null || value.isNull ||
                (value.isTextual && value.asText().isBlank()) || (value.isContainerNode && value.size() == 0)

        /** 共享的父子节点不能原地修改；首次补字段时只复制顶层，保留各对象原有的子树。 */
        private fun mergeObject(
            previous: ObjectNode,
            incoming: JsonNode,
            shared: Boolean,
            missing: (String) -> Boolean,
        ): ObjectNode {
            var merged = previous
            incoming.fields().forEach { (field, value) ->
                if (missing(field)) {
                    if (shared && merged === previous) merged =
                        previous.objectNode().also { it.setAll<JsonNode>(previous) }
                    merged.set<JsonNode>(field, value)
                }
            }
            return merged
        }

        /** 按 [Locale.ROOT] 统一标识大小写，避免受系统语言环境影响。 */
        fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        /** 清理 HTML 标签与颜色标记，并去除展示文本两端空白。 */
        fun clean(value: String): String = (if ('<' in value) value.replace(MARKUP_PATTERN, "") else value)
            .replace("|COLOR|", "").replace("|NO_COLOR|", "").trim()
    }
}