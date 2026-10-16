package bot.wuliang.worldstate.presentation

import bot.wuliang.moudles.VoidTraderItem
import java.util.regex.Pattern

/** 商人物品按中文名称的公共前后缀分组排序；该显示策略不影响原始库存解析。 */
internal object TraderInventoryOrder {
    /** 按商品名称的公共前后缀分组，再按基础名称、中文优先级和完整名称排序；不修改传入列表。 */
    fun sort(items: List<VoidTraderItem>): List<VoidTraderItem> {
        // 基于最长公共子串的前缀/后缀识别算法
        /** 返回两个名称从首字符开始连续相同的部分。 */
        fun longestCommonPrefix(s1: String, s2: String): String {
            val minLength = minOf(s1.length, s2.length)
            for (i in 0 until minLength) {
                if (s1[i] != s2[i]) {
                    return s1.substring(0, i)
                }
            }
            return s1.substring(0, minLength)
        }

        /** 返回两个名称从末字符开始连续相同的部分。 */
        fun longestCommonSuffix(s1: String, s2: String): String {
            val minLength = minOf(s1.length, s2.length)
            for (i in 1..minLength) {
                if (s1[s1.length - i] != s2[s2.length - i]) {
                    return s1.substring(s1.length - i + 1)
                }
            }
            return s1.substring(s1.length - minLength)
        }

        // 计算所有物品之间的公共前缀和后缀
        val prefixCount = mutableMapOf<String, Int>()
        val suffixCount = mutableMapOf<String, Int>()

        // 两两比较计算公共前缀和后缀
        for (i in items.indices) {
            for (j in i + 1 until items.size) {
                val item1 = items[i].item ?: continue
                val item2 = items[j].item ?: continue

                val commonPrefix = longestCommonPrefix(item1.lowercase(), item2.lowercase())
                val commonSuffix = longestCommonSuffix(item1.lowercase(), item2.lowercase())

                // 只考虑有一定长度的公共前缀/后缀（至少2个字符）
                if (commonPrefix.length >= 2) {
                    prefixCount[commonPrefix] = prefixCount.getOrDefault(commonPrefix, 0) + 1
                }

                if (commonSuffix.length >= 2) {
                    suffixCount[commonSuffix] = suffixCount.getOrDefault(commonSuffix, 0) + 1
                }
            }
        }

        // 确定常见前缀和后缀（出现次数>=2）
        val commonPrefixes = prefixCount.filter { it.value >= 2 }.keys
        val commonSuffixes = suffixCount.filter { it.value >= 2 }.keys

        // 找到物品的最佳匹配前缀/后缀
        /** 查找 [itemName] 所属公共前缀组中最长的匹配项；无匹配时返回空字符串。 */
        fun findBestPrefix(itemName: String): String {
            val lowerItemName = itemName.lowercase()
            return commonPrefixes
                .filter { lowerItemName.startsWith(it) }
                .maxByOrNull { it.length } ?: ""
        }

        /** 查找 [itemName] 所属公共后缀组中最长的匹配项；无匹配时返回空字符串。 */
        fun findBestSuffix(itemName: String): String {
            val lowerItemName = itemName.lowercase()
            return commonSuffixes
                .filter { lowerItemName.endsWith(it) }
                .maxByOrNull { it.length } ?: ""
        }

        // 排序逻辑
        return items.sortedWith(compareBy<VoidTraderItem> { item ->
            // 首先将有分组的物品排在前面
            val hasCommonPrefix = findBestPrefix(item.item ?: "").isNotEmpty()
            val hasCommonSuffix = findBestSuffix(item.item ?: "").isNotEmpty()

            // 没有分组的物品排在后面（返回1），有分组的排在前面（返回0）
            if (hasCommonPrefix || hasCommonSuffix) 0 else 1
        }.thenBy { item ->
            // 然后按前缀分组
            findBestPrefix(item.item ?: "")
        }.thenBy { item ->
            // 再按后缀分组
            findBestSuffix(item.item ?: "")
        }.thenBy { item ->
            // 再按基础名称排序（去除常见前缀和后缀）
            val itemName = item.item ?: ""
            val lowerItemName = itemName.lowercase()
            val bestPrefix = findBestPrefix(itemName)
            val bestSuffix = findBestSuffix(itemName)

            var result = lowerItemName
            if (bestPrefix.isNotEmpty()) {
                result = result.substring(bestPrefix.length).trim()
            }
            if (bestSuffix.isNotEmpty()) {
                if (result.endsWith(bestSuffix)) {
                    result = result.substring(0, result.length - bestSuffix.length).trim()
                }
            }
            result
        }.thenByDescending { item ->
            // 优先显示包含中文的物品
            item.item?.let { Pattern.compile("[\\u4e00-\\u9fff]+").matcher(it).find() } ?: false
        }.thenBy { item ->
            // 最后按完整名称排序
            item.item?.lowercase() ?: ""
        })
    }
}