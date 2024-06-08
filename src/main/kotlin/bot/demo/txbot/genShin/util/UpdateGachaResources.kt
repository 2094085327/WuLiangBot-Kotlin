package bot.demo.txbot.genShin.util

import bot.demo.txbot.common.utils.HttpUtil
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import pers.wuliang.robot.common.utils.LoggerUtils.logError
import pers.wuliang.robot.common.utils.LoggerUtils.logInfo
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


/**
 * @description: 更新抽卡资源工具类
 * @author Nature Zero
 * @date 2024/6/7 上午9:18
 */
class UpdateGachaResources {
    data class GachaData(
        var endTime: String,
        var up5: String = "",
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty(value = "up5_2")
        var up52: String? = null,
        val up4: MutableSet<String> = mutableSetOf(),  // 使用Set自动去重
        val weapon5: MutableSet<String> = mutableSetOf(),
        val weapon4: MutableSet<String> = mutableSetOf()
    )

    private val versionMap = mutableMapOf<String, GachaData>()
    private val titleMap = mutableMapOf<String, String>()

    private var roleYamlMap: HashMap<String, String> = HashMap<String, String>()
    private var weaponYamlMap: HashMap<String, String> = HashMap<String, String>()

    /**
     * 处理卡池数据，进行格式化
     *
     * @param gachaTitle 卡池名称
     * @param input 输入的卡池信息
     */
    private fun processGachaData(gachaTitle: String, input: String) {
        val regex = """~ (\d{4}/\d{1,2}/\d{1,2} \d{2}:\d{2}(?::\d{2})?) 版本""".toRegex()
        val matchResult = regex.find(input) ?: return
        val endTime = matchResult.groupValues[1]

        // 检测时间字符串的格式
        val dateTimeFormat = when {
            endTime.count { it == '/' } == 2 && endTime.count { it == ':' } == 1 && endTime.length >= 15 -> "yyyy/M/d H:mm"
            endTime.count { it == '/' } == 2 && endTime.count { it == ':' } == 2 && endTime.length >= 18 -> "yyyy/M/d H:mm:ss"
            else -> throw IllegalArgumentException("未知时间格式: $endTime")
        }

        val parsedEndTime = LocalDateTime.parse(endTime, DateTimeFormatter.ofPattern(dateTimeFormat))
        val formattedEndTime = parsedEndTime
            .withSecond(59)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        // 提取版本号
        val versionRegex = """版本 (\d+\.\d+(?:上半|下半)?)""".toRegex()
        val versionMatchResult = versionRegex.find(input) ?: return
        val version = versionMatchResult.groupValues[1]

        val gachaData = versionMap.getOrPut(version) { GachaData(endTime = formattedEndTime) }

        when {
            gachaTitle.contains("角色活动祈愿") -> {
                val fiveStarChars = input.substringAfter("5星角色").substringBefore("4星角色").trim()
                val fourStarChars = input.substringAfter("4星角色").trim()

                val fiveStarCharList = fiveStarChars.split("「").filter { it.isNotEmpty() }
                    .map { it.substringAfter("·").substringBefore("(").trim() }
                val fourStarCharList = fourStarChars.split("「").filter { it.isNotEmpty() }
                    .map { it.substringAfter("·").substringBefore("(").trim() }

                // 修改这里以正确地从5星和4星字符中获取属性
                val fiveAttributeList = fiveStarChars.split("「").filter { it.isNotEmpty() }
                    .map { it.substringAfter("(").substringBefore(")").trim() }
                val fourAttributeList = fourStarChars.split("「").filter { it.isNotEmpty() }
                    .map { it.substringAfter("(").substringBefore(")").trim() }


                val combinedCharacters = fiveStarCharList + fourStarCharList
                val combinedAttributes = fiveAttributeList + fourAttributeList

                // 使用zip将合并后的列表配对，并构建映射
                combinedCharacters.zip(combinedAttributes).forEach { (char, attr) ->
                    roleYamlMap[char] = attr
                }

                // 只处理第一条数据时的五星角色
                if (gachaData.up5.isEmpty()) {
                    gachaData.up5 = fiveStarCharList.firstOrNull() ?: ""
                } else if (gachaData.up52 == null && fiveStarCharList.isNotEmpty()) {
                    gachaData.up52 = fiveStarCharList.first()
                }

                gachaData.up4.addAll(fourStarCharList)

                val existingTitle = titleMap[version]
                val newTitle = gachaTitle.substringAfter("「").substringBefore("」").trim()

                if (existingTitle.isNullOrBlank() || !existingTitle.contains(newTitle)) {
                    titleMap[version] = existingTitle?.let {
                        val existingFragment = it.substringAfter("「").substringBefore("」").trim()
                        if (!existingFragment.contains(newTitle)) {
                            "${existingFragment}|$newTitle"
                        } else {
                            it
                        }
                    } ?: newTitle
                }
            }

            gachaTitle.contains("武器活动祈愿") -> {
                val fiveStarWeaponIndex = input.indexOf("5星武器")
                val fourStarWeaponIndex = input.indexOf("4星武器")

                if (fiveStarWeaponIndex != -1 && fourStarWeaponIndex != -1) {
                    val fiveStarWeapons = input.substring(fiveStarWeaponIndex + 4, fourStarWeaponIndex).trim()
                    val fourStarWeapons = input.substring(fourStarWeaponIndex + 4).trim()

                    val fiveStarWeaponList = fiveStarWeapons.split("「").filter { it.isNotEmpty() }
                        .map { it.substringAfter("·").substringBefore("」").trim() }
                    val fourStarWeaponList = fourStarWeapons.split("「").filter { it.isNotEmpty() }
                        .map { it.substringAfter("·").substringBefore("」").trim() }

                    // 修改这里以正确地从5星和4星字符中获取属性
                    val fiveWeaponType = fiveStarWeapons.split("「").filter { it.isNotEmpty() }
                        .map { it.substringAfter("「").substringBefore("·").trim() }
                    val fourWeaponType = fourStarWeapons.split("「").filter { it.isNotEmpty() }
                        .map { it.substringAfter("「").substringBefore("·").trim() }

                    val combinedWeapons = fiveStarWeaponList + fourStarWeaponList
                    val combinedTypes = fiveWeaponType + fourWeaponType

                    combinedWeapons.zip(combinedTypes).forEach { (char, attr) ->
                        weaponYamlMap[char] = attr
                    }

                    gachaData.weapon5.addAll(fiveStarWeaponList)
                    gachaData.weapon4.addAll(fourStarWeaponList)
                }
            }
        }
    }

