package bot.demo.txbot.common.exception


/**
 * @description: 响应结果
 * @author Nature Zero
 * @date 2024/8/16 下午2:13
 */
class RespBean(
    val code: Long = 0,
    var message: String? = null,
    private val obj: Any? = null
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
         * @param respBeanEnum 错误信息
         * @return respBean
         */
        fun error(respBeanEnum: RespBeanEnum): RespBean {
            return RespBean(respBeanEnum.code, respBeanEnum.message, null)
        }

        fun error(respBeanEnum: RespBeanEnum, obj: Any?): RespBean {
            return RespBean(respBeanEnum.code, respBeanEnum.message, obj)
        }
    }
}