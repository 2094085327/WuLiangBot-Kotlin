package bot.wuliang.arbitration.log

import bot.wuliang.arbitration.ArbitrationBaselines
import bot.wuliang.arbitration.log.archive.ArbitrationLogArchiveStore
import bot.wuliang.arbitration.log.archive.ArbitrationLogUploader
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.translation.PublicExportService
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class ArbitrationLogService(
    private val translations: PublicExportService,
    private val baselines: ArbitrationBaselines,
    private val archive: ArbitrationLogArchiveStore,
) {
    private data class Download(val parsed: ArbitrationLogParseResult, val digest: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(ArbitrationLogLimits.DOWNLOAD_SECONDS, TimeUnit.SECONDS).followRedirects(false)
        .followSslRedirects(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
                require(addresses.isNotEmpty() && addresses.all(::publicAddress)) { "文件下载地址无效，请重新发送 QQ 文件。" }
            }
        }).build()

    fun analyze(url: String, options: ArbitrationLogOptions, uploader: ArbitrationLogUploader): ArbitrationLogReport {
        // 首次上传始终采集有限详情，之后可从数据库重算，无需再下载原文件。
        val downloaded = download(url)
        val parsed = downloaded.parsed
        require(options.run <= parsed.runs.size) {
            if (parsed.runs.isEmpty()) "没有找到可分析的主机仲裁记录。请确认文件来自主机，且对局至少1分钟。"
            else "这份日志可选择最近${parsed.runs.size}局。"
        }
        val warning = try {
            if (archive.record(parsed, downloaded.digest, uploader))
                "部分对局已有不同的完整统计，已保留原记录并标记待核对。" else null
        } catch (error: java.util.concurrent.CancellationException) {
            throw error
        } catch (error: Exception) {
            logError("仲裁结构化归档失败，异常类型：${error.javaClass.simpleName}")
            "本次分析完成，但历史记录保存失败，请稍后重新发送 EE.log。"
        }
        val report = report(parsed, options)
        return if (warning == null) report else report.copy(warnings = report.warnings + warning)
    }

    fun previous(options: ArbitrationLogOptions, uploader: ArbitrationLogUploader): ArbitrationLogReport {
        // 归档只展开所选一局；保留整份文件的计数，恢复原局序用于展示。
        val parsed = archive.previous(uploader, options.run)
        return report(parsed, options.copy(run = 1)).copy(run = options.run)
    }

    fun record(options: ArbitrationLogOptions, uploader: ArbitrationLogUploader): ArbitrationLogReport {
        val id = requireNotNull(options.recordId) { "请指定记录编号。" }
        val parsed = archive.byRecordId(id, uploader)
        return report(parsed, options.copy(run = 1)).copy(recordId = id.toString())
    }

    private fun report(parsed: ArbitrationLogParseResult, options: ArbitrationLogOptions): ArbitrationLogReport {
        require(translations.ready) { PublicExportService.UNAVAILABLE_MESSAGE }
        val run = parsed.runs[options.run - 1]
        val node = translations.node(run.nodeId)
        return ArbitrationLogAnalysis.report(
            parsed,
            options,
            node.name ?: run.nodeId,
            node.type ?: "未知任务",
            node.faction ?: "未知派系",
            baselines.kpm[run.nodeId]
        )
    }

    private fun download(url: String): Download {
        var address = checkedUrl(url)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ArbitrationLogLimits.DOWNLOAD_SECONDS)
        repeat(4) { redirect ->
            val remaining = deadline - System.nanoTime()
            require(remaining > 0) { "文件下载超时，请重新发送 EE.log。" }
            val call = client.newCall(Request.Builder().url(address).build())
            call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
            call.execute().use { response ->
                if (response.code in 300..399) {
                    require(redirect < 3) { "文件链接跳转过多，请重新发送 EE.log。" }
                    address = checkedUrl(response.header("Location")?.let { address.resolve(it) }?.toString().orEmpty())
                } else {
                    require(response.isSuccessful) { "文件下载失败或链接已过期，请重新发送 EE.log。" }
                    val body = requireNotNull(response.body) { "文件内容为空，请重新发送 EE.log。" }
                    require(body.contentLength() <= ArbitrationLogLimits.MAX_BYTES) {
                        "EE.log 超过${ArbitrationLogLimits.SIZE_LABEL}，请换用较小的日志文件。"
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    val parsed = InputStreamReader(
                        DigestInputStream(LimitedInputStream(body.byteStream()), digest),
                        Charsets.UTF_8
                    ).use {
                        ArbitrationLogParser().parse(it, true)
                    }
                    return Download(parsed, HexFormat.of().formatHex(digest.digest()))
                }
            }
        }
        error("文件下载失败，请重新发送 EE.log。")
    }

    private fun checkedUrl(value: String): HttpUrl {
        val url = value.takeIf { it.length <= 8192 }?.toHttpUrlOrNull()
        require(url != null && url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()) {
            "文件下载地址无效，请重新发送文件。"
        }
        return url
    }

    private class LimitedInputStream(input: InputStream) : FilterInputStream(input) {
        private var count = 0L
        private fun account(n: Int) {
            if (n > 0) count += n
            require(count <= ArbitrationLogLimits.MAX_BYTES) { "EE.log 超过${ArbitrationLogLimits.SIZE_LABEL}处理上限。" }
        }

        override fun read(): Int = `in`.read().also { if (it >= 0) account(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            `in`.read(buffer, offset, length).also(::account)
    }

    private companion object {
        fun publicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
            ) return false
            val bytes = address.address.map { it.toInt() and 255 }
            return if (bytes.size == 4) {
                bytes[0] != 0 && bytes[0] < 224 && !(bytes[0] == 100 && bytes[1] in 64..127) &&
                        !(bytes[0] == 169 && bytes[1] == 254)
            } else bytes[0] in 0x20..0x3f
        }
    }
}