package bot.wuliang.otherUtil

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.houbb.opencc4j.util.ZhConverterUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.URL
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest


/**
 * @description: 一些其他配置和工具类
 * @author Nature Zero
 * @date 2024/2/8 10:00
 */
@Component
@Configuration
@Suppress("unused")
class OtherUtil {
    companion object {
        var gskPort: String = ""

        // 文件数量
        var fileCount = 0
    }

    @Value("\${gensokyo_config.port}")
    fun getPort(gensokyoPort: String) {
        gskPort = gensokyoPort
    }

    /**
     * 从GitHub下载或更新资源
     *
     * @param repoOwner 仓库所有者
     * @param repoName 仓库名
     * @param folderPath 文件夹路径
     * @param targetFolderPath 目标文件夹路径
     * @param accessToken GitHub访问令牌
     * @param downloadUrlPrefix 下载URL前缀
     * @param mirrorUrlPrefix 镜像URL前缀
     * @return 是否成功与更新文件数量
     */
    suspend fun downloadFolderFromGitHub(
        repoOwner: String,
        repoName: String,
        folderPath: String,
        targetFolderPath: String,
        accessToken: String? = null,
        downloadUrlPrefix: String = "raw.githubusercontent.com",
        mirrorUrlPrefix: String = "https://gh.llkk.cc/"
    ): Pair<Boolean, Int> {
        val apiUrl =
            "https://api.github.com/repos/$repoOwner/$repoName/contents/${folderPath.replace(File.separator, "/")}"
        return withContext(Dispatchers.IO) { // 切换到IO线程
            var fileCount = 0 // 计数器
            try {
                val url = URL(apiUrl)
                val connection: URLConnection = url.openConnection()

                if (!accessToken.isNullOrEmpty()) {
                    connection.setRequestProperty("Authorization", "token $accessToken")
                }

                connection.getInputStream().use { `in` ->
                    val objectMapper = jacksonObjectMapper()
                    val jsonNode = objectMapper.readTree(`in`)

                    val downloadJobs = jsonNode.map { file ->
                        async {
                            val fileName = file["name"].textValue()
                            var fileDownloadUrl = file["download_url"]?.textValue()

                            if (fileDownloadUrl == null) {
                                // 这是一个文件夹，递归下载内容
                                val subFolderPath = "${folderPath}${File.separator}$fileName"
                                val (_, count) = downloadFolderFromGitHub(
                                    repoOwner,
                                    repoName,
                                    subFolderPath,
                                    targetFolderPath,
                                    accessToken
                                )
                                fileCount += count // 累加文件计数
                            } else {
                                // 文件，使用国内镜像下载
//                                fileDownloadUrl = fileDownloadUrl.replace("https://", mirrorUrlPrefix)
                                fileDownloadUrl = mirrorUrlPrefix + fileDownloadUrl

                                val targetFilePath = Paths.get(folderPath, fileName).toString()

                                if (!fileExistsWithSameContent(targetFilePath, file["sha"].textValue())) {
                                    downloadFile(fileDownloadUrl, targetFilePath, accessToken)
                                    fileCount += 1
                                }
                            }
                        }
                    }

                    // 等待所有下载任务完成
                    downloadJobs.awaitAll()
                }
                Pair(true, fileCount)

            } catch (e: IOException) {
                e.printStackTrace()
                logError("文件下载异常:", e)
                Pair(false, fileCount)
            }
        }
    }


    /**
     * 判断本地文件是否存在且内容与远程文件相同
     *
     * @param filePath 本地文件路径
     * @param remoteSha 远程文件SHA1
     * @return 是否相同
     */
    private fun fileExistsWithSameContent(filePath: String, remoteSha: String): Boolean {
        val localFile = File(filePath)

        if (localFile.exists()) {
            val localSha = calculateGitHubSHA1(localFile)
            return localSha == remoteSha
        }

        return false
    }

