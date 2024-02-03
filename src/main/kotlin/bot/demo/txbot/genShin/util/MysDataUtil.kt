package bot.demo.txbot.genShin.util

import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.JacksonUtil.objectMapper
import bot.demo.txbot.genShin.database.gachaLog.HtmlEntity
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.pow


class MysDataUtil {


    companion object {
        const val CACHE_PATH = "resources/gachaCache"
        var poolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/gacha.json")
        var upPoolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/pool.json")
        var poolType: String = poolData["poolType"].textValue()
        var nowPoolData: PoolData = PoolData()

        var num5 = 0
        var num4 = 0
        var isUp4 = 0
        var isUp5 = 0
        var lifeNum = 0
        var isBing = false
    }

    object GachaData {
        var permanents: MutableList<HtmlEntity> = mutableListOf()
        var roles: MutableList<HtmlEntity> = mutableListOf()
        var weapons: MutableList<HtmlEntity> = mutableListOf()
    }

    private val fileList: ArrayList<String> = arrayListOf()

    /**
     * 获取历史抽卡数据
     *
     * @param filePath 文件路径
     * @return 读取到的json数据
     */
    fun getGachaData(filePath: String): JsonNode {
        val file = File(filePath)
        val objectMapper = ObjectMapper()
        return objectMapper.readTree(file)
    }

    // 删除数据缓存
    fun deleteDataCache() {
        val folder = File(CACHE_PATH)
        val fiveMinutesAgo = System.currentTimeMillis() - 10 * 60 * 1000
        folder.listFiles()?.forEach { file ->
            if (file.lastModified() < fiveMinutesAgo) {
                println("删除缓存：${file.name}")
                file.delete()
            }
        }
    }

    // 强制删除数据缓存
    fun forceDeleteCache(cachePath: String) {
        val folder = File(cachePath)
        folder.listFiles()?.forEach { file ->
            println("删除缓存：${file.name}")
            file.delete()
        }
    }

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

        println(fileNames)
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
     * 新角色添加属性
     *
     * @param itemName 角色名
     * @param attribute 角色属性
     * @return 返回添加状态
     */
    fun insertAttribute(itemName: String, attribute: String): String {
        val objectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())
        val file = File("resources/genShin/defSet/element/role.yaml")

