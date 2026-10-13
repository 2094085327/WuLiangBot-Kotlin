package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.config.WarframeProperties
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.moudles.Info
import bot.wuliang.moudles.Nodes
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PostConstruct

/** 统一翻译入口；管理已应用快照，并集中处理明确保留的本地词库例外。 */
@Service
class PublicExportService(
    remote: PublicExportRemote,
    properties: WarframeProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val translationAliases = properties.translationAliases
    private val sortieData = properties.sortieData
    private val aliases = recoverExportData { LocalTranslationAliases.load(Path.of(translationAliases)) }
        .onFailure { logger.warn("本地翻译关联表加载失败：{}，原因：{}", translationAliases, it.message) }
        .getOrDefault(LocalTranslationAliases.EMPTY)
    private val store = PublicExportStore(Path.of(properties.publicExportPlus.directory), remote, aliases.entries)
    private val sortieTranslations = recoverExportData { LocalSortieTranslations.load(Path.of(sortieData)) }
        .onFailure { logger.warn("本地突击修正词库加载失败：{}，使用统一索引或原始标识；原因：{}", sortieData, it.message) }
        .getOrDefault(LocalSortieTranslations.EMPTY)
    private val conquestTranslations = loadInfoTranslations(properties.conquestData, CONQUEST_KEY_PATTERN::matches)
    private val calendarTranslations =
        loadInfoTranslations(properties.calendarUpgrades) {
            it.startsWith(
                "/Lotus/Upgrades/Calendar/",
                ignoreCase = true
            )
        }
    private val spiralTranslations = loadInfoTranslations(properties.spiralTranslations) {
        it.isNotBlank() && (!it.startsWith("/") || it.startsWith("/Lotus/Language/Duviri/", ignoreCase = true))
    }
    private val localRevision = PublicExportStore.gitBlobSha(
        listOf(
            sortieTranslations.revision,
            conquestTranslations.revision,
            calendarTranslations.revision,
            spiralTranslations.revision,
            aliases.revision,
        ).joinToString(":").toByteArray(Charsets.UTF_8)
    )

    @Volatile
    private var applied: AppliedExport? = null
    private val missing = ConcurrentHashMap.newKeySet<String>()
    val ready: Boolean get() = applied != null
    val revision: String get() = applied?.manifest?.appliedCommit ?: "uninitialized"

    /**
     * 启动时只加载本地已应用快照，不发起网络更新。
     * 本地快照缺失或无效时保留未初始化状态，由管理员手动更新。
     */
    @PostConstruct
    fun loadLocal() {
        recoverExportData { store.load()?.let(::publish) }
            .onFailure { logger.error("Warframe 词库本地快照无效，等待手动更新", it) }
        if (!ready) logger.warn(UNAVAILABLE_MESSAGE)
    }

    /**
     * 检查远端差异及本地文件完整性，不应用新版本；与更新操作互斥。
     */
    @Synchronized
    fun check(): ExportCheck = store.check()

    /**
     * 先完成磁盘快照的校验与发布，再切换内存索引。
     * 任何发布前的失败都会向调用方抛出，当前内存索引保持可用。
     */
    @Synchronized
    fun update(): ExportCheck {
        val (check, snapshot) = store.update(applied)
        if (snapshot !== applied) publish(snapshot)
        return check
    }

    /**
     * 取得当前已应用索引；未初始化时抛出统一异常，避免把缺词库误当成缺译文。
     */
    fun index(): PublicExportIndex = applied?.index ?: throw PublicExportUnavailableException()

    /**
     * 官方译名优先，缺项再使用本地突击修正和补充词库。
     */
    fun name(key: String, locale: String = "zh"): String {
        val current = index()
        return current.officialField(key, "name", locale)
            ?: (if (locale == "zh") sortieTranslations.name(key) else null) ?: current.name(key, locale)
    }

    fun info(key: String): Info = Info(value = name(key), desc = description(key))

    /**
     * 官方说明优先，缺项再使用本地突击修正和补充词库；未提供说明时返回 null。
     */
    fun description(key: String): String? {
        val current = index()
        return current.officialField(key, "description") ?: sortieTranslations.description(key) ?: current.description(
            key
        )
    }

    /**
     * 派系字段沿用紧凑简称；未匹配的派系仍通过统一索引解析。
     */
    fun faction(key: String): String {
        val current = index()
        return FactionDisplayNames.name(key) ?: current.name(key)
    }

    fun node(id: String): Nodes = index().node(id, ::faction)

    /**
     * 科研偏差、风险和额外变量使用官方字段，缺项由本地独立词库补齐。
     */
    fun conquestInfo(key: String): Info {
        return supplementInfo(key, conquestTranslations.info(key))
    }

    /**
     * 日历增幅优先使用官方译文，缺项由本地独立词库补齐。
     */
    fun calendarUpgradeInfo(key: String): Info {
        return supplementInfo(key, calendarTranslations.info(key))
    }

    /** 双衍轮换使用官方名称，本地词库补齐未导出的情绪、伤害类型、NPC 和地点。 */
    fun spiralName(key: String): String {
        val current = index()
        return current.officialField(key, "name") ?: spiralTranslations.info(key)?.value ?: current.name(key)
    }

    /** 本地专用修正按字段补缺，已有官方译文时不覆盖。 */
    private fun supplementInfo(key: String, supplemental: Info?): Info {
        val current = index()
        return Info(
            value = current.officialField(key, "name") ?: supplemental?.value ?: name(key),
            desc = current.officialField(key, "description") ?: supplemental?.desc ?: description(key),
        )
    }

    /**
     * 在业务缓存键后附加翻译版本；旧译文缓存不再命中，无需清空整库。
     */
    fun cacheKey(base: String): String = "$base:${cacheTag()}"

    /**
     * 组合解析规则版本、多源快照版本和本地补充词库指纹，可用于 Redis 键和图片文件名。
     */
    fun cacheTag(): String {
        val snapshot = applied ?: throw PublicExportUnavailableException()
        return "${PublicExportPolicy.TRANSLATION_CACHE_VERSION}-${snapshot.manifest.appliedCommit}-local-$localRevision"
    }

    /**
     * 按游戏对象路径或英文名称生成紫卡、玄骸武器展示名，保留交易协议使用的 ID 和 slug。
     * 套装单独匹配；缺失译名时保留英文名称或标识，并记录缺失项。
     */
    fun marketName(gameRef: String?, englishName: String?, slug: String?, locale: String = "zh"): String {
        val current = index()
        // Market 套装代表交易组合，不能直接采用 gameRef 指向的单件物品名称。
        if (englishName?.endsWith(" Set", ignoreCase = true) == true) {
            return current.field(englishName, "name", locale) ?: englishName.also {
                recordMissing(
                    slug ?: it,
                    locale,
                    "market-set"
                )
            }
        }
        gameRef?.takeIf { it.isNotBlank() }?.let { current.field(it, "name", locale)?.let { value -> return value } }
        englishName?.takeIf { it.isNotBlank() }
            ?.let { current.field(it, "name", locale)?.let { value -> return value } }
        return (englishName?.takeIf { it.isNotBlank() } ?: gameRef ?: slug.orEmpty()).also {
            recordMissing(gameRef ?: slug ?: it, locale, "market")
        }
    }

    fun localize(item: WfRivenEntity): WfRivenEntity = item.copy(
        zhName = marketName(null, item.enName, item.urlName)
    )

    /**
     * 为新索引挂接缺失项日志，并一次性替换可见快照；每次发布重新统计缺失项。
     */
    private fun publish(snapshot: AppliedExport) {
        missing.clear()
        snapshot.index.onMissing = ::recordMissing
        applied = snapshot
        if (snapshot.manifest.files.none { PublicExportStore.acceptsOfficial(it.path) }) {
            logger.warn("当前快照尚未包含官方对象导出，请执行“更新世界状态词库”切换到官方优先翻译")
        }
        if (!snapshot.manifest.files.map { it.path }.containsAll(PublicExportStore.languageFiles)) {
            logger.warn("当前快照尚未包含完整游戏语言表，请执行“更新世界状态词库”补齐")
        }
        logger.info(
            "世界状态词库已应用：{}，{} 个文件",
            snapshot.manifest.appliedCommit,
            snapshot.manifest.files.size
        )
    }

    /**
     * 按限定的标识范围加载补充词库；格式错误时记录原因并回退为空词库。
     */
    private fun loadInfoTranslations(path: String, accepts: (String) -> Boolean): LocalInfoTranslations =
        recoverExportData { LocalInfoTranslations.load(Path.of(path), accepts) }
            .onFailure { logger.warn("本地世界状态词库加载失败：{}，使用统一索引或原始标识；原因：{}", path, it.message) }
            .getOrDefault(LocalInfoTranslations.EMPTY)

    private fun recordMissing(key: String, locale: String, field: String) {
        // 同一版本的相同缺失项只记录一次，并限制总量，避免外部标识撑满内存或日志。
        val entry = "$locale:$field:$key"
        if (missing.size < 5000 && missing.add(entry)) logger.warn(
            "Warframe 词库缺失翻译 [{}] {}",
            revision,
            entry
        )
    }

    companion object {
        private val CONQUEST_KEY_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*")
        const val UNAVAILABLE_MESSAGE = "Warframe 词库尚未初始化，请管理员执行“更新世界状态词库”"
    }
}

class PublicExportUnavailableException : IllegalStateException(PublicExportService.UNAVAILABLE_MESSAGE)
