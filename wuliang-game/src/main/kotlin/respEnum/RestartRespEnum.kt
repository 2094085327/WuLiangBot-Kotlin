package respEnum

import bot.wuliang.exception.RespCode

enum class RestartRespEnum(
    override val code: Long,
    override val message: String,
):RespCode {
    SUCCESS(2003000, "success"),
    GAME_START_SUCCESS(2003003, "游戏账号创建成功,请在5分钟内开始游戏。\n请使用如「天赋 1 2 3」指令来选择图片中的天赋"),
    CONTINUER_SUCCESS(2003004, "请发送「继续 继续的步数」来进行游戏"),

    SIZE_OUT_ERROR(5003001, "分配的属性值的和不能超过可分配的上限哦"),
    VALUE_OUT_ERROR(5003002, "单项属性值不能大于10哦"),
    DATA_MISSING(5003003, "人生重开数据缺失，请使用「更新资源」指令来下载缺失数据"),
    GAME_NOT_START(5003004, "你还没有开始游戏，请发送「重开」进行游戏"),
    TALENT_NOT_CHOSE(5003005, "你还没有选择天赋,请先选择天赋"),
    ASSIGNED_ATTRIBUTES(5003006, "你已经分配过属性了，请不要重复分配"),
    ASSIGN_ATTRIBUTES_ERROR(5003007, "你分配的属性格式错误，请重新分配"),
    NO_ASSIGN_ATTRIBUTES(5003008, "你还没有分配属性，请先分配属性"),
    ALL_READY_CHOSE_TALENT(5003009, "你已经选择过天赋了,请不要重复分配"),
    TALENT_FORMAT_ERROR(5003010, "你分配的天赋格式错误或范围不正确，请重新分配"),
    ;
}