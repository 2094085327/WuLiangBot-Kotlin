package bot.wuliang.controller

import bot.wuliang.entity.DirectivesCategoryEntity
import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.exception.RespBean
import bot.wuliang.service.DirectivesService
import bot.wuliang.service.impl.DirectivesCategoryServiceImpl
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@RequestMapping("/directives")
class DirectivesController @Autowired constructor(
    private val directivesService: DirectivesService,
    private val directivesCategoryService: DirectivesCategoryServiceImpl
) {

    private val objectMapper = ObjectMapper()

    /**
     * 获取指令列表
     */
    @GetMapping("/list")
    fun getDirectivesList(): RespBean {
        return RespBean.success(directivesService.selectDirectivesList())
    }

    /**
     * 增加指令
     */
    @PostMapping("/addDirectives")
    fun addDirectives(directivesEntity: DirectivesEntity): RespBean {
        return RespBean.toReturn(directivesService.addDirectives(directivesEntity))
    }

    /**
     * 导入指令
     */
    @PostMapping("/import")
    fun importDirectives(file: MultipartFile, covered: Boolean? = false): RespBean {
        try {
            val objectMapper = ObjectMapper()
            val directivesJson = objectMapper.readTree(file.inputStream)
            val allCmd = directivesJson["allCmd"]

            val importCategories = allCmd.fieldNames().asSequence().toList()
            println(importCategories)

/*            val existingCategories = directivesCategoryService.findByCategoryNamesIn(importCategories)
                .associateBy { it.categoryName }

            val newCategories = importCategories.filterNot { existingCategories.containsKey(it) }
                .map {
                    DirectivesCategoryEntity(
                        categoryName = it,
                        categoryDesc =allCmd[it]["description"].textValue(),
                        createTime = Date(),
                    )
                }
            if (newCategories.isNotEmpty())     directivesCategoryService.batchAddCategories(newCategories)


            if (covered == true) {
            }*/

            // 2. 批量查询已存在分类
            val existingCategories = directivesCategoryService.findByCategoryNamesIn(importCategories)
                .associateBy { it.categoryName }.toMutableMap()

            // 3. 准备需要操作的分类集合
            val categoriesToProcess = mutableListOf<DirectivesCategoryEntity>()

            importCategories.forEach { categoryName ->
                val newDesc = allCmd[categoryName]["description"].textValue()

                // 存在且需要覆盖的情况
                existingCategories[categoryName]?.let { existing ->
                    if (covered == true) {
                        categoriesToProcess.add(existing.copy(
                            categoryDesc = newDesc,
                            updateTime = Date() // 添加更新时间
                        ))
                    }
                } ?: run { // 新增的情况
                    categoriesToProcess.add(DirectivesCategoryEntity(
                        categoryName = categoryName,
                        categoryDesc = newDesc,
                        createTime = Date()
                    ))
                }
            }

            // 4. 批量操作数据库
            if (categoriesToProcess.isNotEmpty()) {
                // 拆分新增和更新
                val (toInsert, toUpdate) = categoriesToProcess.partition { it.id == null }

                if (toInsert.isNotEmpty()) {
                    directivesCategoryService.batchAddCategories(toInsert)
                }
//                if (covered == true && toUpdate.isNotEmpty()) {
//                    directivesCategoryService.batchUpdateCategories(toUpdate)
//                }
            }




            return RespBean.success(directivesJson)
        } catch (e: JsonProcessingException) {
            return RespBean.error()
        }
    }

}