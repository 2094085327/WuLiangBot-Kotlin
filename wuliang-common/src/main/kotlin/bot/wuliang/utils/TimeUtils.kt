package bot.wuliang.utils

import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * 时间工具类
 */
object TimeUtils {
    private val timeReplacements = mapOf(
        "d " to "天",
        " d" to "天",
        "h " to "小时",
        " h" to "小时",
        "m " to "分",
        " m" to "分",
        "s " to "秒",
        " s" to "秒",
        "s" to "秒"
    )

    /**
     * 获取当前时间的 Instant 对象
     *
     * @return 当前时间 Instant 对象
     */
    fun getInstantNow(): Instant {
        return Instant.now()
    }

    /**
     * 获取指定时间与当前时间的时间差
     *
     * @param instant 指定时间 instant 对象
     */
    fun toNow(instant: Instant): Long {
        return Duration.between(getInstantNow(), instant).toMillis()
    }

    /**
     * 格式化持续时间为可读字符串
     *
     * @param duration 持续时间
     */
    fun formatDuration(duration: Duration): String {
        return when {
            duration.toDays() > 0 -> "${duration.toDays()} 天 ${duration.toHoursPart()} 小时 ${duration.toMinutesPart()} 分钟 ${duration.toSecondsPart()} 秒"
            duration.toHours() > 0 -> "${duration.toHours()} 小时 ${duration.toMinutesPart()} 分钟 ${duration.toSecondsPart()} 秒"
            duration.toMinutes() > 0 -> "${duration.toMinutes()} 分钟 ${duration.toSecondsPart()} 秒"
            else -> "${duration.toSecondsPart()} 秒"
        }
    }

    /**
     * 计算时间差并格式化为字符串
     *
     * @param nowTime 起始时间
     * @param endTime 结束时间
     * @return 格式化后的时间差字符串，例如"1天2小时30分"
     */
    fun formatTimeDifference(nowTime: LocalDateTime, endTime: LocalDateTime): String {
        val timeDifference = StringBuilder()

        // 计算各个时间单位
        val units = listOf(
            ChronoUnit.MONTHS to "个月",
            ChronoUnit.DAYS to "天",
            ChronoUnit.HOURS to "小时",
            ChronoUnit.MINUTES to "分",
            ChronoUnit.SECONDS to "秒",
        )

        var tempTime = nowTime

        for ((unit, unitName) in units) {
            val amount = unit.between(tempTime, endTime)
            if (amount > 0) {
                timeDifference.append("$amount$unitName")
                tempTime = tempTime.plus(amount, unit) // 更新临时时间
            }
        }

        return timeDifference.toString()
    }

    /**
     * 将秒数转换为可读的时间格式字符串
     *
     * @param seconds 总秒数
     * @return 格式化后的时间字符串，例如"1天2小时30分钟15秒"
     */
    fun formatTimeBySecond(seconds: Long): String {
        val timeDifference = StringBuilder()

        val days = seconds / (24 * 60 * 60)
        var remainingSeconds = seconds % (24 * 60 * 60)

        val hours = remainingSeconds / (60 * 60)
        remainingSeconds %= (60 * 60)

        val minutes = remainingSeconds / 60
        val secondsLeft = remainingSeconds % 60

        if (days > 0) timeDifference.append("${days}天")
        if (hours > 0) timeDifference.append("${hours}小时")
        if (minutes > 0) timeDifference.append("${minutes}分钟")
        timeDifference.append("${secondsLeft}秒")

        return timeDifference.toString()
    }

    /**
     * 计算下一次刷新时间
     *
     * @param startDate 起始日期时间
     * @param now 当前日期时间
     * @param interval 刷新间隔
     * @return 下一次刷新的日期时间
     */
    fun getNextRefreshTime(startDate: LocalDateTime, now: LocalDateTime, interval: Duration): LocalDateTime {
        val durationSinceStart = Duration.between(startDate, now)
        val fullCycles = durationSinceStart.toDays() / interval.toDays()
        val lastRefresh = startDate.plus(interval.multipliedBy(fullCycles))
        return if (lastRefresh.isBefore(now)) lastRefresh.plus(interval) else lastRefresh
    }

    /**
     * 获取当前周的第一天（周一）的 UTC 时间
     */
    fun getFirstDayOfWeek(): Instant {
        val now = Instant.now()

        // 获取本周一的时间（UTC 00:00:00）
        return now
            .atOffset(ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toInstant()
    }

    /**
     * 获取当前周的最后一天（周日）的 UTC 时间
     */
    fun getLastDayOfWeek(): Instant {
        val firstDay = getFirstDayOfWeek().atOffset(ZoneOffset.UTC)
        return firstDay
            .plusDays(6)
            .withHour(23)
            .withMinute(59)
            .withSecond(59)
            .withNano(0)
            .toInstant()
    }

    /**
     * 获取当前天的 UTC 时间
     */
    fun getStartOfDay(): Instant {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        return now
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
    }

    /**
     * 获取下一周的周一早上08：00的 UTC 时间
     */
    fun getNextMonday(): Instant {
        val firstDay = getFirstDayOfWeek().atOffset(ZoneOffset.UTC)
        return firstDay
            .plusWeeks(1)
            .withHour(8)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()

    }

    /**
     * 获取传入时间的下一天的 UTC 时间
     */
    fun getTimeOfNextDay(instant: Instant): Instant {
        return instant.atOffset(ZoneOffset.UTC)
            .plusDays(1) // 将时间加一天
            .toInstant()
    }

    /**
     * 获取下一天的 UTC 时间
     */
    fun getStartOfNextDay(): Instant {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        return now
            .plusDays(1) // 先将时间加一天
            .withHour(0) // 设置为当天的 00:00:00
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
    }

    /**
     * 获取当前天的结束时间
     */
    fun getEndOfDay(): Instant {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        return now
            .withHour(23)
            .withMinute(59)
            .withSecond(59)
            .withNano(0)
            .toInstant()
    }

    fun String.replaceTime(): String {
        return timeReplacements.entries.fold(this) { acc: String, entry: Map.Entry<String, String> ->
            acc.replace(entry.key, entry.value)
        }
    }

    fun String.parseDuration(): Long {
        // 定义正则表达式，匹配天数、小时数、分钟数和秒数
        val regex = Regex("(?:(\\d+)\\s*天)?(?:\\s*(\\d+)\\s*小时)?(?:\\s*(\\d+)\\s*(?:分|分钟))?(?:\\s*(\\d+)\\s*秒)?")

        // 使用正则表达式进行匹配
        val matchResult = regex.find(this) ?: return 0L

        // 提取匹配结果
        val (days, hours, minutes, seconds) = matchResult.destructured

        // 将提取的字符串转换为整数，并处理可能的空字符串情况
        val day = days.toIntOrNull() ?: 0
        val hour = hours.toIntOrNull() ?: 0
        val minute = minutes.toIntOrNull() ?: 0
        val second = seconds.toIntOrNull() ?: 0

        // 计算总秒数
        return day * TimeUnit.DAYS.toSeconds(1) +
                hour * TimeUnit.HOURS.toSeconds(1) +
                minute * TimeUnit.MINUTES.toSeconds(1) +
                second * TimeUnit.SECONDS.toSeconds(1)
    }
}