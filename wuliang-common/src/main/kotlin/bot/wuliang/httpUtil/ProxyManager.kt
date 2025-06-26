package bot.wuliang.httpUtil

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.Proxy
import java.util.concurrent.Semaphore

/**
 * 代理管理器，负责代理的并发控制和冷却管理
 */
class ProxyManager(private val proxies: List<Proxy>) {
    // 每个代理的状态管理
    private val proxyStates = proxies.associateWith { ProxyState() }.toMutableMap()
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 配置参数
    private val cooldownTime = 5000L

    data class ProxyState(
        var requestCount: Int = 0,  // 累计请求数
        var isInCooldown: Boolean = false,
        var cooldownJob: Job? = null,
        val semaphore: Semaphore = Semaphore(10)
    )

    suspend fun acquireProxy(): Proxy {
        while (true) {
            // 随机选择避免饥饿
            val availableProxy = proxies.shuffled().find { proxy ->
                val state = proxyStates[proxy]!!
                !state.isInCooldown && state.semaphore.tryAcquire()
            }

            if (availableProxy != null) {
                mutex.withLock {
                    val state = proxyStates[availableProxy]!!
                    state.requestCount++
                }
                return availableProxy
            }
            delay(50) // 减少等待时间
        }
    }

    suspend fun releaseProxy(proxy: Proxy) {
        mutex.withLock {
            val state = proxyStates[proxy] ?: return
            state.semaphore.release()

            // 每10个请求后冷却，而不是并发为0时
            if (state.requestCount >= 10) {
                state.requestCount = 0
                startCooldown(state)
            }
        }
    }

    private fun startCooldown(state: ProxyState) {
        state.isInCooldown = true
        state.cooldownJob?.cancel()
        state.cooldownJob = scope.launch {
            delay(cooldownTime)
            mutex.withLock {
                state.isInCooldown = false
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}