package bot.wuliang.service.impl

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.CommonConfig
import bot.wuliang.entity.BotConfigEntity
import bot.wuliang.mapper.BotConfigMapper
import bot.wuliang.redis.RedisService
import bot.wuliang.service.BotConfigService
import bot.wuliang.text.Convert
import bot.wuliang.utils.BotConfigUtil
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class BotConfigServiceImpl : ServiceImpl<BotConfigMapper?, BotConfigEntity?>(), BotConfigService {
    @Autowired
    private lateinit var botConfigMapper: BotConfigMapper

    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var botConfigUtil: BotConfigUtil
    private val convert = Convert()

    @PostConstruct
    fun initConfig() {
        if (redisService.getListKey(CommonConfig.BOT_CONFIG_KEY + "*").isNotEmpty()) {
            loadingConfigCache()
            logInfo("加载配置缓存完成")
        }
    }

    override fun selectConfigByKey(configKey: String): String? {
        val cacheConfigKey = botConfigUtil.getCacheKey(configKey)
        val redisValue = convert.toStr(redisService.getValueTyped<String>(cacheConfigKey))
        if (!redisValue.isNullOrBlank()) {
            return redisValue
        }
        val queryWrapper = QueryWrapper<BotConfigEntity?>().apply {
            eq("config_key", configKey)
        }
        val configEntity = botConfigMapper.selectOne(queryWrapper)
        if (configEntity != null) {
            configEntity.configValue?.let { redisService.setValue(cacheConfigKey, it) }
            return configEntity.configValue
        }

        throw RuntimeException("未找到配置为：" + configKey + "的数据，请检查！")
    }

    override fun loadingConfigCache() {
        botConfigMapper.selectList(null).forEach { botConfig ->
            if (botConfig != null) {
                redisService.setValue(botConfigUtil.getCacheKey(botConfig.configKey!!), botConfig.configValue!!)
            }
        }
    }
}