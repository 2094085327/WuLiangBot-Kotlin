package bot.wuliang.service

import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.mapper.WfAliasMapper
import org.springframework.stereotype.Service

/** 用户查询别名的管理入口；游戏译文由 PublicExportService 提供。 */
@Service
class WfAliasService(private val aliasMapper: WfAliasMapper) {
    /**
     * 将用户维护的查询别名转换为英文检索名称。
     */
    fun getOtherName(alias: String): String? = aliasMapper.findEnglishName(alias)

    fun insertOtherName(enName: String, otherName: String): Int =
        aliasMapper.save(enName, otherName)

    fun selectAllOtherName(): List<WfOtherNameEntity> = aliasMapper.findAll()

    fun deleteOtherName(id: Int) = aliasMapper.delete(id)

    fun updateOtherName(id: Int, otherName: String) = aliasMapper.update(id, otherName)
}