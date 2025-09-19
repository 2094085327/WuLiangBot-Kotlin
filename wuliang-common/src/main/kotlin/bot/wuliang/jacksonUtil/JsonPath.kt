package bot.wuliang.jacksonUtil

/**
 * 方便按照路径获取JsonNode
 * 如获取{"a":{"b":{"c":1}}}中的1, 可以使用JsonPath.of("a", "b", "c")
 * @param c 路径
 */
class JsonPath private constructor(c: Collection<String>) : ArrayList<String>(c) {

    companion object {
        fun of(vararg paths: String): JsonPath {
            return JsonPath(listOf(*paths))
        }
    }

    // 支持链式调用
    fun addPath(path: String): JsonPath {
        this.add(path)
        return this
    }
}