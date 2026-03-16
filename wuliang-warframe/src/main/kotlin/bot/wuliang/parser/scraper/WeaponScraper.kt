package bot.wuliang.parser.scraper

import bot.wuliang.config.WARFRAME_WIKIA_BASE_URL
import bot.wuliang.config.WARFRAME_WIKIA_WEAPONS
import bot.wuliang.httpUtil.HttpUtil.urlEncode
import bot.wuliang.jacksonUtil.JacksonUtil.getDoubleOrNull
import bot.wuliang.jacksonUtil.JacksonUtil.getIntOrNull
import bot.wuliang.jacksonUtil.JacksonUtil.getTextOrNull
import bot.wuliang.parser.interfaces.TransformFunction
import bot.wuliang.parser.model.wikia.WikiaAttack
import bot.wuliang.parser.model.wikia.WikiaWeapon
import bot.wuliang.utils.WfStatus.WarframeElement
import com.fasterxml.jackson.databind.JsonNode

class WeaponScraper : TransformFunction {
    companion object {
        private val DAMAGE_TYPES = listOf(
            "Impact", "Slash", "Puncture", "Heat", "Cold",
            "Electricity", "Toxin", "Viral", "Corrosive",
            "Radiation", "Blast", "Magnetic", "Gas", "Void"
        )

        private val ATTACK_FIELDS = listOf(
            "NormalAttack", "Attack1", "Attack2", "Attack3", "Attack4",
            "Attack5", "Attack6", "Attack7", "Attack8", "Attack9", "Attack10",
            "SecondaryAreaAttack", "SecondaryAttack", "ChargeAttack", "AreaAttack"
        )

        // 武器伤害类型对应的乘数，转换为百分比
        private const val PERCENTAGE_MULTIPLIER = 100.0

        // 保留小数位数
        private const val SINGLE_DECIMAL = 1
        private const val DEFAULT_CHANNELING_MULTIPLIER = 1.5

        private fun getBlueprintCost(nameNode: JsonNode, costField: String): String? =
            nameNode[costField]?.takeIf { !it.isMissingNode }?.textValue()
    }

    /**
     * 从 Wikia 武器页面中获取武器数据
     *
     * @return 武器数据对象
     */
    fun scrape(): List<WikiaWeapon> {
        val baseUrl = WARFRAME_WIKIA_WEAPONS
        val suffix = "?action=edit"
        val subModules = listOf("primary")
        // 将基础URL+后缀+模块名拼接后放到一个数组中
        val urls = subModules.map { "$baseUrl/$it$suffix" }
        val wikiaDataScraper = WikiaDataScraper<WikiaWeapon>(urls, "Weapon", this)
        val rawResult = wikiaDataScraper.scrape()
        return rawResult
    }

    /**
     * 解析武器攻击数据
     *
     * @param attack 武器攻击数据的 JsonNode 对象
     * @return 解析后的 WikiaAttack 对象
     */
    fun parseAttack(attack: JsonNode): WikiaAttack {
        return WikiaAttack(
            name = attack.getTextOrNull("AttackName") ?: "",
            duration = attack.getDoubleOrNull("Duration", PERCENTAGE_MULTIPLIER),
            radius = attack.getDoubleOrNull("Radius"),
            speed = attack["FireRate"].asDouble(),
            pellet = attack.getTextOrNull("PelletName")?.let { pelletName ->
                WikiaAttack.Pellet(pelletName, attack["PelletCount"].asInt())
            },
            critChance = attack.getDoubleOrNull("CritChance", PERCENTAGE_MULTIPLIER),
            critMult = attack.getDoubleOrNull("CritMultiplier", decimals = SINGLE_DECIMAL),
            statusChance = attack.getDoubleOrNull("StatusChance", PERCENTAGE_MULTIPLIER),
            chargeTime = attack.getDoubleOrNull("ChargeTime", decimals = SINGLE_DECIMAL),
            shotType = attack.getTextOrNull("ShotType") ?: "",
            shotSpeed = attack.getDoubleOrNull("ShotSpeed", decimals = SINGLE_DECIMAL),
            flight = attack["ShotSpeed"]?.takeIf { !it.isMissingNode }?.asDouble(),
            falloff = attack["Falloff"]?.takeIf { !it.isMissingNode }?.let { falloffNode ->
                WikiaAttack.Falloff(
                    falloffNode.getIntOrNull("StartRange"),
                    falloffNode.getIntOrNull("End"),
                    falloffNode.getIntOrNull("Reduction")
                )
            },
            damage = attack["Damage"]?.takeIf { !it.isMissingNode }?.let { damageNode ->
                DAMAGE_TYPES.mapNotNull { type ->
                    damageNode[type]?.takeIf { !it.isMissingNode }?.asDouble()?.let { value ->
                        type.lowercase() to "%.2f".format(value).toDouble()
                    }
                }.toMap().takeIf { it.isNotEmpty() }
            }
        )
    }

