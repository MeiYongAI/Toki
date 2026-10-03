package io.github.meiyongai.toki.hook

/** 将宿主 Aweme 模型转换为纯策略事实；所有成员在构造阶段验证，运行阶段不猜测字段。 */
internal class FeedModelReader(loader: ClassLoader) {
    val awemeType: Class<*> = loader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
    private val isAd = awemeType.getMethod("isAd")
    private val isSoftAd = awemeType.getMethod("isSoftAd")
    private val type = awemeType.getMethod("getAwemeType")
    private val card = awemeType.getMethod("getCardInsertInfo")
    private val cardType = card.returnType.getMethod("getCardType")
    private val lynxTemplate = card.returnType.getMethod("getLynxTemplate")
    private val mainChannel = lynxTemplate.returnType.getMethod("getMainEntranceChannel")
    private val mainUrl = lynxTemplate.returnType.getMethod("getMainEntranceLynxUrl")
    private val bizChannel = lynxTemplate.returnType.getMethod("getBizChannel")
    private val bizUrl = lynxTemplate.returnType.getMethod("getBizDynamicUrl")
    private val reportedTopicKinds = mutableSetOf<String>()
    private val replay = awemeType.getMethod("isLiveReplay")
    private val aigcInfo = awemeType.getField("aigcInfo")
    private val aiLabelType = aigcInfo.type.getMethod("getAIGCLabelType")
    private val aiModeration = awemeType.getMethod("getModerationAigcInfo")
    private val aiCreatorSegment = aiModeration.returnType.getField("moderationCreatorSegment")
    private val offline = loader.loadClass("com.ss.android.ugc.aweme.feed.model.AwemeExtKt")
        .getMethod("isOfflineVideo", awemeType)
    private val statistics = awemeType.getMethod("getStatistics")
    private val views = statistics.returnType.getMethod("getPlayCount")
    private val likes = statistics.returnType.getMethod("getDiggCount")
    private val video = awemeType.getMethod("getVideo")
    private val duration = video.returnType.getMethod("getDuration")
    private val realDuration = video.returnType.getMethod("getPilotLength")
    private val longVideos = awemeType.getMethod("getLongVideos")
    private val longVideo = loader.loadClass("com.ss.android.ugc.aweme.feed.model.LongVideo").getMethod("getVideo")
    private val description = awemeType.getMethod("getDesc")
    private val contentDescription = awemeType.getMethod("getContentDesc")
    private val contentExtras = awemeType.getMethod("getContentDescExtra")
    private val extras = awemeType.getMethod("getTextExtra")
    private val hashtag = loader.loadClass("com.ss.android.ugc.aweme.model.TextExtraStruct").getMethod("getHashTagName")
    private val author = awemeType.getMethod("getAuthor")
    private val nickname = author.returnType.getMethod("getNickname")
    private val handle = author.returnType.getMethod("getUniqueId")

    /**
     * 从宿主条目读取过滤事实；卡片载荷独立于条目类型，null 字段表示未提供。
     * @param aweme 已反序列化的 Aweme。
     * @param scope 内容来源分类；推荐页以外只读取广告标记。
     * @return 广告事实，以及推荐页需要的类型、模板、AI 标签、统计、时长和分类文本。
     * Callers: FeedFilterHook.filter、FeedFilterHook.hookAdapter、FeedFilterHook.hookRepositories、FeedFilterHook.hookSharedModelAds。
     */
    fun read(aweme: Any, scope: FeedScope): FeedEntry {
        require(awemeType.isInstance(aweme)) { "信息流包含非 Aweme 条目: ${aweme.javaClass.name}" }
        val ad = isAd.invoke(aweme) as Boolean || isSoftAd.invoke(aweme) as Boolean
        if (scope != FeedScope.FOR_YOU) return FeedEntry(ad = ad)
        val itemType = type.invoke(aweme) as Int
        val cardInfo = card.invoke(aweme)
        val template = if (cardInfo == null) null else lynxTemplate.invoke(cardInfo)
        val stats = statistics.invoke(aweme)
        val aiInfo = aigcInfo.get(aweme)
        val aiModerationInfo = if (aiInfo == null) null else aiModeration.invoke(aweme)
        val user = author.invoke(aweme)
        var durationMs = videoDuration(video.invoke(aweme))
        for (item in (longVideos.invoke(aweme) as List<*>?).orEmpty()) {
            if (item != null) durationMs = maxOf(durationMs, videoDuration(longVideo.invoke(item)))
        }
        val tags = ((extras.invoke(aweme) as List<*>?).orEmpty() +
            (contentExtras.invoke(aweme) as List<*>?).orEmpty()).mapNotNull { extra ->
            if (extra == null) null else hashtag.invoke(extra) as String?
        }
        val authors = if (user == null) emptyList() else
            listOfNotNull(nickname.invoke(user) as String?, handle.invoke(user) as String?)
        val entry = FeedEntry(
            awemeType = itemType,
            cardType = if (cardInfo == null) null else cardType.invoke(cardInfo) as Int,
            cardTemplates = if (template == null) emptyList() else listOfNotNull(
                FeedCardTemplate.read(mainChannel.invoke(template) as String?, mainUrl.invoke(template) as String?),
                FeedCardTemplate.read(bizChannel.invoke(template) as String?, bizUrl.invoke(template) as String?)
            ).distinct(),
            ad = ad,
            live = itemType == 101 || replay.invoke(aweme) as Boolean,
            photo = itemType == 150,
            offline = offline.invoke(null, aweme) as Boolean,
            aiLabelType = if (aiInfo == null) 0 else aiLabelType.invoke(aiInfo) as Int,
            aiCreatorSegment = if (aiModerationInfo == null) null else aiCreatorSegment.get(aiModerationInfo) as String?,
            views = if (stats == null) 0 else (views.invoke(stats) as Number).toLong(),
            likes = if (stats == null) 0 else (likes.invoke(stats) as Number).toLong(),
            durationMs = durationMs,
            description = (description.invoke(aweme) as String?).orEmpty(),
            contentDescription = (contentDescription.invoke(aweme) as String?).orEmpty(),
            tags = tags,
            authors = authors
        )
        val kind = FeedTopicKind.classify(entry)
        if (kind != null) {
            val identity = "kind=$kind awemeType=$itemType cardType=${entry.cardType}"
            val first = synchronized(reportedTopicKinds) { reportedTopicKinds.add(identity) }
            if (first) HookRuntime.event("TokiFeedCard", identity)
        }
        return entry
    }

    /**
     * 读取毫秒长度和 real_duration，保留完整内容时长，不进行秒数截断。
     * @param value Video 模型；null 表示无视频载荷。
     * @return 两个已提供时长的最大非负值。
     * Callers: read。
     */
    private fun videoDuration(value: Any?): Long = if (value == null) 0 else maxOf(
        0, (duration.invoke(value) as Number).toLong(), (realDuration.invoke(value) as Number).toLong()
    )
}
