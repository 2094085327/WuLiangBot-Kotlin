package bot.wuliang.parser.interfaces

import com.fasterxml.jackson.databind.JsonNode

interface TransformFunction {
    fun transformFunction(thToTransForm: JsonNode?, imageUrls: Map<String, String>, blueprints: JsonNode): Any
}