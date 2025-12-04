package bot.wuliang.aipOcr

object AipOcrConstants {
    private const val BASE_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/"
    const val BAIDU_OAUTH_URL = "https://aip.baidubce.com/oauth/2.0/token"
    const val BAIDU_BASIC_OCR_URL = BASE_URL + "accurate_basic"
    const val BAIDU_TOKEN_REDIS_KEY = "Wuliang:Baidu:OauthToken"
}