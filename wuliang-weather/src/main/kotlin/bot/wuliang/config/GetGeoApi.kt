package bot.wuliang.config

import bot.wuliang.httpUtil.HttpUtil
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 *@Description: 天气功能接口请求与数据获取
 *@Author zeng
 *@Date 2023/6/11 16:21
 *@User 86188
 */
@Component
class GetGeoApi {
    @Value("\${weather.key}")
    lateinit var key: String

    object GeoData {
        var cityJson: JsonNode? = null
        var weatherJson: JsonNode? = null
        var dailyJson: JsonNode? = null
        var prediction: JsonNode? = null
    }

    fun checkCode(code: String): Boolean {
        return code == "200"
    }

    fun getCityData(city: String): String {
        val url = "https://geoapi.qweather.com/v2/city/lookup?location=$city&key=$key"
        val json = HttpUtil.doGetJson(url)
        GeoData.cityJson = json

        return json["code"].textValue()
    }

    fun getWeatherData(city: String): String {
        val cityCode = getCityData(city)
        if (!checkCode(cityCode)) return cityCode
        val dailyCode = getDailyInfo(city)
        if (!checkCode(dailyCode!!)) return dailyCode
        val perCode = getPreWeather()
        if (!checkCode(perCode!!)) return perCode

        val cityID = GeoData.cityJson?.get("location")?.get(0)?.get("id")?.textValue()
        val url = "https://devapi.qweather.com/v7/weather/now?location=${cityID}&key=$key"
        val json = HttpUtil.doGetJson(url)
        val code = json["code"].textValue()
        if (code == "200") GeoData.weatherJson = json
        return code
    }

    fun getDailyInfo(city: String): String? {
        val cityID = GeoData.cityJson?.get("location")?.get(0)?.get("id")?.textValue()
        val url = "https://devapi.qweather.com/v7/indices/1d?type=1,2&location=${cityID}&key=$key"
        val json = HttpUtil.doGetJson(url)
        val code = json["code"].textValue()
        if (code == "200") GeoData.dailyJson = json
        return code
    }

    fun getPreWeather(): String? {
        val cityID = GeoData.cityJson?.get("location")?.get(0)?.get("id")?.textValue()
        val url = "https://devapi.qweather.com/v7/weather/3d?location=${cityID}&key=$key"
        val json = HttpUtil.doGetJson(url)
        val code = json["code"].textValue()
        if (code == "200") GeoData.prediction = json
        return code
    }

    fun getGeoData(): GeoData {
        return GeoData
    }
}