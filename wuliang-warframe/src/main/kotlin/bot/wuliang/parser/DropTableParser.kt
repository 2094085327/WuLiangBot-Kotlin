package bot.wuliang.parser

import bot.wuliang.parser.model.ParsedDropSource
import org.jsoup.nodes.Document

interface DropTableParser {
    fun support(sectionId: String): Boolean

    fun parse(doc: Document): List<ParsedDropSource>
}