package bot.demo.txbot.geography

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.logAop.SystemLog
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
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
@Component
@ActionService
class GeoMain(
    @Autowired private val geoApi: GetGeoApi,
    @Autowired val webImgUtil: WebImgUtil
) {
    @SystemLog(businessName = "获取城市天气信息")
    @AParameter
    @Executor(action = "\\b^天气\\s(\\S+)")
    fun getWeatherImg(context: Context, matcher: Matcher) {
        context.sendMsg("正在查询信息，请耐心等待")
        webImgUtil.deleteImgCache()

        val city = matcher.group(1)
        val imgName = "${city}天气-${UUID.randomUUID()}"


        if (!geoApi.checkCode(geoApi.getWeatherData(city))) {
            context.sendMsg("没有找到查询的城市信息，请检查是否输入错误")
            return
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/weather",
            imgName = imgName,
            openCache = false
        )
        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)

        System.gc()
    }

    @SystemLog(businessName = "获取城市地理信息")
    @AParameter
    @Executor(action = "\\b^地理\\s(\\S+)")
    fun getGeoImg(context: Context, matcher: Matcher) {
        context.sendMsg("正在查询信息，请耐心等待")
        webImgUtil.deleteImgCache()

        val city = matcher.group(1)
        val imgName = "${city}地理-${UUID.randomUUID()}"

        if (!geoApi.checkCode(geoApi.getWeatherData(city))) {
            context.sendMsg("没有找到查询的城市信息，请检查是否输入错误")
            return
        }
        val imgData =
            WebImgUtil.ImgData(url = "http://localhost:${webImgUtil.usePort}/geo", imgName = imgName, openCache = false)
        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }

}