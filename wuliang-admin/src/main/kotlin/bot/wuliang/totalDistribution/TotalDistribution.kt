package bot.wuliang.totalDistribution

import bot.wuliang.adapter.command.CommandRegistry
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.CommonConfig.RESTART_CONFIG
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.restart.Restart
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.io.File
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

    private val scope = CoroutineScope(Dispatchers.Default)
    private val mapper = ObjectMapper() // 获取 ObjectMapper 对象

    @PostConstruct
    fun creatDailyActiveFile() {
        scope.launch {
            val restartFile = File(RESTART_CONFIG)
            if (!restartFile.exists()) {
                restartFile.createNewFile()
                val jarPath = Restart().getNowJarName()
                val restartJson = mapper.createObjectNode()
                restartJson.put("jar_file", jarPath)
                mapper.writeValue(restartFile, restartJson)
                logInfo("重启配置文件缺失，已自动创建")
            } else {
                val restartJson = mapper.readTree(restartFile) as ObjectNode
                restartJson.put("jar_file", Restart().getNowJarName())
                mapper.writeValue(restartFile, restartJson)
            }
        }
    }

    @EventListener
    fun endEventListenerShutdown(event: ContextClosedEvent) {
        logInfo("程序关闭...进行关键信息保存")
    }

    @PostConstruct
    fun initCommandRegistry() {
        // 初始化命令注册中心
        CommandRegistry.init(applicationContext)
        // 扫描命令
        CommandRegistry.scanCommands("bot.wuliang")
    }
}
