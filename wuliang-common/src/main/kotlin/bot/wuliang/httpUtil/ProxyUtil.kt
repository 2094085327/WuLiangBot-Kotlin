package bot.wuliang.httpUtil

import bot.wuliang.config.CommonConfig.PROXY_CACHE_KEY
import bot.wuliang.httpUtil.entity.ProxyInfo
import bot.wuliang.redis.RedisService
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Component
class ProxyUtil {
    @Autowired
    private lateinit var redisService: RedisService

    // api 接口
    val api = "https://proxy.scdn.io/api/get_proxy.php?protocol=socks5&count=20"

    fun proxyMain(): List<ProxyInfo>? {
        // 从 Redis 获取已存储的代理列表
        val proxies = redisService.getValueTyped<List<ProxyInfo>>(PROXY_CACHE_KEY)

        // 检查是否需要更新代理池
        if (proxies.isNullOrEmpty() || validateProxies(proxies).size < 5) {
            // 获取新的代理列表
            val newProxies = fetchMultipleProxyLists()

            // 验证新代理的有效性
            val validateProxies = validateProxies(newProxies)
            return validateProxies
        }
        return null
    }

    fun fetchMultipleProxyLists(): List<ProxyInfo> {
        val totalProxies = mutableListOf<ProxyInfo>()
        val requestCount = 5

        repeat(requestCount) {
            val jsonNode: JsonNode = HttpUtil.doGetJson(api).get("data").get("proxies")

            val pattern = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}$")

            val proxies = jsonNode.mapNotNull { node ->
                val text = node.asText()
                if (pattern.matches(text)) {
                    val ipPort = text.split(":")
                    ProxyInfo(ip = ipPort[0], port = ipPort[1].toInt())
                } else {
                    null
                }
            }

            totalProxies.addAll(proxies)
        }

        return totalProxies.distinct() // 去重
    }

    fun getProxyApi(): List<ProxyInfo> {
        val jsonNode: JsonNode = HttpUtil.doGetJson(api).get("data").get("proxies")

        // 定义 ip:port 正则表达式
        val pattern = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}$")

        return jsonNode.mapNotNull { node ->
            val text = node.asText()
            // 匹配格式是否正确
            if (pattern.matches(text)) {
                val ipPort = text.split(":")
                ProxyInfo(ip = ipPort[0], port = ipPort[1].toInt())
            } else {
                null // 忽略非法格式
            }
        }
    }

    fun validateProxies(proxyList: List<ProxyInfo>): List<ProxyInfo> {
        val scope = CoroutineScope(Dispatchers.IO)

        val results = runBlocking {
            proxyList.map { proxy ->
                scope.async {
                    val proxyAddress = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxy.ip, proxy.port!!))
                    return@async try {
                        // 用于校验代理的临时解决方案
                        HttpUtil.doGetJson(
                            api,
                            headers = mutableMapOf("Platform" to "xbox"),
                            proxy = proxyAddress
                        )
                        proxy
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull()

        redisService.setValueWithExpiry(PROXY_CACHE_KEY, results, 2L, TimeUnit.DAYS)
        return results
    }


    fun randomProxy(): Proxy? {
        val proxies = redisService.getValueTyped<List<ProxyInfo>>(PROXY_CACHE_KEY) ?: return null
        if (proxies.isEmpty()) return null

        // 随机选择一个代理
        val randomProxy = proxies.random()
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress(randomProxy.ip, randomProxy.port!!))
    }
}