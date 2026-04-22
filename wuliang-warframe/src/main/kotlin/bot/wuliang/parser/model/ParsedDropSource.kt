package bot.wuliang.parser.model

data class ParsedDropSource(
    val category: String,
    val sourceName: String,
    val extra: Map<String, Any>? = null,
    val items: List<ParsedDropItem>
)