    /**
     * 生成卡池Json
     *
     * @return Json字符串
     */
    private fun generateJson(): String {
        val mapper = ObjectMapper().registerKotlinModule()
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // 先将数据按endTime排序
        val sortedEntries = versionMap.entries.sortedBy { (_, data) ->
            LocalDateTime.parse(data.endTime, dateTimeFormatter)
        }

        // 然后构建排序后的finalMap
        val finalMap = sortedEntries.associate { (version, data) ->
            val title = titleMap[version] ?: ""
            "$title-${version.replace("上半", "").replace("下半", "")}" to data
        }

        // 写入压缩后的Json
        return mapper.writeValueAsString(finalMap)
    }

    /**
     * 从Wiki获取卡池数据
     * 如有侵权请联系以删除
     *
     * @return
     */
    private fun getGachaDataFromWiki(): Set<Element> {
        // 获取Wiki页面内容URL
        val url = "https://wiki.biligame.com/ys/%E7%A5%88%E6%84%BF"

        val str = HttpUtil.doGetStr(url)
        // 使用Jsoup解析HTML内容
        val document = Jsoup.parse(str)

        // 定义筛选关键词和需要排除的关键字
        val characterKeywords = listOf("时间", "版本", "5星角色", "4星角色")
        val weaponKeywords = listOf("时间", "版本", "5星武器", "4星武器")
        val generalExcludePhrases = listOf("角色活动祈愿", "武器活动祈愿")

        // 筛选符合条件的表格
        val filteredTables = document.select(".wikitable")
            .filter { element ->
                val text = element.text().lowercase(Locale.getDefault())
                val containsCharacterKeywords = characterKeywords.any { keyword -> text.contains(keyword) }
                val containsWeaponKeywords = weaponKeywords.any { keyword -> text.contains(keyword) }
                val doesNotContainExcludes = generalExcludePhrases.none { exclude -> text.contains(exclude) }
                (containsCharacterKeywords || containsWeaponKeywords) && doesNotContainExcludes
            }

        // 返回筛选结果
        return filteredTables.toSet()
    }

