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
        var historyBaseUrl = "https://relics.run/history/"
        var historyRequestTimeoutSeconds = 120L

        /** 未完成采集的重试间隔；完整采集后等待下一个 UTC 同步时间。 */
        var intervalHours = 1L

        // 目录近期约在 UTC 02:21 生成前一日文件，预留发布缓冲。
        var dailySyncUtcHour = 2
        var dailySyncUtcMinute = 30
        var startupDelaySeconds = 60L
    }

    @PostConstruct
    fun validate() {
        require(userAgent.isNotBlank() && redisKeyPrefix.isNotBlank())
        require(globalRequestsPerSecond in 1..3)
        require(backgroundRequestsPerSecond in 1..2 && backgroundRequestsPerSecond <= globalRequestsPerSecond)
        require(requestTimeoutSeconds in 1..120 && maxAttempts in 1..5)
        require(historyCacheSeconds in 60..86400)
        require(statistics.dailySyncUtcHour in 0..23)
        require(statistics.dailySyncUtcMinute in 0..59)
        require(statistics.historyRequestTimeoutSeconds in 30..600)
        require(statistics.intervalHours in 1..168 && statistics.startupDelaySeconds in 0..3600)
    }
}