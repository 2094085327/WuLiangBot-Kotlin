package bot.wuliang.riven.samples

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

/** 紫卡样本与预热配置 */
@Component
@ConfigurationProperties(prefix = "warframe.market.riven-samples")
class RivenSampleProperties {
    var enabled = false // 样本入库、价格参考和维护总开关；关闭后用户查询短缓存仍可用
    var cacheSeconds = 600L // 非空WM查询结果的缓存时长，秒；范围60～3600
    var emptyCacheSeconds = 1800L // 成功空结果的缓存时长，秒；不短于非空缓存，最多86400
    var failureCooldownMinutes = 15L // 同一查询条件失败后的冷却时长，分钟；范围1～1440

    var dailyLimit = 140 // 每UTC日预热与分析补采的总额度；不包含USER
    var analysis = Analysis()
    var referenceMaxAgeHours = 72L // 三类价格参考的观察窗口，小时；范围1～168，与observationMaxHours取较小值
    var startupDelaySeconds = 90L // 应用就绪后首次检查样本清理和预热的延迟，秒；范围0～3600
    var sellerSoftDays = 7L // 卖家未活跃超过此天数时，有效性权重减半；须小于sellerMaxDays
    var sellerMaxDays = 30L // 卖家未活跃超过此天数时，样本无效；最多90天
    var listingSoftDays = 30L // 挂牌创建超过此天数时，有效性权重减半；须小于listingMaxDays
    var listingMaxDays = 90L // 挂牌创建超过此天数时，样本无效；最多365天
    var observationMaxHours = 72L // 距最近观察超过此小时数时，样本无效；范围1～168
    var retentionDays = 180L // 保留天数，样本按最近观察时间清理，查询状态按最近尝试时间清理；不小于listingMaxDays
    var warmup = Warmup() // 每日热门武器预热的子配置
    var maximumReferenceSamples = 2000 // 相似估价和零洗参考各次读取的样本上限，条；范围100～10000

    class Analysis {
        var dailyLimit = 40 // 每UTC日最多补采尝试次数，失败占额
        var refreshHours = 6L // 同条件成功查询的最短复用时间
        var weaponIntervalMinutes = 60L // 同武器两次分析补采的最短间隔
    }

    /** 每日热门预热；只有样本总开关开启时才生效。 */
    class Warmup {
        var enabled = true // 是否启用预热；关闭后仍保留按需查询和样本清理
        var dailyLimit = 100 // 每UTC日最多预热尝试次数，失败占额，北京时间08:00重置
        var intervalSeconds = 300L // 两次预热尝试的最小间隔，秒，共用Redis的实例共享
        var weaponLimit = 50 // 只预热周榜前50把武器，不向全目录补位
        var hotWeaponCount = 10
        var hotRefreshHours = 4L
        var refreshHours = 24L
    }

    @PostConstruct
    fun validate() {
        require(startupDelaySeconds in 0..3600 && cacheSeconds in 60..3600)
        require(emptyCacheSeconds in cacheSeconds..86400 && failureCooldownMinutes in 1..1440)
        require(observationMaxHours in 1..168)
        require(referenceMaxAgeHours in 1..168)
        require(sellerSoftDays in 1 until sellerMaxDays && sellerMaxDays <= 90)
        require(listingSoftDays in 1 until listingMaxDays && listingMaxDays <= 365)
        require(retentionDays >= listingMaxDays)
        require(dailyLimit in 1..140)
        require(analysis.dailyLimit in 1..dailyLimit && warmup.dailyLimit in 1..dailyLimit)
        require(analysis.dailyLimit + warmup.dailyLimit <= dailyLimit)
        require(analysis.refreshHours in 1..24 && analysis.weaponIntervalMinutes in 1..1440)
        require(warmup.intervalSeconds in 30..3600 && warmup.weaponLimit in 1..50)
        require(warmup.hotWeaponCount in 0..warmup.weaponLimit && warmup.hotRefreshHours in 1..24)
        require(warmup.refreshHours in warmup.hotRefreshHours..72)
        require(maximumReferenceSamples in 100..10000)
    }
}