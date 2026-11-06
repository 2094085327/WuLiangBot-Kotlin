package bot.wuliang.riven.samples

/** WM紫卡搜索条件。 */
data class RivenSampleQuery(
    val positive: List<String> = emptyList(), // 必须包含的正面词条标识；空列表表示不限
    val negative: String? = null, // 负面词条标识；null表示不限，none表示无负面
    val directOnly: Boolean = true, // 是否只查直接出售挂牌，排除竞拍
) {
    fun normalized() = copy(positive = positive.distinct().sorted())

    fun params(weapon: String): Map<String, String> {
        return buildMap {
            put("type", "riven")
            put("weapon_url_name", weapon)
            if (directOnly) put("buyout_policy", "direct")
            put("sort_by", "price_asc")
            if (positive.isNotEmpty()) put("positive_stats", positive.distinct().sorted().joinToString(","))
            negative?.let { put("negative_stats", it) }
        }
    }

}

/** 一组查询条件的最近查询状态。 */
data class RivenQueryObservation(
    val queryKey: String = "", // 武器和完整查询条件生成的摘要，用于查询去重
    val weaponSlug: String = "", // WM武器标识
    val queryJson: String = "{}", // 查询条件的JSON
    val source: String = "", // 查询来源：USER用户、ANALYSIS分析、WARMUP预热
    val lastAttemptAt: Long = 0, // 最近尝试查询的时间，毫秒时间戳
    val lastSuccessAt: Long? = null, // 最近成功查询的时间，毫秒时间戳；null表示未成功
    val rawCount: Int = 0, // 最近成功响应中的原始挂牌数
    val storedCount: Int = 0, // 最近成功响应中通过解码校验且按挂牌ID去重后的数量
    val skippedJson: String = "{}", // 按跳过原因统计的数量JSON
    val lastError: String? = null, // 最近查询错误；成功后清空
    val retryAfter: Long = 0, // 失败冷却截止时间，毫秒时间戳；0表示无冷却
)

/** 本地保存的一条直接出售挂牌样本。 */
data class RivenSampleRecord(
    val auctionId: String = "", // WM挂牌ID，样本唯一标识
    val weaponSlug: String = "", // WM武器标识
    val sellerId: String = "", // WM卖家ID，用于统计不同卖家
    val price: Int = 0, // 直接出售价格，单位为白金P
    val rank: Int = 0, // 紫卡等级，范围0～8
    val rerolls: Int = 0, // 洗卡次数，0表示未洗
    val mastery: Int = 0, // 装备这张紫卡要求的精通段位
    val polarity: String = "", // 紫卡极性标识
    val attributesJson: String = "[]", // 规范化词条JSON，包含标识、正负方向和数值
    val rawItemJson: String = "{}", // WM返回的原始紫卡item对象JSON
    val createdAt: Long = 0, // WM挂牌创建时间，毫秒时间戳
    val updatedAt: Long = 0, // WM挂牌最近修改时间，毫秒时间戳
    val sellerLastSeen: Long? = null, // WM卖家最近活跃时间，毫秒时间戳；null表示未知
    val sellerStatus: String = "unknown", // 观察时卖家状态
    val active: Boolean = false, // 观察时挂牌是否公开、可见且尚未关闭或锁定交易
    val firstObservedAt: Long = 0, // 本地首次观察到该挂牌的时间，毫秒时间戳
    val lastObservedAt: Long = 0, // 本地最近观察到该挂牌的时间，毫秒时间戳
)

/** 一次WM响应的解码结果，不是持久化的采集批次。 */
data class RivenSampleBatch(
    val rawCount: Int, // 响应中的原始挂牌数
    val records: List<RivenSampleRecord>, // 通过解码校验且按挂牌ID去重后的样本
    val invalidatedIds: List<String>, // 本次发现不合格、需将已有记录标为失效的挂牌ID
    val skipped: Map<String, Int>, // 跳过原因及各原因对应的挂牌数
)

/** 单条样本的有效性判断。 */
data class RivenSampleEligibility(
    val eligible: Boolean, // 是否符合样本有效性规则
    val weight: Double, // 有效性权重，受挂牌年龄和卖家活跃度影响；不合格时为0
    val reasons: List<String> // 无效原因或提示代码；部分提示不影响有效性
)

/** 分析报告的挂牌参考，附带独立的相似估价和零洗参考。 */
data class RivenMarketReference(
    val status: String, // 挂牌参考状态，如AVAILABLE可用、LIMITED样本少、DISABLED功能关闭
    val sampleCount: Int = 0, // 当前匹配层级的有效挂牌数
    val sellerCount: Int = 0, // 这些挂牌对应的不同卖家数
    val minimum: Int? = null, // 匹配挂牌的最低价，单位为白金P
    val median: Double? = null, // 匹配挂牌的普通中位价，单位为白金P
    val maximum: Int? = null, // 匹配挂牌的最高价，单位为白金P
    val observedAt: Long? = null, // 匹配样本中最早的最近观察时间，毫秒时间戳
    val source: String = "LOCAL", // 匹配流程来源：LOCAL本地、WM远端补查、CACHE缓存补查
    val matchLevel: String = "SAME_RANK", // 等级范围：SAME_RANK同等级，ALL_RANKS不限等级
    val zeroRollReference: RivenZeroRollReference? = null, // 同武器零洗参考，独立于当前词条匹配
    val attributeMatchLevel: String? = null, // 词条层级：EXACT正负面相同，POSITIVE_ONLY仅正面相同
    val valuation: RivenMarketEstimate? = null, // 按方向词条相似度计算的独立估价
)

/** 方向词条相似度加权分位数。 */
data class RivenMarketEstimate(
    val status: String, // 估价状态：可用、低可信度、无样本、词条未知或查询不可用
    val price: Int? = null, // 相似度加权中位价，单位为白金P
    val lower: Int? = null, // 加权25%分位价，单位为白金P
    val upper: Int? = null, // 加权75%分位价，单位为白金P
    val confidence: String = "LOW", // 规则可信度：HIGH高、MEDIUM中、LOW低
    val sampleCount: Int = 0, // 实际参与本次估价的挂牌数
    val sellerCount: Int = 0, // 参与估价的不同卖家数
    val closeSampleCount: Int = 0, // 参与估价且相似度至少75%的挂牌数
    val relaxed: Boolean = false, // 是否因样本不足将相似度门槛从50%放宽到25%
    val minimumSimilarity: Int? = null, // 参与估价的最低实际相似度，百分数，范围0～100
    val observedAt: Long? = null, // 估价样本中最早的最近观察时间，毫秒时间戳
    val excludedOutlierCount: Int = 0, // 候选池中被异常价格过滤排除的挂牌数
    val outlierCheckedGroupCount: Int = 0, // 候选池中实际检查过异常价格的完整正负词条组数
)

/** 独立的同武器未洗挂牌参考。 */
data class RivenZeroRollReference(
    val status: String, // 零洗参考状态，如AVAILABLE可用、LIMITED样本少、NO_COMPARABLE无样本
    val sampleCount: Int = 0, // 同武器有效零洗挂牌数，不限词条和等级
    val sellerCount: Int = 0, // 这些零洗挂牌对应的不同卖家数
    val median: Double? = null, // 零洗挂牌的普通中位价，单位为白金P
    val observedAt: Long? = null, // 零洗样本中最早的最近观察时间，毫秒时间戳
)