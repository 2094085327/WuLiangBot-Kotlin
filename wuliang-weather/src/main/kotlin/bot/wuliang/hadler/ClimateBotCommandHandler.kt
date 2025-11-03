package bot.wuliang.hadler

import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.utils.BotUtils
import bot.wuliang.utils.GetGeoApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*
import java.util.regex.Matcher

@Component
@ActionService
class ClimateBotCommandHandler(@Autowired val webImgUtil: WebImgUtil, @Autowired private val geoApi: GetGeoApi) {

    @SystemLog(businessName = "获取城市天气信息")
    @AParameter
    @Executor(action = "\\b^天气\\s(\\S+)")
    fun getWeatherImg(context: BotUtils.Context, matcher: Matcher) {
        context.sendMsg("正在查询信息，请耐心等待")
        webImgUtil.deleteImgCache()

        val city = matcher.group(1)
        val imgName = "${city}天气-${UUID.randomUUID()}"

        val cityJson = geoApi.getCityData(city)

        if (!geoApi.checkCode(cityJson["code"].textValue())) {
            context.sendMsg("没有找到查询的城市信息，请检查是否输入错误")
            return
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/climate/weather?city=$city",
            imgName = imgName,
            element = "#app",
            waitElement = ".climate"
        )
        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)

        System.gc()
    }

    @SystemLog(businessName = "获取城市地理信息")
    @AParameter
    @Executor(action = "\\b^地理\\s(\\S+)")
    fun getGeoImg(context: BotUtils.Context, matcher: Matcher) {
        context.sendMsg("正在查询信息，请耐心等待")
        webImgUtil.deleteImgCache()

        val city = matcher.group(1)
        val imgName = "${city}地理-${UUID.randomUUID()}"

        val cityJson = geoApi.getCityData(city)

        if (!geoApi.checkCode(cityJson["code"].textValue())) {
            context.sendMsg("没有找到查询的城市信息，请检查是否输入错误")
            return
        }
        val imgData =
            WebImgUtil.ImgData(
                url = "http://${webImgUtil.frontendAddress}/climate/geography?city=$city",
                imgName = imgName,
                element = "#app",
                waitElement = ".climate"
            )
        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }
}