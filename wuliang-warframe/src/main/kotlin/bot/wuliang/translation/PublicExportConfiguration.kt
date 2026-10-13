package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.config.WarframeProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

/** 统一组装官方主源及社区补充源，避免来源配置散落在下载器中。 */
@Configuration
class PublicExportConfiguration {
    @Bean
    fun publicExportRemote(
        properties: WarframeProperties,
        @Value("\${github.access-token:}") token: String
    ): PublicExportRemote {
        val http = PublicExportHttpClient(properties.exportHttp)
        val plus = properties.publicExportPlus
        val languages = properties.languagesBin
        return CombinedPublicExportRemote(
            plus = GitHubJsonRepository(
                plus.repository, plus.branch, token, PublicExportStore::acceptsPlus, properties.githubExport, http,
            ),
            languages = GitHubJsonRepository(
                languages.repository, languages.branch, token,
                { path -> PublicExportPolicy.LOCALES.any { path == "$it.json" } }, properties.githubExport, http,
            ),
            official = OfficialPublicExportRemote(
                Path.of(plus.directory).resolve(PublicExportPolicy.OFFICIAL_CACHE_DIRECTORY),
                properties.officialExport,
                http,
            ),
        )
    }
}