    /**
     * 下载文件
     *
     * @param fileUrl 文件URL
     * @param targetFilePath 目标文件路径
     * @param accessToken GitHub访问令牌
     */
    private fun downloadFile(fileUrl: String, targetFilePath: String, accessToken: String?) {
        try {
            val url = URL(fileUrl)
            val connection: URLConnection = url.openConnection()
            if (!accessToken.isNullOrEmpty()) {
                connection.setRequestProperty("Authorization", "token $accessToken")
            }

            try {
                connection.getInputStream().use { `in` ->
                    // 父目录不存在则创建
                    val targetPath = Paths.get(targetFilePath)
                    if (!Files.exists(targetPath.parent)) {
                        Files.createDirectories(targetPath.parent)
                        logInfo("创建文件夹：${targetPath.parent}")
                    }

                    FileOutputStream(targetFilePath).use { out ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (`in`.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                        }
                        logInfo("文件下载成功：$targetFilePath")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 计算文件的SHA1
     *
     * @param file 本地文件
     * @return 文件的SHA1
     */
    fun calculateGitHubSHA1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val fis = FileInputStream(file)

        val length = file.length()
        val contentPrefix = "blob $length\u0000".toByteArray(Charsets.UTF_8)

        digest.update(contentPrefix)

        val byteArray = ByteArray(4096)
        var bytesRead: Int

        while (fis.read(byteArray).also { bytesRead = it } != -1) {
            digest.update(byteArray, 0, bytesRead)
        }

        fis.close()

        val hash = digest.digest()

        val sb = StringBuilder()
        for (b in hash) {
            sb.append(String.format("%02x", b))
        }

        return sb.toString()
    }

    /**
     * 获取所有文件夹路径
     *
     * @param rootFolder 根文件夹
     * @param currentFolder 当前文件夹
     * @param excludedFolders 要排除的文件夹名称列表
     * @return 所有文件夹路径
     */
    @Suppress("unused")
    fun getAllRelativePaths(
        rootFolder: File,
        currentFolder: File,
        excludedFolders: List<String> = emptyList()
    ): List<String> {
        val relativePaths = mutableListOf<String>()

        currentFolder.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val folderName = file.name
                if (folderName !in excludedFolders) {
                    val relativePath =
                        "${rootFolder.name}${File.separator}${file.relativeTo(rootFolder).invariantSeparatorsPath}"
                    relativePaths.add(relativePath)
                    relativePaths.addAll(getAllRelativePaths(rootFolder, file, excludedFolders))
                }
            }
        }

        return relativePaths
    }

    /**
     * 找到匹配字符串
     *
     * @param key 待匹配关键字
     * @param keyList 待匹配列表
     * @return 匹配到的字符串列表
     */
    fun findMatchingStrings(key: String, keyList: List<String>): List<String> {
        // 统计key中每个字符的频率
        val keyFrequency = HashMap<Char, Int>()
        key.forEach { char ->
            keyFrequency[char] = keyFrequency.getOrDefault(char, 0) + 1
        }

        val map = HashMap<String, Int>()
        keyList.forEach { eachKey ->
            // 统计eachKey中每个字符的频率
            val eachKeyFrequency = HashMap<Char, Int>()
            eachKey.forEach { char ->
                eachKeyFrequency[char] = eachKeyFrequency.getOrDefault(char, 0) + 1
            }

            // 计算两个频率映射的匹配程度
            var num = 0
            for ((char, freq) in keyFrequency) {
                num += minOf(freq, eachKeyFrequency.getOrDefault(char, 0))
            }

            map[eachKey] = num
        }

        // 将结果按匹配度降序排序并取前5个
        val sortedMap = map.toList().sortedByDescending { (_, value) -> value }.toMap()
        return sortedMap.keys.take(5).toList()
    }

    object STConversion {
        /**
         * 繁体转换为简体
         *
         * @return 转换后的字符串
         */
        fun String.turnZhHans(): String {
            return ZhConverterUtil.toSimple(this)
        }

        fun String.toMd5(): String {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(this.toByteArray(StandardCharsets.UTF_8))
            return BigInteger(1, digest).toString(16).padStart(32, '0')
        }
    }

    /**
     * @Description: 文件路径工具类
     */
    object FileUrlUtils{
        fun String.toPath(): Path = Paths.get(this)
    }

}