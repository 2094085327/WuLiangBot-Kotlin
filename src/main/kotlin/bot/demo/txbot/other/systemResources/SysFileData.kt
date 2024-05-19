package bot.demo.txbot.other.systemResources

data class SysFileData(
    val dirName: String, // 盘符路径
    val sysTypeName: String, // 盘符类型
    val typeName: String, // 文件类型
    val fileTotal: String, // 总大小
    val fileFree: String, // 剩余大小
    val fileUsed: String, // 已用大小
    val fileUsage: Double // 资源的使用率
)