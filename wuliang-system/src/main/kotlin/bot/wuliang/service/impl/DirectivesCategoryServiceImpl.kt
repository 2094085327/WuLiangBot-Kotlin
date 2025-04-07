package bot.wuliang.service.impl

import bot.wuliang.entity.DirectivesCategoryEntity
import bot.wuliang.mapper.DirectivesCategoryMapper
import bot.wuliang.service.DirectivesCategoryService
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class DirectivesCategoryServiceImpl : ServiceImpl<DirectivesCategoryMapper?, DirectivesCategoryEntity?>(),
    DirectivesCategoryService {
    @Autowired
    private lateinit var directivesCategoryMapper: DirectivesCategoryMapper
    override fun selectDirectivesCategoryList(): MutableList<DirectivesCategoryEntity?> {
        return directivesCategoryMapper.selectList(null)
    }

    override fun addDirectivesCategory(directivesCategoryEntity: DirectivesCategoryEntity): Int {
        return directivesCategoryMapper.insert(directivesCategoryEntity)
    }

    override fun findByCategoryNamesIn(categoryNameList: List<String>): List<DirectivesCategoryEntity> {
        return directivesCategoryMapper.findByCategoryNamesIn(categoryNameList)
    }

    override fun batchAddCategories(categories: List<DirectivesCategoryEntity>): List<DirectivesCategoryEntity> {
        return directivesCategoryMapper.batchAddCategories(categories)
    }

    override fun batchUpdateCategories(categories: List<DirectivesCategoryEntity>) {
        directivesCategoryMapper.batchUpdateCategories(categories)
    }
}