package service

import com.baomidou.mybatisplus.extension.service.IService
import entity.DirectivesEntity

interface DirectivesService : IService<DirectivesEntity?> {
    fun selectDirectivesList(): MutableList<DirectivesEntity?>?
    fun addDirectives(directivesEntity: DirectivesEntity): Int
}