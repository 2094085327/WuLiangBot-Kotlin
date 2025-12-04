package bot.wuliang.config

import com.baidu.aip.ocr.AipOcr
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component


@Component
class AipOcrConfig(
    @Value("\${aip_ocr.appid}") var appid: String,
    @Value("\${aip_ocr.apikey}") var apikey: String,
    @Value("\${aip_ocr.secret}") var secret: String
) {
    @Bean
    fun aipOcrClient(): AipOcr = AipOcr(appid, apikey, secret)
}