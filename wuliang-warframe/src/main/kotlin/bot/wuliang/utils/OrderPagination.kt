package bot.wuliang.utils

/** 从命令尾部提取形如 `-2` 的页码，并返回移除页码后的查询内容。 */
data class PagedCommand(
    val content: String,
    val requestedPage: Int?,
) {
    val page: Int
        get() = requestedPage ?: 1

    companion object {
        private val PAGE_SUFFIX = Regex("""(?:^|\s)-(\d+)\s*$""")

        fun parse(raw: String): PagedCommand {
            val trimmed = raw.trim()
            val match = PAGE_SUFFIX.find(trimmed)
            val page = match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
            val content = match?.let { trimmed.removeRange(it.range).trim() } ?: trimmed
            return PagedCommand(content, page)
        }
    }
}

data class PageSlice<T>(
    val items: List<T>,
    val currentPage: Int,
    val totalPages: Int,
) {
    val nextPage: Int?
        get() = (currentPage + 1).takeIf { it <= totalPages }
}

/** 对内存中的已过滤、已排序结果分页；越界页码收敛到最后一页。 */
fun <T> List<T>.paginate(requestedPage: Int, pageSize: Int = 5): PageSlice<T> {
    require(pageSize > 0) { "pageSize must be greater than zero" }
    val totalPages = maxOf(1, (size + pageSize - 1) / pageSize)
    val currentPage = requestedPage.coerceIn(1, totalPages)
    return PageSlice(
        items = drop((currentPage - 1) * pageSize).take(pageSize),
        currentPage = currentPage,
        totalPages = totalPages,
    )
}