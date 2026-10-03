package io.github.meiyongai.toki.hook

import io.github.meiyongai.toki.model.KeywordFilterConfig

/** 内容来源分类；推荐内容规则只作用于 FOR_YOU，广告规则独立于页面分类。 */
internal enum class FeedScope {
    FOR_YOU, OTHER_FEED, OUTSIDE_FEED;

    companion object {
        /**
         * 将网络请求类型转换为过滤作用域。
         * @param feedType 请求参数 LIZ；0 为推荐、1 为关注。
         * @return 推荐流或其他信息流。
         * Callers: FeedFilterHook.hookNetwork。
         */
        fun fromFeedType(feedType: Int): FeedScope =
            if (feedType == 0) FOR_YOU else OTHER_FEED

        /**
         * 将 Adapter 的页面类型转换为内容来源分类；个人主页不进入推荐内容规则。
         * @param eventType BaseFeedPageParams.getEventType()。
         * @return 明确识别的页面作用域。
         * Callers: FeedFilterHook.hookAdapter。
         */
        fun fromEventType(eventType: String?): FeedScope = when (eventType) {
            "homepage_hot" -> FOR_YOU
            "homepage_follow", "homepage_friends", "homepage_nearby", "homepage_long_video",
            "homepage_popular", "homepage_now", "homepage_explore", "homepage_series",
            "general_search", "challenge_from" -> OTHER_FEED
            else -> OUTSIDE_FEED
        }
    }
}

/** 用于纯策略判断的条目数据；直播状态来自内容类型，不能来自作者是否开播。 */
internal data class FeedEntry(
    val awemeType: Int = 0,
    val cardType: Int? = null,
    val cardTemplates: List<FeedCardTemplate> = emptyList(),
    val ad: Boolean = false,
    val live: Boolean = false,
    val photo: Boolean = false,
    val offline: Boolean = false,
    val aiLabelType: Int = 0,
    val aiCreatorSegment: String? = null,
    val views: Long = 0,
    val likes: Long = 0,
    val durationMs: Long = 0,
    val description: String = "",
    val contentDescription: String = "",
    val tags: List<String> = emptyList(),
    val authors: List<String> = emptyList()
)

/** 用户指定的闭区间；0 表示未启用该边界。 */
internal data class FeedRange(val min: Long = 0, val max: Long = 0) {
    /**
     * 判断整数值是否越界，不对视频毫秒数执行截断。
     * @param value 待比较数值。
     * @return true 表示超出已启用边界。
     * Callers: FeedFilterPolicy.reject。
     */
    fun excludes(value: Long): Boolean = (min > 0 && value < min) || (max > 0 && value > max)

    companion object {
        /**
         * 将非负秒数转换为毫秒，超大上限按 Long 可表示的最大时长比较。
         * @param seconds 用户输入的秒数。
         * @return 不溢出的毫秒值。
         * Callers: FeedFilterHook.policy、单元测试。
         */
        fun milliseconds(seconds: Long): Long =
            if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
    }
}

