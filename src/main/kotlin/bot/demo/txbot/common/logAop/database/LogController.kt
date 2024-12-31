package bot.demo.txbot.common.logAop.database

import bot.demo.txbot.common.exception.RespBean
import bot.demo.txbot.common.exception.RespBeanEnum
import bot.demo.txbot.common.logAop.LogEntity
import com.baomidou.mybatisplus.core.metadata.IPage
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


/**
 * @description: 日志控制器
 * @author Nature Zero
 * @date 2024/12/30 19:39
 */
@RestController
@RequestMapping("/log")
class LogController {
    @Autowired
    private lateinit var logService: LogServiceImpl

    @GetMapping("/getLog")
    fun getLog(@RequestParam("pageSize") pageSize: Long, @RequestParam("pageNum") pageNum: Long): RespBean {
        try{
            val iPage: IPage<LogEntity?> = Page(pageNum, pageSize)
            return RespBean.success(logService.getLog(iPage))
        }catch (e: Exception){
            return RespBean.error(RespBeanEnum.LOG_GET_ERROR)
        }
    }
}