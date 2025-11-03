package bot.wuliang.controller

import bot.wuliang.config.ClimateApiConfig.CLIMATE_CACHE_CITY_KEY
import bot.wuliang.config.ClimateApiConfig.CLIMATE_CACHE_WEATHER_KEY
import bot.wuliang.utils.GetGeoApi
import bot.wuliang.exception.RespBean
import bot.wuliang.jacksonUtil.JacksonUtil.asFloatOrDefault
import bot.wuliang.module.City
import bot.wuliang.module.Weather
import bot.wuliang.redis.RedisService
import bot.wuliang.utils.TimeUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import bot.wuliang.utils.WeatherUtil
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit


/**
 *@Description:气候接口实现类
 *@Author zeng
 *@Date 2025/10/28 13:49
 */
@RestController
@RequestMapping("/climate")
class ClimateController(
    @Autowired private val geoApi: GetGeoApi,
    @Autowired val redisService: RedisService
) {
    private val weatherUtil = WeatherUtil()

    @GetMapping("/weather")
    fun getWeatherInfo(@RequestParam("city") city: String): RespBean {
        // 尝试从Redis缓存中获取数据
        val cachedWeather = redisService.getValueTyped<Weather>("$CLIMATE_CACHE_WEATHER_KEY:$city")
        if (cachedWeather != null) {
            return RespBean.success(cachedWeather)
        }

        val cityJson = geoApi.getCityData(city)
        if (!geoApi.checkCode(cityJson["code"].textValue())) {
            return RespBean.error("没有找到查询的城市信息，请检查是否输入错误")
        }
        val cityId = cityJson["location"][0]["id"].textValue()
        val weatherJson = geoApi.getWeatherData(cityId)
        val dailyJson = geoApi.getDailyInfo(cityId)
        val preJson = geoApi.getPreWeather(cityId)

        val weather = Weather(
            city = cityJson["location"][0]["name"].textValue(),
            updateTime = weatherJson["updateTime"].textValue().replace("T", " ").replace("+08:00", ""),
            temp = weatherJson["now"]["temp"].textValue(),
            text = weatherJson["now"]["text"].textValue(),
            feelsLike = weatherJson["now"]["feelsLike"].textValue(),
            humidity = weatherJson["now"]["humidity"].textValue(),
            pressure = weatherJson["now"]["pressure"].textValue(),
            vis = weatherJson["now"]["vis"].textValue(),
            windScale = weatherJson["now"]["windScale"].textValue(),
            windSpeed = weatherJson["now"]["windSpeed"].textValue(),
            windDir = weatherJson["now"]["windDir"].textValue(),
            wind360 = weatherJson["now"]["wind360"].textValue(),
            fxDate = preJson["daily"][0]["fxDate"].textValue(),
            sunrise = preJson["daily"][0]["sunrise"].textValue(),
            sunset = preJson["daily"][0]["sunset"].textValue(),
            textDay = preJson["daily"][0]["textDay"].textValue(),
            textNight = preJson["daily"][0]["textNight"].textValue(),
            tempMax = preJson["daily"][0]["tempMax"].textValue(),
            tempMin = preJson["daily"][0]["tempMin"].textValue(),
            sportText = dailyJson["daily"][0]?.get("text")?.textValue() ?: "",
            carText = dailyJson["daily"][1]?.get("text")?.textValue() ?: ""
        )

        // 将数据存入Redis缓存，过期时间5分钟
        redisService.setValueWithExpiry("$CLIMATE_CACHE_WEATHER_KEY:$city", weather, 5, TimeUnit.MINUTES)

        return RespBean.success(weather)
    }

    @RequestMapping("/geography")
    fun addGeoInfo(@RequestParam("city") city: String): RespBean {
        // 尝试从Redis缓存中获取数据
        val cachedCity = redisService.getValueTyped<City>("$CLIMATE_CACHE_CITY_KEY:$city")
        if (cachedCity != null) {
            return RespBean.success(cachedCity)
        }

        val cityJson = geoApi.getCityData(city)

        if (!geoApi.checkCode(cityJson["code"].textValue())) {
            return RespBean.error("没有找到查询的城市信息，请检查是否输入错误")
        }
        val cityLon = cityJson["location"][0]["lon"].asFloatOrDefault(0.0f)
        val cityLat = cityJson["location"][0]["lat"].asFloatOrDefault(0.0f)
        // 将获得的经纬度调整为统一格式
        val (cityLonChange, cityLatChange) = weatherUtil.formatLatAndLongitude(cityLon, cityLat)

        val cityData = City(
            name = cityJson["location"][0]["name"].textValue(),
            nowTime = TimeUtils.getInstantNow(),
            id = cityJson["location"][0]["id"].textValue(),
            lon = cityLonChange,
            lat = cityLatChange,
            country = cityJson["location"][0]["country"].textValue(),
            adm2 = cityJson["location"][0]["adm2"].textValue(),
            adm1 = cityJson["location"][0]["adm1"].textValue(),
            tz = cityJson["location"][0]["tz"].textValue(),
            utcOffset = cityJson["location"][0]["utcOffset"].textValue(),
            dst = if (cityJson["location"][0]["isDst"].booleanValue()) "否" else "是"
        )


        if (cityJson["location"][1] != null) {
            cityData.nextCity = cityJson["location"][1]["name"].textValue()
            cityData.nextCityId = cityJson["location"][1]["id"].textValue()

            val nextCityLon = cityJson["location"][1]["lon"].asFloatOrDefault(0.0f)
            val nextCityLat = cityJson["location"][1]["lat"].asFloatOrDefault(0.0f)
            val (nextCityLonChange, nextCityLatChange) = weatherUtil.formatLatAndLongitude(nextCityLon, nextCityLat)
            cityData.nextCityLonChange = nextCityLonChange
            cityData.nextCityLatChange = nextCityLatChange
            cityData.nextCountry = cityJson["location"][1]["country"].textValue()
            cityData.nextTz = cityJson["location"][1]["tz"].textValue()
        }

        // 将数据存入Redis缓存，过期时间5分钟
        redisService.setValueWithExpiry("$CLIMATE_CACHE_CITY_KEY:$city", cityData, 5, TimeUnit.MINUTES)

        return RespBean.success(cityData)
    }
}