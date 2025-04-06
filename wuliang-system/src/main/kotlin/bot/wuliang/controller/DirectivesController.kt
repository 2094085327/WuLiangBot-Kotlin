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
  /*  fun importDirectives(file: MultipartFile, covered: Boolean? = false): RespBean {
        try {
            val objectMapper = ObjectMapper()
            val directivesJson = objectMapper.readTree(file.inputStream)
            val allCmd = directivesJson["allCmd"]

            val importCategories = allCmd.fieldNames().asSequence().toList()
            println(importCategories)

            // 批量查询已存在分类
            val existingCategories = directivesCategoryService.findByCategoryNamesIn(importCategories)
                .associateBy { it.categoryName }.toMutableMap()

            // 准备需要操作的分类集合
            val categoriesToProcess = mutableListOf<DirectivesCategoryEntity>()

            importCategories.forEach { categoryName ->
                val newDesc = allCmd[categoryName]["description"].textValue()

                // 存在且需要覆盖的情况
                existingCategories[categoryName]?.let { existing ->
                    if (covered == true) {
                        categoriesToProcess.add(
                            existing.copy(
                                categoryDesc = newDesc,
                                updateTime = Date() // 添加更新时间
                            )
                        )
                    }
                } ?: run { // 新增的情况
                    categoriesToProcess.add(
                        DirectivesCategoryEntity(
                            categoryName = categoryName,
                            categoryDesc = newDesc,
                            createTime = Date()
                        )
                    )
                }
            }

            // 批量操作数据库
            if (categoriesToProcess.isNotEmpty()) {
                // 拆分新增和更新
                val (toInsert, toUpdate) = categoriesToProcess.partition { it.id == null }

                if (toInsert.isNotEmpty()) {
                    directivesCategoryService.batchAddCategories(toInsert)
                }
                if (covered == true && toUpdate.isNotEmpty()) {
                    directivesCategoryService.batchUpdateCategories(toUpdate)
                }
            }

            // 重新查询最新的分类列表
            val latestCategories = directivesCategoryService.findByCategoryNamesIn(importCategories)
            val categoryIdMap = latestCategories.associateBy { it.categoryName }

            // 处理指令本体
            importCategories.forEach { categoryName ->
*//*                val categoryId = categoryIdMap[categoryName]?.id ?: return@forEach

                // 获取该分类下的指令列表
                val commendList = allCmd[categoryName]["commendList"]?.takeIf { it.isArray }
                    ?.map { cmdNode ->
                        DirectivesEntity(
                            directiveName = cmdNode["command"].asText(),
                            description = cmdNode["description"].asText(),
                            detail = cmdNode["detail"].asText(),
                            regex = cmdNode["regex"].asText(),
                            enable = 1,
                            categoryId = categoryId,
                            createTime = Date()
                        )
                    } ?: emptyList()

                // 批量插入新指令
                if (commendList.isNotEmpty()) {
                    directivesService.batchAddDirectives(commendList)
                }*//*
                val categoryId = categoryIdMap[categoryName]?.id ?: return@forEach

                // 获取该分类下的指令列表
                val commendList = allCmd[categoryName]["commendList"]?.takeIf { it.isArray }
                    ?.map { cmdNode ->
                        DirectivesEntity(
                            directiveName = cmdNode["command"].asText(),
                            description = cmdNode["description"].asText(),
                            detail = cmdNode["detail"].asText(),
                            regex = cmdNode["regex"].asText(),
                            enable = 1,
                            categoryId = categoryId,
                            createTime = Date(),
                            updateTime = if (covered == true) Date() else null // 添加更新时间
                        )
                    } ?: emptyList()

                if (covered == true) {
                    // 获取现有指令映射表
                    val existingMap = directivesService.findByCategoryId(categoryId)
                        .associateBy { it.directiveName }

                    // 分离需要更新和新增的指令
                    val (toUpdate, toInsert) = commendList.partition {
                        existingMap.containsKey(it.directiveName)
                    }

                    // 设置ID用于更新
                    val updateList = toUpdate.map { directive ->
                        directive.copy(id = existingMap[directive.directiveName]!!.id)
                    }

                    // 批量操作
                    if (updateList.isNotEmpty()) {
                        directivesService.batchUpdateDirectives(updateList)
                    }
                    if (toInsert.isNotEmpty()) {
                        directivesService.batchAddDirectives(toInsert)
                    }
                } else {
                    // 原有插入逻辑
                    if (commendList.isNotEmpty()) {
                        directivesService.batchAddDirectives(commendList)
                    }
                }

            }


            return RespBean.success(directivesJson)
        } catch (e: JsonProcessingException) {
            return RespBean.error()
        }
    }*/

    fun importDirectives(file: MultipartFile, covered: Boolean? = false): RespBean {
        try {
            val objectMapper = ObjectMapper()
            val directivesJson = objectMapper.readTree(file.inputStream)
            val allCmd = directivesJson["allCmd"]

            val importCategories = allCmd.fieldNames().asSequence().toList()
            println(importCategories)

            // 批量查询已存在分类
            val existingCategories = directivesCategoryService.findByCategoryNamesIn(importCategories)
                .associateBy { it.categoryName }.toMutableMap()

            // 准备需要操作的分类集合
            val categoriesToProcess = mutableListOf<DirectivesCategoryEntity>()

            importCategories.forEach { categoryName ->
                val newDesc = allCmd[categoryName]["description"].textValue()

                existingCategories[categoryName]?.let { existing ->
                    if (covered == true) {
                        categoriesToProcess.add(
                            existing.copy(
                                categoryDesc = newDesc,
                                updateTime = Date() // 添加更新时间
                            )
                        )
                    }
                } ?: run { // 新增的情况
                    categoriesToProcess.add(
                        DirectivesCategoryEntity(
                            categoryName = categoryName,
                            categoryDesc = newDesc,
                            createTime = Date()
                        )
                    )
                }
            }

            // 批量操作数据库
            if (categoriesToProcess.isNotEmpty()) {
                val (toInsert, toUpdate) = categoriesToProcess.partition { it.id == null }

                if (toInsert.isNotEmpty()) {
                    directivesCategoryService.batchAddCategories(toInsert)
                }
                if (covered == true && toUpdate.isNotEmpty()) {
                    directivesCategoryService.batchUpdateCategories(toUpdate)
                }
            }

            // 重新查询最新的分类列表
            val latestCategories = directivesCategoryService.findByCategoryNamesIn(importCategories)
            val categoryIdMap = latestCategories.associateBy { it.categoryName }

            // 收集指令
            val allDirectives = mutableListOf<DirectivesEntity>()
            val directivesToUpdate = mutableListOf<DirectivesEntity>()

            importCategories.forEach { categoryName ->
                val categoryId = categoryIdMap[categoryName]?.id ?: return@forEach

                val commendList = allCmd[categoryName]["commendList"]?.takeIf { it.isArray }
                    ?.map { cmdNode ->
                        DirectivesEntity(
                            directiveName = cmdNode["command"].asText(),
                            description = cmdNode["description"].asText(),
                            detail = cmdNode["detail"].asText(),
                            regex = cmdNode["regex"].asText(),
                            enable = 1,
                            categoryId = categoryId,
                            createTime = Date(),
                            updateTime = if (covered == true) Date() else null // 添加更新时间
                        )
                    } ?: emptyList()

                // 分离需要更新和新增的指令
                if (covered == true) {
                    val existingMap = directivesService.findByCategoryId(categoryId)
                        .associateBy { it.directiveName }

                    commendList.forEach { directive ->
                        if (existingMap.containsKey(directive.directiveName)) {
                            directivesToUpdate.add(directive.copy(id = existingMap[directive.directiveName]!!.id))
                        } else {
                            allDirectives.add(directive)
                        }
                    }
                } else {
                    allDirectives.addAll(commendList)
                }
            }

            // 批量更新和插入指令
            if (covered == true) {
                if (directivesToUpdate.isNotEmpty()) {
                    directivesService.batchUpdateDirectives(directivesToUpdate)
                }
            }
            if (allDirectives.isNotEmpty()) {
                directivesService.batchAddDirectives(allDirectives)
            }

            return RespBean.success(directivesJson)
        } catch (e: JsonProcessingException) {
            return RespBean.error()
        }
    }

}