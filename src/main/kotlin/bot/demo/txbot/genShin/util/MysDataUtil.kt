package bot.demo.txbot.genShin.util

import bot.demo.txbot.genShin.database.gacha.HtmlEntity
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

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


    fun getGachaData(filePath: String): JsonNode {
        val file = File(filePath)
        val objectMapper = ObjectMapper()
        return objectMapper.readTree(file)
    }

    fun deleteDataCache() {
        val folder = File(CACHE_PATH)
        if (folder.exists() && folder.isDirectory) {
            val currentDate = Date()
            val fiveMinutesAgo = Date(currentDate.time - 10 * 60 * 1000)
            folder.listFiles()?.forEach { file ->
                if (file.lastModified() < fiveMinutesAgo.time) {
                    println("删除缓存：${file.name}")
                    file.delete()
                }
            }
        }
    }

    fun forceDeleteCache(cachePath: String) {
        val folder = File(cachePath)
        if (folder.exists() && folder.isDirectory) {
            folder.listFiles()?.forEach { file ->
                println("删除缓存：${file.name}")
                file.delete()
            }
        }
    }

    fun checkFolder(folderPath: String): List<String> {
        val folder = object {}.javaClass.classLoader.getResource(folderPath)

        if (folder == null) {
            println("Folder not found: $folderPath")
            return emptyList()
        }

        val folderPathInFileSystem = Paths.get(folder.toURI())
        val fileNames = mutableListOf<String>()

        Files.walkFileTree(folderPathInFileSystem, setOf(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    // 获取文件名并添加到列表中
                    fileNames.add(file.fileName.toString())
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
                    return FileVisitResult.CONTINUE
                }
            })


        println(fileNames)
        return fileNames
    }

    fun checkFile(fileName: String): Pair<Boolean, String?> {
        val matchingFiles = fileList.filter { it.startsWith(fileName) }
        return if (matchingFiles.isNotEmpty()) Pair(true, matchingFiles[0]) else Pair(false, fileName)
    }

    data class Character(val name: String, val element: String)

    fun insertAttribute(itemName: String, attribute: String): String {
        val objectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())
        val file = File("E:/Learning/bots/Tencent-Bot-Kotlin/resources/genShin/defSet/element/role.yaml")

        try {
            val characterMap: LinkedHashMap<String, String> =
                objectMapper.readValue(file, object : TypeReference<LinkedHashMap<String, String>>() {})

            // 找到值为"火"的最后一条数据的位置
            val lastEntryWithValueOne = characterMap.filterValues { it == attribute }.entries.lastOrNull()
            println(lastEntryWithValueOne)

            return if (lastEntryWithValueOne != null) {
                // 创建新的 LinkedHashMap
                val newCharacterMap = LinkedHashMap<String, String>()

                // 将旧数据写入新 Map，直到需要插入的数据的属性的最后一条
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

    fun getRoleAttribute(roleName: String): String? {
        val objectMapper = ObjectMapper(YAMLFactory())
        val file = File("E:/Learning/bots/Tencent-Bot-Kotlin/resources/genShin/defSet/element/role.yaml")

        try {
            val characterMap: Map<String, String> =
                objectMapper.readValue(file, object : TypeReference<Map<String, String>>() {})


            val element = characterMap[roleName]

            if (element != null) {
                println("$roleName 的属性是 $element")
                return element
            } else {
                println("未找到 $roleName 对应的属性")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


    fun getEachData(data: JsonNode, gachaType: String) {
        when (gachaType) {
            "200" -> {
                GachaData.permanents.clear()
                fileList.addAll(checkFolder("static/img/genshinImg/permanents/"))
            }

            "301" -> {
                GachaData.roles.clear()
                fileList.addAll(checkFolder("static/img/genshinImg/role/"))
            }

            "302" -> {
                GachaData.weapons.clear()
                fileList.addAll(checkFolder("static/img/genshinImg/weapons/"))
            }
        }

        val getData = data["gachaLog"][gachaType]

        getData.forEach { array ->
            val rankType = array["rankType"]
            if (rankType.asInt() == 5) {
                val itemType = array["itemType"].textValue()
                val itemName = array["itemName"].textValue()
                var roleAttribute: String? = null
                var isEmpty: Boolean = false
                var itemFileName: String? = null

                when (gachaType) {
                    "200" -> {
                        val (getIsEmpty, getItemFileName) = checkFile(itemName)
                        isEmpty = getIsEmpty
                        itemFileName = getItemFileName
                        roleAttribute = getRoleAttribute(itemName)

                    }

                    "301" -> {
                        val (getIsEmpty, getItemFileName) = checkFile(itemName)
                        isEmpty = getIsEmpty
                        itemFileName = getItemFileName
                        roleAttribute =
                            if (array["itemType"].textValue() == "角色") getRoleAttribute(itemName) else null
                    }

                    "302" -> {
                        val (getIsEmpty, getItemFileName) = checkFile(itemName)
                        isEmpty = getIsEmpty
                        itemFileName = getItemFileName
                        roleAttribute =
                            if (array["itemType"].textValue() == "角色") getRoleAttribute(itemName) else null

                    }
                }


                val gachaEntity = HtmlEntity(
                    itemId = array["itemId"].textValue(),
                    itemName = itemFileName,
                    itemType = itemType,
                    itemAttribute = roleAttribute,
                    getTime = array["getTime"].textValue(),
                    times = array["times"].asInt(),
                    isEmpty = isEmpty
                )

                when (gachaType) {
                    "200" -> GachaData.permanents.add(gachaEntity)
                    "301" -> GachaData.roles.add(gachaEntity)
                    "302" -> GachaData.weapons.add(gachaEntity)
                }
            }
        }

        val gachaEmpty = HtmlEntity(
            itemId = null,
            itemName = null,
            itemType = null,
            itemAttribute = null,
            getTime = null,
            times = 0,
            isEmpty = false,
        )

        val gachaDataList = when (gachaType) {
            "200" -> GachaData.permanents
            "301" -> GachaData.roles
            "302" -> GachaData.weapons
            else -> mutableListOf()
        }

        val remainingItems = if (gachaDataList.size % 6 != 0) 6 - gachaDataList.size % 6 else 0

        repeat(remainingItems) {
            gachaDataList.add(gachaEmpty)
        }

        println(gachaDataList)

    }

    fun getGachaData(): GachaData {
        return GachaData
    }
}