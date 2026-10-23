package bot.wuliang.api

import bot.wuliang.arbitration.ArbitrationReport
import bot.wuliang.arbitration.ArbitrationService
import bot.wuliang.arbitration.ArbitrationUnavailableException
import bot.wuliang.exception.RespBean
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeArbitrationController(private val service: ArbitrationService) {
    @ApiOperation("当前仲裁、下一场及下一场高价值仲裁")
    @GetMapping("/arbitration")
    fun current(): RespBean<ArbitrationReport> = query(false, "所有", 1)

    @ApiOperation("仲裁排期，支持评级筛选")
    @GetMapping("/arbitrationTable")
    fun table(
        @RequestParam(defaultValue = "所有") filter: String,
        @RequestParam(defaultValue = "1") page: Int,
    ): RespBean<ArbitrationReport> = query(true, filter, page)

    private fun query(table: Boolean, filter: String, page: Int): RespBean<ArbitrationReport> = try {
        RespBean.success(service.query(table, filter, page))
    } catch (error: IllegalArgumentException) {
        RespBean.error(error.message ?: "仲裁参数无效")
    } catch (error: ArbitrationUnavailableException) {
        RespBean.error(error.message.orEmpty())
    }
}