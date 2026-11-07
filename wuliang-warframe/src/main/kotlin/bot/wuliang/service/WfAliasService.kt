package bot.wuliang.service

import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.mapper.WfAliasMapper
import org.springframework.stereotype.Service
import java.util.Locale

/** 用户查询别名的管理入口；游戏译文由 PublicExportService 提供。 */
@Service
class WfAliasService(private val aliasMapper: WfAliasMapper) {
    /**
     * 将用户维护的查询别名转换为英文检索名称。
     */
    fun getOtherName(alias: String): String? = aliasMapper.findEnglishName(alias)

    /** 保存英文物品名称 [enName] 与查询别名 [otherName] 的对应关系，返回受影响行数。 */
    fun insertOtherName(enName: String, otherName: String): Int =
        aliasMapper.save(enName, otherName)

    /** 从数据库读取全部查询别名，不操作接口层的列表缓存。 */
    fun selectAllOtherName(): List<WfOtherNameEntity> = aliasMapper.findAll()

    /** 展示别名优先双字，其次单字，再按长度选择；同长度取较早的记录。 */
    fun preferredAliases(enNames: Collection<String>): Map<String, String> {
        val names = enNames.map { it.trim().lowercase(Locale.ROOT) }.filter(String::isNotEmpty).distinct()
        if (names.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        aliasMapper.findByEnglishNames(names)
            .filter { !it.enItemName.isNullOrBlank() && !it.otherName.isNullOrBlank() }
            .sortedWith(compareBy<WfOtherNameEntity> {
                val alias = it.otherName.orEmpty().trim()
                when (val length = alias.codePointCount(0, alias.length)) {
                    2 -> 0
                    1 -> 1
                    else -> length
                }
            }.thenBy { it.id ?: Int.MAX_VALUE })
            .forEach {
                result.putIfAbsent(it.enItemName!!.trim().lowercase(Locale.ROOT), it.otherName!!.trim())
            }
        return result
    }

    /** 删除数据库中主键为 [id] 的别名，列表缓存由调用方处理。 */
    fun deleteOtherName(id: Int) = aliasMapper.delete(id)

    /** 更新数据库中主键为 [id] 的别名文本，列表缓存由调用方处理。 */
    fun updateOtherName(id: Int, otherName: String) = aliasMapper.update(id, otherName)
}