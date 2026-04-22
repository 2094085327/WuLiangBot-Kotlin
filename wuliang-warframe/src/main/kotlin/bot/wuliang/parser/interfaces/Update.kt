package bot.wuliang.parser.interfaces

interface Update {
    val name: String
    val url: String
    val aliases: List<String>
    val parent: String
    val date: String
}