package bot.wuliang.distribute.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DataSchema(
    val commandKey: String = "",
)
