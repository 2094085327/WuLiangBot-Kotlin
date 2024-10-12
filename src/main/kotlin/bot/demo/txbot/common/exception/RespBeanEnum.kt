package bot.demo.txbot.common.exception

enum class RespBeanEnum(
    val code: Long,
    val message: String
) {
    SUCCESS(200, "SUCCESS"),
    ERROR(500, "服务端异常"),

    BIND_ERROR(5001, "参数校验异常"),
    LOGIN_ERROR(500210, "用户名或者密码不正确"),
    ;

}