package bot.wuliang.worldstate

import com.fasterxml.jackson.annotation.JsonInclude
import org.jsoup.Jsoup
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class WeaponRotationKind(val title: String, val page: String, val anchor: Long) {
    TENET("信条武器", "Tenet_Weapons", Instant.parse("2015-12-03T00:00:00Z").toEpochMilli()),
    CODA("终幕武器", "Coda_Weapons", Instant.parse("2025-03-18T00:00:00Z").toEpochMilli());

    fun period(now: Long): WeaponRotationPeriod {
        val cycle = Math.floorDiv(now - anchor, PERIOD_MILLIS)
        val start = anchor + cycle * PERIOD_MILLIS
        return WeaponRotationPeriod(
            start, start + PERIOD_MILLIS,
            if (this == CODA) if (Math.floorMod(cycle, 2L) == 0L) "A" else "B" else null
        )
    }

    companion object {
        const val PERIOD_MILLIS = 4 * 24 * 60 * 60 * 1000L
    }
}

data class WeaponRotationPeriod(val activation: Long, val expiry: Long, val batch: String?)
data class WikiWeaponOffer(
    val name: String, val element: String, val bonus: Double,
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val displayName: String? = null
)

data class WikiWeaponPage(val updatedAt: Long, val batches: Map<String, List<WikiWeaponOffer>>)

/** 编辑页内嵌的是 Wikitext；只读取加成表，不执行 Lua、模板或页面脚本。 */
object WikiWeaponRotationParser {
    private val tables =
        Regex("<table\\b[^>]*>.*?</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val weapon = Regex("\\{\\{\\s*Weapon\\s*\\|\\s*([^|{}]+)\\s*}}", RegexOption.IGNORE_CASE)
    private val element = Regex("\\{\\{\\s*D\\s*\\|\\s*([A-Za-z]+)\\s*}}", RegexOption.IGNORE_CASE)
    private val bonus =
        Regex("\\{\\{\\s*ValenceBonusPercentageColor\\s*\\|\\s*([0-9]+(?:\\.[0-9]+)?)\\s*}}", RegexOption.IGNORE_CASE)
    private val batch = Regex("Weapon\\s*\\(Batch\\s+([AB])\\)", RegexOption.IGNORE_CASE)
    private val elements = setOf("Electricity", "Cold", "Heat", "Impact", "Magnetic", "Radiation", "Toxin")
    private val editTime = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parse(html: String, kind: WeaponRotationKind): WikiWeaponPage {
        val document = Jsoup.parse(html)
        val source = document.selectFirst("textarea#wpTextbox1")?.`val`()
            ?.takeIf(String::isNotBlank) ?: error("Wiki 编辑页未包含轮换源码")
        val timestamp = document.selectFirst("input[name=wpEdittime]")?.`val`()
            ?: error("Wiki 编辑页未包含修订时间")
        val updatedAt = LocalDateTime.parse(timestamp, editTime).toInstant(ZoneOffset.UTC).toEpochMilli()
        val result = linkedMapOf<String, List<WikiWeaponOffer>>()
        for (match in tables.findAll(source)) {
            val table = Jsoup.parseBodyFragment(match.value).selectFirst("table") ?: continue
            // 排除武器介绍、基础属性和分组清单等其他表格。
            if (table.select("th").none { it.text().trim().equals("Bonus %", ignoreCase = true) }) continue
            val key = if (kind == WeaponRotationKind.CODA) {
                batch.find(table.select("th").text())?.groupValues?.get(1)?.uppercase()
                    ?: error("终幕轮换表缺少批次")
            } else "TENET"
            val offers = table.select("tr").filter { it.select("td").isNotEmpty() }.map { row ->
                val cells = row.select("td")
                require(cells.size == 3) { "Wiki 武器轮换表列数已变化" }
                val rawName = cells[0].text().trim()
                val name = weapon.matchEntire(rawName)?.groupValues?.get(1)?.trim() ?: rawName
                require(
                    name.isNotBlank() && !name.contains("{{") &&
                            (if (kind == WeaponRotationKind.TENET) name.startsWith("Tenet ") else name.contains("Coda "))
                ) {
                    "Wiki 武器名称格式错误"
                }
                val elementName = element.matchEntire(cells[1].text().trim())?.groupValues?.get(1)
                    ?: error("Wiki 武器元素格式错误")
                require(elementName in elements) { "Wiki 武器元素未识别：$elementName" }
                val percentage = bonus.matchEntire(cells[2].text().trim())?.groupValues?.get(1)?.toDouble()
                    ?: error("Wiki 武器加成格式错误")
                require(percentage in 25.0..60.0) { "Wiki 武器加成超出范围" }
                WikiWeaponOffer(name, elementName, percentage)
            }
            require(offers.isNotEmpty() && offers.map { it.name }
                .distinct().size == offers.size) { "Wiki 轮换表为空或存在重复武器" }
            require(result.put(key, offers) == null) { "Wiki 存在重复轮换表" }
        }
        require(result.keys == if (kind == WeaponRotationKind.CODA) setOf("A", "B") else setOf("TENET")) {
            "Wiki 武器轮换表不完整"
        }
        return WikiWeaponPage(updatedAt, result)
    }
}