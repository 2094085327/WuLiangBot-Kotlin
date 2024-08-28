package bot.demo.txbot.other.distribute.annotation

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE_PARAMETER, AnnotationTarget.VALUE_PARAMETER)
annotation class AParameter(val name: String)
