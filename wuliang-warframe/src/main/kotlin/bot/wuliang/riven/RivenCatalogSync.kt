package bot.wuliang.riven

import bot.wuliang.config.WfMarketConfig.WF_MARKET_RIVEN_KEY
import bot.wuliang.entity.WfRivenAttributeEntity
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.mapper.WfRivenAttributeMapper
import bot.wuliang.mapper.WfRivenMapper
import bot.wuliang.redis.RedisService
import bot.wuliang.utils.WfUtil
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class RivenCatalogSyncResult(
    val weaponCount: Int,
    val attributeCount: Int,
)

@Component
class RivenCatalogSync(
    private val wfUtil: WfUtil,
    private val writer: RivenCatalogWriter,
    private val redisService: RedisService,
) {
    fun sync(): RivenCatalogSyncResult {
        val weapons = wfUtil.getRivenItems()
        val attributes = wfUtil.getRivenAttributes()
        RivenCatalogValidator.validate(weapons, attributes)
        writer.replace(weapons, attributes)
        redisService.setValue(WF_MARKET_RIVEN_KEY, weapons)
        return RivenCatalogSyncResult(weapons.size, attributes.size)
    }
}

/** 在同一事务中替换标准紫卡武器和属性目录。 */
@Service
class RivenCatalogWriter(
    private val rivenMapper: WfRivenMapper,
    private val attributeMapper: WfRivenAttributeMapper,
) {
    @Transactional
    fun replace(weapons: List<WfRivenEntity>, attributes: List<WfRivenAttributeEntity>) {
        // lich/sister 仍由各自的数据源维护，因此这里只替换标准紫卡武器。
        rivenMapper.delete(
            QueryWrapper<WfRivenEntity>().notIn("r_group", "lich", "sister")
        )
        attributeMapper.delete(null)
        rivenMapper.insertOrUpdateBatch(weapons)
        attributeMapper.insertOrUpdateBatch(attributes)
    }
}

object RivenCatalogValidator {
    /** 在执行 delete + insert 前拒绝空目录和关键字段异常，防止坏响应清空本地词库。 */
    fun validate(weapons: List<WfRivenEntity>, attributes: List<WfRivenAttributeEntity>) {
        require(weapons.isNotEmpty()) { "Warframe Market v2 返回了空的紫卡武器目录" }
        require(attributes.isNotEmpty()) { "Warframe Market v2 返回了空的紫卡属性目录" }
        require(weapons.all { !it.id.isNullOrBlank() && !it.urlName.isNullOrBlank() }) {
            "紫卡武器目录包含缺少 id 或 slug 的记录"
        }
        require(attributes.all {
            !it.id.isNullOrBlank() && !it.urlName.isNullOrBlank() &&
                !it.zhName.isNullOrBlank() && !it.enName.isNullOrBlank()
        }) { "紫卡属性目录包含缺少 id、slug 或名称的记录" }
        require(weapons.mapNotNull { it.id }.distinct().size == weapons.size) { "紫卡武器目录包含重复 id" }
        require(attributes.mapNotNull { it.id }.distinct().size == attributes.size) { "紫卡属性目录包含重复 id" }
        require(attributes.mapNotNull { it.urlName }.distinct().size == attributes.size) { "紫卡属性目录包含重复 slug" }
    }
}