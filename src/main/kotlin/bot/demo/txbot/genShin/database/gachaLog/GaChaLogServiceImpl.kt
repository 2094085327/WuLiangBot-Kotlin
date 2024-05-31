package bot.demo.txbot.genShin.database.gachaLog

import bot.demo.txbot.genShin.util.UIGF_VERSION
import bot.demo.txbot.other.CACHE_PATH
import bot.demo.txbot.other.DELETE_PERCENTAGE
import bot.demo.txbot.other.MAX_SIZE_MB
import bot.demo.txbot.other.UpdateResources
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger

/**
 *@Description:
 *@Author zeng
 *@Date 2023/10/3 22:30
 *@User 86188
 */
@Service
class GaChaLogServiceImpl : ServiceImpl<GaChaLogMapper?, GaChaLogEntity?>(), GaChaLogService {
    @Autowired
    lateinit var gaChaLogMapper: GaChaLogMapper

    private val logger: Logger = Logger.getLogger(GaChaLogServiceImpl::class.java.getName())


    override fun selectByUid(uid: String): Int? {
        val gachaDataMap: MutableMap<String, Any> = mutableMapOf()
        val objectMapper = jacksonObjectMapper().apply {
            // 设置序列化时的特性，例如缩进和日期格式等
            configure(SerializationFeature.INDENT_OUTPUT, false)
        }


        val judgeWrapper = QueryWrapper<GaChaLogEntity>().eq("uid", uid)
        val dataSize = gaChaLogMapper.selectList(judgeWrapper).size
        if (dataSize == 0) return null

        val queryWrapper = QueryWrapper<GaChaLogEntity>().eq("uid", uid).orderByAsc("time")
        val gachaBefore = gaChaLogMapper.selectList(queryWrapper) ?: mutableListOf()
        logger.info("数据查询完毕")

        // 基础信息
        val exportTimestamp = System.currentTimeMillis()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        gachaDataMap["info"] = mutableMapOf<String, Any>(
            "uid" to uid,
            "lang" to "zh-cn",
            "export_time" to dateFormatter.format(Date(exportTimestamp)),
            "export_timestamp" to exportTimestamp,
            "export_app" to "无量姬",
            "export_app_version" to "v1.0.0",
            "uigf_version" to UIGF_VERSION
        )

        gachaDataMap["list"] = gachaBefore

        // 格式化为Json数据
        val jsonString = objectMapper.writeValueAsString(gachaDataMap)


        // 保存数据
        val folderPath = CACHE_PATH
        val folder = File(folderPath)
        if (!folder.exists()) folder.mkdirs()
        val fileName = "$folderPath/gachaLog-$uid.json"
        val file = File(fileName)
        // 当总缓存超过最大缓存大小时删除最久未修改的文件
        UpdateResources().manageFolderSize(folderPath, MAX_SIZE_MB, DELETE_PERCENTAGE)
        file.writeText(jsonString)
        return dataSize
    }

    override fun insertByUid(
        uid: String,
        gachaType: String,
        itemId: String,
        count: String,
        time: String,
        name: String,
        lang: String,
        itemType: String,
        rankType: String,
        id: String,
        uigfGachaType: String
    ) {
        val gachaInfo = GaChaLogEntity(
            uid = uid,
            gachaType = gachaType,
            itemId = itemId,
            count = count,
            time = time,
            name = name,
            lang = lang,
            itemType = itemType,
            rankType = rankType,
            id = id,
            uigfGachaType = uigfGachaType
        )

        val queryWrapper = QueryWrapper<GaChaLogEntity>().eq("id", id)
        val existGachaInfo = gaChaLogMapper.selectOne(queryWrapper)
        if (existGachaInfo == null) gaChaLogMapper.insert(gachaInfo)
    }
}