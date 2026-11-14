package bot.wuliang.arbitration.log.history

object ArbitrationLogHistoryQuery {
    const val ACTION = "^我的仲裁(?:\\s+(.*))?$"
    const val PAGE_SIZE = 50
    const val HELP = "发送「我的仲裁」查看自己的仲裁记录\n" +
            "翻页：我的仲裁 第2页，也可发送「我的仲裁 2」\n" +
            "按列表编号查看：仲裁分析 #35，或「仲裁分析 #35 详情」(图片中#后的数字即为自己的编号)。\n"

    fun page(arguments: String): Int {
        val value = arguments.trim()
        if (value.isEmpty()) return 1
        require(value.matches(Regex("[0-9]+|第[0-9]+页"))) { "页码格式不正确。\n$HELP" }
        val page = value.removePrefix("第").removeSuffix("页").toIntOrNull()
        require(page != null && page > 0) { "页码需为正整数。\n$HELP" }
        return page
    }
}