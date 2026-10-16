package bot.wuliang.worldstate.presentation

import bot.wuliang.worldstate.model.WorldEntry
import bot.wuliang.worldstate.model.WorldSection
import bot.wuliang.worldstate.parser.BountyGenerator
import bot.wuliang.worldstate.parser.items
import bot.wuliang.worldstate.parser.text
import bot.wuliang.worldstate.source.BountyManifestSource
import com.fasterxml.jackson.databind.JsonNode

/** 赏金重建与面板展示；本地配置读取由数据源负责，种子算法只接收已加载的配置。 */
internal class BountySections(private val display: WorldStateDisplay) {
    private val syndicateNames = mapOf(
        "CetusSyndicate" to "希图斯", "SolarisSyndicate" to "索拉里斯", "EntratiSyndicate" to "英择谛",
        "ZarimanSyndicate" to "坚守者", "EntratiLabSyndicate" to "科维兽", "HexSyndicate" to "六人组"
    )

    /** 将当前集团记录中的经典赏金转换为面板条目，展开候选奖励池并附上说明。 */
    fun classicBounties(syndicates: Map<String, JsonNode>): WorldSection {
        val entries = listOf("CetusSyndicate", "SolarisSyndicate", "EntratiSyndicate").flatMap { tag ->
            val group = syndicates[tag] ?: return@flatMap emptyList()
            group.items("Jobs").mapIndexed { i, job ->
                WorldEntry(
                    "$tag-$i",
                    if (job.has("jobType")) display.name(job.text("jobType")) else if (job.path("isVault")
                            .asBoolean()
                    ) "隔离库赏金" else "赏金 ${i + 1}",
                    syndicateNames.getValue(tag),
                    "等级 ${job.path("minEnemyLevel").asInt()}–${job.path("maxEnemyLevel").asInt()}",
                    listOf("${job.path("xpAmounts").size()} 阶段", "段位要求 ${job.path("masteryReq").asInt()}") +
                            (if (job.path("endless").asBoolean()) listOf("无尽赏金") else emptyList()),
                    display.rewardPool(job.text("rewards")),
                    display.date(group["Activation"]),
                    display.date(group["Expiry"])
                )
            }
        }
        return WorldSection("bounties", "开放世界赏金", entries, "奖励展示为可能获得的奖励池，并非一次任务全部发放。")
    }

    /** 使用当前集团种子和本地配置重建赏金，单个集团失败时保留其他结果并附上失败说明。 */
    fun challengeBounties(syndicates: Map<String, JsonNode>): WorldSection {
        val errors = mutableListOf<String>()
        val entries = BountyManifestSource.manifests.flatMap { (tag, manifest) ->
            val record = syndicates[tag] ?: return@flatMap emptyList()
            if (!record.path("Seed").isIntegralNumber) {
                errors.add("${syndicateNames[tag]}缺少有效种子")
                return@flatMap emptyList()
            }
            // 单个集团重建失败时保留其他集团结果，并附上失败说明。
            runCatching {
                BountyGenerator.compute(manifest, record.path("Seed").asLong(), tag == "HexSyndicate")
                    .mapIndexed { i, bounty ->
                        val tier = bounty.tier
                        val xp = tier.path("xpAmount").asInt()
                        // 扎里曼配置指定了结算物品类型，不能将物品数量统一标成声望。
                        val payout =
                            if (manifest.has("XpItemType")) "${display.name(manifest.text("XpItemType"))} ×$xp" else "声望 $xp"
                        val ally = bounty.ally?.let {
                            mapOf(
                                "Amir" to "阿米尔",
                                "Aoi" to "碧",
                                "Arthur" to "亚瑟",
                                "Eleanor" to "埃莉诺",
                                "Lettie" to "莱蒂",
                                "Quincy" to "昆西"
                            )[it] ?: it
                        }
                        WorldEntry(
                            "$tag-$i",
                            display.node(bounty.node),
                            syndicateNames.getValue(tag),
                            "${if (tag == "HexSyndicate" && i == 6) "终幕者赏金" else "赏金 ${i + 1}"} · ${
                                display.mission(
                                    bounty.node
                                )
                            }",
                            listOf(
                                display.description(bounty.challenge).replace("|ALLY|", ally.orEmpty()),
                                payout,
                                "敌人等级修正 +${tier.path("enemyLevelModifier").asInt()}"
                            ) +
                                    listOfNotNull(
                                        ally?.let { "协助：$it" },
                                        if (tier.path("chemistryAmount")
                                                .asInt() > 0
                                        ) "好感度 ${tier.path("chemistryAmount").asInt()}" else null
                                    ),
                            display.rewardPool(tier.text("rewards")),
                            display.date(record["Activation"]),
                            display.date(record["Expiry"])
                        )
                    }
            }.getOrElse { errors.add("${syndicateNames[tag]}配置不匹配，暂时无法重建"); emptyList() }
        }
        return WorldSection(
            "challenge-bounties", "赏金轮换", entries,
            (listOf("根据官方种子与固定版本配置重建，尚未完成游戏内逐项核验；等级仅显示配置修正量。") + errors).joinToString(
                " "
            )
        )
    }
}