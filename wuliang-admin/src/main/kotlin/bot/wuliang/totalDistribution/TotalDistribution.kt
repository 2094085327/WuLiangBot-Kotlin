package bot.wuliang.totalDistribution

import bot.wuliang.adapter.command.CommandRegistry
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.distribute.annotation.ActionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct


/**
 * @description: 总分发方法，将指令分发至各类
 * @author Nature Zero
 * @date 2024/7/13 下午8:41
 */
@Component
@ActionService
class TotalDistribution {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @EventListener(ContextClosedEvent::class)
    fun endEventListenerShutdown() {
        logInfo("程序关闭")
    }

    @PostConstruct
    fun initCommandRegistry() {
        // 初始化命令注册中心
        CommandRegistry.init(applicationContext)
        // 扫描命令
        CommandRegistry.scanCommands("bot.wuliang")
    }
}