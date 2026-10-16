package bot.wuliang.worldstate.source

import bot.wuliang.config.WARFRAME_ENTRATI_BOUNTIES
import bot.wuliang.config.WARFRAME_HEX_BOUNTIES
import bot.wuliang.config.WARFRAME_ZARIMAN_BOUNTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

/** 本地赏金配置首次读取后复用；文件更新需重启，解析算法本身不承担文件访问。 */
internal object BountyManifestSource {
    val manifests: Map<String, JsonNode> by lazy {
        val mapper = jacksonObjectMapper()
        mapOf(
            "ZarimanSyndicate" to WARFRAME_ZARIMAN_BOUNTIES,
            "EntratiLabSyndicate" to WARFRAME_ENTRATI_BOUNTIES, "HexSyndicate" to WARFRAME_HEX_BOUNTIES
        )
            .mapValues { (_, path) -> mapper.readTree(File(path)).path("data") }
    }
}
