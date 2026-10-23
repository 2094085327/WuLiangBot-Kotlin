package bot.wuliang.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

/** 世界状态与翻译的数据源配置；保留现有 warframe 配置键，默认端点和路径见 WfApiUrl.kt。 */
@Component
@ConfigurationProperties(prefix = "warframe")
class WarframeProperties {
    var officialExport = OfficialExport()
    var publicExportPlus = PublicExportPlus()
    var languagesBin = ExportRepository("calamity-inc/warframe-languages-bin-data")
    var githubExport = GitHubExport()
    var exportHttp = ExportHttp()
    var worldState = WorldState()
    var wikiRotation = WikiRotation()
    var arbitration = Arbitration()
    var sortieData = WARFRAME_SORTIE_DATA
    var conquestData = WARFRAME_CONQUEST_DATA
    var calendarUpgrades = WARFRAME_CALENDAR_UPGRADES
    var translationAliases = WARFRAME_TRANSLATION_ALIASES
    var spiralTranslations = WARFRAME_SPIRAL_TRANSLATIONS
    var synthesisLocations = WARFRAME_SYNTHESIS_LOCATIONS

    class OfficialExport {
        var indexBaseUrl = WARFRAME_EXPORT_INDEX_URL
        var contentBaseUrl = WARFRAME_EXPORT_CONTENT_URL
    }

    open class ExportRepository(var repository: String = "") {
        var branch = "senpai"
    }

    class PublicExportPlus : ExportRepository("2094085327/warframe-public-export-plus") {
        var directory = WARFRAME_EXPORT_DIRECTORY
    }

    class GitHubExport {
        var apiBaseUrl = WARFRAME_EXPORT_GITHUB_API_URL
        var rawBaseUrl = WARFRAME_EXPORT_GITHUB_RAW_URL
        var apiVersion = "2022-11-28"
    }

    class ExportHttp {
        var connectTimeoutSeconds = 15L
        var readTimeoutSeconds = 90L
        var callTimeoutSeconds = 120L
    }

    class WorldState {
        var url = WARFRAME_STATUS_URL
        var cacheSeconds = DEFAULT_CACHE_SECONDS
        var retrySeconds = DEFAULT_RETRY_SECONDS
        var staleSeconds = DEFAULT_STALE_SECONDS
        var requestTimeoutSeconds = 20L
    }

    class WikiRotation {
        var baseUrl = "https://wiki.warframe.com/w/"
        var cacheSeconds = 900L
        var retrySeconds = 60L
        var staleSeconds = 3600L
        var requestTimeoutSeconds = 20L
    }

    class Arbitration {
        var url = WARFRAME_ARBYS_DATA
        var cacheFile = "resources/fileCache/arbitration-schedule.csv"
        var tiersFile = WARFRAME_ARBITRATION_TIERS
        var retrySeconds = 60L
        var requestTimeoutSeconds = 20L
    }

    /** 在配置绑定完成后校验数据源地址、超时和缓存时长，拒绝不满足约束的配置。 */
    @PostConstruct
    fun validate() {
        require(arbitration.retrySeconds > 0 && arbitration.requestTimeoutSeconds > 0) {
            "warframe.arbitration 的重试和请求超时必须为正数"
        }
        require(worldState.cacheSeconds in 30..600) { "warframe.world-state.cache-seconds 必须在 30..600 内" }
        require(worldState.retrySeconds in 1..worldState.cacheSeconds) { "world-state.retry-seconds 必须为正数且不超过 cache-seconds" }
        require(worldState.staleSeconds >= worldState.cacheSeconds) { "world-state.stale-seconds 不能小于 cache-seconds" }
        require(worldState.requestTimeoutSeconds > 0) { "world-state.request-timeout-seconds 必须为正数" }
        require(wikiRotation.cacheSeconds in 60..3600) { "wiki-rotation.cache-seconds 必须在 60..3600 内" }
        require(wikiRotation.retrySeconds in 1..wikiRotation.cacheSeconds) { "wiki-rotation.retry-seconds 必须为正数且不超过 cache-seconds" }
        require(wikiRotation.staleSeconds >= wikiRotation.cacheSeconds) { "wiki-rotation.stale-seconds 不能小于 cache-seconds" }
        require(wikiRotation.requestTimeoutSeconds > 0) { "wiki-rotation.request-timeout-seconds 必须为正数" }
        require(
            listOf(
                exportHttp.connectTimeoutSeconds,
                exportHttp.readTimeoutSeconds,
                exportHttp.callTimeoutSeconds
            ).all { it > 0 }) {
            "warframe.export-http 超时必须为正数"
        }
    }

    companion object {
        const val DEFAULT_CACHE_SECONDS = 300L
        const val DEFAULT_RETRY_SECONDS = 30L
        const val DEFAULT_STALE_SECONDS = 1800L
    }
}