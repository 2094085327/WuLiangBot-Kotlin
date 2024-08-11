package bot.demo.txbot

import bot.demo.txbot.other.APP_PID_PATH
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.ApplicationPidFileWriter
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling


@EnableCaching
@EnableScheduling
@SpringBootApplication
class TencentBotKotlinApplication

fun main(args: Array<String>) {
    runApplication<TencentBotKotlinApplication>(*args) {
        addListeners(ApplicationPidFileWriter(APP_PID_PATH))
    }
}
