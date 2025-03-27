package bot.demo.txbot.bot.wuliang.config

import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer


/**
 * @description: Redis配置
 * @author Nature Zero
 * @date 2024/9/22 14:03
 */
@Configuration
class RedisConfig : CachingConfigurer {
    /**
     * Redis template 配置
     *
     * @param redisConnectionFactory redis连接工厂
     * @return RedisTemplate
     */
    @Bean
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val redisTemplate = RedisTemplate<String, Any>()
        val stringRedisSerializer = StringRedisSerializer()
        val jackson2JsonRedisSerializer = GenericJackson2JsonRedisSerializer()
        redisTemplate.apply {
            // 配置连接工厂
            setConnectionFactory(redisConnectionFactory)
            // 配置 key 序列化方式
            keySerializer = stringRedisSerializer
            // 配置 value 序列化方式: 使用 Jackson2JsonRedisSerializer
            valueSerializer = jackson2JsonRedisSerializer
            // 配置 hash key 序列化方式
            hashKeySerializer = stringRedisSerializer
            // 配置 hash value 序列化方式
            hashValueSerializer = jackson2JsonRedisSerializer
            afterPropertiesSet()
        }
        return redisTemplate
    }
}