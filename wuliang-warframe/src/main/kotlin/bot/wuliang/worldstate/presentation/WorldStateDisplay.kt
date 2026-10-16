package bot.wuliang.worldstate.presentation

import bot.wuliang.translation.PublicExportIndex
import bot.wuliang.worldstate.parser.*
import com.fasterxml.jackson.databind.JsonNode

/** 一次面板组装共用的词库与奖励展示规则。 */
internal class WorldStateDisplay(
    val index: PublicExportIndex,
    val nameLookup: (String) -> String = index::name,
    val descriptionLookup: (String) -> String? = index::description,
    val conquestLookup: (String) -> String = index::name,
    val calendarLookup: (String) -> String = index::name,
) {
    /** 查询物品或协议标识 [key] 的展示名称，空标识使用未提供提示。 */
    fun name(key: String): String = if (key.isBlank()) "未提供" else nameLookup(key)
    /** 读取节点名称；没有已收录的节点名称时保留原始标识 [id]。 */
    fun node(id: String) = index.node(id).name ?: id
    /** 读取节点 [id] 的默认任务类型；没有类型时返回空字符串。 */
    fun mission(id: String) = index.node(id).type.orEmpty()

    /** 将官方日期节点 [value] 转换为 Unix 毫秒；缺项或格式错误时返回 null。 */
    fun date(value: JsonNode?): Long? = WorldStateTime.millis(value)

    /** 读取 [field] 数组中在 [now] 时刻生效的官方记录。 */
    fun current(root: JsonNode, field: String, now: Long) = root.path(field).activeRecords(now)

    /** 查询描述并替换任务数量占位符，清理颜色标记；缺少描述时回退名称。 */
    fun description(key: String): String {
        val count = index.metadata(key)?.path("requiredCount")?.asText()
        val text = descriptionLookup(key) ?: name(key)
        return (if (count != null) text.replace("|COUNT|", count) else text)
            .replace("|OPEN_COLOR|", "").replace("|CLOSE_COLOR|", "")
    }

    /** 先解析官方奖励对象，再生成适用于面板的物品、数量和现金文本。 */
    fun rewardObject(value: JsonNode): List<String> = reward(RewardParser.parse(value))

    /** 将结构化奖励转换为面板文本，保留物品数量且不表示掉落概率。 */
    fun reward(value: RewardState): List<String> = buildList {
        addAll(value.items.map(::name))
        addAll(value.countedItems.map { "${name(it.item)} ×${it.count}" })
        if (value.credits > 0) add("现金 ×${value.credits}")
    }

    /** 展开嵌套奖励数组并去重，只展示候选奖励，不表示单次全部发放或掉落概率。 */
    fun rewardPool(path: String): List<String> {
        if (path.isBlank()) return emptyList()
        val metadata = index.metadata(path) ?: return listOf("奖励表尚未收录：$path")
        val entries = mutableListOf<String>()
        /** 递归展开嵌套奖励数组，将具有物品类型的条目加入候选奖励列表。 */
        fun visit(value: JsonNode) {
            if (value.isArray) value.forEach(::visit)
            else if (value.isObject && value.has("type")) {
                entries.add("${name(value.text("type"))} ×${value.path("itemCount").asInt(1)}")
            }
        }
        visit(metadata)
        return entries.distinct()
    }

}