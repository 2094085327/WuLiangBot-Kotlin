package bot.demo.txbot.game.lifeRestart

const val AGE_JSONPATH: String = "resources/lifeRestart/age.json"
const val EVENT_JSONPATH: String = "resources/lifeRestart/events.json"
const val TALENT_JSONPATH: String = "resources/lifeRestart/talents.json"
const val AGE_EXCEL_PATH: String = "resources/lifeRestart/age.xlsx"
const val EVENT_EXCEL_PATH: String = "resources/lifeRestart/events.xlsx"
const val TALENT_EXCEL_PATH: String = "resources/lifeRestart/talents.xlsx"

const val TYPE_AGE = "age"
const val TYPE_EVENT = "event"
const val TYPE_TALENT = "talent"

const val AGE_JSON_MISS: String = "age.json文件缺失，尝试切换age.xlsx"
const val EVENT_JSON_MISS: String = "events.json文件缺失，尝试切换events.xlsx"
const val TALENT_JSON_MISS: String = "talents.json文件缺失，尝试切换talents.xlsx"

const val CHR: String = "CHR" // 颜值
const val INT: String = "INT" // 智力
const val STR: String = "STR" // 体质
const val MNY: String = "MNY" // 家境
const val EVT: String = "EVT" // 事件
const val LIF: String = "LIF" // 生命
const val SPR: String = "SPR" // 快乐
const val AGE: String = "AGE" // 年龄
const val RDM: String = "RDM" // 随机属性
const val TMS: String = "TMS" // 游戏次数
const val CACHV: String = "CACHV" // 成就达成数 Count Achievement

const val SIZE_OUT: String = "sizeOut"
const val VALUE_OUT: String = "valueOut"

// 天赋配置
data class TalentConfig(
    val talentPullCount: Int = 10, // 从天赋池中抽取的天赋数量
    val talentRate: MutableMap<Any, Int> = mutableMapOf(1 to 100, 2 to 10, 3 to 1, "total" to 1000), // 天赋概率
    val additions: MutableMap<String, MutableList<MutableMap<Int, MutableMap<Int, Int>>>> = mutableMapOf(
        TMS to mutableListOf(
            mutableMapOf(10 to mutableMapOf(2 to 1)),
            mutableMapOf(30 to mutableMapOf(2 to 2)),
            mutableMapOf(50 to mutableMapOf(2 to 3)),
            mutableMapOf(70 to mutableMapOf(2 to 4)),
            mutableMapOf(100 to mutableMapOf(2 to 5)),
        ),
        CACHV to mutableListOf(
            mutableMapOf(10 to mutableMapOf(2 to 1)),
            mutableMapOf(30 to mutableMapOf(2 to 2)),
            mutableMapOf(50 to mutableMapOf(2 to 3)),
            mutableMapOf(70 to mutableMapOf(2 to 4)),
            mutableMapOf(100 to mutableMapOf(2 to 5)),
        ),
    )
)