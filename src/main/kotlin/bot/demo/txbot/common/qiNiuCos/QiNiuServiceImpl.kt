package bot.demo.txbot.common.qiNiuCos

import bot.demo.txbot.common.utils.JacksonUtil
import com.qiniu.common.QiniuException
import com.qiniu.http.Response
import com.qiniu.util.StringMap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import pers.wuliang.robot.common.utils.LoggerUtils.logError
import java.io.File
import java.io.InputStream
import java.util.*


/**
 * @description: 七牛云文件上传服务
 * @author Nature Zero
 * @date 2024/7/19 下午3:54
 */
@Service
class QiNiuServiceImpl(@Autowired private val qiNiuCosConfig: QiNiuCosConfig) : QiNiuService {
    /**
     * 简单上传，使用默认策略，只需要设置上传的空间名就可以了
     *
     * @param fileName 文件上传至七牛云空间的名称
     * @return 文件上传结果
     */
    override fun getUpToken(fileName: String): String {
        return qiNiuCosConfig.auth.uploadToken(
            qiNiuCosConfig.bucketName,
            fileName,
            qiNiuCosConfig.policyExpire,
            StringMap().put("insertOnly", 0)
        )
    }

    /**
     * 获取文件上传至七牛云空间的名称
     *
     * @param filePath 文件路径
     * @param fileName 文件上传至七牛云空间的名称
     * @return 最终文件名称
     */
    private fun determineFinalFileName(filePath: String?, fileName: String?): String {
        return fileName ?: filePath?.substringAfterLast("\\") ?: UUID.randomUUID().toString()
    }

    /**
     * 通用的上传处理方法
     *
     * @param data 上传的数据
     * @param fileName 文件上传至七牛云空间的名称
     * @param mime 文件类型
     * @return 文件上传结果
     */
    private fun uploadFile(data: Any, fileName: String?, mime: String?): String {
        val filePath = when (data) {
            is String -> data
            is File -> data.absolutePath
            else -> null
        }
        val finalFileName = determineFinalFileName(filePath, fileName)
        val saveFile = "${qiNiuCosConfig.path}$finalFileName.$mime"

        return try {
            val res: Response = when (data) {
                is String -> qiNiuCosConfig.uploadManager.put(data, saveFile, getUpToken(saveFile))
                is File -> qiNiuCosConfig.uploadManager.put(data.absolutePath, saveFile, getUpToken(saveFile))
                is InputStream -> qiNiuCosConfig.uploadManager.put(data, saveFile, getUpToken(saveFile), null, mime)
                is ByteArray -> qiNiuCosConfig.uploadManager.put(data, saveFile, getUpToken(saveFile))
                else -> throw IllegalArgumentException("不支持的数据格式")
            }

            val json = JacksonUtil.readTree(res.bodyString())
            println("${qiNiuCosConfig.url}${json["key"].asText()}")
            "${qiNiuCosConfig.url}${json["key"].asText()}"
        } catch (e: QiniuException) {
            logError("上传失败: ${e.response}")
            throw e
        }
    }


    /**
     * 普通上传-通过文件路径上传
     *
     * @param filePath 文件路径
     * @param fileName 文件上传至七牛云空间的名称
     * @return 文件上传结果
     */
    override fun upload(filePath: String, fileName: String?, mime: String?): String {
        return uploadFile(filePath, fileName, mime)
    }

    /**
     * 普通上传-通过文件对象上传
     *
     * @param file 文件对象
     * @param fileName 文件上传至七牛云空间的名称
     * @return 文件上传结果
     */
    override fun upload(file: File, fileName: String?, mime: String?): String {
        return uploadFile(file, fileName, mime)
    }

    /**
     * 普通上传-通过文件输入流上传
     *
     * @param inputStream 文件输入流
     * @param fileName 文件上传至七牛云空间的名称
     * @return 文件上传结果
     * @param mime 文件类型
     */
    override fun upload(inputStream: InputStream, fileName: String?, mime: String?): String {
        return uploadFile(inputStream, fileName, mime)
    }

    /**
     * 普通上传-通过字节数组上传
     *
     * @param byteArray 字节数组
     * @param fileName 文件上传至七牛云空间的名称
     * @return 文件上传结果
     */
    override fun upload(byteArray: ByteArray, fileName: String?, mime: String?): String {
        return uploadFile(byteArray, fileName, mime)
    }


    override fun deleteFile(fileName: String, mime: String?) {
        val key = "${qiNiuCosConfig.path}$fileName.${mime ?: ""}"
        println(key)
        try {
            qiNiuCosConfig.bucketManager.delete(qiNiuCosConfig.bucketName, key)
        } catch (e: QiniuException) {
            logError("图床文件删除失败: ${e.response}")
        }
    }

}