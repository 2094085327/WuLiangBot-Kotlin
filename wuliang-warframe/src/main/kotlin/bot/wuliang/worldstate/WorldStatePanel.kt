package bot.wuliang.worldstate

import bot.wuliang.translation.PublicExportIndex
import bot.wuliang.translation.WorldStateCatalog
import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

data class WorldEntry(
    val id: String,
    val title: String,
    val category: String = "",
    val subtitle: String = "",
    val details: List<String> = emptyList(),
    val rewards: List<String> = emptyList(),
    val activation: Long? = null,
    val expiry: Long? = null,
    val progress: Double? = null,
)

data class WorldSection(val id: String, val title: String, val entries: List<WorldEntry>, val note: String? = null)
data class WorldPanel(
    val fetchedAt: Long,
    val worldTime: Long,
    val serverTime: Long,
    val stale: Boolean,
    val refreshAfterSeconds: Int = 300,
    val cycles: List<WorldEntry>,
    val sections: List<WorldSection>,
)

/** 基于一份官方快照和已应用词库生成只读面板，不发起网络请求或写入 Redis。 */
class WorldStatePanel(
    private val index: PublicExportIndex,
    private val nameLookup: (String) -> String = index::name,
    private val descriptionLookup: (String) -> String? = index::description,
    private val conquestLookup: (String) -> String = index::name,
    private val calendarLookup: (String) -> String = index::name,
) {
    private fun name(key: String): String = if (key.isBlank()) "未提供" else nameLookup(key)
    private fun JsonNode.text(key: String) = path(key).asText("")
    private fun JsonNode.items(key: String) = path(key).takeIf { it.isArray }?.toList().orEmpty()
    private fun node(id: String) = index.node(id).name ?: id
    private fun mission(id: String) = index.node(id).type.orEmpty()

    private fun date(value: JsonNode?): Long? = WorldStateTime.millis(value)

    private fun active(value: JsonNode, now: Long): Boolean =
        (date(value["Activation"]) ?: Long.MIN_VALUE) <= now && (date(value["Expiry"]) ?: Long.MAX_VALUE) > now

    private fun current(root: JsonNode, field: String, now: Long) = root.items(field).filter { active(it, now) }

    // 同集团有效记录重叠时取开始时间最新的一条，避免同时展示两轮赏金。
    private fun currentSyndicates(root: JsonNode, now: Long) = current(root, "SyndicateMissions", now)
        .groupBy { it.text("Tag") }.mapValues { (_, entries) -> entries.maxBy { date(it["Activation"]) ?: 0 } }

    private fun description(key: String): String {
        val count = index.metadata(key)?.path("requiredCount")?.asText()
        val text = descriptionLookup(key) ?: name(key)
        return (if (count != null) text.replace("|COUNT|", count) else text)
            .replace("|OPEN_COLOR|", "").replace("|CLOSE_COLOR|", "")
    }

    private fun rewardObject(value: JsonNode): List<String> = buildList {
        addAll(value.items("items").map { name(it.asText()) })
        addAll(value.items("countedItems").map { "${name(it.text("ItemType"))} ×${it.path("ItemCount").asInt()}" })
        if (value.path("credits").asInt() > 0) add("现金 ×${value.path("credits").asInt()}")
    }

    /** 展开嵌套奖励数组并去重，只展示候选奖励，不表示单次全部发放或掉落概率。 */
    private fun rewardPool(path: String): List<String> {
        if (path.isBlank()) return emptyList()
        val metadata = index.metadata(path) ?: return listOf("奖励表尚未收录：$path")
        val entries = mutableListOf<String>()
        fun visit(value: JsonNode) {
            if (value.isArray) value.forEach(::visit)
            else if (value.isObject && value.has("type")) {
                entries.add("${name(value.text("type"))} ×${value.path("itemCount").asInt(1)}")
            }
        }
        visit(metadata)
        return entries.distinct()
    }

    fun parse(snapshot: WorldSnapshot, now: Long = System.currentTimeMillis()): WorldPanel {
        val root = snapshot.data
        val syndicates = currentSyndicates(root, now)
        val sections = listOf(
            fissures(root, now), classicBounties(syndicates), challengeBounties(syndicates),
            nightwave(root, now), sorties(root, now), weekly(root, now),
            events(root, now), traders(root, now, false), traders(root, now, true),
            traders(root, now, true, "EvergreenManifest"),
            invasions(root), deals(root, now),
        )
        return WorldPanel(
            snapshot.fetchedAt, root.path("Time").asLong() * 1000, now,
            snapshot.stale || now - root.path("Time").asLong() * 1000 > 600_000,
            cycles = WorldStateCycles.parse(root, now), sections = sections
        )
    }

    private fun fissures(root: JsonNode, now: Long): WorldSection {
        val entries = listOf("ActiveMissions", "VoidStorms").flatMap { field ->
            current(root, field, now).map { v ->
                val id = v.text("Node")
                val tier = v.text(if (field == "VoidStorms") "ActiveMissionTier" else "Modifier")
                val label = mapOf(
                    "VoidT1" to "古纪",
                    "VoidT2" to "前纪",
                    "VoidT3" to "中纪",
                    "VoidT4" to "后纪",
                    "VoidT5" to "安魂",
                    "VoidT6" to "全能"
                )[tier] ?: tier
                WorldEntry(
                    v.path("_id").text("\$oid"), node(id),
                    if (field == "VoidStorms") "九重天" else if (v.path("Hard").asBoolean()) "钢铁之路" else "普通",
                    "$label · ${if (v.has("MissionType")) name(v.text("MissionType")) else mission(id)}",
                    activation = date(v["Activation"]), expiry = date(v["Expiry"])
                )
            }
        }.sortedBy { it.expiry }
        return WorldSection("fissures", "虚空裂缝", entries)
    }

    private val syndicateNames = mapOf(
        "CetusSyndicate" to "希图斯", "SolarisSyndicate" to "索拉里斯", "EntratiSyndicate" to "英择谛",
        "ZarimanSyndicate" to "坚守者", "EntratiLabSyndicate" to "科维兽", "HexSyndicate" to "六人组"
    )

    private fun classicBounties(syndicates: Map<String, JsonNode>): WorldSection {
        val entries = listOf("CetusSyndicate", "SolarisSyndicate", "EntratiSyndicate").flatMap { tag ->
            val group = syndicates[tag] ?: return@flatMap emptyList()
            group.items("Jobs").mapIndexed { i, job ->
                WorldEntry(
                    "$tag-$i",
                    if (job.has("jobType")) name(job.text("jobType")) else if (job.path("isVault")
                            .asBoolean()
                    ) "隔离库赏金" else "赏金 ${i + 1}",
                    syndicateNames.getValue(tag),
                    "等级 ${job.path("minEnemyLevel").asInt()}–${job.path("maxEnemyLevel").asInt()}",
                    listOf("${job.path("xpAmounts").size()} 阶段", "段位要求 ${job.path("masteryReq").asInt()}") +
                            (if (job.path("endless").asBoolean()) listOf("无尽赏金") else emptyList()),
                    rewardPool(job.text("rewards")),
                    date(group["Activation"]),
                    date(group["Expiry"])
                )
            }
        }
        return WorldSection("bounties", "开放世界赏金", entries, "奖励展示为可能获得的奖励池，并非一次任务全部发放。")
    }

    // 仅使用当前有效记录中的官方种子，不根据过期记录预测下一轮。
    private fun challengeBounties(syndicates: Map<String, JsonNode>): WorldSection {
        val errors = mutableListOf<String>()
        val entries = BountyGenerator.manifests.flatMap { (tag, manifest) ->
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
                            if (manifest.has("XpItemType")) "${name(manifest.text("XpItemType"))} ×$xp" else "声望 $xp"
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
                            "$tag-$i", node(bounty.node), syndicateNames.getValue(tag),
                            "${if (tag == "HexSyndicate" && i == 6) "终幕者赏金" else "赏金 ${i + 1}"} · ${
                                mission(
                                    bounty.node
                                )
                            }",
                            listOf(
                                description(bounty.challenge).replace("|ALLY|", ally.orEmpty()),
                                payout,
                                "敌人等级修正 +${tier.path("enemyLevelModifier").asInt()}"
                            ) +
                                    listOfNotNull(
                                        ally?.let { "协助：$it" },
                                        if (tier.path("chemistryAmount")
                                                .asInt() > 0
                                        ) "好感度 ${tier.path("chemistryAmount").asInt()}" else null
                                    ),
                            rewardPool(tier.text("rewards")), date(record["Activation"]), date(record["Expiry"])
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

    private fun nightwave(root: JsonNode, now: Long): WorldSection = WorldSection(
        "nightwave", "午夜电波",
        current(root.path("SeasonInfo"), "ActiveChallenges", now).map { v ->
            val key = v.text("Challenge")
            val kind = if (v.path("Daily").asBoolean()) "每日" else if (key.contains("Hard")) "精英每周" else "每周"
            WorldEntry(
                v.path("_id").text("\$oid"), name(key), kind, details = listOf(description(key)),
                activation = date(v["Activation"]), expiry = date(v["Expiry"])
            )
        })

    private fun sorties(root: JsonNode, now: Long): WorldSection = WorldSection(
        "sorties", "突击与执刑官",
        listOf("Sorties", "LiteSorties").flatMap { field ->
            current(root, field, now).flatMap { v ->
                v.items(if (field == "Sorties") "Variants" else "Missions").mapIndexed { i, m ->
                    WorldEntry(
                        "$field-$i", node(m.text("node")), if (field == "Sorties") "每日突击" else "执刑官猎杀",
                        "任务 ${i + 1} · ${name(m.text("missionType"))}",
                        listOf(
                            name(
                                WorldStateCatalog.bosses[v.text("Boss")]?.first ?: v.text("Boss")
                            )
                        ) + if (m.has("modifierType")) listOf(
                            name(m.text("modifierType")),
                            description(m.text("modifierType"))
                        ) else emptyList(),
                        activation = date(v["Activation"]), expiry = date(v["Expiry"])
                    )
                }
            }
        })

    private fun weekly(root: JsonNode, now: Long): WorldSection {
        val entries = mutableListOf<WorldEntry>()
        current(root, "EndlessXpSchedule", now).maxByOrNull { date(it["Activation"]) ?: 0 }?.let { circuit ->
            circuit.items("CategoryChoices").forEach { c ->
                entries.add(
                    WorldEntry(
                        c.text("Category"),
                        if (c.text("Category") == "EXC_HARD") "钢铁之路回廊" else "普通回廊",
                        "回廊",
                        details = c.items("Choices").map {
                            val choice = it.asText()
                            // 钢铁回廊选项是协议中的短标识，需先映射为灵化适配器路径再翻译。
                            name(
                                if (c.text("Category") == "EXC_HARD") index.incarnonAdapter(choice)
                                    ?: choice else choice
                            )
                        },
                        activation = date(circuit["Activation"]),
                        expiry = date(circuit["Expiry"])
                    )
                )
            }
        }
        current(root, "Conquests", now).forEach { c ->
            c.items("Missions").forEachIndexed { i, m ->
                entries.add(
                    WorldEntry(
                        "${c.text("Type")}-${i}",
                        name(m.text("missionType")),
                        if (c.text("Type") == "CT_HEX") "时光科研" else "深层科研",
                        name(m.text("faction")),
                        m.items("difficulties").flatMap { d ->
                            listOf("${if (d.text("type") == "CD_HARD") "精英" else "普通"}：${conquestLookup(d.text("deviation"))}") + d.items(
                                "risks"
                            ).map { conquestLookup(it.asText()) }
                        },
                        activation = date(c["Activation"]),
                        expiry = date(c["Expiry"])
                    )
                )
            }
        }
        current(root, "KnownCalendarSeasons", now).forEach { season ->
            season.items("Days").forEach { day ->
                val dayNumber = day.path("day").asInt()
                val calendarDate = runCatching {
                    LocalDate.ofYearDay(1999, dayNumber).let { "${it.monthValue} 月 ${it.dayOfMonth} 日" }
                }.getOrDefault("第 $dayNumber 天")
                entries.add(
                    WorldEntry(
                        "calendar-$dayNumber",
                        "1999 日历 · $calendarDate",
                        "1999 日历",
                        details = day.items("events").flatMap { event ->
                            listOfNotNull(
                                event["challenge"]?.asText()?.let(::description),
                                event["reward"]?.asText()?.let(::name),
                                event["upgrade"]?.asText()?.let(calendarLookup),
                                event["character"]?.asText()?.let { "生日：${name(it)}" })
                        } + listOfNotNull(WorldStateCatalog.birthdays[dayNumber]?.let { "生日：${name(it)}" }),
                        activation = date(season["Activation"]),
                        expiry = date(season["Expiry"])
                    )
                )
            }
        }
        return WorldSection("weekly", "周常", entries)
    }

    private fun events(root: JsonNode, now: Long): WorldSection = WorldSection(
        "events", "活动与警报",
        current(root, "Goals", now).mapIndexed { i, v ->
            WorldEntry(
                "goal-$i",
                name(v.text("Desc")),
                "活动",
                v.text("Node").takeIf(String::isNotEmpty)?.let(::node).orEmpty(),
                listOfNotNull(v["ToolTip"]?.asText()?.let(::name)),
                rewardObject(v.path("Reward")),
                date(v["Activation"]),
                date(v["Expiry"])
            )
        } + current(root, "Alerts", now).mapIndexed { i, v ->
            val m = v.path("MissionInfo")
            WorldEntry(
                "alert-$i",
                node(m.text("location")),
                "警报",
                name(m.text("missionType")),
                rewards = rewardObject(m.path("missionReward")),
                activation = date(v["Activation"]),
                expiry = date(v["Expiry"])
            )
        })

    private fun traders(root: JsonNode, now: Long, prime: Boolean, manifestField: String = "Manifest"): WorldSection {
        val evergreen = prime && manifestField == "EvergreenManifest"
        val sectionId = if (!prime) "trader" else if (evergreen) "varzia-evergreen" else "varzia"
        val title = if (!prime) "虚空商人" else if (evergreen) "瓦奇娅 · 常驻商品" else "瓦奇娅 · 轮换商品"
        val entries = mutableListOf<WorldEntry>()
        root.items(if (prime) "PrimeVaultTraders" else "VoidTraders")
            .filter { (date(it["Expiry"]) ?: Long.MAX_VALUE) > now }.forEachIndexed { traderIndex, trader ->
                val start = date(trader["Activation"])
                val end = date(trader["Expiry"])
                val category = if (prime) "瓦奇娅" else "虚空商人"
                // 两份清单分别解析、去重，避免相同商品跨清单时丢失归属。
                val inventory = trader.items(manifestField)
                if (inventory.isEmpty() || (start != null && start > now)) entries.add(
                    WorldEntry(
                        "$sectionId-$traderIndex-unavailable",
                        category,
                        category,
                        node(trader.text("Node")),
                        listOf(if (start != null && start > now) "尚未到访" else "官方暂未发布商品"),
                        activation = start,
                        expiry = if (evergreen) null else end
                    )
                )
                else inventory.distinctBy { it.text("ItemType") }.forEachIndexed { i, item ->
                    val prices = buildList {
                        if (item.has("PrimePrice")) add(
                            "${
                                item.path("PrimePrice").asInt()
                            } ${if (prime) "御品阿耶精华" else "杜卡德金币"}"
                        )
                        if (item.has("RegularPrice")) add(
                            "${
                                item.path("RegularPrice").asInt()
                            } ${if (prime) "阿耶精华" else "现金"}"
                        )
                        addAll(
                            item.items("ItemPrices")
                                .map { "${name(it.text("ItemType"))} ×${it.path("ItemCount").asInt()}" })
                    }
                    entries.add(
                        WorldEntry(
                            "$sectionId-$traderIndex-$i",
                            name(item.text("ItemType")),
                            category,
                            node(trader.text("Node")),
                            prices,
                            activation = start,
                            expiry = if (evergreen) null else end
                        )
                    )
                }
            }
        return WorldSection(sectionId, title, entries)
    }

    private fun invasions(root: JsonNode): WorldSection = WorldSection(
        "invasions", "入侵",
        root.items("Invasions").filterNot { it.path("Completed").asBoolean() }.mapIndexed { i, v ->
            val infestation = v.text("Faction") == "FC_INFESTATION"
            val goal = v.path("Goal").asDouble()
            val progress = if (goal > 0) ((if (infestation) 1 + v.path("Count").asDouble() / goal else
                (1 + v.path("Count").asDouble() / goal) / 2) * 100).coerceIn(0.0, 100.0) else null
            WorldEntry(
                "invasion-$i", node(v.text("Node")), if (infestation) "感染爆发" else "阵营入侵",
                "${name(v.text("Faction"))} / ${name(v.text("DefenderFaction"))}",
                details = listOf(
                    "进攻方：${rewardObject(v.path("AttackerReward")).joinToString("、").ifEmpty { "无" }}",
                    "防守方：${rewardObject(v.path("DefenderReward")).joinToString("、").ifEmpty { "无" }}"
                ), progress = progress
            )
        })

    private fun deals(root: JsonNode, now: Long): WorldSection = WorldSection(
        "deals", "限时特惠",
        current(root, "DailyDeals", now).mapIndexed { i, v ->
            WorldEntry(
                "daily-$i", name(v.text("StoreItem")), "达尔沃特惠", "${v.path("SalePrice").asInt()} 白金",
                listOf(
                    "原价 ${v.path("OriginalPrice").asInt()} 白金 · 优惠 ${v.path("Discount").asInt()}%",
                    "剩余 ${(v.path("AmountTotal").asInt() - v.path("AmountSold").asInt()).coerceAtLeast(0)} / ${
                        v.path(
                            "AmountTotal"
                        ).asInt()
                    }"
                ),
                activation = date(v["Activation"]), expiry = date(v["Expiry"])
            )
        } + root.items("FlashSales").filter {
            !it.path("HideFromMarket").asBoolean() && (date(it["StartDate"]) ?: 0) <= now && (date(it["EndDate"])
                ?: Long.MAX_VALUE) > now
        }.mapIndexed { i, v ->
            WorldEntry(
                "flash-$i", name(v.text("TypeName")), "商店特惠", details = listOfNotNull(
                    v["PremiumOverride"]?.asInt()?.let { "$it 白金" },
                    v["Discount"]?.asDouble()?.let { "折扣参数 $it" }),
                activation = date(v["StartDate"]), expiry = date(v["EndDate"])
            )
        })
}