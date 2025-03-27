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
    }
}