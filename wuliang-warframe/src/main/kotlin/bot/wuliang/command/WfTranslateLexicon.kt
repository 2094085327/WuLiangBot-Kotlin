package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WfMarketConfig.WF_MARKET_ITEMS_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_ITEM_LEXICON_KEY_PREFIX
import bot.wuliang.config.WfMarketConfig.WF_MARKET_LICHES_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_RIVENS_VERSION_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_SISTERS_VERSION_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.market.MarketCatalogSource
import bot.wuliang.redis.RedisService
import bot.wuliang.riven.RivenCatalogSync
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.service.WfRivenService
import bot.wuliang.translation.PublicExportService
import kotlinx.coroutines.*
import org.springframework.stereotype.Component

/**
 * @description: Warframe 翻译词库
 * @author Nature Zero
 * @date 2024/5/20 下午11:38
 */
@Component
@ActionService
class WfTranslateLexicon(
    private val marketCatalogSource: MarketCatalogSource,
    private val wfRivenService: WfRivenService,
    private val wfMarketItemService: WfMarketItemService,
    private val redisService: RedisService,

    private val rivenCatalogSync: RivenCatalogSync,
    private val translations: PublicExportService,
) {
    /**
     * 手动检查文件差异，仅报告待更新或待修复内容，不切换当前词库。
     */
    @SystemLog(businessName = "检查Warframe世界状态词库")
    @AParameter
    @Executor(action = "检查世界状态词库更新")
    suspend fun checkWorldStateTranslations(context: ExecutionContext) {
        syncTranslations(context, apply = false)
    }

    /**
     * 手动下载、校验并应用官方对象导出、Plus 补充数据和完整语言表；新版本同时隔离旧结果和图片缓存。
     */
    @SystemLog(businessName = "更新Warframe世界状态词库")
    @AParameter
    @Executor(action = "更新世界状态词库")
    suspend fun updateWorldStateTranslations(context: ExecutionContext) {
        syncTranslations(context, apply = true)
    }

    /**
     * 共用检查和更新入口，将阻塞 IO 放到专用调度器，并分开处理操作失败与通知失败。
     */
    private suspend fun syncTranslations(context: ExecutionContext, apply: Boolean) {
        sendOperationNotice(
            context,
            if (apply) "开始同步官方对象导出、Plus 补充数据和完整游戏语言表" else "开始检查官方对象导出、Plus 补充数据和完整游戏语言表版本",
            "开始"
        )
        val result = try {
            withContext(Dispatchers.IO) { if (apply) translations.update() else translations.check() }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            logError("世界状态词库操作失败", error)
            sendOperationNotice(context, "词库操作失败，保留已应用快照：${error.message}", "失败")
            return
        }
        val sources = result.sources.entries.joinToString("，") { (source, commit) -> "$source ${commit.take(12)}" }
        val message = "${if (apply) "词库已应用" else "检查完成"}：${sources.ifBlank { result.commit.take(12) }}，" +
                "变化或待修复 ${result.changed.size} 个文件，移除 ${result.removed.size} 个文件" +
                if (!apply) "；本次未应用更新" else ""
        logInfo("世界状态词库 $message")
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
            logError("Warframe 世界状态词库 ${phase}通知发送失败，不影响操作结果", error)
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

    /** 检查 Market 集合版本，按变更同步物品、紫卡及玄骸目录，并向当前会话反馈执行结果。 */
    @SystemLog(businessName = "更新Warframe词库")
    @OptIn(DelicateCoroutinesApi::class)
    @AParameter
    @Executor(action = "更新词库")
    fun upDataWfTranslateLexicon(context: ExecutionContext) {
        GlobalScope.launch {
            if (!translations.ready) {
                context.sender.sendText("世界状态词库尚未初始化，本次仅同步 Market 物品目录，跳过紫卡和玄骸武器目录")
            }
            try {
                // 获取中英文JSON数据并解析
                context.sender.sendText("因本次更新数据量较大，预计花费5-10分钟不等，请耐心等待")

                val marketVersions = runCatching { marketCatalogSource.getMarketCollectionVersions() }
                    .onFailure { logError("获取 Warframe Market Collection 版本失败，跳过市场词库更新", it) }
                    .getOrNull()

                // 使用async并行执行插入操作
                val marketJob = async {
                    marketVersions?.get("items")?.let { version ->
                        runCatching {
                            // 切换名称来源后强制同步一次，即使 Market 版本未变也修复已存的英文套装名。
                            updateCollectionIfChanged("物品", "$version:market-zh-v1", WF_MARKET_ITEMS_VERSION_KEY) {
                                wfMarketItemService.updateMarketItem(marketCatalogSource.getMarketItems())
                                // 查询缓存保存完整物品实体，同步成功后清理，避免继续展示旧名称。
                                val keys = redisService.getListKey("$WF_MARKET_ITEM_LEXICON_KEY_PREFIX*")
                                if (keys.isNotEmpty()) redisService.deleteKey(keys)
                            }
                        }.onFailure { logError("物品词库更新失败", it) }
                    }
                }
                val rivenJob = async {
                    if (!translations.ready) return@async
                    marketVersions?.get("rivens")?.let { version ->
                        runCatching {
                            // 属性恢复 Market 中文后，即使远端版本不变也要修复曾被 Plus 回退值覆盖的记录。
                            updateCollectionIfChanged(
                                "紫卡",
                                "$version:${translations.revision}:market-attributes-v1",
                                WF_MARKET_RIVENS_VERSION_KEY
                            ) {
                                rivenCatalogSync.sync()
                            }
                        }.onFailure { logError("紫卡词库更新失败", it) }
                    }
                }
                val lichJob = async {
                    if (!translations.ready) return@async
                    marketVersions?.get("liches")?.let { version ->
                        runCatching {
                            updateCollectionIfChanged(
                                "赤毒玄骸",
                                "$version:${translations.revision}",
                                WF_MARKET_LICHES_VERSION_KEY
                            ) {
                                wfRivenService.insertRiven(marketCatalogSource.getLichItems())
                            }
                        }.onFailure { logError("赤毒玄骸词库更新失败", it) }
                    }
                }
                val sisterJob = async {
                    if (!translations.ready) return@async
                    marketVersions?.get("sisters")?.let { version ->
                        runCatching {
                            updateCollectionIfChanged(
                                "信条玄骸",
                                "$version:${translations.revision}",
                                WF_MARKET_SISTERS_VERSION_KEY
                            ) {
                                wfRivenService.insertRiven(marketCatalogSource.getSisterItems())
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