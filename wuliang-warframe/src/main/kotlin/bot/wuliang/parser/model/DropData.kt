package bot.wuliang.parser.model

data class DropData(
    /**
     * 掉落地点
     */
    val location: String,
    /**
     * 掉落物品
     */
    val type: String,
    /**
     * 掉落概率
     */
    val chance: Double,
    /**
     * 掉落稀有度
     */
    val rarity: String
)