/** 网络、缓存和 Adapter 统一使用的纯函数过滤策略。 */
internal data class FeedFilterPolicy(
    val removeAds: Boolean = false,
    val removeLive: Boolean = false,
    val removePhoto: Boolean = false,
    val removeOffline: Boolean = false,
    val removeAiGenerated: Boolean = false,
    val removeTopicRecommendations: Boolean = false,
    val removeCreatorRecommendations: Boolean = false,
    val views: FeedRange = FeedRange(),
    val likes: FeedRange = FeedRange(),
    val duration: FeedRange = FeedRange(),
    val keywords: KeywordFilterConfig = KeywordFilterConfig()
) {
    /**
     * 判断当前页面是否需要过滤：广告开关对所有页面生效，内容规则仅对推荐页生效。
     * @param scope 数据来源分类；作者主页及未知页面也参与广告过滤。
     * @return 是否需要读取条目并执行过滤。
     * Callers: FeedFilterHook.filter、FeedFilterHook.hookAdapter。
     */
    fun applies(scope: FeedScope): Boolean = removeAds || (scope == FeedScope.FOR_YOU &&
            (removeLive || removePhoto || removeOffline || removeAiGenerated || removeTopicRecommendations ||
                removeCreatorRecommendations || views != FeedRange() ||
                likes != FeedRange() || duration != FeedRange() || keywords.totalCount > 0))

    /**
     * 在数据准入时判断是否拒绝条目。
     * @param entry 从宿主模型读取的完整事实。
     * @param scope 来源作用域。
     * @return true 表示不得进入当前信息流。
     * Callers: FeedFilterHook.filter、FeedFilterHook.hookAdapter、单元测试。
     */
    fun reject(entry: FeedEntry, scope: FeedScope): Boolean {
        return reason(entry, scope) != null
    }

    /**
     * 先判断跨页面广告规则，再判断仅限推荐页的内容规则，返回首次命中的原因。
     * @param entry 完整条目事实。
     * @param scope 数据作用域。
     * @return 固定拒绝原因；null 表示允许。
     * Callers: reject、FeedFilterHook。
     */
    fun reason(entry: FeedEntry, scope: FeedScope): String? {
        if (removeAds && entry.ad) return "广告"
        if (scope != FeedScope.FOR_YOU) return null
        return when {
            removeTopicRecommendations && FeedTopicKind.classify(entry) != null -> "话题推荐"
            // 与宿主用户推荐卡的选择条件一致：本地条目类型 4004，或服务端卡片类型 49。
            removeCreatorRecommendations && (entry.awemeType == 4004 || entry.cardType == 49) -> "创作者推荐"
            removeLive && entry.live -> "直播"
            removePhoto && entry.photo -> "图文"
            removeOffline && entry.offline -> "离线"
            // AI 标签属于内容条目，普通视频与照片轮播使用相同标记。
            removeAiGenerated && !entry.live &&
                (entry.aiLabelType in 1..2 || entry.aiCreatorSegment == "short_drama_aigc") -> "AI生成内容"
            views.excludes(entry.views) -> "浏览数"
            likes.excludes(entry.likes) -> "点赞数"
            !entry.photo && !entry.live && duration != FeedRange() && entry.durationMs <= 0 -> "时长未提供"
            !entry.photo && duration.excludes(entry.durationMs) -> "视频时长"
            matches(listOf(entry.description, entry.contentDescription), keywords.desc, "") -> "文案关键词"
            matches(entry.tags, keywords.tag, "#") -> "标签关键词"
            matches(entry.authors, keywords.author, "@") -> "作者关键词"
            else -> null
        }
    }

    /**
     * 按所属分类进行不区分大小写的关键词匹配。
     * @param values 当前分类的宿主文本。
     * @param words 用户配置词条。
     * @param prefix 标签或作者的可选前缀。
     * @return 是否命中非空关键词。
     * Callers: reject。
     */
    private fun matches(values: List<String>, words: List<String>, prefix: String): Boolean =
        words.any { raw ->
            val word = raw.trim().removePrefix(prefix).trim()
            word.isNotEmpty() && values.any { it.contains(word, ignoreCase = true) }
        }

    /**
     * 复制筛选列表，保持输入列表、顺序及条目身份不变；允许输出为空。
     * @param items 只读或可写的输入列表。
     * @param scope 数据作用域。
     * @param read 将宿主对象转换为策略数据的函数。
     * @return 新的可写列表，绝不回填被拒绝内容。
     * Callers: FeedFilterHook.filter、单元测试。
     */
    fun <T : Any> filter(items: List<T>, scope: FeedScope, read: (T) -> FeedEntry): ArrayList<T> =
        items.filterTo(ArrayList(items.size)) { !reject(read(it), scope) }
}
