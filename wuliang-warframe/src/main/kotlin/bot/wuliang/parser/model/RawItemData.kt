package bot.wuliang.parser.model

import bot.wuliang.parser.model.wikia.WikiaData
import bot.wuliang.utils.WfLexiconUtil

data class RawItemData(
   val api: List<WfLexiconUtil.CategoryData> = emptyList(),
   val manifest: List<ImageManifestItem> = emptyList(),
   val drops: List<DropData> = emptyList(),
   val wikia: WikiaData = WikiaData(),
)
