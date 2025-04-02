package bot.wuliang.service

import com.baomidou.mybatisplus.extension.service.IService
import bot.wuliang.entity.DirectivesEntity

interface DirectivesService : IService<DirectivesEntity?> {
    fun selectDirectivesList(): MutableList<DirectivesEntity?>?
    fun addDirectives(directivesEntity: DirectivesEntity): Int
}