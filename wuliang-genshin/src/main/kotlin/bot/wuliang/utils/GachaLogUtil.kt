package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.config.*
import bot.wuliang.entity.GenShinItemEntity
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.qiNiuCos.QiNiuService
import bot.wuliang.tencentCos.CosFileServiceImpl
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*


/**
 * @description: 抽卡记录数据工具类
 * @author Nature Zero
 * @date 2024/2/5 20:04
 */
@Component
class GachaLogUtil(
    @Autowired private val webImgUtil: WebImgUtil,
    @Autowired private val qiNiuService: QiNiuService,
    @Autowired private val txCosService: CosFileServiceImpl
) {

    /**
     * 抽卡数据
     */
    object GachaData {
        var permanents: MutableList<GenShinItemEntity> = mutableListOf()
        var roles: MutableList<GenShinItemEntity> = mutableListOf()
        var weapons: MutableList<GenShinItemEntity> = mutableListOf()
        var mixPool: MutableList<GenShinItemEntity> = mutableListOf()
        var roleCount: MutableList<CountDetail> = mutableListOf()
        var weaponCount: MutableList<CountDetail> = mutableListOf()
        var permanentCount: MutableList<CountDetail> = mutableListOf()
        var mixCount: MutableList<CountDetail> = mutableListOf()
    }

    /**
     * 每个卡池的具体抽卡情况
     *
     * @property alreadyCount 已经抽取的次数
     * @property ave 平均抽数
     * @property allCount 总抽数
     * @property fiveUpCount 五星数量
     * @property luckImg 运气图片
     */
    data class CountDetail(
        val alreadyCount: Int,
        val ave: String,
        val allCount: Int,
        val fiveUpCount: String,
        val luckImg: String
    )

    /**
     * 用于检查文件是否缺失的数组
     */
    private val fileList: ArrayList<String> = arrayListOf()


    /**
     * 检查文件是否缺失
     *
     * @param folderPath 文件夹路径
     * @return 读取到的文件名
     */
    private fun checkFolder(folderPath: String): List<String> {
        val folder = File(folderPath)

        if (!folder.exists() || !folder.isDirectory) {
            logWarn("文件夹不存在或不是一个有效的文件夹: $folderPath")
            return emptyList()
        }

        val fileNames = mutableListOf<String>()

        try {
            folder.listFiles()?.forEach { file ->
                fileNames.add(file.name)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            logError("读取错误: $folderPath")
        }

        return fileNames
    }


    /**
     * 检查文件是否存在
     *
     * @param fileName 文件名称
     * @return 文件状态与文件内容
     */
    private fun checkFile(fileName: String): Pair<Boolean, String?> {
        val matchingFiles = fileList.filter { it.startsWith(fileName) }
        return if (matchingFiles.isNotEmpty()) Pair(true, matchingFiles[0]) else Pair(false, fileName)
    }


    /**
     * 读取角色属性
     *
     * @param roleName 角色名字
     * @return null 或 角色属性
     */
    private fun getRoleAttribute(roleName: String): String? {
        val objectMapper = ObjectMapper(YAMLFactory())
        val file = File(ROLE_YAML)

        try {
            val characterMap: Map<String, String> =
                objectMapper.readValue(file, object : TypeReference<Map<String, String>>() {})

            val element = characterMap[roleName]

            if (element != null) return element

        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


    /**
     * 查找当前时间所在的卡池
     *
     * @param targetTimeString 目标时间
     * @return 返回找到的卡池名称
     */
    private fun findPoolName(targetTimeString: String): String? {
        val targetTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(targetTimeString)

        for ((key, value) in upPoolData.fields()) {
            val endTimeString = value["endTime"].asText()
            val endTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(endTimeString)

            if (!targetTime.after(endTime)) return key
        }
        return null
    }

    /**
     * 获取抽卡运气图片
     *
     * @param allFiveCount 所有五星数量
     * @param upCount up五星数量
     * @param ave 5星平均抽数
     * @param gachaType 卡池类型
     * @return 返回抽卡运气图片
     */
    private fun getLuckImg(allFiveCount: Double, upCount: Double, ave: Double, gachaType: String): String {
        val probability: Double = when (gachaType) {
            "200" -> 1.0 - (ave / 90.0)
            "301" -> (1.0 - (ave / 90.0)) * 0.3 + (upCount / allFiveCount) * 0.7
            "302" -> (1.0 - (ave / 80.0)) * 0.3 + (upCount / allFiveCount) * 0.7
            else -> 0.0
        }
        return when {
            probability <= 0.2 -> "寄"
            probability <= 0.4 -> "惨"
            probability <= 0.6 -> "平"
            probability <= 0.8 -> "吉"
            else -> "欧"
        }
    }

    /**
     * 获取每个卡池的数据
     *
     * @param data 获取到的数据
     * @param gachaType 需要处理的卡池类型
     */
    fun getEachData(data: JsonNode, gachaType: String) {
        // 根据gachaType选择相应的文件夹路径、物品列表和计数列表
        val (folderPaths, itemList, countList) = when (gachaType) {
            "200" -> Triple(listOf(ROLE_IMG, WEAPON_IMG), GachaData.permanents, GachaData.permanentCount)
            "301" -> Triple(listOf(ROLE_IMG), GachaData.roles, GachaData.roleCount)
            "302" -> Triple(listOf(WEAPON_IMG), GachaData.weapons, GachaData.weaponCount)
            "500" -> Triple(listOf(ROLE_IMG, WEAPON_IMG), GachaData.mixPool, GachaData.mixCount)
            else -> return // 如果gachaType不匹配，直接返回
        }

        // 清空物品列表和计数列表
        itemList.clear()
        countList.clear()

        // 检查文件夹，获取物品并添加到 fileList
        folderPaths.forEach { folderPath ->
            val items = checkFolder(folderPath)
            fileList.addAll(items)
        }

        var unFiveStarTimes = 0 // 记录未获取五星物品的次数
        var fiveCount = 0 // 记录五星物品的数量
        val timesList = mutableListOf<Int>() // 记录每次获取五星物品前的次数

        // 创建抽卡网页数据
        fun createGachaEntity(array: JsonNode, unFiveStarTimes: Int, isUp: Boolean): GenShinItemEntity {
            val itemType = array["item_type"].textValue() // 获取物品类型
            val itemName = array["name"].textValue() // 获取物品名称
            val (isEmpty, itemFileName) = checkFile(itemName) // 检查物品文件
            val roleAttribute = if (itemType == "角色") getRoleAttribute(itemName) else null // 如果是角色，获取角色属性

            // 返回GenShinItemEntity对象
            return GenShinItemEntity(
                itemId = array["id"].textValue(),
                itemName = itemFileName,
                itemType = itemType,
                itemAttribute = roleAttribute,
                getTime = array["time"].textValue(),
                times = unFiveStarTimes + 1,
                isEmpty = isEmpty,
                isUp = isUp
            )
        }

        // 判断物品是否为Up物品
        fun isItemUp(gachaType: String, upData: JsonNode, itemName: String): Boolean {
            return when (gachaType) {
                "301" -> upData["up5"].asText() == itemName || (upData.has("up5_2") && upData["up5_2"].asText() == itemName)
                "500" -> true
                else -> upData["weapon5"].any { it.asText() == itemName }
            }
        }

        val filteredData = data["list"].filter { it["uigf_gacha_type"].textValue() == gachaType } // 过滤出匹配的gachaType的数据
        val filteredDataCount = filteredData.size // 记录过滤后的数据数量

        filteredData.forEach { array ->
            if (array["rank_type"].asInt() == 5) { // 如果是五星物品
                val itemName = array["name"].textValue()
                val upPoolName = findPoolName(array["time"].textValue()) // 查找对应的池名称
                val upData = upPoolData[upPoolName] // 获取Up物品数据

                val isUp = isItemUp(gachaType, upData, itemName) // 判断物品是否为Up物品
                if (isUp) fiveCount++ // 如果是Up物品，增加五星计数

                val gachaEntity = createGachaEntity(array, unFiveStarTimes, isUp) // 创建GenShinItemEntity对象
                itemList.add(gachaEntity) // 将GenShinItemEntity对象添加到物品列表
                timesList.add(unFiveStarTimes) // 将未获取五星物品的次数添加到列表
                unFiveStarTimes = 0 // 重置未获取五星物品的次数
            } else {
                unFiveStarTimes++ // 如果不是五星物品，增加未获取五星物品的次数
            }
        }

        // 根据综合评价获取抽卡运气
        val luckImg = getLuckImg(itemList.size.toDouble(), fiveCount.toDouble(), timesList.average(), gachaType)

        // 将计数详情添加到计数列表
        countList.add(
            CountDetail(
                alreadyCount = unFiveStarTimes,
                ave = String.format("%.1f", timesList.average()),
                allCount = filteredDataCount,
                fiveUpCount = if (gachaType == "200") itemList.size.toString() else "$fiveCount/${itemList.size}",
                luckImg = luckImg
            )
        )
        itemList.reverse() // 反转物品列表
    }

    /**
     * 获取解析后的真实URL并加入参数
     *
     * @param url 输入的未经过解析的URL
     * @return 返回解析后的URL
     */
    fun toUrl(url: String): String {
        return try {
            // 中文正则，去除链接中的中文
            val regexChinese = "[\u4e00-\u9fa5]"
            // 获取无中文的链接
            val noChineseUrl = url.replace(regexChinese.toRegex(), "")
            // 从"#"处分割链接去除链接中的[#/log]并获取分割后的链接
            val splitUrl1 = noChineseUrl.split("#".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
            // 从[?]处分割链接以拼接到接口链接上
            val splitUrl2 = splitUrl1.split("\\?".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
            // 含参链接
            "$GACHA_LOG_URL?$splitUrl2"
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 对关键参数进行拼接
     *
     * @param gachaType 抽卡类型
     * @param url       未拼接的链接
     * @param times     页数
     * @return 返回拼接后的URL
     */
    fun getUrl(url: String, gachaType: String = "301", times: Int, endId: String = "0", size: Int? = 20): String {
        return toUrl(url) + "&gacha_type=${gachaType}&page=${times}&size=${size}&end_id=${endId}"
    }

    /**
     * 对URL进行检查以判断过期或错误等情况
     *
     * @param url 需要检查的URL
     * @return 返回检查后的状态
     */
    fun checkApi(url: String): Pair<String, String?> {
        return try {
            val urls: String = toUrl(url)
            val dealUrl = getUrl(url = urls, gachaType = "301", times = 1, endId = "0", size = 1)
            val jsonObject = HttpUtil.doGetJson(dealUrl)
            val retcode = jsonObject["retcode"].asInt()
            var uid: String? = null

            if (retcode == 0) uid = jsonObject["data"]["list"][0]["uid"].asText()
            Pair(retcode.toString(), uid)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair("500", null)
        }
    }

    /**
     * 通过链接获取抽卡记录
     *
     * @param url 获取的链接
     * @return 返回获取到的数据
     */
    fun getDataByUrl(url: String): JsonNode {
        val urls: String = toUrl(url)
        return HttpUtil.doGetJson(urls)
    }

    /**
     * 获取抽卡记录并发送图片
     *
     * @param gameUid 游戏Uid
     * @param imgData 图片数据
     */
    fun getGachaLog(
        context: BotUtils.Context,
        gameUid: String,
        imgData: WebImgUtil.ImgData,
    ) {
        try {
//            qiNiuService.getFileInfo(imgData)
            if (txCosService.checkFileExist(imgData.imgName!!, "jpeg")) webImgUtil.sendNewImage(context, imgData)
            else throw Exception("缓存图片不存在")
        } catch (e: Exception) {
            logWarn("缓存图片不存在，开始生成图片")
            val gachaData = MysDataUtil().getGachaData("$GACHA_LOG_FILE$gameUid.json")
            val pools = arrayOf("200", "301", "302", "500")
            pools.forEach { type ->
                getEachData(gachaData, type)
            }

            webImgUtil.sendNewImage(context, imgData)
        }
    }

    /**
     * 发送非缓存截图
     *
     * @param imgData 图片数据
     */
    /*    private fun sendNewImage(imgData: WebImgUtil.ImgData) {
            webImgUtil.sendNewImage(context, imgData)
            when (BotUtils.ContextProvider.currentEvent) {
                is PrivateMessageEvent -> {
                    context.sendPrivateMsg("发送完毕，可能因网络波动未显示图片，请稍后再试")
                }

                is AnyMessageEvent -> {
                    context.sendMsg("发送完毕，可能因网络波动未显示图片，请稍后再试")
                }
            }
        }*/

    fun checkCache(imgData: WebImgUtil.ImgData): File? {
        val folder = File(GACHA_CACHE_PATH)
        val matchingFile = folder.listFiles()?.firstOrNull { it.nameWithoutExtension == imgData.imgName }

        return matchingFile
    }

    fun convertStringToUuidFormat(inputString: String): String {
        // 将输入字符串转换为十六进制哈希值
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(inputString.toByteArray())
        val hexId = hashBytes.joinToString("") { "%02x".format(it) }

        // 构建新的UUID字符串，使用哈希值的十六进制表示填充
        val newUuid = (hexId.substring(0, 8) +
                "-${hexId.substring(8, 12)}" +
                "-${hexId.substring(12, 16)}" +
                "-${hexId.substring(16, 20)}" +
                "-${hexId.substring(20, 32)}").uppercase(
            Locale.getDefault()
        )

        return newUuid
    }

    fun getGachaData(): GachaData {
        return GachaData
    }
}