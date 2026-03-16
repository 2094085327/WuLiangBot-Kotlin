package bot.wuliang.parser.scraper

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WARFRAME_WIKIA_API
import bot.wuliang.config.WARFRAME_WIKIA_BLUEPRINTS
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.parser.interfaces.TransformFunction
import com.fasterxml.jackson.databind.JsonNode
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitUntilState
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

class WikiaDataScraper<T>(
    private val url: Any,
    private val luaDataName: String,
    private val transFormFunction: TransformFunction
) {
    private var blueprintsDataCache: JsonNode? = null

    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var browserContext: BrowserContext? = null

    companion object {
        private val BROWSER_ARGS = listOf(
            "--no-sandbox",
            "--disable-setuid-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--disable-blink-features=AutomationControlled",
            "--disable-infobars"
        )
        private const val USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    /**
     * 确保浏览器已初始化
     */
    private fun ensureBrowser() {
        if (browser == null || !browser!!.isConnected) {
            playwright = Playwright.create()
            browser = playwright!!.chromium().launch(
                BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(BROWSER_ARGS)
            )
        }
    }

    /**
     * 确保浏览器上下文已初始化
     */
    private fun ensureBrowserContext() {
        ensureBrowser()
        browserContext = browser!!.newContext(
            Browser.NewContextOptions()
                .setUserAgent(USER_AGENT)
                .setViewportSize(1920, 1080)
                .setLocale("en-US")
                .setTimezoneId("America/New_York")
        )
    }

    /**
     * 关闭浏览器上下文和浏览器资源
     */
    fun closeResources() {
        browserContext?.close()
        browser?.close()
        playwright?.close()
        browserContext = null
        browser = null
        playwright = null
    }

    /**
     * 获取蓝图数据
     * @return 蓝图数据的 JsonNode 对象
     */
    private fun getBlueprintsData(): JsonNode? {
        if (blueprintsDataCache != null) {
            return blueprintsDataCache
        }

        val bluePrintsData = getLuaData("$WARFRAME_WIKIA_BLUEPRINTS?action=edit")
        val luaDataToObject = convertLuaDataToObject(bluePrintsData) ?: return null
        blueprintsDataCache = JacksonUtil.readTree(luaDataToObject)
        return blueprintsDataCache
    }

    /**
     * 获取物品图片 URL
     * @param things 包含物品信息的 JsonNode 对象
     * @return 物品标题到图片 URL 的映射
     */
    fun getImageUrl(things: JsonNode): Map<String, String> {
        val titles = things.fields().asSequence()
            .mapNotNull { (_, node) -> node["Image"].takeUnless { it.isMissingNode }?.let { "File:${it.textValue()}" } }
            .toList()

        if (titles.isEmpty()) return emptyMap()

        val titleBatches = titles.chunked(50)
        val urlList = mutableMapOf<String, String>()

        titleBatches.forEach { batch -> processBatch(batch, urlList) }
        logInfo("成功获取所有物品图片 URL，共 ${titles.size} 个条目。")

        return urlList.toMap()
    }

    /**
     * 批次处理物品标题，获取图片 URL
     * @param batch 物品标题批次
     * @param urlList 用于存储结果的映射，键为物品标题，值为图片 URL
     */
    fun processBatch(batch: List<String>, urlList: MutableMap<String, String>) {
        val params = listOf(
            "action=query",
            "titles=${batch.joinToString("|")}",
            "prop=imageinfo",
            "iiprop=url",
            "format=json"
        )
        val url = "${WARFRAME_WIKIA_API}?${params.joinToString("&")}"
        logInfo("构建请求 URL: $url")

        ensureBrowserContext()

        var page: Page? = null
        try {
            page = browserContext!!.newPage()
            val response = page.waitForResponse(
                { resp ->
                    resp.url().contains(url) && resp.status() == 200
                },
                Page.WaitForResponseOptions().setTimeout(60000.0)
            ) {
                page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))
            }

            val contentType = response.headers()["content-type"]
            requireNotNull(contentType) { "响应内容为空" }
            require(contentType.contains("application/json")) { "响应类型不是 JSON: $contentType" }

            val jsonContent = response.text()
            require(jsonContent.trim().startsWith("{")) { "无法获取到标准 JSON" }

            parseBatchResponse(jsonContent, urlList)
        } catch (e: Exception) {
            throw RuntimeException("所有方式均无法获取数据", e)
        } finally {
            page?.close()
        }
    }

    /**
     * 解析批次响应，提取物品标题和图片 URL
     * @param jsonContent 包含物品数据的 JSON 字符串
     * @param urlList 用于存储结果的映射，键为物品标题，值为图片 URL
     */
    private fun parseBatchResponse(jsonContent: String, urlList: MutableMap<String, String>) {
        try {
            val jsonNode = JacksonUtil.readTree(jsonContent)
            jsonNode["query"]["pages"].fields().forEach { entry ->
                val pageId = entry.key
                pageId.toIntOrNull()?.takeIf { it > -1 }?.let {
                    val pageData = entry.value
                    val title = pageData["title"].asText().removePrefix("File:")
                    val imageUrl = pageData["imageinfo"]?.get(0)?.get("url")?.textValue()
                    if (imageUrl != null) {
                        urlList[title] = imageUrl
                    }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("解析批次响应失败:", e)
        }
    }

    /**
     * 从 Wikia 页面抓取数据并进行转换
     * @param url Wikia 页面 URL 或 URL 列表
     * @param luaDataName Lua 数据名称，用于转换 Lua 数据为 JSON 对象
     * @param transFormFunction 转换函数，用于将 JSON 对象转换为目标类型
     * @return 转换后的物品列表
     */
    fun scrape(): List<T> {
        val dataObject = when (url) {
            is String -> {
                val luaData = getLuaData(url)
                val convertLuaDataToObject = convertLuaDataToObject(luaData) ?: return emptyList()
                JacksonUtil.readTree(convertLuaDataToObject)
            }

            is List<*> -> {
                val results = url.filterIsInstance<String>().mapNotNull { urlString ->
                    val luaData = getLuaData(urlString)
                    @Suppress("UNCHECKED_CAST")
                    convertLuaDataToObject(luaData) as? Map<String, Any?>
                }

                // 合并所有 LinkedHashMap 类型的结果
                val mergedResult = mutableMapOf<String, Any?>()
                results.forEach { result -> mergedResult.putAll(result) }
                JacksonUtil.readTree(mergedResult)
            }

            else -> return emptyList()
        }

        if (dataObject.isMissingNode || dataObject.isEmpty) {
            return emptyList()
        }

        val blueprintsData = getBlueprintsData() ?: return emptyList()

        val imageUrlMap = runBlocking { getImageUrl(dataObject) }

        val things = mutableListOf<Any>()

        try {
            dataObject.fields().forEach { entry ->
                val thingToTransform = entry.value

                if (thingToTransform != null && !thingToTransform.isMissingNode) {
                    val transformedThing =
                        transFormFunction.transformFunction(thingToTransform, imageUrlMap, blueprintsData)
                    things.add(transformedThing)
                }
            }

            things.sortBy { thing ->
                when (thing) {
                    is Map<*, *> -> (thing["name"] as? String) ?: ""
                    else -> thing.toString()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        closeResources()

        @Suppress("UNCHECKED_CAST")
        return things as List<T>
    }

    /**
     * 从 Wikia 页面抓取 Lua 数据
     * @param url Wikia 页面 URL
     * @return 包含 Lua 数据的字符串
     */
    fun getLuaData(url: String): String {
        ensureBrowserContext()
        var page: Page? = null
        try {
            page = browserContext!!.newPage()
            logInfo("正在访问 WIKI 页面: $url")
            page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))

            page.waitForSelector("#wpTextbox1", Page.WaitForSelectorOptions().setTimeout(300000.0))

            logInfo("成功加载页面内容")

            return page.content().let { Jsoup.parse(it).select("#wpTextbox1").text() }

        } catch (e: Exception) {
            throw RuntimeException("获取页面数据失败", e)
        } finally {
            page?.close()
        }
    }


    /**
     * 将 Lua 脚本执行结果转换为 Kotlin 对象
     * @param luaScript 包含 Lua 脚本的字符串
     * @param luaDataName Lua 数据名称，用于指定要转换的 Lua 数据
     * @return 转换后的 Kotlin 对象 (通常是 Map 或 List)
     */
    fun convertLuaDataToObject(luaScript: String): Any? {
        // 初始化 Lua 运行环境
        val globals = JsePlatform.standardGlobals()

        // 加载并运行 Lua 脚本
        // chunk.call() 会返回 Lua 脚本中 return 的值
        val chunk = globals.load(luaScript)
        val luaValue = chunk.call()

        // 将 LuaValue 转换为 Kotlin 的 Map/List 结构
        return recursiveConvert(luaValue)
    }

    /**
     * 递归转换 LuaValue 为 Kotlin 对象
     * @param value LuaValue，通常是 Lua 脚本执行结果
     * @return 转换后的 Kotlin 对象 (通常是 Map 或 List)
     */
    private fun recursiveConvert(value: LuaValue): Any? {
        return when {
            value.istable() -> {
                val table = value.checktable()
                if (isLuaArray(table)) {
                    // 如果是数组结构 (Key 为连续数字)
                    val list = mutableListOf<Any?>()
                    for (i in 1..table.length()) {
                        list.add(recursiveConvert(table.get(i)))
                    }
                    list
                } else {
                    // 如果是对象结构 (Key 为字符串)
                    val map = mutableMapOf<String, Any?>()
                    val keys = table.keys()
                    for (k in keys) {
                        val keyStr = k.tojstring()
                        map[keyStr] = recursiveConvert(table.get(k))
                    }
                    map
                }
            }

            value.isboolean() -> value.toboolean()
            value.isint() -> value.toint()
            value.isnumber() -> value.todouble()
            value.isstring() -> value.tojstring()
            value.isnil() -> null
            else -> value.tojstring()
        }
    }

    /**
     * 判断 Lua Table 是数组还是 Map
     * @param table LuaTable，要判断的 Lua 表
     * @return 如果是数组结构返回 true，否则返回 false
     */
    private fun isLuaArray(table: LuaTable): Boolean {
        val len = table.length()
        if (len == 0) {
            // 可能是空数组，也可能为空 Map，通常 Lua 中空 Table 视为 Map 或根据上下文处理
            // 这里可以通过检查 keys 的类型进一步细化
            val keys = table.keys()
            return keys.isEmpty()
        }
        // 如果有非数字 Key，则不是纯数组
        val keys = table.keys()
        for (k in keys) {
            if (!k.isint()) return false
        }
        return true
    }


}