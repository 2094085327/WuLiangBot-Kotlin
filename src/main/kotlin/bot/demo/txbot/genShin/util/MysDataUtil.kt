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


class MysDataUtil {


    companion object {
        const val CACHE_PATH = "resources/gachaCache"
        var poolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/gacha.json")
        var upPoolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/pool.json")
        var poolType = poolData["poolType"].textValue()
        var nowPoolData: PoolData = PoolData()

        var num5 = 0
        var num4 = 0
        var isUp5 = 0
        var lifeNum = 0
        var isBing = false
    }

    object GachaData {
        var permanents: MutableList<HtmlEntity> = mutableListOf()
        var roles: MutableList<HtmlEntity> = mutableListOf()
        var weapons: MutableList<HtmlEntity> = mutableListOf()
    }

    val fileList: ArrayList<String> = arrayListOf()

    // 获取历史抽卡数据
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

    fun checkFolder(folderPath: String): List<String> {
        val folder = File(folderPath)

        if (!folder.exists() || !folder.isDirectory) {
            println("文件夹不存在或不是一个有效的文件夹: $folderPath")
            return emptyList()
        }

        val fileNames = mutableListOf<String>()

        try {
            // 使用 File 对象构造 BufferedReader
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


    // 检查文件是否存在
    private fun checkFile(fileName: String): Pair<Boolean, String?> {
        val matchingFiles = fileList.filter { it.startsWith(fileName) }
        return if (matchingFiles.isNotEmpty()) Pair(true, matchingFiles[0]) else Pair(false, fileName)
    }


    // 插入属性
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

    // 获取角色属性
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


    fun runGacha() {
        initGacha()
        lottery()
    }

    private fun initGacha() {
        poolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/gacha.json")
        upPoolData = JacksonUtil.getJsonNode("resources/genShin/defSet/gacha/pool.json")
        poolType = poolData["poolType"].textValue()

        num5 = 0
        num4 = 0
        isUp5 = 0
        lifeNum = 0
    }


    // 获取每个卡池的数据
    fun getEachData(data: JsonNode, gachaType: String) {
        val folderPath = when (gachaType) {
            "200" -> "resources/genShin/GenShinImg/permanents/"
            "301" -> "resources/genShin/GenShinImg/role/"
            "302" -> "resources/genShin/GenShinImg/weapons/"
            else -> return
        }

        val itemList = when (gachaType) {
            "200" -> GachaData.permanents
            "301" -> GachaData.roles
            "302" -> GachaData.weapons
            else -> return
        }

        itemList.clear()
        fileList.addAll(checkFolder(folderPath))

        val gachaEmpty = HtmlEntity(
            itemId = null,
            itemName = null,
            itemType = null,
            itemAttribute = null,
            getTime = null,
            times = 0,
            isEmpty = false,
        )

        val getData = data["gachaLog"][gachaType]

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
                    times = array["times"].asInt(),
                    isEmpty = isEmpty
                )

                itemList.add(gachaEntity)
            }
        }

        val remainingItems = if (itemList.size % 6 != 0) 6 - itemList.size % 6 else 0
        repeat(remainingItems) {
            itemList.add(gachaEmpty)
        }
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
    )

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

    fun mergeRole(version: String): Pair<ArrayNode, ArrayNode> {
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


    fun getGachaPool(): PoolData {
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
        println(poolDataList)
        return poolDataList
    }

    fun probability(): Int {
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

    fun lottery() {
        nowPoolData = getGachaPool()
        var num = 0
        for (i in 1..1000) {
            lottery5()
            num += 1
//            println(num)
        }
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


    fun lottery5(): Boolean {
        var isBigUp = false
        val tmpChance5 = probability()
        var type = poolType
        val random = (1..10000).random()

        if (random > tmpChance5) {
            num5 += 1
            return false
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

        if (tmpName != "薙草之稻光") lifeNum += 1
        val userGacha = UserGacha(
            name = tmpName,
            type = type,
            isBigUp = isBigUp,
            num = nowCardNum,
            star = 5,
            isBing = isBing,
            have = false
        )

        println(userGacha)

        return true
    }


    fun getGachaData(): GachaData {
        return GachaData
    }
}