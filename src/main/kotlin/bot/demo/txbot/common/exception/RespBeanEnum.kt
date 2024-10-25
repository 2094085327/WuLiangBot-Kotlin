package bot.demo.txbot.common.exception

enum class RespBeanEnum(
    val code: Long,
    val message: String
) {
    SUCCESS(200, "SUCCESS"),
    ERROR(500, "服务端异常"),

    BIND_ERROR(5001, "参数校验异常"),
    LOGIN_ERROR(500210, "用户名或者密码不正确"),
    JAR_NOT_FOUND(500201, "当前路径下不存在Jar文件"),
    JAR_ERROR(500202, "获取当前运行JAR异常"),
    JAR_NOT_RUN(500203, "当前程序并未运行在Jar中"),
    ;

}