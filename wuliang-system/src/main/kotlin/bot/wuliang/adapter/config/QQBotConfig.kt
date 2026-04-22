package bot.wuliang.adapter.config

import io.github.kloping.qqbot.Starter
import io.github.kloping.qqbot.api.Intents
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class QQBotConfig(
    @Value("\${wuLiang.bot-config.appid}") private val appid: String,
    @Value("\${wuLiang.bot-config.token}") private val token: String,
    @Value("\${wuLiang.bot-config.secret}") private val secret: String
) {

    /**
     * 创建 QQBot 启动器
     */
    @Bean
    fun qqStarter(): Starter {
        val starter = Starter(appid, token, secret)
        starter.config.code = Intents.PUBLIC_INTENTS.and(Intents.GROUP_INTENTS)
        return starter
    }
}