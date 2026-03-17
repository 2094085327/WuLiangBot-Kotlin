package bot.wuliang.exception


/**
 * @description: 响应结果
 * @author Nature Zero
 * @date 2024/8/16 下午2:13
 */
class RespBean<T>(
    val code: Long = 0,
    var message: String? = null,
    val obj: T? = null
) {


    companion object {
        /**
         * 成功返回结果
         *
         * @return RespBean
         */
        fun <T> success(): RespBean<T> {
            return RespBean(RespBeanEnum.SUCCESS.code, RespBeanEnum.SUCCESS.message, null)
        }

        fun <T> success(obj: T?): RespBean<T> {
            return RespBean(RespBeanEnum.SUCCESS.code, RespBeanEnum.SUCCESS.message, obj)
        }


        fun error(): RespBean<Nothing> {
            return RespBean(RespBeanEnum.ERROR.code, RespBeanEnum.ERROR.message, null)
        }

        /**
         * 失败返回结果
         *
         * @param respCode 错误信息
         * @return respBean
         */
        fun error(respCode: RespCode): RespBean<Nothing> {
            return RespBean(respCode.code, respCode.message, null)
        }

        fun <T> error(respCode: RespCode, obj: T?): RespBean<T> {
            return RespBean(respCode.code, respCode.message, obj)
        }

        fun <T> error(obj: T?): RespBean<T> {
            return RespBean(RespBeanEnum.ERROR.code, RespBeanEnum.ERROR.message, obj)
        }

        fun <T> error(message: String): RespBean<T> {
            return RespBean(RespBeanEnum.ERROR.code, message, null)
        }


        /**
         * 返回结果
         *
         * @param result 布尔类型
         */
        fun <T> toReturn(result: Boolean): RespBean<out T> {
            return if (result) success() else error()
        }

        /**
         * 返回结果
         *
         * @param result 布尔类型
         */
        fun <T> toReturn(result: Boolean, obj: T?): RespBean<out T> {
            return if (result) success(obj) else error()
        }

        /**
         * 响应返回结果
         *
         * @param rows 影响行数
         * @return 操作结果
         */
        fun <T> toReturn(rows: Int): RespBean<out T> {
            return if (rows > 0) success() else error()
        }

        /**
         * 响应返回结果
         *
         * @param rows 影响行数
         * @return 操作结果
         */
        fun <T> toReturn(rows: Int, obj: T?): RespBean<out T> {
            return if (rows > 0) success(obj) else error()
        }
    }
}