package bot.wuliang.service.impl

import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.mapper.DirectivesMapper
import bot.wuliang.service.DirectivesService
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class DirectivesServiceImpl : ServiceImpl<DirectivesMapper?, DirectivesEntity?>(), DirectivesService {
    @Autowired
    lateinit var directivesMapper: DirectivesMapper

    /** 按条件读取指令及分类信息，展示方式由消息发送和图片服务负责。 */
    override fun selectDirectivesList(directivesEntity: DirectivesEntity?): List<DirectivesEntity> =
        directivesMapper.selectDirectivesList(directivesEntity)

    override fun selectDirectivesMatch(match: String): MutableList<DirectivesEntity> {
        return directivesMapper.selectDirectivesMatch(match)
    }

    override fun batchAddDirectives(directivesList: List<DirectivesEntity>): Int {
        return directivesMapper.batchAddDirectives(directivesList)
    }

    override fun findByCategoryIds(categoryIds: List<Long>): List<DirectivesEntity> {
        return directivesMapper.findByCategoryIds(categoryIds)
    }

    override fun deleteDirective(id: Long): Int {
        return directivesMapper.deleteDirectiveById(id)
    }
}
