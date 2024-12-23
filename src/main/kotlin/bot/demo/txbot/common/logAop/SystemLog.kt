package bot.demo.txbot.common.logAop

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class SystemLog(
    val businessName: String
)
