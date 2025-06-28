package bot.wuliang.exception


/**
 * @description: 响应结果
 * @author Nature Zero
 * @date 2024/8/16 下午2:13
 */
class RespBean(
    val code: Long = 0,
    var message: String? = null,
    val obj: Any? = null
) {


    companion object {
        /**
         * 成功返回结果
         *
         * @return RespBean
         */
        fun success(): RespBean {
            return RespBean(RespBeanEnum.SUCCESS.code, RespBeanEnum.SUCCESS.message, null)
        }

        fun success(obj: Any?): RespBean {
            return RespBean(RespBeanEnum.SUCCESS.code, RespBeanEnum.SUCCESS.message, obj)
        }


        fun error(): RespBean {
            return RespBean(RespBeanEnum.ERROR.code, RespBeanEnum.ERROR.message, null)
        }

        /**
         * 失败返回结果
         *
         * @param respCode 错误信息
         * @return respBean
         */
        fun error(respCode: RespCode): RespBean {
            return RespBean(respCode.code, respCode.message, null)
        }

        fun error(respCode: RespCode, obj: Any?): RespBean {
            return RespBean(respCode.code, respCode.message, obj)
        }

        /**
         * 返回结果
         *
         * @param result 布尔类型
         */
        fun toReturn(result: Boolean): RespBean {
            return if (result) success() else error()
        }

        /**
         * 返回结果
         *
         * @param result 布尔类型
         */
        fun toReturn(result: Boolean,obj: Any?): RespBean {
            return if (result) success(obj) else error()
        }

        /**
         * 响应返回结果
         *
         * @param rows 影响行数
         * @return 操作结果
         */
        fun toReturn(rows:Int): RespBean {
           return if (rows > 0) success() else error();
        }
        /**
         * 响应返回结果
         *
         * @param rows 影响行数
         * @return 操作结果
         */
        fun toReturn(rows:Int,obj: Any?): RespBean {
           return if (rows > 0) success(obj) else error();
        }
    }
}