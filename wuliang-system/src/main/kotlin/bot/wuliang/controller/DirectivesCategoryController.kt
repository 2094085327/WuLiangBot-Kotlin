package bot.wuliang.controller

import bot.wuliang.entity.DirectivesCategoryEntity
import bot.wuliang.exception.RespBean
import bot.wuliang.service.impl.DirectivesCategoryServiceImpl
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api(tags = ["系统指令分类接口"])
@RestController
@RequestMapping("/category")
class DirectivesCategoryController @Autowired constructor(private val directivesCategoryService: DirectivesCategoryServiceImpl) {

    /**
     * 获取指令分类列表
     *
     * @return RespBean
     */
    @ApiOperation("获取指令分类列表")
    @RequestMapping("/list")
    fun getDirectivesList(): RespBean {
        return RespBean.success(directivesCategoryService.selectDirectivesCategoryList())
    }

    /**
     * 新增指令分类
     *
     * @param directivesCategoryEntity 指令分类实体
     * @return
     */
    @ApiOperation("新增指令分类列表")
    @PostMapping("/add")
    fun addDirectivesCategory(directivesCategoryEntity: DirectivesCategoryEntity): RespBean {
        return RespBean.toReturn(directivesCategoryService.addDirectivesCategory(directivesCategoryEntity))
    }
}