package bot.wuliang.redis

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
import javax.annotation.Resource


/**
 * @description: RedisService 类提供了简化的 Redis 操作接口，用于在 Spring Boot 应用中存储和检索数据。它通过 RedisTemplate 与 Redis 服务器交互，执行常见的操作如设置值、获取值、设置值带过期时间和删除值。
 * @author Nature Zero
 * @date 2024/9/22 14:08
 */
@Service
@Suppress("unused")
class RedisService {
    @Qualifier("reactiveRedisTemplate")
    @Autowired
    private lateinit var reactiveRedisTemplate: ReactiveRedisTemplate<Any, Any>

    @Resource
    private lateinit var redisTemplate: RedisTemplate<String, Any>


    // 作用: 向 Redis 中存储一个键值对
    fun setValue(key: String, value: Any) {
        redisTemplate.opsForValue()[key] = value
    }

    // 作用: 从 Redis 中获取指定键的值
    fun getValue(key: String): Any? {
        if (key == "") return null
        try {
            return redisTemplate.opsForValue().get(key)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // 泛型版本的 getValue 方法
    @Suppress("UNCHECKED_CAST")
    fun <T> getValue(key: String, clazz: Class<T>): T? {
        if (key == "") return null
        if (!hasKey(key)) return null
        try {
            return redisTemplate.opsForValue().get(key) as T
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // 作用: 向 Redis 中存储一个键值对，并设置其过期时间
    // timeout 指定时间量，timeUnit 指定时间单位
    fun setValueWithExpiry(key: String, value: Any, timeout: Long, timeUnit: TimeUnit) {
        redisTemplate.opsForValue()[key, value, timeout] = timeUnit
    }

    fun incrementValue(key: String): Int? {
        return Objects.requireNonNull(redisTemplate.opsForValue().increment(key))?.toInt()
    }

    fun decrementValue(key: String): Int? {
        return Objects.requireNonNull(redisTemplate.opsForValue().decrement(key))?.toInt()
    }

    /**
     * 从redis中获取key对应的过期时间;
     * 如果该值有过期时间，就返回相应的过期时间;
     * 如果该值没有设置过期时间，就返回-1;
     * 如果没有该值，就返回-2;
     * @param key
     * @return
     */
    fun getExpire(key: String): Long? {
        return redisTemplate.opsForValue().operations.getExpire(key)
    }


    fun getExpireAndValue(key: String): Pair<Long?, Any?> {
        val expiry = redisTemplate.opsForValue().operations.getExpire(key)
        val value = redisTemplate.opsForValue().get(key)
        if (expiry == -1L) return Pair(expiry, value)
        if (expiry == -2L) Pair(expiry, null)
        return Pair(expiry, value)
    }

    fun hasKey(key: String): Boolean {
        return redisTemplate.hasKey(key)
    }

    fun setExpire(key: String, expire: Duration): Boolean {
        if (hasKey(key)) {
            redisTemplate.expire(key, expire)
            return true
        }
        return false
    }

    // 作用: 从 Redis 中删除指定键及其对应的值
    fun deleteKey(key: String) {
        redisTemplate.delete(key)
    }

    /**
     * 删除多个key
     *
     * @param keys key集合
     */
    fun deleteKey(keys: Set<String>) {
        redisTemplate.delete(keys)
    }

    /**
     * 获取所有符合条件的key
     *
     * @param prefix key前缀
     * @return key集合
     */
    fun getListKey(prefix: String): MutableSet<String> {
        return redisTemplate.keys(prefix)
    }
}