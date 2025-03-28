package bot.wuliang.user.mapper

import bot.wuliang.user.entity.UserEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper

/**
 *@Description: 用户数据库操作
 *@Author zeng
 *@Date 2023/10/3 21:51
 *@User 86188
 */
@Mapper
interface UserMapper : BaseMapper<UserEntity?>