package bot.wuliang.jacksonUtil

import com.fasterxml.jackson.databind.JsonNode
import java.util.function.Function

/**
 * JsonNode操作函数， 可以用于获取JsonNode中的某个节点
 */
fun interface JsonOperateFunction : Function<JsonNode, JsonNode> {
    companion object {
        fun fromPath(path: JsonPath): JsonOperateFunction {
            return JsonOperateFunction { node: JsonNode ->
                var nodeVar = node
                for (s in path) {
                    nodeVar = nodeVar[s]
                }
                nodeVar
            }
        }

        fun emptyOperate(): JsonOperateFunction {
            return JsonOperateFunction { node: JsonNode -> node }
        }
    }
}