package bot.demo.txbot.common.utils

import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component


/**
 * @description: 一些其他配置和工具类
 * @author Nature Zero
 * @date 2024/2/8 10:00
 */
@Component
@Configuration
class OtherUtil {
    companion object {
        var gskPort: String = ""
    }

    @Value("\${gensokyo_config.port}")
    fun getKey(gensokyoPort: String) {
        gskPort = gensokyoPort
    }

    fun getRealId(event: AnyMessageEvent): String {
        val fictitiousId = event.userId
        return HttpUtil.doGetJson("http://localhost:$gskPort/getid?type=2&id=$fictitiousId")["id"].textValue()
    }
    fun getRealId(event: PrivateMessageEvent): String {
        val fictitiousId = event.userId
        return HttpUtil.doGetJson("http://localhost:$gskPort/getid?type=2&id=$fictitiousId")["id"].textValue()
    }

}