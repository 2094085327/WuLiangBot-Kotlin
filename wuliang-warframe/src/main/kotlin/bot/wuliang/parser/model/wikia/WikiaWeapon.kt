package bot.wuliang.parser.model.wikia

/**
 * 维基武器数据类
 * @property regex 用于匹配武器名称的正则表达式
 * @property name 武器名称
 * @property url 武器维基页面的URL
 * @property mr 使用武器所需的大师段位
 * @property type 武器类型
 * @property rivenDisposition 武器的裂罅倾向性
 * @property statusChance 武器的状态触发几率
 * @property ammo 武器的最大弹药量
 * @property polarities 武器的极性槽
 * @property tags 武器的标签
 * @property vaulted 武器是否已绝版
 * @property introduced 武器的推出时间
 * @property marketCost 市场价格
 * @property bpCost 蓝图价格
 * @property thumbnail 武器缩略图的URL
 * @property attacks 武器拥有的攻击列表
 */
data class WikiaWeapon(
    val regex: String? = null,
    val name: String? = null,
    val uniqueName: String? = null,
    val url: String? = null,
    val mr: Int? = null,
    val type: String? = null,
    val rivenDisposition: Int? = null,
    val statusChance: Double? = null,
    val ammo: Int? = null,
    val polarities: List<String>? = null,
    val tags: List<String>? = null,
    val vaulted: Boolean? = null,
    val introduced: String? = null,
    val marketCost: String? = null,
    val bpCost: String? = null,
    val thumbnail: String? = null,
    val attacks: List<WikiaAttack?>? = null,
)
