package bot.demo.txbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class TencentBotKotlinApplication

fun main(args: Array<String>) {
    runApplication<TencentBotKotlinApplication>(*args)
}
