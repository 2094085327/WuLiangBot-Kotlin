package bot.demo.txbot.geography

import bot.demo.txbot.common.utils.WebImgUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.stereotype.Component
import java.util.regex.Matcher


/**
 *@Description:
 *@Author zeng
 *@Date 2023/6/11 15:14
 *@User 86188
 */
@Shiro
@Component
class GeoMain {
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(.*)天气")
    fun getWeatherImg(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        if (event != null) {
            val geoApi = GetGeoApi()
            if (!geoApi.checkCode(geoApi.getWeatherData(matcher?.group(1) ?: ""))) {
                bot.sendMsg(event, "没有找到'${matcher?.group(1)}'的信息，请检查是否输入错误", false)
                return
            }
            val imgUrl =
                WebImgUtil().getImgFromWeb(url = "http://localhost:${WebImgUtil.usePort}/weather", channel = true)
            val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
            bot.sendMsg(event, sendMsg, false)
            // 群聊模式下通过图床发送图片
            // val imgData = GetGeoImg().loadImg(imgPath)
            // val imgUrl = imgData?.get("url")?.textValue()
            // val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
            // bot.sendMsg(event, sendMsg, false)
            // imgData?.get("delete")?.let { GetGeoImg().removeImg(it.textValue()) }
            System.gc()
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(.*)地理")
    fun getGeoImg(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        if (event != null) {
            val geoApi = GetGeoApi()
            if (!geoApi.checkCode(geoApi.getCityData(matcher?.group(1) ?: ""))) {
                bot.sendMsg(event, "没有找到'${matcher?.group(1)}'的信息，请检查是否输入错误", false)
                return
            }
            val imgUrl = WebImgUtil().getImgFromWeb(url = "http://localhost:${WebImgUtil.usePort}/geo", channel = true)
            val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
            bot.sendMsg(event, sendMsg, false)
            // 群聊模式下通过图床发送图片
            // val imgData = GetGeoImg().loadImg(imgPath)
            // val imgUrl = imgData?.get("url")?.textValue()
            // val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
            // bot.sendMsg(event, sendMsg, false)
            // imgData?.get("delete")?.let { GetGeoImg().removeImg(it.textValue()) }
            System.gc()
        }
    }

}