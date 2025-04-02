package utils

class WeatherUtil {
    fun formatLatAndLongitude(lon: Float, lat: Float): Pair<String, String> {
        val cityLon = if (lon < 0) -lon else lon
        val cityLat = if (lat < 0) -lat else lat
        val cityLonChange = "${cityLon}°${if (lon < 0) "W" else "E"}"
        val cityLatChange = "${cityLat}°${if (lat < 0) "S" else "N"}"
        return Pair(cityLonChange, cityLatChange)
    }

}