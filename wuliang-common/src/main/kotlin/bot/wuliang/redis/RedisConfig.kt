package bot.wuliang.redis

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
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
     * @param lettuceConnectionFactory redis连接工厂
     * @return RedisTemplate
     */
    @Bean
    fun redisTemplate(lettuceConnectionFactory: LettuceConnectionFactory): RedisTemplate<String, Any> {
        val redisTemplate = RedisTemplate<String, Any>()
        val stringRedisSerializer = StringRedisSerializer()
        val om: ObjectMapper = JsonMapper.builder()
            .addModule(JavaTimeModule())
            .build()
            .apply {
                val ptv = BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType(Any::class.java)
                    .build()

                // 使用属性格式而不是数组格式
                activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY)
            }
        val jackson2JsonRedisSerializer = GenericJackson2JsonRedisSerializer(om)
        redisTemplate.apply {
            // 配置连接工厂
            setConnectionFactory(lettuceConnectionFactory)
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