package bot.wuliang.translation

import java.io.IOException

/** 仅对文件读取、JSON 解析和内容校验失败降级；JVM Error 与其他程序错误直接向上传播。 */
internal inline fun <T> recoverExportData(action: () -> T): Result<T> = try {
    Result.success(action())
} catch (error: IOException) {
    Result.failure(error)
} catch (error: IllegalArgumentException) {
    Result.failure(error)
}
