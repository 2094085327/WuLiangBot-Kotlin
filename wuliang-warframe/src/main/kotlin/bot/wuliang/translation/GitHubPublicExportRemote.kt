package bot.wuliang.translation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/** 从 GitHub 提交与 Git Tree 获取版本信息。 */
@Component
class GitHubPublicExportRemote(
    @Value("\${warframe.public-export-plus.branch:senpai}") private val branch: String,
    @Value("\${github.access-token:}") private val token: String,
    @Value("\${warframe.public-export-plus.repository:2094085327/warframe-public-export-plus}")
    private val repository: String = "2094085327/warframe-public-export-plus",
) : PublicExportRemote {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    private val mapper = jacksonObjectMapper()

    init {
        require(REPOSITORY_PATTERN.matches(repository)) { "Public Export Plus repository 必须为 owner/repo 格式" }
        require(branch.isNotBlank()) { "Public Export Plus branch 不能为空" }
    }

    private val apiUrl = "$GITHUB_API_URL/repos/$repository/".toHttpUrl()
    private val rawUrl = "$GITHUB_RAW_URL/$repository/".toHttpUrl()

    /**
     * 先解析配置分支的提交，再读取该提交对应的根目录树；拒绝被截断的文件清单。
     */
    override fun revision(): ExportRevision {
        val commit = getApiJson("commits", branch)
        val sha = commit.path("sha").asText()
        val treeSha = commit.path("commit").path("tree").path("sha").asText()
        require(SHA_PATTERN.matches(sha)) { "GitHub 未返回有效的 commit SHA" }
        require(SHA_PATTERN.matches(treeSha)) { "GitHub 未返回有效的 tree SHA" }
        val tree = getApiJson("git", "trees", treeSha)
        require(!tree.path("truncated").asBoolean()) { "GitHub 文件清单被截断，拒绝应用不完整快照" }
        require(tree.path("tree").isArray) { "GitHub 未返回有效的文件清单" }
        val files = tree.path("tree")
            .filter { it.path("type").asText() == "blob" && PublicExportStore.accepts(it.path("path").asText()) }
            .map { ExportFile(it.path("path").asText(), it.path("sha").asText(), it.path("size").asLong()) }
            .sortedBy { it.path }
        return ExportRevision(sha, files)
    }

    /**
     * 从固定提交读取原始文件；字节完整性由快照存储层按清单校验。
     */
    override fun download(commit: String, file: ExportFile): ByteArray {
        val url = rawUrl.newBuilder().addPathSegment(commit).addPathSegment(file.path).build()
        return get(requestBuilder(url).build())
    }

    private fun getApiJson(vararg segments: String): JsonNode {
        val url = apiUrl.newBuilder().apply {
            segments.forEach { addPathSegment(it) }
        }.build()
        val request = requestBuilder(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        return mapper.readTree(get(request))
    }

    private fun requestBuilder(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("User-Agent", "Wuliang-Public-Export-Plus")

    /**
     * 统一处理下载超时、HTTP 错误及文件大小上限；认证令牌只附加到 GitHub API 请求。
     */
    private fun get(request: Request): ByteArray {
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val detail =
                    if (response.code == 403 || response.code == 429) "（可能达到 GitHub 限额，请稍后重试或配置 GitHub token）" else ""
                error("Public Export Plus 下载失败：HTTP ${response.code}$detail")
            }
            require(response.body.contentLength() <= MAX_FILE_BYTES) { "上游文件超过允许大小" }
            response.body.byteStream().use { stream ->
                val bytes = stream.readNBytes(MAX_FILE_BYTES + 1)
                require(bytes.size <= MAX_FILE_BYTES) { "上游文件超过允许大小" }
                bytes
            }
        }
    }

    private companion object {
        const val GITHUB_API_URL = "https://api.github.com"
        const val GITHUB_RAW_URL = "https://raw.githubusercontent.com"
        const val MAX_FILE_BYTES = 64_000_000
        val SHA_PATTERN = Regex("[0-9a-f]{40}")
        val REPOSITORY_PATTERN = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?/[A-Za-z0-9_.-]+")
    }
}