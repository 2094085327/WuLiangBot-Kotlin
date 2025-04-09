package bot.wuliang.config

import io.swagger.models.auth.In
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.oas.annotations.EnableOpenApi
import springfox.documentation.service.*
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.contexts.OperationContext
import springfox.documentation.spi.service.contexts.SecurityContext
import springfox.documentation.spring.web.plugins.Docket

@EnableOpenApi
@Configuration
class SwaggerConfig(
    /** 是否开启swagger */
    @Value("\${swagger.enabled}") var enabled: Boolean,
    /** 设置请求的统一前缀 */
    @Value("\${swagger.pathMapping}") var pathMapping: String
) {

    @Bean
    fun createRestApi(): Docket {
        //swagger设置，基本信息，要解析的接口及路径等
        return Docket(DocumentationType.OAS_30)
            .enable(enabled)
            .apiInfo(apiInfo())
            .select() //设置通过什么方式定位需要自动生成文档的接口，这里定位方法上的@ApiOperation注解
//            .apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation::class.java)) //接口URI路径设置，any是全路径，也可以通过PathSelectors.regex()正则匹配
            .apis(RequestHandlerSelectors.any()) //接口URI路径设置，any是全路径，也可以通过PathSelectors.regex()正则匹配
            .paths(PathSelectors.any())
            .build()
            /* 设置安全模式，swagger可以设置访问token */
            .securitySchemes(securitySchemes())
            .securityContexts(securityContexts())
            .pathMapping(pathMapping);
    }

    //生成接口信息，包括标题、联系人，联系方式等
    private fun apiInfo(): ApiInfo {
        return ApiInfoBuilder()
            .title("WuLiang-Bot接口文档")
            .description("无量姬的接口")
            .version("1.0")
            .build()
    }


    /**
     * 安全模式，这里指定token通过Authorization头请求头传递
     */
    private fun securitySchemes(): List<SecurityScheme> {
        val apiKeyList: MutableList<SecurityScheme> = ArrayList()
        apiKeyList.add(ApiKey("Authorization", "Authorization", In.HEADER.toValue()))
        return apiKeyList
    }


    /**
     * 安全上下文
     */
    private fun securityContexts(): List<SecurityContext> {
        val securityContexts: MutableList<SecurityContext> = java.util.ArrayList()
        securityContexts.add(
            SecurityContext.builder()
                .securityReferences(defaultAuth())
                .operationSelector { o: OperationContext -> o.requestMappingPattern().matches("/.*".toRegex()) }
                .build())
        return securityContexts
    }


    /**
     * 默认的安全上引用
     */
    private fun defaultAuth(): List<SecurityReference> {
        val authorizationScope = AuthorizationScope("global", "accessEverything")
        val authorizationScopes = arrayOfNulls<AuthorizationScope>(1)
        authorizationScopes[0] = authorizationScope
        val securityReferences: MutableList<SecurityReference> = java.util.ArrayList()
        securityReferences.add(SecurityReference("Authorization", authorizationScopes))
        return securityReferences
    }

}