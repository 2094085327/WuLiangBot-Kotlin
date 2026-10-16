package bot.wuliang.worldstate.service

import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.utils.TimeUtils
import bot.wuliang.worldstate.parser.WorldStateCycles
import bot.wuliang.worldstate.source.WorldStateSource
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 将共用周期算法转换为机器人所需的时间文本；倒计时按本次查询时间计算。 */
@Service
class WorldCycleService(
    private val worldStateSource: WorldStateSource,
) {

    /** 基于一份官方数据和同一查询时刻计算平原状态，转换为北京时间文本与实时倒计时。 */
    fun getCycleStatuses(): Map<String, WfStatusVo.WordStatus> {
        val data = worldStateSource.get()
        val now = System.currentTimeMillis()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai"))
        return WorldStateCycles.parse(data, now)
            .filter { it.id in setOf("earth", "cetus", "cambion", "vallis") }
            .associate { cycle ->
                cycle.id to WfStatusVo.WordStatus(
                    displayState = cycle.subtitle,
                    activation = cycle.activation?.let { formatter.format(Instant.ofEpochMilli(it)) },
                    expiry = cycle.expiry?.let { formatter.format(Instant.ofEpochMilli(it)) },
                    timeLeft = cycle.expiry?.let { TimeUtils.formatTimeBySecond(((it - now) / 1000).coerceAtLeast(0)) }
                )
            }
    }
}