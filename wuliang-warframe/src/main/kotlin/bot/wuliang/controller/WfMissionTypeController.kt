package bot.wuliang.controller

import bot.wuliang.service.impl.WfMissionTypeServiceImpl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


/**
 * @description: TODO
 * @author Nature Zero
 * @date 2024/11/24 18:59
 */

@RestController
@RequestMapping("/wfMission")
class WfMissionTypeController @Autowired constructor(
    private val wfMissionTypeService: WfMissionTypeServiceImpl
) {
    // 目标网页的 URL
    val url =
        "https://warframe-web-assets.nyc3.cdn.digitaloceanspaces.com/uploads/cms/hnfvc0o3jnfvc873njb03enrf56.html#missionRewardsm"

    // 创建 OkHttpClient 实例
    val client = OkHttpClient()

    @RequestMapping("/getMissionRewards")
    fun getMissionRewards(): MutableList<Map<String, Any>>? {
        // 创建 Request 对象
        val request = Request.Builder()
            .url(url)
            .build()

        // 发送请求并获取响应
        val response = client.newCall(request).execute()

        if (response.isSuccessful) {
            // 获取响应的 HTML 内容
            val htmlContent = response.body.string()


            // 使用 Jsoup 解析 HTML
            val doc: Document = Jsoup.parse(htmlContent)

            // 通过选择器找到 id=missionRewards 的 h3 标签
            val h3Element: Element? = doc.select("h3#missionRewards").first()

            if (h3Element != null) {
                // 找到 h3 标签的下一个兄弟节点中的 table 标签
                val tableElement: Element? = h3Element.nextElementSibling()?.select("table")?.first()

                if (tableElement != null) {
                    println("找到的 table 标签内容:")
                    var currentTitle: String? = null
                    var currentRotation: String? = null
                    var currentRotationData: MutableMap<String, MutableList<Map<String, String>>> = mutableMapOf()

                    val data: MutableList<Map<String, Any>> = mutableListOf()

                    val allMissionType = wfMissionTypeService.getAllMissionType()


                    var count = 0
                    for (tr in tableElement.select("tr")) {
                        when {
                            // 遇到空行，表示任务结束
                            tr.hasClass("blank-row") -> {
                                if (currentTitle != null) {
                                    println("Adding task: $currentTitle")
                                    data.add(
                                        mapOf(
                                            "title" to currentTitle.substringBefore(" ("),
                                            "missionType" to (allMissionType.find {
                                                it?.missionType.equals(
                                                    currentTitle!!.substringAfter("(").substringBefore(")"),
                                                    ignoreCase = true
                                                )
                                            }?.missionName ?: currentTitle),
                                            "rotations" to currentRotationData.toMap()
                                        )
                                    )
                                }
                                // 重置任务相关数据
                                currentTitle = null
                                currentRotation = null
                                currentRotationData = mutableMapOf()
                            }

                            // 解析任务标题或轮次标题
                            tr.select("th[colspan=2]").isNotEmpty() -> {
                                val th = tr.select("th[colspan=2]").first()
                                if (currentTitle == null) {
                                    currentTitle = th?.text()
                                    println("Parsed task title: $currentTitle")
                                } else {
                                    currentRotation = th?.text()
                                    println("Parsed rotation title: $currentRotation")
                                }
                            }

                            // 解析奖励内容
                            tr.select("td").size == 2 -> {
                                val td = tr.select("td")
                                val name = td.first()?.text()
                                val probability = td.last()?.text()?.substringAfter("(")?.substringBefore("%)")

                                println("Parsed reward: Name = $name, Probability = $probability, Rotation = $currentRotation")

                                if (currentRotation != null) {
                                    currentRotationData.getOrPut(currentRotation) { mutableListOf() }
                                        .add(mapOf("name" to name, "probability" to probability) as Map<String, String>)
                                } else {
                                    currentRotationData.getOrPut("No Rotation") { mutableListOf() }
                                        .add(mapOf("name" to name, "probability" to probability) as Map<String, String>)
                                }
                            }
                        }
                        count += 1
                        if (count == 100) break
                    }

                    // 确保在循环结束时添加最后一个任务的数据
                    if (currentTitle != null) {
                        println("Adding final task: $currentTitle")
                        data.add(
                            mapOf(
                                "title" to currentTitle.substringBefore(" ("),
                                "missionType" to (allMissionType.find {
                                    it?.missionType.equals(
                                        currentTitle.substringAfter("(").substringBefore(")"), ignoreCase = true
                                    )
                                }?.missionName ?: currentTitle),
                                "rotations" to currentRotationData.toMap()
                            )
                        )
                    }

                    return data
//                    return data.subList(0, 10)
                } else {
                    println("没有找到 table 标签")
                }
            } else {
                println("没有找到 id=missionRewards 的 h3 标签")
            }
        } else {
            println("请求失败，状态码: ${response.code}")
        }

        // 关闭响应
        response.close()
        return null
    }

}