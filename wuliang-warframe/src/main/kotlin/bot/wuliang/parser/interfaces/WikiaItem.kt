package bot.wuliang.parser.interfaces

import bot.wuliang.parser.enums.Tag

interface WikiaItem {
    val wikiaThumbnail: String?
    val wikiaUrl: String?
    val tags: List<Tag>?
    val introduced: Update?
    val wikiAvailable: Boolean?
}