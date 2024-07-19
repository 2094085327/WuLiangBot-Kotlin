package bot.demo.txbot.common.tencentCos

data class PicUploadResult(
    // 文件惟一标识
    private val uid: String,

    // 文件名
    private val name: String,

    // 状态有：uploading done error removed
    private val status: String,

    // 服务端响应内容，如：'{"status": "success"}'
    private val response: String,
)
