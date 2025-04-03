package bot.wuliang.controller

import bot.wuliang.entity.DirectivesCategoryEntity
import bot.wuliang.exception.RespBean
import bot.wuliang.service.impl.DirectivesCategoryServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/category")
class DirectivesCategoryController @Autowired constructor(private val directivesCategoryService: DirectivesCategoryServiceImpl) {


    @RequestMapping("/list")
    fun getDirectivesList(): RespBean {
        return RespBean.success(directivesCategoryService.selectDirectivesCategoryList())
    }

    @RequestMapping("/add")
    fun addDirectivesCategory(directivesCategoryEntity: DirectivesCategoryEntity): RespBean {
        return RespBean.toReturn(directivesCategoryService.addDirectivesCategory(directivesCategoryEntity))
    }
}