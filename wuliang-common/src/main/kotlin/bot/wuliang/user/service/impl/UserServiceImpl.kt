package bot.wuliang.user.service.impl

import bot.wuliang.user.entity.UserEntity
import bot.wuliang.user.mapper.UserMapper
import bot.wuliang.user.service.UserService
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 *@Description: 用户服务实现类
 *@Author zeng
 *@Date 2023/10/3 22:30
 *@User 86188
 */
@Service
class UserServiceImpl : ServiceImpl<UserMapper?, UserEntity?>(), UserService {
    @Autowired
    lateinit var userMapper: UserMapper

    override fun selectGenUidByRealId(realId: String): String? {
        val queryWrapper = QueryWrapper<UserEntity>().eq("real_id", realId)
        return userMapper.selectOne(queryWrapper)?.genUid
    }

    override fun insertGenUidByRealId(realId: String, genUid: String) {
        val gachaInfo = UserEntity(realId = realId, genUid = genUid)

        val queryWrapper = QueryWrapper<UserEntity>().eq("real_id", realId)
        val existGachaInfo = userMapper.selectOne(queryWrapper)
        if (existGachaInfo == null) userMapper.insert(gachaInfo)
        else userMapper.update(gachaInfo, queryWrapper)
    }
}