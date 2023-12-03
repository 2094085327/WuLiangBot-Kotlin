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
        val carText = dailyJson["daily"][1]["text"].textValue()
        val sportText = dailyJson["daily"][0]["text"].textValue()
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

        model.addAttribute("city", city)
        model.addAttribute("time", time)
        model.addAttribute("temp", "${temp}°C $text")
        model.addAttribute("tempNow", "${temp}°C")
        model.addAttribute("carText", carText)
        model.addAttribute("sportText", sportText)
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
        var cityLon = cityJson["location"][0]["lon"].textValue().toFloat()
        var cityLat = cityJson["location"][0]["lat"].textValue().toFloat()
        var country = cityJson["location"][0]["country"].textValue()
        val cityLonChange: String?
        var cityLatChange: String? = null
        // 将获得的经纬度调整为统一格式
        if (cityLon < 0) {
            cityLon = -cityLon
            cityLonChange = "$cityLon°W"
        } else {
            cityLonChange = "$cityLon°E"
        }
        if (cityLat < 0) {
            cityLat = -cityLat
            cityLatChange = "$cityLat°S"
        } else {
            cityLatChange = "$cityLat°N"
        }

        model.addAttribute("city", city)
        model.addAttribute("time", formattedTime)
        model.addAttribute("cityId", cityId)
        model.addAttribute("lon", cityLonChange)
        model.addAttribute("lat", cityLatChange)
        model.addAttribute("country", country)
        return "Geography/Geography"
    }
}