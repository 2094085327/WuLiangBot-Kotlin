package bot.wuliang.worldstate.source

import bot.wuliang.config.WARFRAME_ENTRATI_BOUNTIES
import bot.wuliang.config.WARFRAME_HEX_BOUNTIES
import bot.wuliang.config.WARFRAME_ZARIMAN_BOUNTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

/** 本地赏金配置首次读取后复用；文件更新需重启，解析算法本身不承担文件访问。 */
internal object BountyManifestSource {
    private val mapper = jacksonObjectMapper()
    private val manifests = mapOf(
        "ZarimanSyndicate" to WARFRAME_ZARIMAN_BOUNTIES,
        "EntratiLabSyndicate" to WARFRAME_ENTRATI_BOUNTIES,
        "HexSyndicate" to WARFRAME_HEX_BOUNTIES,
    ).mapValues { (_, path) -> lazy { mapper.readTree(File(path)).path("data") } }

    val tags: List<String> = manifests.keys.toList()

    /** 按集团独立加载，某份配置缺失不会阻断其他集团的查询。 */
    fun manifest(tag: String): JsonNode = manifests.getValue(tag).value
}
