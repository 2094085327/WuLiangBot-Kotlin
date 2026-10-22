package bot.wuliang.market

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

/** Market 共享请求预算、每日成交采集和按需历史缓存配置。 */
@Component
@ConfigurationProperties(prefix = "warframe.market")
class MarketProperties {
    var userAgent = "Wuliang-Bot"
    var redisKeyPrefix = "Wuliang:Warframe:Market:{http}"
    var globalRequestsPerSecond = 3
    var backgroundRequestsPerSecond = 1
    var requestTimeoutSeconds = 30L
    var maxAttempts = 3
    var historyCacheSeconds = 1800L
    var statistics = Statistics()

    class Statistics {
        var enabled = true
        var platform = "pc"
        var workers = 3

        /** 未完成采集的重试间隔；完整采集后等待下一个 UTC 同步时间。 */
        var intervalHours = 1L
        var dailySyncUtcHour = 1
        var startupDelaySeconds = 60L
        var maxRoundHours = 7L
        var maxConsecutiveFailures = 5

        /** 每个商品每个目标统计日：首次尝试后最多再重试三轮。HTTP 单次请求重试另计。 */
        var dailyRetryLimit = 3
    }

    @PostConstruct
    fun validate() {
        require(userAgent.isNotBlank() && redisKeyPrefix.isNotBlank())
        require(globalRequestsPerSecond in 1..3)
        require(backgroundRequestsPerSecond in 1..2 && backgroundRequestsPerSecond <= globalRequestsPerSecond)
        require(requestTimeoutSeconds in 1..120 && maxAttempts in 1..5)
        require(historyCacheSeconds in 60..86400)
        require(statistics.dailySyncUtcHour in 0..23)
        require(statistics.workers in 1..3)
        require(statistics.intervalHours in 1..168 && statistics.startupDelaySeconds in 0..3600)
        require(statistics.maxRoundHours in 1..24 && statistics.maxConsecutiveFailures in 1..100)
        require(statistics.dailyRetryLimit in 0..24)
    }
}