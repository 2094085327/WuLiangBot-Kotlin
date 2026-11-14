package bot.wuliang.arbitration.log

/** 下载、直接解析和指令提示共用限制。原文件只流式读取。 */
object ArbitrationLogLimits {
    const val MAX_MIB = 256
    const val SIZE_LABEL = "${MAX_MIB}MiB"
    const val MAX_BYTES = MAX_MIB * 1024 * 1024L
    const val MAX_CHARACTERS = MAX_BYTES
    const val DOWNLOAD_SECONDS = 180L
    const val UPLOAD_WAIT_MINUTES = 5
    const val UPLOAD_WAIT_MILLIS = UPLOAD_WAIT_MINUTES * 60_000L
}