package bot.demo.txbot.warframe.warframeResp


/**
 * @description: 原神返回结果
 * @author Nature Zero
 * @date 2024/9/18 16:47
 */
@Suppress("unused")
class WarframeRespBean(
    val code: Long = 0,
    var message: String,
    private val obj: Any? = null
) {


    companion object {
        /**
         * 成功返回结果
         *
         * @return WarframeRespBean
         */
        fun success(): WarframeRespBean {
            return WarframeRespBean(WarframeRespEnum.SUCCESS.code, WarframeRespEnum.SUCCESS.message, null)
        }

        fun success(obj: Any?): WarframeRespBean {
            return WarframeRespBean(WarframeRespEnum.SUCCESS.code, WarframeRespEnum.SUCCESS.message, obj)
        }

        /**
         * 失败返回结果
         *
         * @param WarframeRespBean 错误信息
         * @return respBean
         */
        fun error(warframeRespEnum: WarframeRespEnum): WarframeRespBean {
            return WarframeRespBean(warframeRespEnum.code, warframeRespEnum.message, null)
        }

        fun error(warframeRespEnum: WarframeRespEnum, obj: Any?): WarframeRespBean {
            return WarframeRespBean(warframeRespEnum.code, warframeRespEnum.message, obj)
        }
    }
}