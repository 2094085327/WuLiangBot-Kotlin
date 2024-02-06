package bot.demo.txbot.genShin.util

import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.genShin.database.gachaLog.HtmlEntity
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayList


/**
 * @description: 抽卡记录数据工具类
 * @author Nature Zero
 * @date 2024/2/5 20:04
 */
class GachaLogUtil {
    companion object {
        var upPoolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/pool.json")
    }

    /**
     * 抽卡数据
     */
    object GachaData {
        var permanents: MutableList<HtmlEntity> = mutableListOf()
        var roles: MutableList<HtmlEntity> = mutableListOf()
        var weapons: MutableList<HtmlEntity> = mutableListOf()
        var roleCount: MutableList<CountDetail> = mutableListOf()
        var weaponCount: MutableList<CountDetail> = mutableListOf()
        var permanentCount: MutableList<CountDetail> = mutableListOf()
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
            println("文件夹不存在或不是一个有效的文件夹: $folderPath")
            return emptyList()
        }

        val fileNames = mutableListOf<String>()

        try {
            folder.listFiles()?.forEach { file ->
                fileNames.add(file.name)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            println("读取错误: $folderPath")
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
     * 将卡池301和卡池400数据合并
     *
     * @param array1 卡池1
     * @param array2 卡池2
     * @return 返回合并后的数据
     */
    private fun mergeJsonArrays(array1: JsonNode, array2: JsonNode): JsonNode {
        val resultArray = mutableListOf<JsonNode>()
        resultArray.addAll(array1)
        resultArray.addAll(array2)
        return ObjectMapper().valueToTree(resultArray)
    }

    /**
     * 将合并后的Json数据按照时间进行排序
     *
     * @param jsonArray 传入的待排序的数据
     * @return 返回排序后的数据
     */
    private fun sortJsonArrayByTime(jsonArray: JsonNode): JsonNode {
        if (jsonArray.isArray) {
            val array = jsonArray as ArrayNode
            val sortedArray = array.sortedByDescending { it["getTime"].asText() }.reversed()
            return ObjectMapper().valueToTree(sortedArray)
        }
        return jsonArray
    }
    /**
     * 读取角色属性
     *
     * @param roleName 角色名字
     * @return null 或 角色属性
     */
    private fun getRoleAttribute(roleName: String): String? {
        val objectMapper = ObjectMapper(YAMLFactory())
        val file = File("resources/genShin/defSet/element/role.yaml")

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
        val folderPaths = when (gachaType) {
            "200" -> listOf("resources/genShin/GenShinImg/role/", "resources/genShin/GenShinImg/weapons/")
            "301" -> listOf("resources/genShin/GenShinImg/role/")
            "302" -> listOf("resources/genShin/GenShinImg/weapons/")
            else -> return
        }
        val itemList = when (gachaType) {
            "200" -> GachaData.permanents
            "301" -> GachaData.roles
            "302" -> GachaData.weapons
            else -> return
        }

        val countList = when (gachaType) {
            "200" -> GachaData.permanentCount
            "301" -> GachaData.roleCount
            "302" -> GachaData.weaponCount
            else -> return
        }

        itemList.clear()
        countList.clear()
        folderPaths.forEach { folderPath ->
            val items = checkFolder(folderPath)
            fileList.addAll(items)
        }

        var getData = data["gachaLog"][gachaType]

        if (gachaType == "301") {
            val mergedArray = mergeJsonArrays(data["gachaLog"]["301"], data["gachaLog"]["400"])
            getData = sortJsonArrayByTime(mergedArray)
        }


        var unFiveStarTimes = 0
        var fiveCount = 0
        val timesList = mutableListOf<Int>()
        getData.forEach { array ->
            if (array["rankType"].asInt() == 5) {
                val itemType = array["itemType"].textValue()
                val itemName = array["itemName"].textValue()

                val (isEmpty, itemFileName) = checkFile(itemName)
                val roleAttribute = if (itemType == "角色") getRoleAttribute(itemName) else null

                val gachaEntity = HtmlEntity(
                    itemId = array["itemId"].textValue(),
                    itemName = itemFileName,
                    itemType = itemType,
                    itemAttribute = roleAttribute,
                    getTime = array["getTime"].textValue(),
                    times = unFiveStarTimes + 1,
                    isEmpty = isEmpty,
                    isUp = false
                )

                val upPoolName = findPoolName(array["getTime"].textValue())
                val upData = upPoolData[upPoolName]
                if (gachaType == "301") {
                    if (upData["up5"].asText() == itemName) {
                        gachaEntity.isUp = true
                        fiveCount += 1
                    } else {
                        if (upData.has("up5_2") && upData["up5_2"].asText() == itemName) {
                            gachaEntity.isUp = true
                            fiveCount += 1
                        }
                    }
                } else {
                    for (weapon5 in upData["weapon5"]) {
                        if (weapon5.asText() == itemName) {
                            gachaEntity.isUp = true
                            fiveCount += 1
                            break
                        }
                    }
                }

                itemList.add(gachaEntity)
                timesList.add(unFiveStarTimes)
                unFiveStarTimes = 0
            } else {
                unFiveStarTimes += 1
            }
        }
        val luckImg = getLuckImg(itemList.size.toDouble(), fiveCount.toDouble(), timesList.average(), gachaType)

        countList.add(
            CountDetail(
                alreadyCount = unFiveStarTimes,
                ave = String.format("%.1f", timesList.average()),
                allCount = getData.size(),
                fiveUpCount = if (gachaType == "200") itemList.size.toString() else "${fiveCount}/${itemList.size}",
                luckImg = luckImg
            )
        )
        itemList.reverse()
    }



    fun getGachaData(): GachaData {
        return GachaData
    }
}