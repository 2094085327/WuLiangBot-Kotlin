package bot.demo.txbot.common.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.logging.Logger


/**
 * @description: 一些其他配置和工具类
 * @author Nature Zero
 * @date 2024/2/8 10:00
 */
@Component
@Configuration
class OtherUtil {
    private val logger: Logger = Logger.getLogger(ExcelReader::class.java.getName())

    companion object {
        var gskPort: String = ""
    }

    @Value("\${gensokyo_config.port}")
    fun getPort(gensokyoPort: String) {
        gskPort = gensokyoPort
    }

    /**
     * 获取真实ID
     *
     * @param event 任意消息事件
     * @return 真实ID
     */
    fun getRealId(event: AnyMessageEvent): String {
        val fictitiousId = event.userId
        return HttpUtil.doGetJson("http://localhost:$gskPort/getid?type=2&id=$fictitiousId")["id"].textValue()
    }

    /**
     * 获取真实ID
     *
     * @param event 私聊消息事件
     * @return 真实ID
     */
    fun getRealId(event: PrivateMessageEvent): String {
        val fictitiousId = event.userId
        return HttpUtil.doGetJson("http://localhost:$gskPort/getid?type=2&id=$fictitiousId")["id"].textValue()
    }

    /**
     * 从GitHub下载或更新资源
     *
     * @param repoOwner 仓库所有者
     * @param repoName 仓库名
     * @param folderPath 文件夹路径
     * @param targetFolderPath 目标文件夹路径
     * @param accessToken GitHub访问令牌
     */
    fun downloadFolderFromGitHub(
        repoOwner: String,
        repoName: String,
        folderPath: String,
        targetFolderPath: String,
        accessToken: String? = null
    ) {
        try {
            val apiUrl =
                String.format("https://api.github.com/repos/%s/%s/contents/%s", repoOwner, repoName, folderPath)
            val url = URL(apiUrl)
            val connection: URLConnection = url.openConnection()
            if (!accessToken.isNullOrEmpty()) {
                connection.setRequestProperty("Authorization", "token $accessToken")
            }
            try {
                connection.getInputStream().use { `in` ->
                    val objectMapper = jacksonObjectMapper()
                    val jsonNode = objectMapper.readTree(`in`)

                    for (file in jsonNode) {
                        val fileName = file["name"].textValue()
                        // 使用国内镜像下载
                        val fileDownloadUrl =
                            file["download_url"].textValue().replace("raw.githubusercontent", "raw.gitmirror")

                        val targetFilePath = Paths.get(targetFolderPath, fileName).toString()

                        if (!fileExistsWithSameContent(targetFilePath, file["sha"].textValue()))
                            downloadFile(fileDownloadUrl, targetFilePath, accessToken)
                    }
                    logger.info("文件夹下载成功！")
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } catch (e: IOException) {
            e.printStackTrace()
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
                    FileOutputStream(targetFilePath).use { out ->
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (`in`.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                        }
                        logger.info("文件下载成功：$targetFilePath")
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


}