package bot.demo.txbot.geography

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 *@Description:
 *@Author zeng
 *@Date 2023/6/11 16:56
 *@User 86188
 */
@Controller
class GeoWeb {

    fun formatLatAndLongitude(lon: Float, lat: Float): Pair<String, String> {
        val cityLon = if (lon < 0) -lon else lon
        val cityLat = if (lat < 0) -lat else lat
        val cityLonChange = "${cityLon}°${if (lon < 0) "W" else "E"}"
        val cityLatChange = "${cityLat}°${if (lat < 0) "S" else "N"}"
        return Pair(cityLonChange, cityLatChange)
    }

    @RequestMapping("/weather")
    fun addWeatherInfo(model: Model): String {
        val geoData = GetGeoApi().getGeoData()
        val cityJson = geoData.cityJson!!
        val weatherJson = geoData.weatherJson!!
        val dailyJson = geoData.dailyJson!!
        val preJson = geoData.prediction!!

        val city = cityJson["location"][0]["name"].textValue()
        val time = weatherJson["updateTime"].textValue().replace("T", " ").replace("+08:00", "")
        val temp = weatherJson["now"]["temp"].textValue()
        val text = weatherJson["now"]["text"].textValue()
        val feelsLike = weatherJson["now"]["feelsLike"].textValue()
        val humidity = weatherJson["now"]["humidity"].textValue()
        val pressure = weatherJson["now"]["pressure"].textValue()
        val vis = weatherJson["now"]["vis"].textValue()
        val windScale = weatherJson["now"]["windScale"].textValue()
        val windSpeed = weatherJson["now"]["windSpeed"].textValue()
        val windDir = weatherJson["now"]["windDir"].textValue()
        val wind360 = weatherJson["now"]["wind360"].textValue()
        val fxDate = preJson["daily"][0]["fxDate"].textValue()
        val sunrise = preJson["daily"][0]["sunrise"].textValue()
        val sunset = preJson["daily"][0]["sunset"].textValue()
        val textDay = preJson["daily"][0]["textDay"].textValue()
        val textNight = preJson["daily"][0]["textNight"].textValue()
        val tempMax = preJson["daily"][0]["tempMax"].textValue()
        val tempMin = preJson["daily"][0]["tempMin"].textValue()

        val names = listOf("sportText", "carText")
        dailyJson["daily"].forEachIndexed { index, node ->
            val textNode = node["text"]
            if (textNode != null) {
                val textValue = textNode.textValue()
                if (index < names.size) {
                    model.addAttribute(names[index], textValue)
                }
            }
        }

        model.addAttribute("city", city)
        model.addAttribute("time", time)
        model.addAttribute("temp", "${temp}°C $text")
        model.addAttribute("tempNow", "${temp}°C")
        model.addAttribute("feelsLike", "${feelsLike}°C")
        model.addAttribute("humidity", "${humidity}%")
        model.addAttribute("text", text)
        model.addAttribute("pressure", "$pressure hpa")
        model.addAttribute("vis", "$vis km")
        model.addAttribute("windDir", "${windDir}${wind360}°")
        model.addAttribute("windInfo", "${windScale}级-${windSpeed} m/s")
        model.addAttribute("fxDate", fxDate)
        model.addAttribute("sunrise", sunrise)
        model.addAttribute("sunset", sunset)
        model.addAttribute("textDay", textDay)
        model.addAttribute("textNight", textNight)
        model.addAttribute("tempMax", "${tempMax}°C")
        model.addAttribute("tempMin", "${tempMin}°C")

        return "Geography/Weather"
    }

    @RequestMapping("/geo")
    fun addGeoInfo(model: Model): String {
        val geoData = GetGeoApi().getGeoData()
        val cityJson = geoData.cityJson!!
        val currentTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-M-d H:m")
        val formattedTime = currentTime.format(formatter)

        val city = cityJson["location"][0]["name"].textValue()
        val cityId = cityJson["location"][0]["id"].textValue()
        val cityLon = cityJson["location"][0]["lon"].textValue().toFloat()
        val cityLat = cityJson["location"][0]["lat"].textValue().toFloat()
        val country = cityJson["location"][0]["country"].textValue()
        val adm2 = cityJson["location"][0]["adm2"].textValue()
        val adm1 = cityJson["location"][0]["adm1"].textValue()
        val tz = cityJson["location"][0]["tz"].textValue()
        val utcOffset = cityJson["location"][0]["utcOffset"].textValue()
        val isDst = cityJson["location"][0]["isDst"].asInt()
        // 将获得的经纬度调整为统一格式
        val (cityLonChange, cityLatChange) = formatLatAndLongitude(cityLon, cityLat)

        val isDstText: String = if (isDst == 0) "否" else "是"

        model.addAttribute("city", city)
        model.addAttribute("time", formattedTime)
        model.addAttribute("cityId", cityId)
        model.addAttribute("lon", cityLonChange)
        model.addAttribute("lat", cityLatChange)
        model.addAttribute("country", country)
        model.addAttribute("adm2", adm2)
        model.addAttribute("adm1", adm1)
        model.addAttribute("tz", tz)
        model.addAttribute("utcOffset", utcOffset)
        model.addAttribute("isDst", isDstText)

        if (cityJson["location"][1] != null) {

            val nextCity = cityJson["location"][1]["name"].textValue()
            val nextCityId = cityJson["location"][1]["id"].textValue()
            val nextCityLon = cityJson["location"][1]["lon"].textValue().toFloat()
            val nextCityLat = cityJson["location"][1]["lat"].textValue().toFloat()
            val nextCountry = cityJson["location"][1]["country"].textValue()
            val nextTz = cityJson["location"][1]["tz"].textValue()

            val (nextCityLonChange, nextCityLatChange) = formatLatAndLongitude(nextCityLon, nextCityLat)

            model.addAttribute("nextCity", nextCity)
            model.addAttribute("nextCityId", nextCityId)
            model.addAttribute("nextCityLonChange", nextCityLonChange)
            model.addAttribute("nextCityLatChange", nextCityLatChange)
            model.addAttribute("nextCountry", nextCountry)
            model.addAttribute("nextTz", nextTz)
        }
        return "Geography/Geography"
    }
}