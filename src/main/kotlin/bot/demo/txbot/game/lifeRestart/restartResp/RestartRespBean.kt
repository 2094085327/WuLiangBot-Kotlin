package bot.demo.txbot.game.lifeRestart.restartResp


/**
 * @description: RestartRespBean
 * @author Nature Zero
 * @date 2024/9/23 23:51
 */
class RestartRespBean(
    val code: Long = 0,
    var message: String,
    private val obj: Any? = null
) {


    companion object {
        /**
         * 成功返回结果
         *
         * @return RespBean
         */
        fun success(): RestartRespBean {
            return RestartRespBean(RestartRespEnum.SUCCESS.code, RestartRespEnum.SUCCESS.message, null)
        }

        fun success(obj: Any?): RestartRespBean {
            return RestartRespBean(RestartRespEnum.SUCCESS.code, RestartRespEnum.SUCCESS.message, obj)
        }

        /**
         * 失败返回结果
         *
         * @param respBeanEnum 错误信息
         * @return respBean
         */
        fun error(respBeanEnum: RestartRespEnum): RestartRespBean {
            return RestartRespBean(respBeanEnum.code, respBeanEnum.message, null)
        }

        fun error(respBeanEnum: RestartRespEnum, obj: Any?): RestartRespBean {
            return RestartRespBean(respBeanEnum.code, respBeanEnum.message, obj)
        }
    }
}