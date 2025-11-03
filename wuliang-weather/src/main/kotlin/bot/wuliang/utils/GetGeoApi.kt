package bot.wuliang.utils

import bot.wuliang.config.ClimateApiConfig.CLIMATE_CITY_API
import bot.wuliang.config.ClimateApiConfig.CLIMATE_INDICES_API
import bot.wuliang.config.ClimateApiConfig.CLIMATE_WEATHER_API
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

    fun checkCode(code: String): Boolean {
        return code == "200"
    }

    fun getCityData(city: String): JsonNode {
        val url = "$CLIMATE_CITY_API/lookup?location=$city&key=$key"

        return HttpUtil.doGetJson(url)
    }

    fun getWeatherData(cityId: String): JsonNode {
        val url = "$CLIMATE_WEATHER_API/now?location=${cityId}&key=$key"
        return HttpUtil.doGetJson(url)
    }

    fun getDailyInfo(cityId: String): JsonNode {
        val url = "$CLIMATE_INDICES_API/1d?type=1,2&location=${cityId}&key=$key"
        return HttpUtil.doGetJson(url)
    }

    fun getPreWeather(cityId: String): JsonNode {
        val url = "$CLIMATE_WEATHER_API/3d?location=${cityId}&key=$key"
        return HttpUtil.doGetJson(url)
    }
}