package bot.wuliang.service.impl

import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.entity.vo.DirectivesVo
import bot.wuliang.mapper.DirectivesMapper
import bot.wuliang.service.DirectivesService
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class DirectivesServiceImpl : ServiceImpl<DirectivesMapper?, DirectivesEntity?>(), DirectivesService {
    @Autowired
    lateinit var directivesMapper: DirectivesMapper
    override fun selectDirectivesList(directivesEntity: DirectivesEntity?): MutableList<DirectivesVo> {
        return directivesMapper.selectDirectivesList(directivesEntity)
    }

    override fun selectDirectivesMatch(match: String): MutableList<DirectivesEntity> {
        return directivesMapper.selectDirectivesMatch(match)
    }

    override fun addDirectives(directivesEntity: DirectivesEntity): Int {
        return directivesMapper.insert(directivesEntity)
    }

    override fun batchAddDirectives(directivesList: List<DirectivesEntity>): Int {
        return directivesMapper.batchAddDirectives(directivesList)
    }

    override fun findByCategoryIds(categoryIds: List<Long>): List<DirectivesEntity> {
        return directivesMapper.findByCategoryIds(categoryIds)
    }

    override fun batchUpdateDirectives(directivesToUpdate: MutableList<DirectivesEntity>) {
        return directivesMapper.batchUpdateDirectives(directivesToUpdate)
    }
}