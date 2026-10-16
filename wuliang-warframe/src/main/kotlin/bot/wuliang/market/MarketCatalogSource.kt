package bot.wuliang.market

import bot.wuliang.config.*
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.WfRivenAttributeEntity
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.riven.RivenGroups
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.WfMarketItemParser
import bot.wuliang.utils.WfRivenAttributeParser
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service

/** 获取 Market 集合版本与物品目录，供增量同步和词库更新共用。 */
@Service
class MarketCatalogSource(private val translations: PublicExportService) {
    /** 缺少集合版本时终止同步，避免把不完整的目录记录为已更新。 */
    fun getMarketCollectionVersions(): Map<String, String> {
        val collections = HttpUtil.doGetJson(
            url = WARFRAME_MARKET_VERSIONS_V2,
            headers = LANGUAGE_EN
        )["data"]["collections"]

        return listOf("items", "rivens", "liches", "sisters").associateWith { collection ->
            collections[collection]?.textValue()?.takeIf { it.isNotBlank() }
                ?: error("Warframe Market API 版本校验缺失: $collection")
        }
    }

    /** 读取物品 `i18n` 中 [language] 对应的名称；字段缺失时返回 null。 */
    private fun JsonNode.localizedName(language: String): String? {
        return this["i18n"]?.get(language)?.get("name")?.textValue()
    }

    /**
     * 使用 Market 自身的简体中文目录，包含游戏词库没有维护的套装交易名称。
     */
    fun getMarketItems(): List<WfMarketItemEntity> {
        val json = HttpUtil.doGetJson(url = WARFRAME_MARKET_ITEMS_V2, headers = LANGUAGE_ZH_HANS)
        val items = json["data"]

        return items.map(WfMarketItemParser::parse)
    }

    /**
     * 获取紫卡武器目录；武器名称使用统一翻译，紫卡属性名称由独立属性接口处理。
     */
    fun getRivenItems(): List<WfRivenEntity> {
        val json = HttpUtil.doGetJson(url = WARFRAME_MARKET_RIVEN_ITEMS_V2, headers = LANGUAGE_EN)
        val items = json["data"]

        return items.map { item ->
            val i18n = item["i18n"]
            WfRivenEntity(
                id = item["id"].textValue(),
                urlName = item["slug"].textValue(),
                zhName = translations.marketName(
                    item["gameRef"]?.textValue(),
                    i18n["en"]["name"]?.textValue(),
                    item["slug"]?.textValue()
                ),
                enName = translations.marketName(
                    item["gameRef"]?.textValue(),
                    i18n["en"]["name"]?.textValue(),
                    item["slug"]?.textValue(),
                    "en"
                ),
                rGroup = item["group"]?.textValue(),
                reqMasteryRank = item["reqMasteryRank"]?.floatValue(),
                rivenType = item["rivenType"]?.textValue(),
                disposition = item["disposition"]?.floatValue(),
            )
        }
    }

    /**
     * 共用玄骸与姐妹武器目录的解析逻辑，保留所属分组及 Market 协议标识。
     */
    private fun getV2WeaponItems(url: String, group: String): List<WfRivenEntity> {
        val items = HttpUtil.doGetJson(url = url, headers = LANGUAGE_EN)["data"]
        return items.map { item ->
            WfRivenEntity(
                id = item["id"].textValue(),
                urlName = item["slug"].textValue(),
                zhName = translations.marketName(
                    item["gameRef"]?.textValue(),
                    item.localizedName("en"),
                    item["slug"]?.textValue()
                ),
                enName = translations.marketName(
                    item["gameRef"]?.textValue(),
                    item.localizedName("en"),
                    item["slug"]?.textValue(),
                    "en"
                ),
                rGroup = group,
                reqMasteryRank = item["reqMasteryRank"]?.floatValue(),
            )
        }
    }

    /** 获取玄骸武器目录，补充统一翻译并保留玄骸分组标识。 */
    fun getLichItems(): List<WfRivenEntity> =
        getV2WeaponItems(WARFRAME_MARKET_LICH_WEAPONS_V2, RivenGroups.LICH)

    /** 获取姐妹武器目录，补充统一翻译并保留姐妹分组标识。 */
    fun getSisterItems(): List<WfRivenEntity> =
        getV2WeaponItems(WARFRAME_MARKET_SISTER_WEAPONS_V2, RivenGroups.SISTER)

    /**
     * 紫卡属性沿用 Market 的简体中文目录，避免被 Plus 缺失项回退为英文。
     */
    fun getRivenAttributes(): List<WfRivenAttributeEntity> {
        val items = HttpUtil.doGetJson(
            url = WARFRAME_MARKET_RIVEN_ATTRIBUTES_V2,
            headers = LANGUAGE_ZH_HANS
        )["data"]
        return items.map(WfRivenAttributeParser::parse)
    }

}