package bot.wuliang.api

import bot.wuliang.config.WfMarketConfig.WF_ALL_OTHER_NAME_KEY
import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.exception.RespBean
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.service.WfAliasService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.*

/** 管理 Warframe 物品别名，保留原有管理接口路径。 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeAliasController(
    private val wfAliasService: WfAliasService,
    private val redisService: RedisService,
) {

    /**
     * 新增别名
     */
    @ApiOperation("新增别名")
    @PostMapping("/wfManage/setOtherName")
    fun addOtherName(
        @RequestParam("itemName") itemName: String,
        @RequestParam("otherName") otherName: String,
    ): RespBean<Nothing> {
        redisService.deleteKey(WF_ALL_OTHER_NAME_KEY)
        return RespBean.toReturn(wfAliasService.insertOtherName(itemName, otherName))
    }

    /** 读取全部查询别名；缓存未命中时从数据库加载并填充别名缓存。 */
    @GetMapping("/allOtherName")
    @Suppress("UNCHECKED_CAST")
    fun allOtherName(): RespBean<List<WfOtherNameEntity>> {
        if (redisService.getValue(WF_ALL_OTHER_NAME_KEY) == null) {
            val allOtherName = wfAliasService.selectAllOtherName()
            redisService.setValue(WF_ALL_OTHER_NAME_KEY, allOtherName)
            return RespBean.success(allOtherName)
        } else return RespBean.success(redisService.getValue(WF_ALL_OTHER_NAME_KEY) as List<WfOtherNameEntity>)
    }

    /** 删除 [id] 对应的别名并使别名列表缓存失效，操作失败时返回错误响应。 */
    @RequestMapping("/deleteOtherName")
    fun deleteOtherName(@RequestParam("other_name_id") id: Int): RespBean<Nothing> {
        try {
            wfAliasService.deleteOtherName(id)
            redisService.deleteKey(WF_ALL_OTHER_NAME_KEY)
            return RespBean.success()
        } catch (_: Exception) {
            return RespBean.error(WarframeRespEnum.DELETE_OTHER_NAME_ERROR)
        }
    }

    /** 更新 [id] 对应的别名文本 [otherName]，成功后使别名列表缓存失效。 */
    @RequestMapping("/updateOtherName")
    fun updateOtherName(
        @RequestParam("other_name_id") id: Int,
        @RequestParam("other_name") otherName: String
    ): RespBean<Nothing> {
        try {
            wfAliasService.updateOtherName(id, otherName)
            redisService.deleteKey(WF_ALL_OTHER_NAME_KEY)
            return RespBean.success()
        } catch (_: Exception) {
            return RespBean.error(WarframeRespEnum.UPDATE_OTHER_NAME_ERROR)
        }
    }
}