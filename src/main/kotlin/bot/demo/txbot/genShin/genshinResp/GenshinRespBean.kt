package bot.demo.txbot.genShin.genshinResp


/**
 * @description: 原神返回结果
 * @author Nature Zero
 * @date 2024/9/18 16:47
 */
@Suppress("unused")
class GenshinRespBean(
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
        fun success(): GenshinRespBean {
            return GenshinRespBean(GenshinRespEnum.SUCCESS.code, GenshinRespEnum.SUCCESS.message, null)
        }

        fun success(obj: Any?): GenshinRespBean {
            return GenshinRespBean(GenshinRespEnum.SUCCESS.code, GenshinRespEnum.SUCCESS.message, obj)
        }

        /**
         * 失败返回结果
         *
         * @param respBeanEnum 错误信息
         * @return respBean
         */
        fun error(respBeanEnum: GenshinRespEnum): GenshinRespBean {
            return GenshinRespBean(respBeanEnum.code, respBeanEnum.message, null)
        }

        fun error(respBeanEnum: GenshinRespEnum, obj: Any?): GenshinRespBean {
            return GenshinRespBean(respBeanEnum.code, respBeanEnum.message, obj)
        }
    }
}