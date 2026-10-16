package bot.wuliang.riven

import org.springframework.stereotype.Service

/** 把机器人周榜筛选条件转换为原有前端查询参数。 */
@Service
class RivenRankingQuery {

    /** 解析武器类型、排序方向及已洗标记，生成原有周榜页面使用的查询字符串；没有条件时返回空字符串。 */
    fun toQueryString(params: String): String {
        if (params.isEmpty()) return ""

        val parts = params.trim().lowercase().split("\\s+".toRegex())
        val urlParams = mutableListOf<String>()

        // 支持的武器类型
        val weaponTypes = mapOf(
            "步枪" to "Rifle Riven Mod",
            "手枪" to "Pistol Riven Mod",
            "近战" to "Melee Riven Mod",
            "霰弹枪" to "Shotgun Riven Mod",
            "组合枪" to "Kitgun Riven Mod",
            "zaw" to "Zaw Riven Mod",
            "archgun" to "Archgun Riven Mod"
        )

        // 排序方向
        val sortDirections = mapOf(
            "正序" to "asc",
            "倒序" to "desc"
        )

        var type: String? = null
        var sort: String? = null
        var rerolled: String? = null

        for (part in parts) {
            // 检查武器类型
            weaponTypes.forEach { (key, value) ->
                if (part.contains(key, ignoreCase = true)) {
                    type = value
                }
            }

            // 检查排序方向
            sortDirections.forEach { (key, value) ->
                if (part.contains(key, ignoreCase = true)) {
                    sort = value
                }
            }

            // 检查是否已洗
            if (part.contains("0洗", ignoreCase = true) || part.contains("未洗", ignoreCase = true)) {
                rerolled = "false"
            } else if (part.contains("非0洗", ignoreCase = true) || part.contains("已洗", ignoreCase = true)) {
                rerolled = "true"
            }
        }

        // 构建URL参数
        type?.let { urlParams.add("type=$it") }
        sort?.let { urlParams.add("sort=$it") }
        rerolled?.let { urlParams.add("rerolled=$it") }

        return if (urlParams.isNotEmpty()) {
            "?" + urlParams.joinToString("&")
        } else {
            ""
        }
    }
}