    /**
     * 解析震地攻击数据以清理统计信息
     * @param attackNode 包含震地攻击数据的 JsonNode，包含 SlamAttack, SlamRadialDmg 等字段
     * @return WikiaWeapon.SlamAttack? 解析后的震地攻击对象，如果无数据则返回 null
     */
    private fun parseSlam(attackNode: JsonNode): WikiaAttack.SlamAttack? {
        if (attackNode.isMissingNode || attackNode.isNull) return null
        val damage = attackNode.getDoubleOrNull("SlamAttack") ?: 0.0
        val radialDamage = attackNode.getDoubleOrNull("SlamRadialDmg") ?: 0.0
        val element = attackNode.getTextOrNull("SlamRadialElement")
        val proc = attackNode.getTextOrNull("SlamRadialProc")
        val radius = attackNode["SlamRadius"]?.takeIf { !it.isMissingNode }?.asDouble() ?: 0.0

        return WikiaAttack.SlamAttack(
            damage = damage,
            radial = WikiaAttack.SlamRadial(
                damage = radialDamage,
                element = element,
                proc = proc,
                radius = radius
            )
        )
    }

    /**
     * 解析元素攻击数据
     *
     * @param node 包含元素攻击数据的 JsonNode
     * @param attackField 攻击字段名称
     * @param elementField 元素字段名称
     * @return 解析后的元素攻击字符串，如果无数据则返回 null
     */
    private fun parseElementalAttack(node: JsonNode, attackField: String, elementField: String): String? {
        val attack = node[attackField]?.takeIf { !it.isMissingNode }?.asText() ?: return null
        val element = node[elementField]?.takeIf { !it.isMissingNode }?.textValue()
        val elementValue = element?.let { WarframeElement[it] }
        return if (elementValue != null) "$attack$elementValue" else attack
    }

    /**
     * 构建武器Wiki页面URL
     *
     * @param name 武器名称
     * @return 武器Wiki页面URL
     */
    private fun buildWikiUrl(name: String): String =
        "$WARFRAME_WIKIA_BASE_URL/${name.replace(" ", "_").urlEncode()}"

    /**
     * 构建武器攻击列表
     *
     * @param node 包含武器攻击数据的 JsonNode
     * @return 解析后的武器攻击列表
     */
    private fun buildAttacksList(node: JsonNode): List<WikiaAttack> {
        val baseAttacks = ATTACK_FIELDS.mapNotNull { field ->
            node[field]?.takeIf { !it.isMissingNode }?.let { parseAttack(it) }
        }

        val arrayAttacks = node["Attacks"]?.mapNotNull { parseAttack(it) } ?: emptyList()

        val allAttacks = baseAttacks + arrayAttacks

        if (allAttacks.isEmpty()) return allAttacks

        val firstAttack = allAttacks[0]
        val slideValue = parseElementalAttack(node, "SlideAttack", "SlideElement")
        val jumpValue = parseElementalAttack(node, "JumpAttack", "JumpElement")
        val wallValue = parseElementalAttack(node, "WallAttack", "WallElement")
        val channelMult = node["ChannelMult"]?.asDouble() ?: DEFAULT_CHANNELING_MULTIPLIER
        val channelingValue = if (slideValue != null && jumpValue != null && wallValue != null) channelMult else null
        val slamValue = node["SlamAttack"]?.let { parseSlam(it) }

        return listOf(
            firstAttack.copy(
                slide = slideValue,
                jump = jumpValue,
                wall = wallValue,
                channeling = channelingValue,
                slam = slamValue
            )
        ) + allAttacks.drop(1)
    }

    /**
     * 转换JsonNode为WikiaWeapon对象
     *
     * @param thToTransForm 包含武器数据的 JsonNode
     * @param imageUrls 包含武器图片URL的映射
     * @param blueprints 包含武器蓝图数据的 JsonNode
     * @return 转换后的 WikiaWeapon 对象
     */
    override fun transformFunction(
        thToTransForm: JsonNode?,
        imageUrls: Map<String, String>,
        blueprints: JsonNode
    ): WikiaWeapon {
        if (thToTransForm == null || thToTransForm.isMissingNode) return WikiaWeapon()

        val name = thToTransForm.getTextOrNull("Name") ?: ""
        val traits = thToTransForm["Traits"]?.takeIf { !it.isMissingNode }?.map { it.textValue() }
        val imageName = thToTransForm.getTextOrNull("Image") ?: ""

        return WikiaWeapon(
            regex = "^${name.lowercase().replace(" ", "\\s")}$",
            name = name,
            uniqueName = thToTransForm.getTextOrNull("InternalName") ?: "",
            url = buildWikiUrl(name),
            mr = thToTransForm.getIntOrNull("Mastery") ?: 0,
            type = thToTransForm.getTextOrNull("Class") ?: thToTransForm.getTextOrNull("Type") ?: "",
            rivenDisposition = thToTransForm.getIntOrNull("Disposition"),
            statusChance = thToTransForm["ChargeAttack"]?.getDoubleOrNull("StatusChance", PERCENTAGE_MULTIPLIER),
            polarities = thToTransForm["Polarities"]?.takeIf { !it.isMissingNode }?.map { it.textValue() },
            ammo = thToTransForm.getIntOrNull("MaxAmmo"),
            tags = traits,
            vaulted = traits?.contains("Vaulted"),
            introduced = thToTransForm.getTextOrNull("Introduced") ?: "",
            marketCost = blueprints["Name"]?.let { getBlueprintCost(it, "MarketCost") },
            bpCost = blueprints["Name"]?.let { getBlueprintCost(it, "BPCost") },
            thumbnail = imageUrls[imageName] ?: imageUrls[imageName.replace("_", " ")],
            attacks = buildAttacksList(thToTransForm)
        )
    }
}
