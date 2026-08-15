package bot.wuliang.controller

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WfMarketConfig.WF_MARKET_ITEMS_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_LICHES_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_RIVENS_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_RIVEN_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_SISTERS_VERSION_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.redis.RedisService
import bot.wuliang.service.WfLexiconService
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.service.WfRivenService
import bot.wuliang.utils.WfUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


/**
 * @description: Warframe 翻译词库
 * @author Nature Zero
 * @date 2024/5/20 下午11:38
 */
@Component
@ActionService
class WfTranslateLexicon {
    @Autowired
    lateinit var wfLexiconService: WfLexiconService

    @Autowired
    lateinit var wfRivenService: WfRivenService

    @Autowired
    lateinit var wfMarketItemService: WfMarketItemService

    @Autowired
    lateinit var redisService: RedisService

    @Autowired
    lateinit var wfUtil: WfUtil

    private fun updateCollectionIfChanged(
        collectionName: String,
        version: String,
        versionCacheKey: String,
        updater: () -> Unit
    ) {
        val cachedVersion = redisService.getValue(versionCacheKey)?.toString()
        if (cachedVersion == version) {
            logInfo("Warframe Market $collectionName 版本未变化，跳过更新")
            return
        }

        updater()
        redisService.setValue(versionCacheKey, version)
        logInfo("Warframe Market $collectionName 更新完成，版本：$version")
    }

    @SystemLog(businessName = "更新Warframe词库")
    @OptIn(DelicateCoroutinesApi::class)
    @AParameter
    @Executor(action = "更新词库")
    fun upDataWfTranslateLexicon(context: ExecutionContext) {
        GlobalScope.launch {
            try {
                // 获取中英文JSON数据并解析
                context.sender.sendText("因本次更新数据量较大，预计花费5-10分钟不等，请耐心等待")

                val marketVersions = runCatching { wfUtil.getMarketCollectionVersions() }
                    .onFailure { logError("获取 Warframe Market Collection 版本失败，跳过市场词库更新", it) }
                    .getOrNull()

                // 使用async并行执行插入操作
                val marketJob = async {
                    marketVersions?.get("items")?.let { version ->
                        runCatching {
                            updateCollectionIfChanged("物品", version, WF_MARKET_ITEMS_VERSION_KEY) {
                                wfMarketItemService.updateMarketItem(wfUtil.getMarketItems())
                            }
                        }.onFailure { logError("物品词库更新失败", it) }
                    }
                }
                val rivenJob = async {
                    marketVersions?.get("rivens")?.let { version ->
                        runCatching {
                            updateCollectionIfChanged("紫卡", version, WF_MARKET_RIVENS_VERSION_KEY) {
                                val rivenList = wfUtil.getRivenItems()
                                wfRivenService.insertRiven(rivenList)
                                redisService.setValue(WF_MARKET_RIVEN_KEY, rivenList)
                            }
                        }.onFailure { logError("紫卡词库更新失败", it) }
                    }
                }
                val lichJob = async {
                    marketVersions?.get("liches")?.let { version ->
                        runCatching {
                            updateCollectionIfChanged("赤毒玄骸", version, WF_MARKET_LICHES_VERSION_KEY) {
                                wfRivenService.insertRiven(wfUtil.getLichItems())
                            }
                        }.onFailure { logError("赤毒玄骸词库更新失败", it) }
                    }
                }
                val sisterJob = async {
                    marketVersions?.get("sisters")?.let { version ->
                        runCatching {
                            updateCollectionIfChanged("信条玄骸", version, WF_MARKET_SISTERS_VERSION_KEY) {
                                wfRivenService.insertRiven(wfUtil.getSisterItems())
                            }
                        }.onFailure { logError("信条玄骸词库更新失败", it) }
                    }
                }

                // 等待所有任务完成
                marketJob.await()
                rivenJob.await()
                lichJob.await()
                sisterJob.await()


                context.sender.sendText("词库更新完成")
            } finally {
                System.gc()
            }
        }
    }
}