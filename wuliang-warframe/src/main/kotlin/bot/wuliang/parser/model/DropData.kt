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
    val rarity: String,
    /**
     * 官方掉落数据中的轮次信息目前包含在 location 文本中，保留该字段
     * 方便后续调用方按轮次筛选；没有轮次的掉落保持为空。
     */
    val rotation: String? = null,

    /** 关联的遗物 uniqueName（普通掉落为空）。 */
    val uniqueName: String? = null,
)