package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.moudles.Nodes
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*

/** 结合导出对象与本地化字典解析游戏标识；对象路径需要先解析元数据，不能一律当作字典键。 */
class PublicExportIndex(
    documents: Map<String, JsonNode>,
    aliases: Map<String, String> = emptyMap(),
) {
    private val aliases = aliases.mapKeys { normalize(it.key) }
    private val dictionaries: Map<String, Map<String, String>>
    private val objects = linkedMapOf<String, JsonNode>()
    private val supplementalObjects = linkedMapOf<String, JsonNode>()
    private val officialIds = mutableSetOf<String>()
    private val officialObjects = mapOf("en" to linkedMapOf(), "zh" to linkedMapOf<String, JsonNode>())

    private data class OfficialReference(val id: String, val field: String)

    private val officialReferences = linkedMapOf<String, MutableList<OfficialReference>>()
    private val identities = linkedMapOf<String, String>()
    private val recipes = mutableSetOf<String>()
    private val relics = mutableSetOf<String>()
    private val byEnglish: Map<String, List<String>>
    private val byChinese: Map<String, List<String>>
    private val byOfficialEnglish: Map<String, List<String>>
    private val byOfficialChinese: Map<String, List<String>>
    private val missionTypesByIndex: Map<Int, String>
    private val factionsByIndex: Map<Int, String>
    var onMissing: (String, String, String) -> Unit = { _, _, _ -> }

    init {
        require(documents.keys.containsAll(PublicExportStore.requiredFiles)) { "词库缺少必要文件" }
        PublicExportStore.validateOfficialFiles(documents.keys)
        PublicExportStore.requiredFiles.filter { it.startsWith("Export") }.forEach { file ->
            require(
                documents.getValue(file).isObject && documents.getValue(file).size() > 0
            ) { "$file 目录为空或格式错误" }
        }
        val languageFiles = documents.keys.intersect(PublicExportStore.languageFiles)
        require(languageFiles.isEmpty() || languageFiles == PublicExportStore.languageFiles) { "完整语言表必须同时包含中文和英文" }
        fun readDictionary(file: String): Map<String, String> {
            val root = documents.getValue(file)
            require(root.isObject && root.size() > 0) { "$file 字典为空或格式错误" }
            val result = linkedMapOf<String, String>()
            root.fields().forEach { (key, value) ->
                require(value.isTextual) { "$file 字典包含非文本值：$key" }
                val old = result.put(normalize(key), value.asText())
                require(old == null || old == value.asText()) { "$file 字典存在大小写冲突：$key" }
            }
            return result.filterValues { it.isNotBlank() }
        }
        dictionaries = PublicExportPolicy.LOCALES.associateWith { locale ->
            // 每种语言内部优先 Plus；合并完中文以后才回退英文，避免过早返回英文。
            val supplemental = "languages.$locale.json".takeIf { it in documents }?.let(::readDictionary).orEmpty()
            supplemental + readDictionary("dict.$locale.json")
        }
        documents.toSortedMap().filterKeys { it.startsWith("Export") }.forEach { (file, root) ->
            require(root.isContainerNode) { "$file 必须是对象或数组" }
            fun collect(node: JsonNode) {
                if (node.isObject) node.fields().forEach { (key, value) ->
                    // 节点目录还包含九重天节点、中继站和接合点，必须完整收录。
                    // 仅按 SolNode 等前缀筛选会静默丢弃上游已有的有效节点。
                    val regionEntry = file == "ExportRegions.json" && node === root
                    // 奖励表以嵌套数组保存，需保留整个表供面板展开候选奖励。
                    val objectEntry = value.isObject || (file == "ExportRewards.json" && value.isArray)
                    val identityKey = regionEntry || key.startsWith("/") || REGION_KEY_PATTERN.matches(key)
                    if (objectEntry && identityKey) {
                        val normalized = normalize(key)
                        val previous = objects[normalized]
                        require(identities[normalized] == null || identities[normalized] == key || previous == value) {
                            "导出对象存在大小写冲突：${identities[normalized]} / $key"
                        }
                        identities.putIfAbsent(normalized, key)
                        if (previous == null) objects[normalized] = value.deepCopy()
                        else if (previous is ObjectNode) value.fields().forEach { (field, content) ->
                            if (!previous.has(field)) previous.set<JsonNode>(field, content)
                        }
                        if (file == "ExportRecipes.json") recipes.add(normalized)
                        if (file == "ExportRelics.json") relics.add(normalized)
                    }
                    collect(value)
                } else if (node.isArray) node.forEach(::collect)
            }
            collect(root)
        }
        supplementalObjects.putAll(objects.mapValues { it.value.deepCopy() })
        // 官方对象以数组内的 uniqueName 标识，递归遍历以同时收录电波、额外奖励和技能。
        documents.toSortedMap().filterKeys(PublicExportStore::acceptsOfficial).forEach { (file, root) ->
            val locale = if (file.endsWith(".zh.json")) "zh" else "en"
            val category = file.removePrefix("official.").removeSuffix(".$locale.json")
            require(root.isObject && root.path(category).isArray && root.path(category).size() > 0) {
                "$file 官方对象目录为空或格式错误"
            }
            fun collectOfficial(node: JsonNode) {
                if (node.isObject) {
                    val key = node["uniqueName"]?.takeIf(JsonNode::isTextual)?.asText()?.takeIf(String::isNotBlank)
                    if (key != null) {
                        val normalized = normalize(key)
                        officialIds.add(normalized)
                        val previous = officialObjects.getValue(locale)[normalized]
                        if (previous == null) officialObjects.getValue(locale)[normalized] = node.deepCopy()
                        else if (previous is ObjectNode) node.fields().forEach { (field, value) ->
                            if (missingField(previous[field])) previous.set<JsonNode>(field, value)
                        }
                        identities.putIfAbsent(normalized, key)
                        if (category == "ExportRecipes") recipes.add(normalized)
                    }
                    node.elements().forEachRemaining(::collectOfficial)
                } else if (node.isArray) node.forEach(::collectOfficial)
            }
            collectOfficial(root)
        }
        // 调用方也会直接传语言键；只借用 Plus 的关联，以官方文本替换该键的过期译文。
        supplementalObjects.forEach { (id, node) ->
            if (id in officialIds) {
                listOf("name", "description", "systemName").forEach { field ->
                    node[field]?.takeIf(JsonNode::isTextual)?.asText()
                        ?.takeIf { it.startsWith("/Lotus/Language/", ignoreCase = true) }?.let { key ->
                            officialReferences.getOrPut(normalize(key)) { mutableListOf() }
                                .add(OfficialReference(id, field))
                        }
                }
            }
        }
        // 元数据按字段合并，官方非空值优先；Plus 的语言键单独保留供缺失译文回退。
        (officialObjects.getValue("zh") + officialObjects.getValue("en")).forEach { (key, node) ->
            val primary = node.deepCopy<JsonNode>()
            if (primary is ObjectNode) objects[key]?.fields()?.forEach { (field, value) ->
                if (missingField(primary[field])) primary.set<JsonNode>(field, value)
            }
            objects[key] = primary
        }
        require(documents.getValue("ExportRegions.json").size() > 0) { "节点目录为空" }
        require(documents.getValue("ExportMissionTypes.json").size() > 0) { "任务类型目录为空" }
        fun catalogIndices(prefix: String): Map<Int, String> {
            val entries = linkedMapOf<Int, String>()
            identities.keys.filter { it.startsWith(prefix) }.forEach { key ->
                objects[key]?.get("index")?.takeIf(JsonNode::isNumber)?.asInt()?.let { entries.putIfAbsent(it, key) }
            }
            return entries
        }
        missionTypesByIndex = catalogIndices("mt_")
        factionsByIndex = catalogIndices("fc_")
        val english = linkedMapOf<String, MutableList<String>>()
        dictionaries.getValue("en").forEach { (key, value) ->
            if (value.length in 1..160) english.getOrPut(normalize(clean(value))) { mutableListOf() }.add(key)
        }
        identities.forEach { (key, _) ->
            resolve(key, "en", "name", mutableSetOf())?.let {
                english.getOrPut(normalize(clean(it))) { mutableListOf() }.add(key)
            }
        }
        byEnglish = english
        byChinese = english.values.asSequence().flatMap { it.asSequence() }.distinct().mapNotNull { key ->
            resolve(key, "zh", "name", mutableSetOf())?.let { normalize(clean(it)) to key }
        }.groupBy({ it.first }, { it.second })
        fun officialNames(locale: String): Map<String, List<String>> =
            officialObjects.getValue(locale).keys.mapNotNull { id ->
                officialField(id, "name", locale)?.let { normalize(it) to id }
            }.groupBy({ it.first }, { it.second })
        byOfficialEnglish = officialNames("en")
        byOfficialChinese = officialNames("zh")
    }

    /**
     * 按原始标识取得导出对象元数据，同时兼容可转换的 StoreItems 路径。
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

    /** 只按协议标识解析名称，不通过英文同名反查，以免借用无关物品的译名。 */
    fun nameById(key: String): String = resolve(key, "zh", "name", mutableSetOf())?.let(::clean)
        ?: key.also { onMissing(it, "zh", "name") }

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

    fun name(key: String, locale: String = "zh"): String =
        field(key, "name", locale) ?: key.also { onMissing(key, locale, "name") }

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
            val officialValues = byOfficialEnglish[normalize(clean(key))].orEmpty()
                .mapNotNull { officialField(it, field, locale) }.distinct()
            if (officialValues.size == 1) return officialValues.single()
            val candidates = byEnglish[normalize(clean(key))].orEmpty()
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
        val officialEnMatches = byOfficialEnglish[normalized].orEmpty().map { name(it) }.distinct()
        if (officialEnMatches.isNotEmpty()) return officialEnMatches
        val officialZhMatches = byOfficialChinese[normalized].orEmpty().map { name(it, "en") }.distinct()
        if (officialZhMatches.isNotEmpty()) return officialZhMatches
        val enMatches = byEnglish[normalized].orEmpty().map { name(it) }.distinct()
        if (enMatches.isNotEmpty()) return enMatches
        return byChinese[normalized].orEmpty().map { name(it, "en") }.distinct()
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
        private val REGION_KEY_PATTERN = Regex("(?:SolNode|SettlementNode|ClanNode|MT_|FC_).*", RegexOption.IGNORE_CASE)
        private val MARKUP_PATTERN = Regex("<[^>]+>")
        private fun missingField(value: JsonNode?): Boolean = value == null || value.isNull ||
                (value.isTextual && value.asText().isBlank()) || (value.isContainerNode && value.size() == 0)

        fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        fun clean(value: String): String = value.replace(MARKUP_PATTERN, "")
            .replace("|COLOR|", "").replace("|NO_COLOR|", "").trim()
    }
}