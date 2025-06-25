package bot.wuliang.httpUtil

import bot.wuliang.botLog.logUtil.LoggerUtils.logDebug
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Proxy
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore

/**
 * 代理管理器，负责代理的并发控制和冷却管理
 */
class ProxyManager(private val proxies: List<Proxy>) {
    private val proxyStates = mutableMapOf<Proxy, ProxyState>()
    private val mutex = Mutex()
    private val availableProxies = ConcurrentLinkedQueue(proxies)

    // 使用 CoroutineScope
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class ProxyState(
        var currentConcurrency: Int = 0,
        var lastUsedTime: Long = 0,
        var isInCooldown: Boolean = false,
        var cooldownJob: Job? = null
    )

    init {
        proxies.forEach { proxy ->
            proxyStates[proxy] = ProxyState()
        }
    }

    // 修复代理获取逻辑
    suspend fun acquireProxy(timeout: Long = 5000L): Proxy {
        return withTimeout(timeout) {
            // 使用信号量来控制代理的并发获取
            val semaphore = Semaphore(0)
            var acquiredProxy: Proxy? = null

            mutex.withLock {
                if (availableProxies.isNotEmpty()) {
                    acquiredProxy = availableProxies.poll()
                }
            }

            if (acquiredProxy != null) {
                logDebug("成功获取代理 $acquiredProxy，剩余可用代理: ${availableProxies.size}")
                return@withTimeout acquiredProxy!!
            } else {
                // 如果当前没有可用代理，启动一个异步任务监听代理释放
                scope.launch {
                    while (true) {
                        delay(100) // 定期检查是否有代理被释放
                        mutex.withLock {
                            if (availableProxies.isNotEmpty()) {
                                acquiredProxy = availableProxies.poll()
                                semaphore.release()
                                return@launch
                            }
                        }
                    }
                }

                semaphore.acquire() // 等待代理可用
                return@withTimeout acquiredProxy!!
            }
        }
    }


    // 修复代理释放逻辑
    fun releaseProxy(proxy: Proxy) {
        try {
            // 确保代理不会重复添加到队列中
            if (!availableProxies.contains(proxy)) {
                availableProxies.offer(proxy)
                logDebug("释放代理 $proxy，当前可用代理数: ${availableProxies.size}")
            } else {
                logWarn("代理 $proxy 已在可用队列中，跳过释放")
            }
        } catch (e: Exception) {
            logError("释放代理异常: ${e.message}")
            // 强制释放时也要检查重复
            if (!availableProxies.contains(proxy)) {
                availableProxies.offer(proxy)
            }
        }
    }

    // 强制释放（异常情况使用）
    fun forceRelease(proxy: Proxy) {
        if (!availableProxies.contains(proxy)) {
            availableProxies.offer(proxy)
            logError("强制释放代理: $proxy，当前可用代理数: ${availableProxies.size}")
        }
    }

    // 获取当前状态信息
    fun getStatus(): String {
        return "可用代理: ${availableProxies.size}/${proxies.size}"
    }

    fun close() {
        scope.cancel()
        availableProxies.clear()
        logInfo("ProxyManager 已关闭")
    }
}