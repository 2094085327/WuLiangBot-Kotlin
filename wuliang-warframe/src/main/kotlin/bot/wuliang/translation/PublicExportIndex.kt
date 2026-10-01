package bot.wuliang.translation

import bot.wuliang.moudles.Info
import bot.wuliang.moudles.Nodes
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.*

/** 结合导出对象与本地化字典解析游戏标识；对象路径需要先解析元数据，不能一律当作字典键。 */
class PublicExportIndex(documents: Map<String, JsonNode>) {
    private val dictionaries: Map<String, Map<String, String>>
    private val objects = linkedMapOf<String, JsonNode>()
    private val identities = linkedMapOf<String, String>()
    private val recipes = mutableSetOf<String>()
    private val relics = mutableSetOf<String>()
    private val byEnglish: Map<String, List<String>>
    private val byChinese: Map<String, List<String>>
    var onMissing: (String, String, String) -> Unit = { _, _, _ -> }

    init {
        require(documents.keys.containsAll(PublicExportStore.requiredFiles)) { "词库缺少必要文件" }
        PublicExportStore.requiredFiles.filter { it.startsWith("Export") }.forEach { file ->
            require(
                documents.getValue(file).isObject && documents.getValue(file).size() > 0
            ) { "$file 目录为空或格式错误" }
        }
        dictionaries = listOf("en", "zh").associateWith { locale ->
            val root = documents.getValue("dict.$locale.json")
            require(root.isObject && root.size() > 0) { "$locale 字典为空或格式错误" }
            val result = linkedMapOf<String, String>()
            root.fields().forEach { (key, value) ->
                require(value.isTextual) { "$locale 字典包含非文本值：$key" }
                val old = result.put(normalize(key), value.asText())
                require(old == null || old == value.asText()) { "$locale 字典存在大小写冲突：$key" }
            }
            result
        }
        documents.toSortedMap().filterKeys { it.startsWith("Export") }.forEach { (file, root) ->
            require(root.isContainerNode) { "$file 必须是对象或数组" }
            fun collect(node: JsonNode) {
                if (node.isObject) node.fields().forEach { (key, value) ->
                    // 节点目录还包含九重天节点、中继站和接合点，必须完整收录。
                    // 仅按 SolNode 等前缀筛选会静默丢弃上游已有的有效节点。
                    val regionEntry = file == "ExportRegions.json" && node === root
                    if (value.isObject && (regionEntry || key.startsWith("/") || Regex(
                            "(?:SolNode|SettlementNode|ClanNode|MT_|FC_).*",
                            RegexOption.IGNORE_CASE
                        ).matches(key))
                    ) {
                        val normalized = normalize(key)
                        val previous = objects[normalized]
                        require(identities[normalized] == null || identities[normalized] == key || previous == value) {
                            "导出对象存在大小写冲突：${identities[normalized]} / $key"
                        }
                        identities.putIfAbsent(normalized, key)
                        if (previous == null) objects[normalized] = value.deepCopy<JsonNode>()
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
        require(documents.getValue("ExportRegions.json").size() > 0) { "节点目录为空" }
        require(documents.getValue("ExportMissionTypes.json").size() > 0) { "任务类型目录为空" }
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
        byChinese = english.values.flatten().distinct().mapNotNull { key ->
            resolve(key, "zh", "name", mutableSetOf())?.let { normalize(clean(it)) to key }
        }.groupBy({ it.first }, { it.second })
    }

    /**
     * 按原始标识取得导出对象元数据，同时兼容可转换的 StoreItems 路径。
     */
    fun metadata(key: String): JsonNode? = objects[objectKey(key)]

    /** 只按协议标识解析名称，不通过英文同名反查，以免借用无关物品的译名。 */
    fun nameById(key: String): String = resolve(key, "zh", "name", mutableSetOf())?.let(::clean)
        ?: key.also { onMissing(it, "zh", "name") }

    /**
     * 把 Market 武器 slug 对应到进化适配器对象路径；只有唯一匹配时才返回结果。
     */
    fun incarnonAdapter(slug: String): String? {
        val basename = slug.replace("_", "") + "IncarnonUnlocker"
        return identities.values.filter {
            it.startsWith("/Lotus/Types/Items/MiscItems/IncarnonAdapters/") &&
                    it.substringAfterLast('/').equals(basename, ignoreCase = true)
        }.singleOrNull()
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
        val label = node["name"]?.asText()?.let(::name) ?: id
        val system = node["systemName"]?.asText()?.let(::name)
        return Nodes(
            name = if (system.isNullOrBlank()) label else "$label ($system)",
            faction = if (id.equals("SolNode450", ignoreCase = true)) {
                factionName("/Lotus/Language/Missions/DualDefenseCompare")
            } else if (node.has("secondaryFaction") || node.has("secondaryFactionIndex")) {
                factionName("/Lotus/Language/Missions/MissionName_Crossfire")
            } else node["faction"]?.asText()?.let(factionName),
            type = node["missionName"]?.asText()?.let(::name) ?: node["missionType"]?.asText()?.let(::name)
        )
    }

    /** 按完整中英文名称反查，返回去重后的候选译名，由调用方处理多个结果。 */
    fun translateQuery(query: String): List<String> {
        val normalized = normalize(clean(query))
        val enMatches = byEnglish[normalized].orEmpty().map { name(it) }.distinct()
        if (enMatches.isNotEmpty()) return enMatches
        return byChinese[normalized].orEmpty().map { name(it, "en") }.distinct()
    }

    /**
     * 递归解析字典键、对象字段及父对象，使用 seen 防止循环引用。
     * 蓝图和遗物按模板拼接名称，挑战说明按 required 或 requiredCount 替换数量占位符。
     */
    private fun resolve(key: String, locale: String, field: String, seen: MutableSet<String>): String? {
        val normalized = objectKey(key)
        if (!seen.add("$normalized:$field")) return null
        if (field == "name") dictionary(key, locale)?.let { return it }
        val item = objects[normalized] ?: return null
        if (field == "name" && normalized in recipes) {
            val result = item["resultType"]?.asText() ?: return null
            val resultName = resolve(result, locale, "name", seen.toMutableSet()) ?: result
            return (dictionary("/Lotus/Language/Items/BlueprintAndItem", locale)
                ?: "|ITEM| Blueprint").replace("|ITEM|", resultName)
        }
        if (field == "name" && normalized in relics) {
            val era = item["era"]?.asText() ?: return null
            val category = item["category"]?.asText() ?: return null
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
        if (text != null) {
            val localized = dictionary(text, locale) ?: if (text.startsWith("/")) {
                resolve(text, locale, "name", seen.toMutableSet()) ?: text.also { onMissing(it, locale, field) }
            } else text
            val required = item["required"] ?: item["requiredCount"]
            return if (required?.isNumber == true) localized.replace("|COUNT|", required.asText()) else localized
        }
        return item["parentName"]?.asText()?.let { resolve(it, locale, field, seen) }
    }

    /**
     * 中文缺失时回退同一键的英文文本，并记录缺失；不读取已退役的旧词库。
     */
    private fun dictionary(key: String, locale: String): String? {
        val normalized = normalize(key)
        dictionaries.getValue(locale)[normalized]?.takeIf { it.isNotBlank() }?.let { return it }
        if (locale == "zh") dictionaries.getValue("en")[normalized]?.let {
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
        if (objects.containsKey(normalized)) return normalized
        return if (normalized.startsWith("/lotus/storeitems/")) normalized.replaceFirst(
            "/lotus/storeitems/",
            "/lotus/"
        ) else normalized
    }

    companion object {
        fun normalize(value: String): String = value.lowercase(Locale.ROOT)
        fun clean(value: String): String = value.replace(Regex("<[^>]+>"), "")
            .replace("|COLOR|", "").replace("|NO_COLOR|", "").trim()
    }
}