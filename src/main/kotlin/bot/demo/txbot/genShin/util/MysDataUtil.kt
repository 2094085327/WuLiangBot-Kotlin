package bot.demo.txbot.genShin.util

import bot.demo.txbot.genShin.database.gacha.HtmlEntity
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes


class MysDataUtil {
    companion object {
        const val CACHE_PATH = "resources/gachaCache"
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
        val folder = object {}.javaClass.classLoader.getResource(folderPath)

        if (folder == null) {
            println("文件未找到: $folderPath")
            return emptyList()
        }

        val folderPathInFileSystem = Paths.get(folder.toURI())
        val fileNames = mutableListOf<String>()

        try {
            Files.walkFileTree(folderPathInFileSystem, setOf(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        fileNames.add(file.fileName.toString())
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                        return FileVisitResult.CONTINUE
                    }
                })
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


    // 获取每个卡池的数据
    fun getEachData(data: JsonNode, gachaType: String) {
        val folderPath = when (gachaType) {
            "200" -> "static/img/GenShinImg/permanents/"
            "301" -> "static/img/GenShinImg/role/"
            "302" -> "static/img/GenShinImg/weapons/"
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
        val poolData = getGachaData("resources/genShin/defSet/gacha/pool.json")
        return poolData.fields().asSequence().map { it.key }.toList()
    }

    fun findPoolData(name: String, id: String): Pair<String, JsonNode>? {
        val poolData = getGachaData("resources/genShin/defSet/gacha/pool.json")

        val iterator = poolData.fields()
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


    fun changePoolOpen(poolInfo: Pair<String, JsonNode>, poolFormat: String) {
        val (name, _) = poolInfo
        val poolData = getGachaData("resources/genShin/defSet/gacha/gacha.json")

        val objRoot = poolData as ObjectNode
        objRoot.put("openPool", name)
        objRoot.put("poolName", poolFormat)
        val objectMapper = ObjectMapper()
        objectMapper.writeValue(File("resources/genShin/defSet/gacha/gacha.json"), poolData)
    }

    data class PoolData(
        // 卡池名称
        val poolName: String,
        // 4星up角色
        val up4Role: JsonNode,
        // 4星up武器
        val up4Weapon: JsonNode,
        // 3星常驻武器
        val weapon3: JsonNode,
        // 4星常驻角色
        val role4: JsonNode,
        // 4星常驻武器
        val weapon4: JsonNode,
        // 5星up角色
        val up5: JsonNode,
        // 5星常驻武器武器
        val weapon5: JsonNode,
        // 5星常驻角色
        val role5: JsonNode,
    )

    private fun getDetailUp5(openPool: String, poolName: String, detailPoolInfo: JsonNode): JsonNode {
        return if ('|' in openPool) {
            if (openPool.startsWith(poolName)) {
                detailPoolInfo["up5"]
            } else {
                detailPoolInfo["up5_2"]
            }
        } else {
            detailPoolInfo["up5"]
        }
    }

    fun getGachaPool() {
        val poolData = getGachaData("resources/genShin/defSet/gacha/gacha.json")
        val openPool = poolData["openPool"].textValue()
        val poolName = poolData["poolName"].textValue()
        val detailPoolInfo = getGachaData("resources/genShin/defSet/gacha/pool.json")
        val poolInfo = detailPoolInfo[openPool]

        val up5 = getDetailUp5(openPool, poolName, poolInfo)

        val poolDataList = PoolData(
            poolName = poolName,
            up4Role = poolInfo["up4"],
            up4Weapon = poolInfo["weapon4"],
            weapon3 = poolData["weapon3"],
            role4 = poolData["role4_base"],
            weapon4 = poolData["weapon4"],
            up5 = up5,
            weapon5 = poolData["weapon5"],
            role5 = poolData["role5_base"],
        )

        println(poolDataList)
    }


    fun getGachaData(): GachaData {
        return GachaData
    }
}