        try {
            val characterMap: LinkedHashMap<String, String> =
                objectMapper.readValue(file, object : TypeReference<LinkedHashMap<String, String>>() {})

            // 检查配置文件中是否已经存在角色
            if (characterMap.containsKey(itemName)) {
                return "201"
            }

            val lastEntryWithValueOne = characterMap.entries.lastOrNull { it.value == attribute }

            return if (lastEntryWithValueOne != null) {
                val newCharacterMap = characterMap.toMutableMap()

                for ((key, value) in characterMap) {
                    newCharacterMap[key] = value
                    if (key == lastEntryWithValueOne.key) newCharacterMap[itemName] = attribute
                }

                objectMapper.writeValue(file, newCharacterMap)
                "200"
            } else {
                "404"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return e.toString()
        }
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
     * 抽卡主程序
     *
     * @return
     */
    fun runGacha(): MutableList<UserGacha?> {
        initGacha()
        return lottery()
    }

    /**
     * 初始化抽卡数据
     *
     */
    private fun initGacha() {
        poolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/gacha.json")
        upPoolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/pool.json")
        poolType = poolData["poolType"].textValue()

        num5 = 0
        num4 = 0
        isUp5 = 0
        isUp4 = 0
        lifeNum = 0
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
        itemList.clear()
        folderPaths.forEach { folderPath ->
            val items = checkFolder(folderPath)
            fileList.addAll(items)
        }
//        fileList.addAll(checkFolder(folderPath))

        var getData = data["gachaLog"][gachaType]

        if (gachaType == "301") {
            val mergedArray = mergeJsonArrays(data["gachaLog"]["301"], data["gachaLog"]["400"])
            getData = sortJsonArrayByTime(mergedArray)
        }


        var unFiveStarTimes = 0
        // TODO 在最后一次抽卡后，如果不是五星，将unFiveStarTimes的次数作为已抽卡但为出金的次数
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
                    isEmpty = isEmpty
                )

                itemList.add(gachaEntity)
                unFiveStarTimes = 0
            } else {
                unFiveStarTimes += 1
            }
        }
        itemList.reverse()
    }

    fun findEachPoolName(): List<String> {
        return upPoolData.fields().asSequence().map<MutableMap.MutableEntry<String, JsonNode>?, String> { it!!.key }
            .toList()
    }

    fun findPoolData(name: String, id: String): Pair<String, JsonNode>? {
        val iterator = upPoolData.fields()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val key = entry.key
            val keyParts = key.split("-")
            val entryName = if (keyParts.size > 1) keyParts[0] else key
            val entryId = if (keyParts.size > 1) keyParts[1] else ""

            if (entryName.contains(name) && entryId.trim() == id) {
                return Pair(key, entry.value)
            }
        }

        return null
    }


    fun changePoolOpen(poolInfo: Pair<String, JsonNode>, poolFormat: String, poolType: String) {
        val (name, _) = poolInfo
        val gachaJsonPath = "resources/genShin/defSet/gacha/gacha.json"
        val poolDataChange = JacksonUtil.getJsonNode(gachaJsonPath)
        val objRoot = poolDataChange as ObjectNode
        objRoot.put("openPool", name)
        objRoot.put("poolName", poolFormat)
        objRoot.put("poolType", poolType)
        objectMapper.writeValue(File(gachaJsonPath), poolDataChange)
    }

    data class PoolData(
        // 当前卡池up的四星
        val up4: JsonNode? = null,
        // 当前卡池4星
        val role4: JsonNode? = null,
        // 当前卡池4星武器
        val weapon4: JsonNode? = null,
        // 当前卡池up的5星
        val up5: JsonNode? = null,
        // 当前卡池常驻5星
        val five: JsonNode? = null,
        // 当前卡池常驻5星武器
        val fiveW: JsonNode? = null,
        // 3星常驻
        var weapon3: JsonNode? = null,
    )

    /**
     * 获取到当前卡池的具体5星
     *
     * @param openPool 当前开放的卡池
     * @param poolName 卡池名称
     * @param detailPoolInfo 具体的卡池数据
     * @return 返回获取到的5星
     */
    private fun getDetailUp5(openPool: String, poolName: String, detailPoolInfo: JsonNode): String {
        return if ('|' in openPool) {
            if (openPool.startsWith(poolName)) {
                detailPoolInfo["up5"].textValue()
            } else {
                detailPoolInfo["up5_2"].textValue()
            }
        } else {
            detailPoolInfo["up5"].textValue()
        }
    }

    private fun mergeRole(version: String): Pair<ArrayNode, ArrayNode> {
        val newAdd = getGachaData("resources/genShin/defSet/gacha/newAdd.json")
        // 获取版本列表
        val versions = newAdd.fieldNames().asSequence().toList()
        // 获取当前版本的索引
        val currentIndex = versions.indexOf(version)
        // 获取前一个版本的索引
        val previousIndex = if (currentIndex > 0) currentIndex - 1 else currentIndex
        val previousVersion = versions.getOrNull(previousIndex)

        val role4Base = poolData["role4_base"]
        val role5Base = poolData["role5_base"]
        val addRole4 = role4Base as ArrayNode
        val addRole5 = role5Base as ArrayNode

        val preRole4 = newAdd[previousVersion]["role4"]
        preRole4.forEach {
            addRole4.add(it)
        }
        val preRole5 = newAdd[previousVersion]["role5"]
        preRole5.forEach {
            addRole5.add(it)
        }

        return Pair(addRole4, addRole5)
    }


    private fun getGachaPool(): PoolData {
        val openPool = poolData["openPool"].textValue()
        val poolName = poolData["poolName"].textValue()
        val poolInfo = upPoolData[openPool]

        // 获取到启用的卡池的up5
        val up5 = getDetailUp5(openPool, poolName, poolInfo)
        // 将角色池的up5转换为jsonNode
        val up5Node: JsonNode = TextNode.valueOf(up5)
        val up5Array = mutableListOf(up5Node)
        val up5ArrayNode: ArrayNode = objectMapper.valueToTree(up5Array)


        val mergeData = mergeRole(poolName.split("-")[1])
        // 截止到前一个版本的4星角色作为常驻角色
        val role4 = mergeData.first
        val role5 = mergeData.second

        val openPoolName = poolData["openPool"].textValue()

        var poolDataList = PoolData()
        if (poolType == "weapon") {
            poolDataList = PoolData(
                up4 = upPoolData[openPoolName]["weapon4"],
                up5 = upPoolData[openPoolName]["weapon5"],
                role4 = role4,
                weapon4 = upPoolData[openPoolName]["weapon4"],
                five = poolData["weapon5"],
            )
        }
        if (poolType == "role") {
            poolDataList = PoolData(
                up4 = upPoolData[openPoolName]["up4"],
                up5 = up5ArrayNode,
                role4 = role4,
                weapon4 = poolData["weapon4"],
                five = role5,
            )
        }

        if (poolType == "permanent") {
            poolDataList = PoolData(
                up4 = null,
                up5 = null,
                role4 = role4,
                weapon4 = poolData["weapon4"],
                five = role5,
                fiveW = poolData["weapon5"],
            )
        }

        poolDataList.weapon3 = poolData["weapon3"]

        println(poolDataList)
        return poolDataList
    }

    private fun probability(): Int {
        var tmpChance5 = poolData["chance5"].asInt()
        if (poolType == "role" || poolType == "permanent") {
            if (num5 >= 90) {
                tmpChance5 = 10000
            } else if (num5 >= 74) {
                tmpChance5 = 590 + (num5 - 74) * 530
            } else {
                if (num5 >= 60) {
                    tmpChance5 = poolData["chance5"].asInt() + (num5 - 50) * 40
                }
            }
        }

        if (poolType == "weapon") {
            if (num5 in 10..20) {
                tmpChance5 += (num5 - 10) * 30
            } else if (num5 >= 62) {
                tmpChance5 += (num5 - 61) * 700
            } else if (num5 >= 45) {
                tmpChance5 += (num5 - 45) * 60
            }
        }

        return tmpChance5
    }

    private fun lottery(): MutableList<UserGacha?> {
        val itemList: MutableList<UserGacha?> = mutableListOf()
        nowPoolData = getGachaPool()

        for (i in 1..10) {
            val lottery5 = lottery5()
            if (lottery5.first) {
                itemList.add(lottery5.second)
                continue
            }
            val lottery4 = lottery4()
            if (lottery4.first) {
                itemList.add(lottery4.second)
                continue
            }
            itemList.add(lottery3())
        }
        println(itemList)
        return itemList
    }


    data class UserGacha(
        // 抽中物品名称
        val name: String? = null,
        // 物品为等级
        val star: Int? = null,
        // 物品类型
        val type: String? = null,
        // 次数
        val num: Int? = null,
        // 是否为大保底
        val isBigUp: Boolean? = null,
        // 是否为定轨
        val isBing: Boolean? = null,
        // 是否已拥有
        val have: Boolean? = null,
    )

    fun getBingWeapon(): String {
        if (poolType != "weapon") return ""
        val name = "薙草之稻光"
        return name
    }


    private fun lottery5(): Pair<Boolean, UserGacha?> {
        var isBigUp = false
        val tmpChance5 = probability()
        var type = poolType
        val random = (1..10000).random()

        if (random > tmpChance5) {
            num5 += 1
            return Pair(false, null)
        }

        val nowCardNum = num5 + 1
        num5 = 0
        num4 += 1
        var tmpUp = poolData["wai"].asInt()

        if (isUp5 == 1) {
            tmpUp = 101
        }

        if (poolType == "permanent") tmpUp = 0

        val tmpName: String
        if (poolType == "weapon" && lifeNum >= 2) {
            tmpName = getBingWeapon()
            println("定轨了")
            lifeNum = 0
            isBing = true
        } else if ((1..100).random() <= tmpUp) {
            if (isUp5 == 1) isBigUp = true
            isUp5 = 0
            // 通过 Random 类生成一个随机索引
            val randomIndex = Random().nextInt(nowPoolData.up5!!.size())

            // 获取随机选择的武器
            tmpName = nowPoolData.up5!![randomIndex].asText()
            println("抽中up：$tmpName")

            if (tmpName == getBingWeapon()) lifeNum = 0
        } else {
            if (poolType == "permanent") {
                if ((1..100).random() <= 50) {
                    tmpName = nowPoolData.five!![(0 until nowPoolData.five!!.size()).random()].asText()
                    type = "role"
                    println("抽中常驻up角色：$tmpName")
                } else {
                    tmpName = nowPoolData.fiveW!![(0 until nowPoolData.fiveW!!.size()).random()].asText()
                    type = "weapon"
                    println("抽中常驻up武器：$tmpName")
                }
            } else {
                // 歪了大保底+1
                println("nowPoolData.five:${nowPoolData.five}")
                isUp5 = 1
                tmpName = nowPoolData.five!![(0 until nowPoolData.five!!.size()).random()].asText()
            }
        }

        if (tmpName != getBingWeapon()) lifeNum += 1
        val userGacha = UserGacha(
            name = tmpName,
            type = type,
            isBigUp = isBigUp,
            num = nowCardNum,
            star = 5,
            isBing = isBing,
            have = false
        )

        return Pair(true, userGacha)
    }

    private fun lottery4(): Pair<Boolean, UserGacha?> {
        var tmpChance4 = poolData["chance4"].asInt()
        if (num4 >= 9) {
            tmpChance4 += 10000
        } else if (num4 >= 5) {
            tmpChance4 += ((num4.toDouble() - 4).pow(2) * 500).toInt()
        }

        if ((1..10000).random() > tmpChance4) {
            num4 += 1
            return Pair(false, null)
        }

        num4 = 0
        var tmpUp = 50
        if (poolType == "weapon") tmpUp = 75
        if (isUp4 == 1) {
            isUp4 = 0
            tmpUp = 100
        }

        if (poolType == "permanent") tmpUp = 0
        val type: String
        val tmpName: String
        if ((1..100).random() <= tmpUp) {
            val randomIndex = Random().nextInt(nowPoolData.up4!!.size())

            // 获取随机选择的武器
            tmpName = nowPoolData.up4!![randomIndex].asText()
            type = poolType
        } else {
            isUp4 = 1
            if ((1..100).random() <= 50) {
                tmpName = nowPoolData.role4!![(0 until nowPoolData.role4!!.size()).random()].asText()
                type = "role"
            } else {
                tmpName = nowPoolData.weapon4!![(0 until nowPoolData.weapon4!!.size()).random()].asText()
                type = "weapon"
            }
        }

        val userGacha = UserGacha(
            name = tmpName,
            type = type,
            star = 4,
            have = false
        )

        return Pair(true, userGacha)
    }

    /**
     * 抽取3星
     *
     * @return UserGacha 抽取到的3星的数据
     */
    private fun lottery3(): UserGacha {
        val tmpName = nowPoolData.weapon3!![(0 until nowPoolData.weapon3!!.size()).random()].asText()
        return UserGacha(
            name = tmpName,
            type = "weapon",
            star = 3,
            have = false
        )
    }


    fun getGachaData(): GachaData {
        return GachaData
    }
}