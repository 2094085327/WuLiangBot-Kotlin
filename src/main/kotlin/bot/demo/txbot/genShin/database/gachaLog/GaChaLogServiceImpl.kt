package bot.demo.txbot.genShin.database.gachaLog

import bot.demo.txbot.genShin.util.MysDataUtil
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File

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

    private val objectMapper = ObjectMapper()

    val gachaDataMap = mutableMapOf(
        "gachaLog" to mutableMapOf<String, Any>(),
        "uid" to "",
        "gachaType" to mutableMapOf(
            "100" to "新手祈愿",
            "200" to "常驻祈愿",
            "301" to "角色活动祈愿",
            "302" to "武器活动祈愿"
        ),
    )

    override fun selectByUid(uid: String): Int? {
        val judgeWrapper = QueryWrapper<GaChaLogEntity>().eq("uid", uid)
        val dataSize = gaChaLogMapper.selectList(judgeWrapper).size
        if (dataSize == 0) return null


        val types = listOf(200, 301, 302)
        val gachaLogMap = mutableMapOf<String, List<GaChaLogEntity?>>()
        for (type in types) {
            val queryWrapper = QueryWrapper<GaChaLogEntity>().eq("uid", uid).eq("type", type).orderByDesc("get_time")
            val gachaBefore = gaChaLogMapper.selectList(queryWrapper) ?: mutableListOf()
            gachaLogMap[type.toString()] = gachaBefore
        }
        println("数据查询完毕")
        gachaDataMap["gachaLog"] = gachaLogMap
        gachaDataMap["uid"] = uid
        val json = objectMapper.writeValueAsString(gachaDataMap)
        val folderPath = MysDataUtil.CACHE_PATH
        val folder = File(folderPath)
        if (!folder.exists()) folder.mkdirs()
        val fileName = "$folderPath/gachaLog-$uid.json"
        val file = File(fileName)
        file.writeText(json)
        return dataSize
    }

//    override fun selectByUid(uid: String): Boolean {
////        val queryWrapper = QueryWrapper<GaChaEntity>().eq("uid", uid)
////        val gachaInfo = gaChaMapper.selectList(queryWrapper)
////        return gachaInfo.size != 0
//        return false
//    }

    override fun insertByUid(
        uid: String,
        type: String,
        itemName: String,
        times: Int,
        itemType: String,
        rankType: Int,
        itemId: String,
        getTime: String,
    ) {
        val gachaInfo = GaChaLogEntity(
            uid = uid,
            gachaType = type,
            itemName = itemName,
            times = times,
            updateTime = System.currentTimeMillis().toString(),
            itemType = itemType,
            rankType = rankType,
            itemId = itemId,
            getTime = getTime
        )

        val queryWrapper = QueryWrapper<GaChaLogEntity>().eq("item_id", itemId)
        val existGachaInfo = gaChaLogMapper.selectOne(queryWrapper)
        if (existGachaInfo == null) gaChaLogMapper.insert(gachaInfo)
    }
}