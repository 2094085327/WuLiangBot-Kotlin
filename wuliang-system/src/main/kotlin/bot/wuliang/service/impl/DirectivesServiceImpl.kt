package bot.wuliang.service.impl

import bot.wuliang.distribute.service.SchemaAutoDiscoveryService
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
    @Autowired
    private lateinit var schemaDiscoveryService: SchemaAutoDiscoveryService
    override fun selectDirectivesList(directivesEntity: DirectivesEntity?): List<DirectivesEntity> {
        val directivesList = directivesMapper.selectDirectivesList(directivesEntity)
        directivesList.forEach { directive->
            val commandKey = directive.commandKey

            val schema = commandKey?.let { schemaDiscoveryService.discoverSchema(it) }

            directive.supportMd = schema?.isNotEmpty() ?: false
        }

        return directivesList
    }

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