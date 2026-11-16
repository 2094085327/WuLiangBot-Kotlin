package bot.wuliang.translation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode

/** 常驻目录只保留翻译、节点、奖励和紫卡数值实际读取的字段。 */
internal object ExportObjectProjection {
    private val fields = setOf(
        "name", "description", "systemName", "parentName", "resultType", "era", "category",
        "required", "requiredCount", "index", "secondaryFaction", "secondaryFactionIndex",
        "factionIndex", "faction", "missionIndex", "missionName", "missionType",
    )
    private val entryFields = setOf("tag", "prefixTag", "canBeBuff", "canBeCurse")
    private val valueFields = setOf("value", "locTag", "reverseValueSymbol")

    /** 原树仍供递归发现嵌套标识；新目录不持有未使用的原始子树。 */
    fun project(node: JsonNode, id: String, rewards: Boolean = false): JsonNode {
        if (node.isArray) return JsonNodeFactory.instance.arrayNode().also { result ->
            node.forEach { result.add(project(it, id, rewards)) }
        }
        if (node !is ObjectNode) return node
        return select(node, fields).also { result ->
            if (rewards) {
                node["type"]?.let { result.set<JsonNode>("type", it.deepCopy()) }
                node["itemCount"]?.let { result.set<JsonNode>("itemCount", it.deepCopy()) }
            }
            if (id.startsWith("/lotus/upgrades/mods/randomized/")) {
                node["upgradeEntries"]?.let { entries ->
                    result.set<JsonNode>(
                        "upgradeEntries", if (!entries.isArray) entries.deepCopy() else
                            JsonNodeFactory.instance.arrayNode().also { projected ->
                                entries.forEach { entry ->
                                    val value = select(entry, entryFields)
                                    entry["upgradeValues"]?.let { values ->
                                        value.set<JsonNode>(
                                            "upgradeValues", if (!values.isArray) values.deepCopy() else
                                                JsonNodeFactory.instance.arrayNode().also { pool ->
                                                    values.forEach { pool.add(select(it, valueFields)) }
                                                })
                                    }
                                    projected.add(value)
                                }
                            })
                }
            }
        }
    }

    /** 字段中保留原有空值和类型，避免改变补缺与合法性判断。 */
    private fun select(node: JsonNode, names: Set<String>): ObjectNode =
        JsonNodeFactory.instance.objectNode().also { result ->
            node.fields().forEach { (name, value) ->
                if (name in names) result.set<JsonNode>(name, value.deepCopy())
            }
        }
}