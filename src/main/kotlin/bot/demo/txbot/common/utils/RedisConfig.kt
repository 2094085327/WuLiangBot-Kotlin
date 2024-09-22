//package bot.demo.txbot.common.utils
//
//import com.fasterxml.jackson.annotation.JsonAutoDetect
//import com.fasterxml.jackson.annotation.PropertyAccessor
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.data.redis.connection.RedisConnectionFactory
//import org.springframework.data.redis.core.RedisTemplate
//import org.springframework.data.redis.core.StringRedisTemplate
//import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
//import org.springframework.data.redis.serializer.StringRedisSerializer
//
//
///**
// * @description: Redis配置类
// * @author Nature Zero
// * @date 2024/5/20 下午11:22
// */
//
//@Configuration
//class RedisConfig {
////    @Bean
////    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory?): RedisTemplate<String, Any> {
////        //大多数情况，都是选用<String, Object>
////        val redisTemplate = RedisTemplate<String, Any>()
////
////        //key序列化
////        redisTemplate.keySerializer = StringRedisSerializer()
////        //value序列化 使用JSON的序列化对象，对数据key和value进行序列化转换
////        redisTemplate.valueSerializer = GenericJackson2JsonRedisSerializer()
////        //hash类型value序列化
////        redisTemplate.hashKeySerializer = StringRedisSerializer()
////        redisTemplate.hashValueSerializer = GenericJackson2JsonRedisSerializer()
////
////        //注入连接工厂
////        redisTemplate.setConnectionFactory(redisConnectionFactory!!)
////        return redisTemplate
////    }
//
////    @Bean
////    @ConditionalOnMissingBean(name = ["redisTemplate"])
////    fun redisTemplate(
////        redisConnectionFactory: RedisConnectionFactory?
////    ): RedisTemplate<String, Any> {
////        val jackson2JsonRedisSerializer = Jackson2JsonRedisSerializer(
////            Any::class.java
////        )
////        val om = ObjectMapper()
////        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
////        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL)
////        jackson2JsonRedisSerializer.setObjectMapper(om)
////
////        val template = RedisTemplate<String, Any>()
////        template.setConnectionFactory(redisConnectionFactory!!)
////        template.keySerializer = jackson2JsonRedisSerializer
////        template.valueSerializer = jackson2JsonRedisSerializer
////        template.hashKeySerializer = jackson2JsonRedisSerializer
////        template.hashValueSerializer = jackson2JsonRedisSerializer
////        template.afterPropertiesSet()
////        return template
////    }
//
//    @Bean
//    @ConditionalOnMissingBean(name = ["redisTemplate"])
//    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory?): RedisTemplate<String, Any> {
//        val jackson2JsonRedisSerializer = Jackson2JsonRedisSerializer(Any::class.java)
//
//        // 配置 ObjectMapper
//        val om = ObjectMapper()
//        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
//        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL)
//        jackson2JsonRedisSerializer.setObjectMapper(om)
//
//        // 创建 RedisTemplate 实例
//        val template = RedisTemplate<String, Any>()
//        template.setConnectionFactory(redisConnectionFactory!!)
//
//        // 对 key 使用 StringRedisSerializer，避免 key 被序列化为 JSON
//        template.keySerializer = StringRedisSerializer()
//
//        // 对 value 使用 Jackson2JsonRedisSerializer，进行 JSON 序列化
//        template.valueSerializer = jackson2JsonRedisSerializer
//
//        // 对 hashKey 和 hashValue 也分别设置序列化方式
//        template.hashKeySerializer = StringRedisSerializer()
//        template.hashValueSerializer = jackson2JsonRedisSerializer
//
//        template.afterPropertiesSet()
//        return template
//    }
//
//
//
//    @Bean
//    @ConditionalOnMissingBean(StringRedisTemplate::class)
//    fun stringRedisTemplate(
//        redisConnectionFactory: RedisConnectionFactory?
//    ): StringRedisTemplate {
//        val template = StringRedisTemplate()
//        template.setConnectionFactory(redisConnectionFactory!!)
//        return template
//    }
//}