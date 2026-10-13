package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.config.WarframeProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.nio.file.Path

/** 从单个 GitHub 仓库读取根目录 JSON；只下载已固定提交的文件。 */
internal class GitHubJsonRepository(
    private val repository: String,
    private val branch: String,
    private val token: String,
    private val accepts: (String) -> Boolean,
    private val settings: WarframeProperties.GitHubExport,
    private val http: PublicExportHttpClient,
) : FilePublicExportRemote {
    private val mapper = jacksonObjectMapper()

    init {
        require(REPOSITORY_PATTERN.matches(repository)) { "词库 repository 必须为 owner/repo 格式" }
        require(branch.isNotBlank()) { "词库 branch 不能为空" }
    }

    private val apiUrl = settings.apiBaseUrl.toHttpUrl().newBuilder()
        .addPathSegment("repos").addPathSegments(repository).addPathSegment("").build()
    private val rawUrl = settings.rawBaseUrl.toHttpUrl().newBuilder()
        .addPathSegments(repository).addPathSegment("").build()

    /**
     * 先解析配置分支的提交，再读取该提交对应的根目录树；拒绝被截断的文件清单。
     */
    override fun revision(): ExportRevision {
        val commit = getApiJson("commits", branch)
        val sha = commit.path("sha").asText()
        val treeSha = commit.path("commit").path("tree").path("sha").asText()
        require(PublicExportPolicy.SHA_PATTERN.matches(sha)) { "GitHub 未返回有效的 commit SHA" }
        require(PublicExportPolicy.SHA_PATTERN.matches(treeSha)) { "GitHub 未返回有效的 tree SHA" }
        val tree = getApiJson("git", "trees", treeSha)
        require(!tree.path("truncated").asBoolean()) { "GitHub 文件清单被截断，拒绝应用不完整快照" }
        require(tree.path("tree").isArray) { "GitHub 未返回有效的文件清单" }
        val files = tree.path("tree")
            .filter { it.path("type").asText() == "blob" && accepts(it.path("path").asText()) }
            .map { ExportFile(it.path("path").asText(), it.path("sha").asText(), it.path("size").asLong()) }
            .sortedBy { it.path }
        return ExportRevision(sha, files)
    }

    /**
     * 从固定提交读取原始文件；字节完整性由快照存储层按清单校验。
     */
    override fun download(commit: String, file: ExportFile): ByteArray {
        val url = rawUrl.newBuilder().addPathSegment(commit).addPathSegment(file.path).build()
        return get(http.request(url).build())
    }

    override fun downloadTo(commit: String, file: ExportFile, target: Path) {
        val url = rawUrl.newBuilder().addPathSegment(commit).addPathSegment(file.path).build()
        http.download(http.request(url).build(), target, ::downloadError)
    }

    private fun getApiJson(vararg segments: String): JsonNode {
        val url = apiUrl.newBuilder().apply {
            segments.forEach { addPathSegment(it) }
        }.build()
        val request = http.request(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", settings.apiVersion)
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        return http.read(request, ::downloadError) { mapper.readTree(it) }
    }

    private fun get(request: Request): ByteArray = http.get(request, ::downloadError)

    private fun downloadError(status: Int): String {
        val detail = if (status == 403 || status == 429) "（可能达到 GitHub 限额，请稍后重试或配置 GitHub token）" else ""
        return "词库 $repository 下载失败：HTTP $status$detail"
    }

    private companion object {
        val REPOSITORY_PATTERN = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?/[A-Za-z0-9_.-]+")
    }
}