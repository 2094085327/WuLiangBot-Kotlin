package bot.demo.txbot.config

import com.baomidou.mybatisplus.annotation.DbType
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement


/**
 * @description: MyBatisPlus配置类
 * @author Nature Zero
 * @date 2024/12/30 19:50
 */
@EnableTransactionManagement
@Configuration
class MyBatisPlusConfig {

    @Bean
    fun paginationInnerInterceptor(): PaginationInnerInterceptor {
        val paginationInterceptor = PaginationInnerInterceptor()
        // 设置最大单页限制数量，默认 500 条，-1 不受限制
        paginationInterceptor.maxLimit = -1L
        paginationInterceptor.dbType = DbType.MYSQL
        // 开启 count 的 join 优化,只针对部分 left join
        paginationInterceptor.isOptimizeJoin = true
        return paginationInterceptor
    }

    @Bean
    fun mybatisPlusInterceptor(): MybatisPlusInterceptor {
        val interceptor = MybatisPlusInterceptor()
        interceptor.addInnerInterceptor(PaginationInnerInterceptor(DbType.MYSQL)) // 如果配置多个插件, 切记分页最后添加
        // 如果有多数据源可以不配具体类型, 否则都建议配上具体的 DbType
        return interceptor
    }
}