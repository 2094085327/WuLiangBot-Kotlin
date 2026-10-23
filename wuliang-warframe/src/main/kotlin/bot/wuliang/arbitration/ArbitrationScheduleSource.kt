package bot.wuliang.arbitration

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import bot.wuliang.config.WarframeProperties
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** 有效排期持续复用；仅本地缓存缺失、损坏或未覆盖当前时段时请求接口。 */
@Service
class ArbitrationScheduleSource(properties: WarframeProperties) {
    private val settings = properties.arbitration
    private val client = OkHttpClient.Builder().callTimeout(settings.requestTimeoutSeconds, TimeUnit.SECONDS).build()
    private val cacheFile = Path.of(settings.cacheFile).toAbsolutePath()
    private var schedule: ArbitrationSchedule? = null
    private var retryAt = 0L

    data class Snapshot(val schedule: ArbitrationSchedule)

    @Synchronized
    fun snapshot(now: Long): Snapshot {
        if (!covers(now) && now >= retryAt) {
            schedule = null
            if (Files.isRegularFile(cacheFile)) {
                try {
                    val local = Files.newBufferedReader(cacheFile).use { ArbitrationSchedule.read(it, now) }
                    check(local.covers(now)) { "本地排期未覆盖当前小时" }
                    schedule = local
                    retryAt = 0L
                } catch (error: Exception) {
                    logError("仲裁排期磁盘缓存无效，将重新获取：{}", error.message, error)
                }
            }
        }
        if (!covers(now) && now >= retryAt) {
            try {
                val updated = client.newCall(Request.Builder().url(settings.url).build()).execute().use { response ->
                    check(response.isSuccessful) { "仲裁排期 HTTP ${response.code}" }
                    requireNotNull(response.body).charStream().buffered().use { ArbitrationSchedule.read(it, now) }
                }
                check(updated.covers(now)) { "排期未覆盖当前小时" }
                schedule = updated
                retryAt = 0L
                persist(updated)
            } catch (error: Exception) {
                retryAt = now + TimeUnit.SECONDS.toMillis(settings.retrySeconds)
                logWarn("仲裁排期刷新失败：{}", error.message, error)
            }
        }
        if (!covers(now)) throw ArbitrationUnavailableException("仲裁排期暂不可用或已到期，请稍后重试。")
        return Snapshot(requireNotNull(schedule))
    }

    private fun covers(now: Long) = schedule?.covers(now) == true

    private fun persist(value: ArbitrationSchedule) {
        var temporary: Path? = null
        try {
            Files.createDirectories(cacheFile.parent)
            temporary = Files.createTempFile(cacheFile.parent, "arbitration-", ".tmp")
            Files.newBufferedWriter(temporary).use { writer ->
                for (index in 0 until value.size) {
                    writer.append((value.activationAt(index) / 1000).toString())
                    writer.append(',').append(value.nodeIdAt(index))
                    writer.newLine()
                }
            }
            Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            logWarn("仲裁排期缓存写入失败，继续使用内存数据：{}", error.message, error)
        } finally {
            temporary?.let { Files.deleteIfExists(it) }
        }
    }

}