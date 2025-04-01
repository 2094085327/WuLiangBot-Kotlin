package bot.wuliang.utils

import bot.wuliang.config.GACHA_JSON
import bot.wuliang.config.POOL_JSON
import bot.wuliang.jacksonUtil.JacksonUtil


/**
 * @description: 初始化原神数据
 * @author Nature Zero
 * @date 2024/4/3 8:39
 */
class InitGenShinData {
    companion object {
        var upPoolData = JacksonUtil.getJsonNode(POOL_JSON)
        var poolData = JacksonUtil.getJsonNode(GACHA_JSON)
        fun initGachaLogData() {
            upPoolData = JacksonUtil.getJsonNode(POOL_JSON)
            poolData = JacksonUtil.getJsonNode(GACHA_JSON)
        }
    }
}