package bot.demo.txbot.other.distribute.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Executor(val action: String)
