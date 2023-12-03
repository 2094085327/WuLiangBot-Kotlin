package bot.demo.txbot.geography

import bot.demo.txbot.common.utils.HttpUtil
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


/**
 *@Description:
 *@Author zeng
 *@Date 2023/6/11 15:00
 *@User 86188
 */
@Component
class GetGeoImg {
    companion object {
        var key: String = ""
    }

    @Value("\${image_load.key}")
    fun getKey(imgKey: String) {
        key = imgKey
    }

    val headers: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0;Win64;x64)AppleWebKit/537.36 (KHTML,like Gecko)Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
        "Authorization" to key,
        "Content-Type" to "multipart/form-data"
    )
    val baseUrl = "https://sm.ms/api/v2"


//    fun loadImg(imgPath: String): JsonNode? {
////        val headers: Map<String, String> = mapOf(
////            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0;Win64;x64)AppleWebKit/537.36 (KHTML,like Gecko)Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
////            "Authorization" to key,
////            "Content-Type" to "multipart/form-data"
////        )
//
//        val files: Map<String, File> = mapOf("smfile" to File(imgPath))
//
//        val params: Map<String, String> = mapOf("format" to "json")
//
//        val imgData = HttpUtil.doPostJson(
//            url = "$baseUrl/upload",
//            files = files,
//            params = params,
//            headers = headers
//        )
//
//        return imgData["data"]
//    }

    fun removeImg(imgUrl: String) {
        HttpUtil.doGetStr(url = imgUrl, headers = headers)
        println("已经删除图片：$imgUrl")
    }


}