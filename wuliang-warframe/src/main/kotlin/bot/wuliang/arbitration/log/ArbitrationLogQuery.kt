package bot.wuliang.arbitration.log

object ArbitrationLogQuery {
    const val ACTION = "^仲裁(?:分析|日志)(?:\\s+(.*))?$"
    const val HELP =
        "直接发送主机的 EE.log，分析最近一局仲裁；或先发「仲裁分析」，${ArbitrationLogLimits.UPLOAD_WAIT_MINUTES}分钟内发送文件。\n" +
                "使用「仲裁分析 上次 详情」查询自己上次仲裁详细情况。\n" +
                "使用「我的仲裁」查看编号，再发「仲裁分析 #35 详情」查看该局。\n" +
                "可用参数「详情/第x局/#编号/无掉率/无数量/无祝福/无巡回/无加成」\n" +
                "例：仲裁分析 详情 无祝福。仅接收 EE.log，最大${ArbitrationLogLimits.SIZE_LABEL}，少于1分钟的对局自动排除。"

    fun parse(arguments: String): ArbitrationLogOptions {
        var value = ArbitrationLogOptions()
        val seen = mutableSetOf<String>()
        for (token in arguments.trim().split(Regex("\\s+")).filter(String::isNotBlank)) {
            val key = when {
                token.matches(Regex("第[0-9]+局")) -> "run"
                token.matches(Regex("#[0-9]+")) -> "record"
                else -> token
            }
            require(seen.add(key)) { "参数重复：$token。\n$HELP" }
            value = when (key) {
                "run" -> {
                    val n = token.drop(1).dropLast(1).toIntOrNull()
                    require(n != null && n in 1..1000) { "局数需为1至1000的整数。" }
                    value.copy(run = n)
                }

                "record" -> {
                    val id = token.removePrefix("#").toLongOrNull()
                    require(id != null && id > 0) { "记录编号需为有效的正整数。" }
                    value.copy(recordId = id)
                }

                "无掉率" -> value.copy(dropChance = false)
                "无数量" -> value.copy(resource = false)
                "无祝福" -> value.copy(blessing = false)
                "无巡回" -> value.copy(retriever = false)
                "无加成" -> value.copy(dropChance = false, resource = false, blessing = false, retriever = false)
                "详情" -> value.copy(details = true)
                "上次" -> value.copy(previous = true)
                else -> throw IllegalArgumentException("无法识别参数：$token。\n$HELP")
            }
        }
        require(value.recordId == null || (!value.previous && "run" !in seen)) {
            "「#编号」不能与「上次」或「第几局」同时使用。"
        }
        return value
    }
}