package bot.wuliang.config

object ClimateApiConfig {
    /** 缓存key*/
    const val CLIMATE_CACHE_KEY = "Wuliang:Climate"

    const val CLIMATE_CACHE_WEATHER_KEY = "$CLIMATE_CACHE_KEY:Weather"

    const val CLIMATE_CACHE_CITY_KEY = "$CLIMATE_CACHE_KEY:City"

    private const val CLIMATE_BASE_API = "https://devapi.qweather.com/v7"

    const val CLIMATE_WEATHER_API = "$CLIMATE_BASE_API/weather"

    const val CLIMATE_INDICES_API = "$CLIMATE_BASE_API/indices"

    const val CLIMATE_CITY_API = "https://geoapi.qweather.com/v2/city"
}