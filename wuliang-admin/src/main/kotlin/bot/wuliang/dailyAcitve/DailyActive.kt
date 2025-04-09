package bot.wuliang.dailyAcitve

import bot.wuliang.botLog.database.service.LogService
import bot.wuliang.botLog.logAop.LogEntity
import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.config.CommonConfig.DAILY_ACTIVE_PATH
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.exception.RespBean
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import com.fasterxml.jackson.databind.ObjectMapper
import lombok.Data
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*


/**
 * @description: 日活
 * @author Nature Zero
 * @date 2025/2/10 23:00
 */
@RestController
@RequestMapping("/dailyActive")
@Component
class DailyActive {
    @Autowired
    lateinit var logService: LogService
    private val objectMapper = ObjectMapper() // 获取 ObjectMapper 对象

    @Autowired
    lateinit var webImgUtil: WebImgUtil

    data class DailyActiveResponse(
        val data: List<DailyActiveData>? = null
    )

    @Data
    data class DailyActiveData(
        val date: String? = null,
        val dailyActiveUsers: Int? = null,
        val totalUpMessages: Int? = null
    )

    fun findMissDates(dates: List<String>): MutableList<String> {
        val missingDates: MutableList<String> = ArrayList()

        val dateList = ArrayList<LocalDate>()
        dates.forEach { dateStr -> dateList.add(LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE)) }

        for (i in 0 until dateList.size - 1) {
            val current: LocalDate = dateList[i]
            val next: LocalDate = dateList[i + 1]

            val daysBetween = ChronoUnit.DAYS.between(current, next)
            if (daysBetween > 1) {
                var tempDate: LocalDate = current.plusDays(1)
                while (tempDate.isBefore(next)) {
                    missingDates.add(tempDate.toString())
                    tempDate = tempDate.plusDays(1)
                }
            }
        }

        return missingDates
    }

    /**
     * 获取日活数据
     *
     * @param model
     * @return
     */
    @RequestMapping("/dailyActive")
    fun dailyActive(model: Model): String {
        return "Other/DailyActive"
    }


    @GetMapping("/dailyJson")
    fun dailyJson(@RequestParam("day") day: Int = 30): RespBean {
        initDailyActive()
        val helpJson = JacksonUtil.getJsonNode(DAILY_ACTIVE_PATH)
        val dailyActiveResponse = objectMapper.readValue(helpJson.toString(), DailyActiveResponse::class.java)

        // 获取当前日期
        val currentDate = LocalDate.now()

        if (day == -1) return RespBean.success(DailyActiveResponse(data = dailyActiveResponse.data.orEmpty()))

        // 过滤出最近 day 天的数据
        val filteredData = dailyActiveResponse.data.orEmpty()
            .filter { it.date != null && LocalDate.parse(it.date).isAfter(currentDate.minusDays(day.toLong())) }
            .sortedBy { it.date }
            .take(day)

        return RespBean.success(DailyActiveResponse(data = filteredData))
    }

    /**
     * 获取日活数据
     *
     * @param context
     */
    @AParameter
    @Executor(action = "日活")
    @SystemLog(businessName = "机器人日活信息")
    fun daily(context: BotUtils.Context) {
        initDailyActive()
        val imageData = WebImgUtil.ImgData(
            imgName = "dailyActive-${UUID.randomUUID()}",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/dailyActive"
        )
        webImgUtil.sendNewImage(context, imageData)
        webImgUtil.deleteImg(imageData)
    }

    /**
     * 初始化日活信息
     */
    @Scheduled(cron = "59 59 * * * ? ")
    fun initDailyActive() {
        return File(DAILY_ACTIVE_PATH).let { jsonFile ->
            // 读取并解析基础数据
            val dailyResponse = objectMapper.readValue(jsonFile, DailyActiveResponse::class.java)
            val existingDates = dailyResponse.data.orEmpty().mapNotNull { it.date }

            // 计算缺失日期并生成时间范围，强制包含当前日期
            val currentDate = LocalDate.now().toString()
            val missingDates = findMissDates(existingDates).toMutableList().apply {
                if (!contains(currentDate)) {
                    add(currentDate)
                }
            }

            val timeRanges = missingDates.mapToTimeRanges()

            // 获取并处理日志数据
            val newData = logService.selectLogByTime(timeRanges)
                .processLogs()

            // 合并并完善最终数据
            val finalData = dailyResponse.data.orEmpty()
                .mergeWith(newData)
                .addMissingEntries(missingDates)
                .sortedByDate()

            // 将最终数据写回文件
            val updatedResponse = DailyActiveResponse(data = finalData)
            objectMapper.writeValue(jsonFile, updatedResponse)
        }
    }

    /**
     * 扩展函数：将缺失日期转换为时间范围
     *
     */
    private fun MutableList<String>.mapToTimeRanges() = map { dateStr ->
        val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE)
        mapOf(
            "startTime" to date.atStartOfDay(),
            "endTime" to date.plusDays(1).atStartOfDay()
        )
    }.toMutableList()

    /**
     * 扩展函数：处理日志数据
     *
     */
    private fun List<LogEntity>.processLogs() = groupBy {
        it.createTime?.format(DateTimeFormatter.ISO_DATE)
    }.mapNotNull { (date, logs) ->
        date?.let {
            DailyActiveData(
                date = it,
                dailyActiveUsers = logs.map { log -> log.userId }.toSet().size,
                totalUpMessages = logs.size
            )
        }
    }

    /**
     * 扩展函数：合并数据集
     *
     * @param newData 新数据
     */
    private fun List<DailyActiveData>.mergeWith(newData: List<DailyActiveData>) =
        (this + newData).distinctBy { it.date }

    /**
     * 扩展函数：添加缺失日期的默认数据
     *
     * @param missingDates 缺失日期
     */
    private fun List<DailyActiveData>.addMissingEntries(missingDates: List<String>) =
        this + missingDates.map { date ->
            DailyActiveData(
                date = date,
                dailyActiveUsers = 0,
                totalUpMessages = 0
            )
        }

    // 扩展函数：按日期排序
    private fun List<DailyActiveData>.sortedByDate() = sortedBy { it.date }
}