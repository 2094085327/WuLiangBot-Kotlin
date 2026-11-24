package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
import bot.wuliang.redis.RedisService
import bot.wuliang.service.DirectivesService
import bot.wuliang.tencentCos.CosFileServiceImpl
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

private const val HELP_IMAGE_PREFIX = "help-"

// Redis 不保存这两个时间字段。冷缓存和热缓存都沿用热缓存的图片名。
private fun DirectivesEntity.helpImageContent() = copy(createTime = null, updateTime = null)

internal fun List<DirectivesEntity>.helpMenuImageName() = "$HELP_IMAGE_PREFIX${map { it.helpImageContent() }.toMd5()}"
internal fun DirectivesEntity.helpDetailImageName() = "${HELP_IMAGE_PREFIX}detail-$id-${helpImageContent().toMd5()}"

@Component
class DirectivesUtils(
    private val redisService: RedisService,
    private val directivesService: DirectivesService,
    private val cos: CosFileServiceImpl,
) {
    @PostConstruct
    fun initRedisCacheDirectives() {
        getDirectivesList()
    }

    @Scheduled(fixedDelay = 3_600_000)
    fun configureHelpImageExpiration() = cos.ensureLifecycleRule(HELP_IMAGE_PREFIX, 30)

    @Synchronized
    fun getDirectivesList(): List<DirectivesEntity> =
        redisService.getValueTyped<List<DirectivesEntity>>(DIRECTIVES_KEY) ?: loadDirectives()

    private fun loadDirectives(): List<DirectivesEntity> {
        val directives = directivesService.selectDirectivesList(null)
        if (directives.isNotEmpty()) {
            redisService.setValue(DIRECTIVES_KEY, directives)
            logInfo("指令缓存已加载")
        }
        return directives
    }

    @Synchronized
    fun <T> changeDirectives(change: () -> T): T {
        val previous = getDirectivesList()
        try {
            return change()
        } finally {
            // 导入可能只写入了部分数据。按数据库中的最终内容刷新帮助。
            redisService.deleteKey(DIRECTIVES_KEY)
            try {
                val current = loadDirectives()
                if (previous != current) {
                    val obsolete = helpImageNames(previous) - helpImageNames(current)
                    obsolete.forEach { cos.deleteFile(it, "jpeg") }
                }
            } catch (e: Exception) {
                logError("刷新指令缓存或清理旧帮助图失败", e)
            }
        }
    }

    private fun helpImageNames(directives: List<DirectivesEntity>): Set<String> = buildSet {
        add(directives.helpMenuImageName())
        directives.filter { it.id != null && it.enable == 1 }.mapTo(this) { it.helpDetailImageName() }
    }
}