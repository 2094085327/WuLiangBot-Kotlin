package bot.wuliang.distribute.controller

import bot.wuliang.distribute.dto.FieldMetaDto
import bot.wuliang.distribute.service.SchemaAutoDiscoveryService
import bot.wuliang.exception.RespBean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/schema")
class SchemaController(
    private val discoveryService: SchemaAutoDiscoveryService
) {
    @GetMapping("/list")
    fun listSchemas(@RequestParam commandKey: String): RespBean<List<FieldMetaDto>> {
        val schema = discoveryService.getSchemaForCommand(commandKey)
        return RespBean.success(schema)
    }

    @GetMapping("/allKeys")
    fun listAllKeys(): RespBean<List<String>> {
        // 返回所有已注册的 commandKey 列表
        val keys = discoveryService.schemaCache.keys.toList()
        return RespBean.success(keys)
    }
}


