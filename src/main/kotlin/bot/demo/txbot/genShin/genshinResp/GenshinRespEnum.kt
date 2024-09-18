package bot.demo.txbot.genShin.genshinResp

enum class GenshinRespEnum(
    val code: Long,
    val message: String
) {
    SUCCESS(2001000, "success"),

    // 原神信息
    SEARCH_HISTORY(2001001, "正在查询历史数据，请稍等"),
    DISCLAIMER(
        2001002,
        "免责声明:您将通过扫码完成获取米游社sk以及ck\n本Bot将不会保存您的登录状态\n我方仅提供米游社查询及相关游戏内容服务,若您的账号封禁、被盗等处罚与我方无关\n害怕风险请勿扫码~"
    ),
    LOGIN_SUCCESS(2001003, "登录成功,正在获取抽卡数据，时间根据抽卡次数不同需花费30秒至1分钟不等，请耐心等待"),
    SEND_LINK_FAIL(2001004, "请通过私聊无量姬发送链接，避免信息泄露，请及时撤回消息"),
    GET_LINK(2001005, "收到链接，正在处理中，请耐心等待"),
    IMPORT_HISTORY_SUCCESS(2001007, "抽卡记录导入完成"),
    IMPORT_HISTORY(5001009, "正在导入记录，请稍等"),


    // 原神异常
    POOL_FORMAT_ERROR(
        5001001, "你输入的格式似乎不正确哦,请使用指令「全部卡池」查看可以启用的卡池"
    ),
    POOL_NOTFOUND(5001002, "未找到你查询的卡池呢,请使用指令「全部卡池」查看可以启用的卡池"),
    BIND_NOTFOUND(5001003, "Uid还没有绑定，请发送「抽卡记录」进行绑定"),
    NO_USER_RECORD(5001004, "当前账户下无抽卡记录，快使用「抽卡记录」指令获取抽卡记录吧！"),
    LINK_INCOMPLETE(5001005, "链接不完整，请复制全部内容（可能输入法复制限制），或者复制的不是历史记录页面链接"),
    LINK_EXPIRED(5001006, "链接已过期，请重新获取"),
    LINK_SUCCESS(5001007, "链接验证成功,正在获取抽卡数据，时间根据抽卡次数不同需花费30秒至1分钟不等，请耐心等待"),
    LINK_FAIL(5001008, "抽卡链接格式不正确，请仔细检查或联系管理员"),
    IMPORT_HISTORY_EMPTY(5001009, "没有可以导入的记录"),
    IMPORT_HISTORY_FAIL(5001010, "记录导入失败，请检查记录格式是否符合UIGF标准"),

    ;
}