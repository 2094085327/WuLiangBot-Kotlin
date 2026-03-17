package bot.wuliang.controller

import bot.wuliang.parser.DropTableParser
import bot.wuliang.parser.model.ParsedDropSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


/**
 * @description: TODO
 * @author Nature Zero
 * @date 2024/11/24 18:59
 */

@RestController
@RequestMapping("/wfMission")
class WfMissionTypeController (
    private val parsers: List<DropTableParser>
)  {
    // 目标网页的 URL
    val url =
        "https://warframe-web-assets.nyc3.cdn.digitaloceanspaces.com/uploads/cms/hnfvc0o3jnfvc873njb03enrf56.html#missionRewardsm"

    // 创建 OkHttpClient 实例
    val client = OkHttpClient()

    @RequestMapping("/getMissionRewards")
    fun getMissionRewards(): List<ParsedDropSource>? {
        // 创建 Request 对象
        val request = Request.Builder()
            .url(url)
            .build()

        // 发送请求并获取响应
        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            // 获取响应的 HTML 内容
            val htmlContent = response.body.string()


            // 使用 Jsoup 解析 HTML
            val doc: Document = Jsoup.parse(htmlContent)

            response.close()


            val parser = parsers.find { it.support("missionRewards") } ?: return null
            val list = parser.parse(doc)
            return list



        } else {
            println("没有找到 id=missionRewards 的 h3 标签")
        }
        return null
    }
}