    /**
     * 运行获取数据
     *
     * @param combinedFilteredTables 筛选出的wikitable集合
     * @return Json字符串
     */
    private fun organizeRunGetData(combinedFilteredTables: Set<Element>): String {
        // 提取每个符合条件的wikitable中class为ys-qy-title的数据
        combinedFilteredTables.forEach { table ->
            val qyTitles = table.select(".ys-qy-title")
            var titleAlt = ""
            qyTitles.forEach { titleElement ->
                val imgElements = titleElement.select("img")
                imgElements.forEach { imgElement ->
                    val alt = imgElement.attr("alt")
                    titleAlt = alt
                }
            }
            titleAlt = if (titleAlt == "") qyTitles.text() else titleAlt

            processGachaData(titleAlt, table.text())
        }
        return generateJson()
    }

    /**
     * 写入Json到文件
     *
     * @param json Json字符串
     */
    private fun writeGachaJsonToFile(json: String) {
        val file = File(POOL_JSON)
        file.writeText(json)
    }

    /**
     * 获取最后一个卡池的结束时间
     *
     * @return 卡池的结束时间
     */
    private fun getLastGachaEndTime(): String? {
        val file = File(POOL_JSON)
        if (file.exists()) {
            val mapper = ObjectMapper().registerKotlinModule()
            val mapType = object : TypeReference<Map<String, Map<String, Any>>>() {}

            val map: Map<String, Map<String, Any>> = mapper.readValue(file, mapType)

            val lastEntry = map.entries.lastOrNull()
            val lastEndTime = lastEntry?.value?.get("endTime")?.toString()

            return lastEndTime
        }

        return null
    }

    /**
     * 判断是否需要更新
     *
     * @return 布尔值
     */
    private fun isGachaEndTimeBeforeNow(): Boolean {
        val lastEndTime = getLastGachaEndTime() ?: return true

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val endTime = LocalDateTime.parse(lastEndTime, formatter)
        val now = LocalDateTime.now()

        return endTime.isBefore(now)
    }

    /**
     * 读取角色Yaml
     *
     * @return HashMap
     */
    private fun readYaml(filePath: String): HashMap<String, String> {
        val objectMapper = ObjectMapper(YAMLFactory())
        val roleYaml = File(filePath)
        val typeRef = object : TypeReference<HashMap<String, String>>() {}
        val content = roleYaml.readText().trim()
        if (content.isEmpty() || content.isBlank()) {
            logError("Yaml 文件为空，无法读取")
            return HashMap()
        }

        return try {
            objectMapper.readValue(content, typeRef)
        } catch (e: Exception) {
            logError("读取Yaml 文件时发生错误: ${e.message}")
            HashMap()
        }
    }

    /**
     * 写入Yaml
     *
     * @param filePath 文件路径
     * @param data HashMap
     */
    private fun writeToYaml(filePath: String, data: HashMap<String, String>) {
        val objectMapper = ObjectMapper(YAMLFactory())
        val outputFile = File(filePath)

        val sortedData = data.toList().sortedBy { (_, value) -> value }.toMap()

        try {
            // 将HashMap写入YAML文件
            objectMapper.writeValue(outputFile, sortedData)
            logInfo("HashMap已成功写入YAML文件。")
        } catch (e: Exception) {
            logError("写入文件时发生错误：${e.message}")
        }
    }

    /**
     * 更新卡池数据主方法
     *
     */
    fun getDataMain() {
        // 判断是否需要更新
        if (isGachaEndTimeBeforeNow()) {
            roleYamlMap = readYaml(ROLE_YAML)
            weaponYamlMap = readYaml(WEAPON_YAML)

            val combinedFilteredTables = getGachaDataFromWiki()
            val json = organizeRunGetData(combinedFilteredTables)
            writeGachaJsonToFile(json)
            logInfo("更新卡池数据成功")

            writeToYaml(ROLE_YAML, roleYamlMap)
            writeToYaml(WEAPON_YAML, weaponYamlMap)

            System.gc()
        } else logInfo("当前卡池未结束，跳过更新")
    }
}