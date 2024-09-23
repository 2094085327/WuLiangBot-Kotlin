package bot.demo.txbot.warframe.warframeResp

enum class WarframeRespEnum(
    val code: Long,
    val message: String
) {
    SUCCESS(2001000, "success"),

    // Warframe信息
    SEARCH_WIKI(2002001, "你查询的物品的wiki地址可能是:"),


    // Warframe异常
    SEARCH_NOT_FOUND(5002001, "无量姬没有找到你查询的物品呢,也许你想找的是:"),
    SEARCH_MATCH_NOT_FOUND(5002002, "无量姬没有找到任何的匹配项目呢，快检查一下有没有输入错误吧~"),
    SEARCH_ERROR(5002003, "无量姬查询失败了，请稍后重试"),
    SEARCH_RIVEN_NOT_FOUND(5002004, "当前没有任何在线的玩家出售这种词条的"),
    INCARNON_ERROR(5002005, "啊哦~本周灵化数据不见了"),
    SPIRALS_ERROR(5002006, "啊哦~当前时间没有双衍平原数据，请联系开发者检查"),
    SPIRALS_ABNORMAL_ERROR(5002006, "啊哦~双衍平原天气数据出现异常，请联系开发者检查")
    ;
}