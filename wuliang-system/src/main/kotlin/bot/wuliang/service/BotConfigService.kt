package bot.wuliang.service

import bot.wuliang.entity.BotConfigEntity
import com.baomidou.mybatisplus.extension.service.IService

interface BotConfigService : IService<BotConfigEntity?> {

    /**
     * 根据键名查询参数配置信息
     *
     * @param configKey 参数键名
     * @return 参数键值
     */
    fun selectConfigByKey(configKey: String): String?

    /**
     * 加载配置缓存数据
     */
    fun loadingConfigCache()
}