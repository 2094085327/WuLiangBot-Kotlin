package bot.wuliang.arbitration

data class ArbitrationQuery(val table: Boolean, val filter: String = "所有", val page: Int = 1)

object ArbitrationCommandQuery {
    const val ACTION = "^仲裁(表)?(.*)$"
    const val HELP = "仲裁：查看当前、下一场和下一场高价值仲裁。\n" +
            "仲裁表：查看全部排期，每页15条；添加 -2 翻页。\n" +
            "筛选：高价值 / S / A+ / A / A- / B / C / 未评级。\n" +
            "例如：仲裁表 高价值 -2。高价值为 S、A+、A。"

    fun parse(table: Boolean, arguments: String): ArbitrationQuery {
        require(table || arguments.isBlank()) { HELP }
        var filter: String? = null
        var page: Int? = null
        for (token in arguments.trim().split(Regex("\\s+")).filter(String::isNotBlank)) {
            if (token.startsWith("-")) {
                require(page == null) { "只能指定一个页码。\n$HELP" }
                page = token.drop(1).takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
                require(page != null && page in 1..100000) { "页码需为 -1 至 -100000。\n$HELP" }
            } else {
                require(filter == null && token != "所有" && token in ArbitrationService.FILTERS) {
                    "请选择一个有效评级；查看全部排期请直接使用「仲裁表」。\n$HELP"
                }
                filter = token
            }
        }
        return ArbitrationQuery(table, filter ?: "所有", page ?: 1)
    }
}
