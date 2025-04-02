package bot.wuliang.controller

import bot.wuliang.exception.RespBean
import bot.wuliang.entity.DirectivesEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import bot.wuliang.service.DirectivesService

@RestController
@RequestMapping("/directives")
class DirectivesController @Autowired constructor(
    private val directivesService: DirectivesService
) {

    @GetMapping("/list")
    fun getDirectivesList(): RespBean {
        directivesService.selectDirectivesList()
        return RespBean.success()
    }


    @PostMapping("/addDirectives")
    fun addDirectives(directivesEntity: DirectivesEntity): RespBean {
        return RespBean.toReturn(directivesService.addDirectives(directivesEntity))
    }

    @GetMapping("/tttt")
    fun tttt(): RespBean {
        return RespBean.success()
    }

}