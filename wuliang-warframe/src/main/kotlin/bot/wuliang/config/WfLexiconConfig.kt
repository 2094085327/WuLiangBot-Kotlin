package bot.wuliang.config

object WfLexiconConfig {
    /** Warframe缓存key*/
    const val WF_MARKET_CACHE_KEY = "Wuliang:Warframe:"

    /**
     * 词库哈希缓存
     */
    const val WF_LEXICON_EXPORT_CACHE_KEY = WF_MARKET_CACHE_KEY + "Lexicon:ExportHash"

    /**
     * 词库具体缓存
     */
    const val WF_LEXICON_ENDPOINTS_KEY = WF_MARKET_CACHE_KEY + "Lexicon:Endpoints:"
}