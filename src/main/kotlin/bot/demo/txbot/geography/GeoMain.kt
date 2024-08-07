package bot.demo.txbot.geography

import bot.demo.txbot.common.utils.WebImgUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*
import java.util.regex.Matcher


/**
 *@Description:
 *@Author zeng
 *@Date 2023/6/11 15:14
 *@User 86188
 */
@Shiro
@Component
class GeoMain(
    @Autowired private val geoApi: GetGeoApi,
    @Autowired val webImgUtil: WebImgUtil
) {


    private fun sendNewImage(bot: Bot, event: AnyMessageEvent?, imgName: String, city: String, webUrl: String) {

        if (!geoApi.checkCode(geoApi.getWeatherData(city))) {
            bot.sendMsg(event, "没有找到查询的城市信息，请检查是否输入错误", false)
            return
        }
        val imgData = WebImgUtil.ImgData(url = webUrl, imgName = imgName, openCache = false)
        webImgUtil.sendNewImage(bot, event, imgData)
        webImgUtil.deleteImgByQiNiu(imgData = imgData)
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "天气 (.*)")
    fun getWeatherImg(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        if (event == null) return

        bot.sendMsg(event, "正在查询信息，请耐心等待", false)
        webImgUtil.deleteImgCache()

        val city = matcher?.group(1)
        val imgName = "${city}天气-${UUID.randomUUID()}"
        sendNewImage(bot, event, imgName, city ?: "", "http://localhost:${webImgUtil.usePort}/weather")

        System.gc()
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "地理 (.*)")
    fun getGeoImg(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        if (event == null) return

        bot.sendMsg(event, "正在查询信息，请耐心等待", false)
        webImgUtil.deleteImgCache()

        val city = matcher?.group(1)
        val imgName = "${city}地理-${UUID.randomUUID()}"
        sendNewImage(bot, event, imgName, city ?: "", "http://localhost:${webImgUtil.usePort}/geo")
        System.gc()
    }

}