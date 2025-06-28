package bot.wuliang.controller

import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.entity.DirectivesCategoryEntity
import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.exception.RespBean
import bot.wuliang.redis.RedisService
import bot.wuliang.service.DirectivesService
import bot.wuliang.service.impl.DirectivesCategoryServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

/**
 * 系统指令接口
 * @module 系统指令接口
 */
@Api(tags = ["系统指令接口"])
@RestController
@RequestMapping("/directives")
class DirectivesController @Autowired constructor(
    private val directivesService: DirectivesService,
    private val directivesCategoryService: DirectivesCategoryServiceImpl,
    private val redisService: RedisService
) {


    /**
     * 获取指令列表
     * @param directivesEntity 指令Entity
     * @return 成功信息
     */
    @ApiOperation("获取指令列表")
    @GetMapping("/list")
    fun getDirectivesList(directivesEntity: DirectivesEntity?): RespBean {
        if (redisService.hasKey(DIRECTIVES_KEY)) {
            return RespBean.success(redisService.getValue(DIRECTIVES_KEY))
        }

        val selectDirectivesList = directivesService.selectDirectivesList(directivesEntity)

        if (selectDirectivesList.isNotEmpty()) {
            redisService.setValue(DIRECTIVES_KEY, selectDirectivesList)
        }
        return RespBean.success(selectDirectivesList)
    }

    /**
     * 增加指令
     */
    @ApiOperation("新增指令")
    @PostMapping("/addDirectives")
    fun addDirectives(@RequestBody directivesEntity: DirectivesEntity): RespBean {
        redisService.deleteKey(DIRECTIVES_KEY)
        return RespBean.toReturn(directivesService.save(directivesEntity))
    }

    /**
     * 更新指令
     */
    @ApiOperation("更新指令")
    @PutMapping("/updateDirectives")
    fun updateDirectives(@RequestBody directivesEntity: DirectivesEntity): RespBean {
        redisService.deleteKey(DIRECTIVES_KEY)
        return RespBean.toReturn(directivesService.updateById(directivesEntity))
    }

    /**
     * 更新指令
     */
    @ApiOperation("批量更新指令")
    @PutMapping("/updateBatchDirectives")
    fun updateBatchDirectives(@RequestBody directivesEntity: Collection<DirectivesEntity>): RespBean {
        redisService.deleteKey(DIRECTIVES_KEY)
        return RespBean.toReturn(directivesService.updateBatchById(directivesEntity))
    }

    /**
     * 删除指令
     */
    @ApiOperation("删除单个指令")
    @DeleteMapping("/delete/{id}")
    fun deleteDirectives(@PathVariable id: Long): RespBean {
        redisService.deleteKey(DIRECTIVES_KEY)
        return RespBean.toReturn(directivesService.deleteDirective(id))
    }

    /**
     * 导入指令
     */
    @ApiOperation("导入指令列表")
    @PostMapping("/import")
    fun importDirectives(file: MultipartFile, covered: Boolean? = false): RespBean {
        redisService.deleteKey(DIRECTIVES_KEY)
        return try {
            val objectMapper = ObjectMapper()
            val directivesJson = objectMapper.readTree(file.inputStream)
            val allCmd = directivesJson["allCmd"]

            // 分类处理优化
            val importCategories = allCmd.fieldNames().asSequence().toList()
            val existingCategories = directivesCategoryService.findByCategoryNamesIn(importCategories)
                .associateByTo(mutableMapOf()) { it.categoryName }

            val categoriesToProcess = mutableListOf<DirectivesCategoryEntity>().apply {
                importCategories.forEach { categoryName ->
                    val categoryNode = allCmd[categoryName]
                    val newDesc = categoryNode["description"].textValue()

                    existingCategories[categoryName]?.let { existing ->
                        if (covered == true) {
                            add(
                                existing.copy(
                                    categoryDesc = newDesc,
                                    updateTime = Date()
                                )
                            )
                        }
                    } ?: run {
                        add(
                            DirectivesCategoryEntity(
                                categoryName = categoryName,
                                categoryDesc = newDesc,
                                createTime = Date()
                            )
                        )
                    }
                }
            }

            // 批量处理分类（自动填充ID）
            categoriesToProcess.partition { it.id == null }.let { (toInsert, toUpdate) ->
                if (toInsert.isNotEmpty()) {
                    directivesCategoryService.batchAddCategories(toInsert).forEach {
                        existingCategories[it.categoryName] = it // 自动填充ID后的实体
                    }
                }
                if (covered == true && toUpdate.isNotEmpty()) {
                    directivesCategoryService.batchUpdateCategories(toUpdate)
                }
            }

            // 指令处理优化
            val categoryIds = existingCategories.values.mapNotNull { it.id }
            val existingDirectivesMap = directivesService.findByCategoryIds(categoryIds)
                .groupBy { it.categoryId to it.directiveName } // 使用复合键

            val (allDirectives, directivesToUpdate) = mutableListOf<DirectivesEntity>() to mutableListOf<DirectivesEntity>()

            importCategories.forEach { categoryName ->
                val categoryEntity = existingCategories[categoryName] ?: return@forEach
                val categoryNode = allCmd[categoryName]
                val commendList = categoryNode["commendList"]?.takeIf { it.isArray }
                    ?.mapIndexed { _, cmdNode ->
                        DirectivesEntity(
                            directiveName = cmdNode["command"].asText(),
                            description = cmdNode["description"].asText(),
                            detail = cmdNode["detail"].asText(),
                            regex = cmdNode["regex"].asText(),
                            enable = 1,
                            categoryId = categoryEntity.id!!,
                            createTime = Date(),
                            updateTime = if (covered == true) Date() else null
                        ).apply {
                            existingDirectivesMap[categoryEntity.id to directiveName]?.firstOrNull()?.let {
                                id = it.id // 设置已有ID用于更新
                            }
                        }
                    } ?: emptyList()
                commendList.asSequence()
                    .forEach { item ->
                        if (item.id == null) {
                            allDirectives.add(item)
                        } else {
                            directivesToUpdate.add(item)
                        }
                    }
            }

            // 批量处理指令
            if (covered == true && directivesToUpdate.isNotEmpty()) {
                directivesService.updateBatchById(directivesToUpdate as Collection<DirectivesEntity?>?)
            }
            if (allDirectives.isNotEmpty()) {
                directivesService.batchAddDirectives(allDirectives)
            }

            RespBean.success(directivesJson)
        } catch (e: Exception) {
            RespBean.error().apply { message = "处理失败: ${e.message}" }
        }
    }

}