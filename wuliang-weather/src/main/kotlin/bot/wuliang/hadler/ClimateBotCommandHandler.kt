package bot.wuliang.hadler

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
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
    suspend fun getWeatherImg(context: ExecutionContext, matcher: Matcher) {
        context.sender.sendText("正在查询信息，请耐心等待")
        webImgUtil.deleteImgCache()

        val city = matcher.group(1)
        val imgName = "${city}天气-${UUID.randomUUID()}"

        val cityJson = geoApi.getCityData(city)

        if (!geoApi.checkCode(cityJson["code"].textValue())) {
            context.sender.sendText("没有找到查询的城市信息，请检查是否输入错误")
            return
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/climate/weather?city=$city",
            imgName = imgName,
            element = "#app",
            waitElement = ".climate"
        )
        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)

        webImgUtil.deleteImg(imgData = imgData)

        System.gc()
    }

    @SystemLog(businessName = "获取城市地理信息")
    @AParameter
    @Executor(action = "\\b^地理\\s(\\S+)")
    suspend fun getGeoImg(context: ExecutionContext, matcher: Matcher) {
        context.sender.sendText("正在查询信息，请耐心等待")
        webImgUtil.deleteImgCache()

        val city = matcher.group(1)
        val imgName = "${city}地理-${UUID.randomUUID()}"

        val cityJson = geoApi.getCityData(city)

        if (!geoApi.checkCode(cityJson["code"].textValue())) {
            context.sender.sendText("没有找到查询的城市信息，请检查是否输入错误")
            return
        }
        val imgData =
            WebImgUtil.ImgData(
                url = "http://${webImgUtil.frontendAddress}/climate/geography?city=$city",
                imgName = imgName,
                element = "#app",
                waitElement = ".climate"
            )
        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)

        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }
}