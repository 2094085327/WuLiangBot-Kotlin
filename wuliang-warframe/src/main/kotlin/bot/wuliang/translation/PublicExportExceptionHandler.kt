package bot.wuliang.translation

import bot.wuliang.exception.RespBean
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class PublicExportExceptionHandler {
    /**
     * 把词库未初始化异常转换为可读的接口响应，提示管理员完成手动初始化。
     */
    @ExceptionHandler(PublicExportUnavailableException::class)
    fun unavailable(): RespBean<Nothing> = RespBean.error(PublicExportService.UNAVAILABLE_MESSAGE)
}