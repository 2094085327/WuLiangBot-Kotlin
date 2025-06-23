package bot.wuliang.httpUtil

import bot.wuliang.httpUtil.entity.ProxyInfo
import bot.wuliang.redis.RedisService
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

@Component
class ProxyUtil {
    @Autowired
    private lateinit var redisService: RedisService

    // api 接口
    val api = "http://49.232.127.250:3751/api/v2/http"

    @Scheduled(cron = "0 30 * * * ?")
    fun proxyMain(): List<ProxyInfo>? {
        // 从 Redis 获取已存储的代理列表
        val proxies = redisService.getValueTyped<List<ProxyInfo>>("Wuliang:http:proxy")

        // 检查是否需要更新代理池
        if (proxies.isNullOrEmpty() || validateProxies(proxies).size < 5) {
            // 获取新的代理列表
            val newProxies = getProxyApi()

            // 验证新代理的有效性
            val validateProxies = validateProxies(newProxies)
            return validateProxies
        }
        return null
    }

    fun getProxyApi(): List<ProxyInfo> {
        val jsonNode: JsonNode = HttpUtil.doGetJson(api).get("proxies")

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
                    val proxyAddress = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.ip, proxy.port!!))
                    return@async try {
                        HttpUtil.doGetStr("http://httpbin.org/ip", proxy = proxyAddress)
                        proxy
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll()
        }.filterNotNull()

        redisService.setValueWithExpiry("Wuliang:http:proxy", results, 2L, TimeUnit.DAYS)
        return results
    }


}