package bot.wuliang.botLog.logAop

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SystemLog(
    val businessName: String
)
