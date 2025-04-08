package bot.wuliang.utils

import bot.wuliang.config.CommonConfig
import org.springframework.stereotype.Component

@Component
class BotConfigUtil {


    /**
     * 设置cache key
     *
     * @param configKey 参数键
     * @return 缓存键key
     */
    fun getCacheKey(configKey: String): String {
        return CommonConfig.BOT_CONFIG_KEY + configKey
    }
}