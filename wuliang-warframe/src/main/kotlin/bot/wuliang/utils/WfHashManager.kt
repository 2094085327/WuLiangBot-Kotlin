package bot.wuliang.utils

import bot.wuliang.config.WfLexiconConfig.WF_LEXICON_EXPORT_CACHE_KEY
import bot.wuliang.redis.RedisService
import bot.wuliang.utils.WfLexiconUtil.EndpointResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WfHashManager {
    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var wfScraper: WfScraper

    fun updateExportCache(): Map<String, String> {
        val locales = listOf("en", "zh")
        val endpointsList = mutableListOf<String>()
        val hashes = mutableMapOf<String, String>()
        for (locale in locales) {
            val result = wfScraper.fetchEndpoints(locale = locale)
            if (result is EndpointResult.Endpoints) {
                val endpoints = result.list
                endpointsList.addAll(endpoints)
            }
        }
        endpointsList
            .flatMap { it.split("!00_") }
            .chunked(2)
            .filter { it.size == 2 }
            .forEach { (key, hash) ->
                hashes[key] = hash
            }

        return hashes
    }

    fun isHashUpdated(currentHashes: Map<String, String>): Boolean {
        val cachedHashes = redisService.getAllHash(WF_LEXICON_EXPORT_CACHE_KEY)
        if (cachedHashes.size != currentHashes.size) return false
        return currentHashes.all { (k, v) -> cachedHashes[k] == v }
    }

    fun saveToRedis(hashes: Map<String, String>) {
        if (hashes.isEmpty()) return
        redisService.setAllHashes(WF_LEXICON_EXPORT_CACHE_KEY, hashes)
    }
}