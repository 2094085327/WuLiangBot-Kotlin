package bot.wuliang.arbitration

import bot.wuliang.config.WarframeProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

/** 任务评级和日志熟练度共用的本地节点基准。启动读取一次。 */
@Service
class ArbitrationBaselines(properties: WarframeProperties, mapper: ObjectMapper) {
    final val vitusPerHour: Map<String, Double>
    final val kpm: Map<String, Double>

    init {
        val data = Files.newInputStream(Path.of(properties.arbitration.baselinesFile)).use { mapper.readTree(it) }
        vitusPerHour = readNodes(data.path("baselines"), "baselines", allowZero = true)
        kpm = readNodes(data.path("kpm"), "kpm", allowZero = false)
    }

    private fun readNodes(nodes: JsonNode, field: String, allowZero: Boolean): Map<String, Double> {
        require(nodes.isObject && !nodes.isEmpty && nodes.size() <= 2000) {
            "仲裁基准文件的${field}需包含非空节点对象，最多2000项。"
        }
        return nodes.fields().asSequence().associate { (id, value) ->
            val number = value.asDouble()
            require(id.matches(NODE_ID) && value.isNumber && number.isFinite() && (if (allowZero) number >= 0 else number > 0)) {
                "仲裁基准文件中节点${id}的${field}无效。"
            }
            id to number
        }
    }

    private companion object {
        val NODE_ID = Regex("[A-Za-z][A-Za-z0-9]*")
    }
}