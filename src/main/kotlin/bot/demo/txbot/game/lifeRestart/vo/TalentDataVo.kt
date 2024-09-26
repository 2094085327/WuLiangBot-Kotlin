package bot.demo.txbot.game.lifeRestart.vo


/**
 * @description: 天赋数据类
 * @author Nature Zero
 * @date 2024/3/18 14:56
 */
data class TalentDataVo(
    /**
     * 天赋id
     */
    val id: String? = null,
    /**
     * 天赋等级
     */
    val grade: Int? = null,
    /**
     * 天赋名称
     */
    val name: String? = null,
    /**
     * 天赋描述
     */
    val description: String? = null,
    /**
     * 排除此天赋的条件
     */
    val exclude: List<String> = listOf(),
    /**
     * 对属性的影响
     */
    val effect: Map<String, Int> = mapOf(),
    /**
     * 天赋激活条件
     */
    val condition: String? = null,
    /**
     * 天赋替换
     */
    val replacement: Map<String, List<Any>> = mapOf()
)