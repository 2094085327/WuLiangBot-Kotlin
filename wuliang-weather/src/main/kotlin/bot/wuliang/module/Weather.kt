package bot.wuliang.module

import com.fasterxml.jackson.annotation.JsonProperty

data class Weather(
    // 城市信息
    @JsonProperty("city")
    val city: String? = null,
    @JsonProperty("updateTime")
    val updateTime: String? = null,
    @JsonProperty("temp")
    val temp: String? = null,
    @JsonProperty("text")
    val text: String? = null,
    @JsonProperty("feelsLike")
    val feelsLike: String? = null,
    @JsonProperty("humidity")
    val humidity: String? = null,
    @JsonProperty("pressure")
    val pressure: String? = null,
    @JsonProperty("vis")
    val vis: String? = null,
    @JsonProperty("windScale")
    val windScale: String? = null,
    @JsonProperty("windSpeed")
    val windSpeed: String? = null,
    @JsonProperty("windDir")
    val windDir: String? = null,
    @JsonProperty("wind360")
    val wind360: String? = null,
    @JsonProperty("fxDate")
    val fxDate: String? = null,
    @JsonProperty("sunrise")
    val sunrise: String? = null,
    @JsonProperty("sunset")
    val sunset: String? = null,
    @JsonProperty("textDay")
    val textDay: String? = null,
    @JsonProperty("textNight")
    val textNight: String? = null,
    @JsonProperty("tempMax")
    val tempMax: String? = null,
    @JsonProperty("tempMin")
    val tempMin: String? = null,

    // 生活指数
    @JsonProperty("sportText")
    val sportText: String? = null,
    @JsonProperty("carText")
    val carText: String
)
