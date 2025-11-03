package bot.wuliang.module

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class City(
    // 城市名称
    @JsonProperty("name") val name: String? = null,

    // 城市ID
    @JsonProperty("id") val id: String? = null,

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JsonProperty("nowTime") val nowTime: Instant? = null,

    // 经纬度
    @JsonProperty("lon") val lon: String? = null,
    @JsonProperty("lat") val lat: String? = null,

    // 国家
    @JsonProperty("country") val country: String? = null,

    // 地区信息
    @JsonProperty("adm1") val adm1: String? = null, // 省/州
    @JsonProperty("adm2") val adm2: String? = null, // 市

    // 时区信息
    @JsonProperty("tz") val tz: String? = null,
    @JsonProperty("utcOffset") val utcOffset: String? = null,

    // 是否处于夏令时
    @JsonProperty("dst") val dst: String? = null,
    @JsonProperty("nextCity") var nextCity: String? = null,
    @JsonProperty("nextCityId") var nextCityId: String? = null,
    @JsonProperty("nextCityLonChange") var nextCityLonChange: String? = null,
    @JsonProperty("nextCityLatChange") var nextCityLatChange: String? = null,
    @JsonProperty("nextCountry") var nextCountry: String? = null,
    @JsonProperty("nextTz") var nextTz: String? = null
)
