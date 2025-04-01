package bot.wuliang.service.impl

import bot.wuliang.entity.GenShinEntity
import bot.wuliang.mapper.GenShinMapper
import bot.wuliang.service.GenShinService
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.toolkit.Wrappers
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 *@Description:
 *@Author zeng
 *@Date 2023/10/4 11:24
 *@User 86188
 */
@Service
class GenShinServiceImpl : ServiceImpl<GenShinMapper?, GenShinEntity?>(), GenShinService {
    @Autowired
    lateinit var genShinMapper: GenShinMapper


    private fun insertOrUpdate(genshininfo: GenShinEntity, uid: String, qqId: String?) {
        val queryWrapper: QueryWrapper<GenShinEntity> =
            Wrappers.query<GenShinEntity?>().apply { eq("uid", uid).eq("qqid", qqId) }
        if (genShinMapper.selectOne(queryWrapper) == null) {
            genShinMapper.insert(genshininfo)
        } else {
            genShinMapper.update(genshininfo, queryWrapper)
        }
    }


    override fun insertByUid(uid: String, qqId: String?, nickName: String) {
        val genShinEntity = GenShinEntity(
            uid = uid,
            qqId = qqId,
            nickname = nickName,
            sToken = "",
            push = 0,
            deletes = 1,
            status = 0,
            updateTime = LocalDateTime.now()
        )
        insertOrUpdate(genShinEntity, uid, qqId)
    }

    override fun selectByQqId(qqId: String): String {
        val queryWrapper = QueryWrapper<GenShinEntity>().eq("qqid", qqId)
        val genShinEntity = genShinMapper.selectList(queryWrapper) ?: return "Null"

        if (genShinEntity.size == 1) return genShinEntity[0]?.uid.toString()

        val latestData = genShinEntity.maxByOrNull { it?.updateTime ?: LocalDateTime.MIN }
        return latestData?.uid.toString()
    }

    override fun getUidByQqId(qqId: String): String {
        val queryWrapper = QueryWrapper<GenShinEntity>().eq("qqId", qqId).orderByDesc("updatetime")
        val genshininfo = genShinMapper.selectList(queryWrapper)
        return if (genshininfo.size == 0) {
            "无记录"
        } else {
            genshininfo[0]?.uid.toString()
        }
    }

    override fun selectByUid(uid: String): String {
        val queryWrapper = QueryWrapper<GenShinEntity>().eq("uid", uid)
        val genShinEntity = genShinMapper.selectOne(queryWrapper) ?: return "Null"

        return genShinEntity.uid.toString()
    }

}