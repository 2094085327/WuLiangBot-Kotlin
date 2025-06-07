package bot.wuliang.utils.sql

import bot.wuliang.utils.StringUtils
import cn.hutool.core.exceptions.UtilException


/**
 * sql操作工具类
 */
object SqlUtil {
    /**
     * 定义常用的 sql关键字
     */
    const val SQL_REGEX =
        "select |insert |delete |update |drop |count |exec |chr |mid |master |truncate |char |and |declare "

    /**
     * 仅支持字母、数字、下划线、空格、逗号、小数点（支持多个字段排序）
     */
    const val SQL_PATTERN = "[a-zA-Z0-9_ ,.]+"

    /**
     * 检查字符，防止注入绕过
     */
    fun escapeOrderBySql(value: String?): String? {
        if (StringUtils.isNotEmpty(value) && !isValidOrderBySql(value)) {
            throw UtilException("参数不符合规范，不能进行查询")
        }
        return value
    }

    /**
     * 验证 order by 语法是否符合规范
     */
    fun isValidOrderBySql(value: String?): Boolean {
        return value?.matches(Regex(SQL_PATTERN)) ?: false
    }

}
