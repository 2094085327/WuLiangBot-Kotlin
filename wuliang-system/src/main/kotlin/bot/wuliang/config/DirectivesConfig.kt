package bot.wuliang.config

object DirectivesConfig {
    /** 结构版本隔离旧指令缓存，避免反序列化已移除的模板配置字段。 */
    const val DIRECTIVES_KEY = "Wuliang:System:Directives:v2"
}
