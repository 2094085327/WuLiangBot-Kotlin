package bot.wuliang.worldstate

import bot.wuliang.config.WARFRAME_ENTRATI_BOUNTIES
import bot.wuliang.config.WARFRAME_HEX_BOUNTIES
import bot.wuliang.config.WARFRAME_ZARIMAN_BOUNTIES
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

/**
 * 根据官方种子重建赏金轮换。
 * @see <a href="https://github.com/calamity-inc/wf.browse.oracle"> wf.browse.oracle</a>
 */
object BountyGenerator {
    // 与其他玩法数据共用外部资源目录；首次读取后缓存，修改文件需重启生效。
    val manifests: Map<String, JsonNode> by lazy {
        val mapper = jacksonObjectMapper()
        mapOf(
            "ZarimanSyndicate" to WARFRAME_ZARIMAN_BOUNTIES,
            "EntratiLabSyndicate" to WARFRAME_ENTRATI_BOUNTIES, "HexSyndicate" to WARFRAME_HEX_BOUNTIES
        )
            .mapValues { (_, file) -> mapper.readTree(File(file)).path("data") }
    }

    /** 保留原始游戏标识和档位配置，中文名称、挑战描述及奖励由面板统一解析。 */
    data class Bounty(val node: String, val challenge: String, val ally: String?, val tier: JsonNode)

    /** 按参考算法推进 64 位随机状态，同一种子得到相同的随机序列。 */
    class Random64(private var state: Long) {
        fun int(min: Int, max: Int): Int {
            require(max >= min)
            // 只有一个候选时不消耗随机状态，否则后续赏金会与参考结果错位。
            if (max == min) return min
            // 保留 Long 运算溢出的回绕行为，不能替换为普通随机函数。
            state = 0x5851F42D4C957F2DL * state + 0x14057B7EF767814FL
            return min + (((state ushr 32) and 0x3FFFFFFFL) % (max.toLong() - min + 1)).toInt()
        }

        fun <T> shuffle(list: MutableList<T>) {
            for (size in list.size downTo 2) java.util.Collections.swap(list, int(1, size) - 1, size - 1)
        }
    }

    /** 截去指定难度或挑战标记，供跨档位去重；Normal 按参考算法保留。 */
    fun normalize(path: String): String {
        val name = path.substringAfterLast('/')
        for (token in listOf("Easy", "VeryHard", "Hard", "Challenge")) {
            val pos = name.indexOf(token)
            if (pos >= 0) return name.substring(0, pos)
        }
        return name
    }

    /** 每个集团独立使用自己的种子；hex 启用六人组的节点和协助角色规则。 */
    fun compute(manifest: JsonNode, seed: Long, hex: Boolean = false): List<Bounty> {
        val random = Random64(seed)
        val nodes = manifest.path("ChallengeMissionNodes").map { it.asText() }.toMutableList()
        require(nodes.isNotEmpty())
        random.shuffle(nodes)
        val fallback = if (hex) listOf("SolNode852", "SolNode850", "SolNode854") else nodes.toList()
        val allies = if (hex) mutableListOf("Amir", "Aoi", "Arthur", "Eleanor", "Lettie", "Quincy") else mutableListOf()
        if (hex) {
            // 普通赏金节点数量需匹配六名角色；额外档位稍后从备用节点中选择。
            if (allies.size < nodes.size) {
                val candidates = mutableListOf("SolNode851", "SolNode852", "SolNode853", "SolNode854")
                random.shuffle(candidates)
                while (allies.size < nodes.size && candidates.isNotEmpty()) nodes.remove(candidates.removeAt(0))
            }
            random.shuffle(allies)
            // 按参考算法交换冲突角色，不额外重抽，避免改变随机序列。
            val exclusions = mapOf(
                "SolNode856" to "Arthur", "SolNode852" to "Lettie", "SolNode851" to "Amir",
                "SolNode850" to "Eleanor", "SolNode853" to "Aoi", "SolNode854" to "Quincy"
            )
            for (i in 0 until minOf(allies.size, nodes.size)) {
                if (allies[i] == exclusions[nodes[i]]) java.util.Collections.swap(allies, i, (i + 1) % allies.size)
            }
        }
        // 种子奇偶决定阵营标识，仅用于筛选配置中有阵营限制的挑战。
        val faction = if (seed and 1L == 0L) "FC_GRINEER" else "FC_CORPUS"
        val seen = mutableSetOf<String>()
        return manifest.path("ChallengeMissionDifficultyTiers").mapIndexed { i, tier ->
            // 六人组的终幕者档位使用备用节点，并在返回结果时留空协助角色。
            val node = nodes.getOrNull(i) ?: fallback[random.int(1, fallback.size) - 1]
            val pool = tier.path("challenges").filter {
                (it.path("Node").asText() == "" || it.path("Node").asText() == node) &&
                        (it.path("CheckFaction").asInt() == 0 || it.path("Faction").asText() == faction)
            }.flatMap { it.path("challenges").map(JsonNode::asText) }
            // 优先避开已出现的挑战类型；全部重复时复用原池，保留候选顺序。
            val fresh = pool.filter { normalize(it) !in seen }
            val candidates = fresh.ifEmpty { pool }
            require(candidates.isNotEmpty()) { "赏金配置不匹配：$node" }
            val challenge = candidates[random.int(1, candidates.size) - 1]
            seen.add(normalize(challenge))
            Bounty(node, challenge, if (i < nodes.size) allies.getOrNull(i) else null, tier)
        }
    }
}