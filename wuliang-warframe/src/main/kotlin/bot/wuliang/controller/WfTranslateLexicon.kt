package bot.wuliang.controller

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WfMarketConfig.WF_MARKET_ITEMS_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_LICHES_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_RIVENS_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_SISTERS_VERSION_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.redis.RedisService
import bot.wuliang.riven.RivenCatalogSync
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.service.WfRivenService
import bot.wuliang.utils.WfUtil
import bot.wuliang.translation.PublicExportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component


/**
 * @description: Warframe 翻译词库
 * @author Nature Zero
 * @date 2024/5/20 下午11:38
 */
@Component
@ActionService
class WfTranslateLexicon(
    private val wfRivenService: WfRivenService,
    private val wfMarketItemService: WfMarketItemService,
    private val redisService: RedisService,
    private val wfUtil: WfUtil,
    private val rivenCatalogSync: RivenCatalogSync,
    private val translations: PublicExportService,
) {
    /**
     * 手动检查文件差异，仅报告待更新或待修复内容，不切换当前词库。
     */
    @SystemLog(businessName = "检查Public Export Plus词库")
    @AParameter
    @Executor(action = "检查世界状态词库更新")
    suspend fun checkWorldStateTranslations(context: ExecutionContext) {
        syncTranslations(context, apply = false)
    }

    /**
     * 手动下载、校验并应用 Plus 快照；新翻译版本同时隔离旧结果和图片缓存。
     */
    @SystemLog(businessName = "更新Public Export Plus词库")
    @AParameter
    @Executor(action = "更新世界状态词库")
    suspend fun updateWorldStateTranslations(context: ExecutionContext) {
        syncTranslations(context, apply = true)
    }

    /**
     * 共用检查和更新入口，将阻塞 IO 放到专用调度器，并分开处理操作失败与通知失败。
     */
    private suspend fun syncTranslations(context: ExecutionContext, apply: Boolean) {
        sendOperationNotice(context, if (apply) "开始同步 Public Export Plus 词库" else "开始检查 Public Export Plus 文件版本", "开始")
        val result = try {
            withContext(Dispatchers.IO) { if (apply) translations.update() else translations.check() }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logError("Public Export Plus 词库操作失败", error)
            sendOperationNotice(context, "词库操作失败，保留已应用快照：${error.message}", "失败")
            return
        }
        val message = "${if (apply) "词库已应用" else "检查完成"}：${result.commit.take(12)}，" +
            "变化或待修复 ${result.changed.size} 个文件，移除 ${result.removed.size} 个文件" +
            if (!apply) "；本次未应用更新" else ""
        logInfo("Public Export Plus $message")
        sendOperationNotice(context, message, "完成")
    }

    /** 通知投递失败不改变后台操作结果，协程取消仍向上传播。 */
    private suspend fun sendOperationNotice(
        context: ExecutionContext,
        message: String,
        phase: String
    ) {
        try {
            context.sender.sendText(message)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logError("Public Export Plus 词库 ${phase}通知发送失败，不影响操作结果", error)
        }
    }

    /**
     * 版本不变时跳过同步，只有更新器成功返回后才写入版本标记，以便失败后重试。
     */
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
            if (!translations.ready) {
                context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
                return@launch
            }
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
                            updateCollectionIfChanged("物品", "$version:${translations.revision}", WF_MARKET_ITEMS_VERSION_KEY) {
                                wfMarketItemService.updateMarketItem(wfUtil.getMarketItems())
                            }
                        }.onFailure { logError("物品词库更新失败", it) }
                    }
                }
                val rivenJob = async {
                    marketVersions?.get("rivens")?.let { version ->
                        runCatching {
                            // 属性恢复 Market 中文后，即使远端版本不变也要修复曾被 Plus 回退值覆盖的记录。
                            updateCollectionIfChanged("紫卡", "$version:${translations.revision}:market-attributes-v1", WF_MARKET_RIVENS_VERSION_KEY) {
                                rivenCatalogSync.sync()
                            }
                        }.onFailure { logError("紫卡词库更新失败", it) }
                    }
                }
                val lichJob = async {
                    marketVersions?.get("liches")?.let { version ->
                        runCatching {
                            updateCollectionIfChanged("赤毒玄骸", "$version:${translations.revision}", WF_MARKET_LICHES_VERSION_KEY) {
                                wfRivenService.insertRiven(wfUtil.getLichItems())
                            }
                        }.onFailure { logError("赤毒玄骸词库更新失败", it) }
                    }
                }
                val sisterJob = async {
                    marketVersions?.get("sisters")?.let { version ->
                        runCatching {
                            updateCollectionIfChanged("信条玄骸", "$version:${translations.revision}", WF_MARKET_SISTERS_VERSION_KEY) {
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
