package bot.demo.txbot.common.utils

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer


/**
 * @description: Redis配置类
 * @author Nature Zero
 * @date 2024/5/20 下午11:22
 */

@Configuration
class RedisConfig {
    @Bean
    @ConditionalOnMissingBean(name = ["redisTemplate"])
    fun redisTemplate(
        redisConnectionFactory: RedisConnectionFactory?
    ): RedisTemplate<String, Any> {
        val jackson2JsonRedisSerializer = Jackson2JsonRedisSerializer(
            Any::class.java
        )
        val om = ObjectMapper()
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL)
        jackson2JsonRedisSerializer.setObjectMapper(om)

        val template = RedisTemplate<String, Any>()
        template.setConnectionFactory(redisConnectionFactory!!)
        template.keySerializer = jackson2JsonRedisSerializer
        template.valueSerializer = jackson2JsonRedisSerializer
        template.hashKeySerializer = jackson2JsonRedisSerializer
        template.hashValueSerializer = jackson2JsonRedisSerializer
        template.afterPropertiesSet()
        return template
    }


    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate::class)
    fun stringRedisTemplate(
        redisConnectionFactory: RedisConnectionFactory?
    ): StringRedisTemplate {
        val template = StringRedisTemplate()
        template.setConnectionFactory(redisConnectionFactory!!)
        return template
    }
}