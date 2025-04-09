package bot.wuliang

import bot.wuliang.config.APP_PID_PATH
import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.ApplicationPidFileWriter
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling


@EnableCaching
@EnableScheduling
@SpringBootApplication(
    scanBasePackages = ["bot.wuliang.**"]
)
@MapperScan("bot.wuliang.**.mapper")
class TencentBotKotlinApplication

fun main(args: Array<String>) {
    runApplication<TencentBotKotlinApplication>(*args) {
        addListeners(ApplicationPidFileWriter(APP_PID_PATH))
    }
}
