package io.github.meiyongai.toki.hook

import io.github.meiyongai.toki.model.KeywordFilterConfig
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

/** 验证过滤策略、作用域及列表输入契约，不依赖 Android 或 TikTok 运行环境。 */
class FeedFilterPolicyTest {
    /**
     * 验证 TikTok 的 AI 短剧专用标记适用于视频和照片，不依赖普通标签枚举。
     * 无入参；同时验证推荐作用域和禁用状态。
     * @return Unit；判定不符合要求时由 JUnit 报告失败。
     * Callers: JUnit 测试运行器。
     */
    @Test fun explicitlyMarkedAiDramaIsRejected() {
        val rules = FeedFilterPolicy(removeAiGenerated = true)
        val entry = FeedEntry(aiCreatorSegment = "short_drama_aigc")
        assertEquals("AI生成内容", rules.reason(entry, FeedScope.FOR_YOU))
        assertTrue(rules.reject(entry.copy(photo = true), FeedScope.FOR_YOU))
        assertFalse(rules.reject(entry, FeedScope.OTHER_FEED))
        assertFalse(rules.copy(removeAiGenerated = false).reject(entry, FeedScope.FOR_YOU))
    }

    /** 验证其他审核分类和文本提及不被猜测为AI标记。@return Unit。Callers: JUnit。 */
    @Test fun unmarkedCreatorSegmentsArePreserved() {
        val rules = FeedFilterPolicy(removeAiGenerated = true)
        for (segment in listOf(null, "", "short_drama", "SHORT_DRAMA_AIGC", "other")) {
            assertFalse(rules.reject(FeedEntry(aiCreatorSegment = segment, description = "AI generated"), FeedScope.FOR_YOU))
        }
    }

    /**
     * 验证创作者标签和自动标签对视频与照片均生效，未标记及未知枚举保持允许。
     * 无入参；遍历两种内容形式和明确标签边界。
     * @return Unit；判定不符合要求时由 JUnit 报告失败。
     * Callers: JUnit 测试运行器。
     */
    @Test fun explicitAiLabelsApplyToVideosAndPhotos() {
        val rules = FeedFilterPolicy(removeAiGenerated = true)
        assertTrue(rules.applies(FeedScope.FOR_YOU))
        for (photo in listOf(false, true)) {
            for (label in listOf(1, 2)) {
                val entry = FeedEntry(photo = photo, aiLabelType = label)
                assertEquals("AI生成内容", rules.reason(entry, FeedScope.FOR_YOU))
            }
            for (label in listOf(-1, 0, 3, 100)) {
                val entry = FeedEntry(photo = photo, aiLabelType = label, description = "AI generated")
                assertNull(rules.reason(entry, FeedScope.FOR_YOU))
            }
        }
    }

    /**
     * 验证 AI 规则受推荐作用域及开关控制，直播保持不受影响。
     * 无入参；同时检查普通视频和照片轮播。
     * @return Unit；作用域或开关行为不符合要求时由 JUnit 报告失败。
     * Callers: JUnit 测试运行器。
     */
    @Test fun aiRulePreservesScopeAndContentType() {
        val rules = FeedFilterPolicy(removeAiGenerated = true)
        for (photo in listOf(false, true)) {
            val entry = FeedEntry(photo = photo, aiLabelType = 2)
            assertTrue(rules.reject(entry, FeedScope.FOR_YOU))
            assertFalse(rules.reject(entry, FeedScope.OTHER_FEED))
            assertFalse(rules.reject(entry, FeedScope.OUTSIDE_FEED))
            assertFalse(rules.copy(removeAiGenerated = false).reject(entry, FeedScope.FOR_YOU))
        }
        assertFalse(rules.reject(FeedEntry(live = true, aiLabelType = 2), FeedScope.FOR_YOU))
    }

    /**
     * 验证仅启用 AI 过滤时移除已加载的 AI 照片，保留普通照片及视频顺序。
     * 无入参；使用同一列表模拟配置热更新与后续内容准入。
     * @return Unit；列表内容、顺序或开关行为不符合要求时由 JUnit 报告失败。
     * Callers: JUnit 测试运行器。
     */
    @Test fun aiToggleRescreensMixedPhotoAndVideoList() {
        val source = listOf(
            FeedEntry(photo = true, description = "ordinary photo"),
            FeedEntry(photo = true, aiLabelType = 1),
            FeedEntry(durationMs = 45000),
            FeedEntry(photo = true, aiLabelType = 2),
            FeedEntry(durationMs = 45000, aiLabelType = 1)
        )
        val disabled = FeedFilterPolicy()
        val loaded = disabled.filter(source, FeedScope.FOR_YOU) { it }
        assertEquals(source, loaded)
        val enabled = disabled.copy(removeAiGenerated = true)
        assertFalse(enabled.removePhoto)
        assertEquals(listOf(source[0], source[2]), enabled.filter(loaded, FeedScope.FOR_YOU) { it })
        assertEquals(source, loaded)
        assertEquals(source, disabled.filter(source, FeedScope.FOR_YOU) { it })
    }

    /**
     * 验证 AI 照片过滤与普通照片过滤、视频时长限制可以独立组合。
     * 无入参；照片无需具备视频时长，AI 照片仍由 AI 标记拒绝。
     * @return Unit；独立开关或组合规则不符合要求时由 JUnit 报告失败。
     * Callers: JUnit 测试运行器。
     */
    @Test fun aiPhotoFilteringIsIndependentOfPhotoAndDurationRules() {
        val rules = FeedFilterPolicy(removeAiGenerated = true, duration = FeedRange(40000, 50000))
        val photo = FeedEntry(photo = true)
        assertNull(rules.reason(photo, FeedScope.FOR_YOU))
        assertEquals("AI生成内容", rules.reason(photo.copy(aiLabelType = 1), FeedScope.FOR_YOU))
        assertEquals("图文", rules.copy(removePhoto = true).reason(photo, FeedScope.FOR_YOU))
        assertNull(rules.reason(FeedEntry(durationMs = 45000), FeedScope.FOR_YOU))
        assertEquals("视频时长", rules.reason(FeedEntry(durationMs = 60000), FeedScope.FOR_YOU))
    }

    /** 验证AI开关热更新可对同一已加载列表重筛，不改变时长和离线规则。@return Unit。Callers: JUnit。 */
    @Test fun aiToggleRechecksLoadedItemsIndependently() {
        val source = listOf(FeedEntry(durationMs = 45000), FeedEntry(durationMs = 45000, aiLabelType = 1),
            FeedEntry(durationMs = 45000, offline = true), FeedEntry(durationMs = 60000))
        val rules = FeedFilterPolicy(removeOffline = true, duration = FeedRange(40000, 50000))
        assertEquals(source.take(2), rules.filter(source, FeedScope.FOR_YOU) { it })
        assertEquals(source.take(1), rules.copy(removeAiGenerated = true).filter(source, FeedScope.FOR_YOU) { it })
    }

    /** 验证 30～60 秒已加载列表改为 40～50 秒时，端点保留且两侧内容均移除。@return Unit。Callers: JUnit。 */
    @Test fun narrowedRangeRechecksEveryLoadedItem() {
        val source = listOf(30000L, 39999L, 40000L, 45000L, 50000L, 50001L, 60000L)
            .map { FeedEntry(durationMs = it) }
        val loaded = FeedFilterPolicy(duration = FeedRange(30000, 60000))
            .filter(source, FeedScope.FOR_YOU) { it }
        val updated = FeedFilterPolicy(duration = FeedRange(40000, 50000))
            .filter(loaded, FeedScope.FOR_YOU) { it }
        assertEquals(listOf(40000L, 45000L, 50000L), updated.map { it.durationMs })
        assertEquals(7, loaded.size)
    }

    /** 验证离线插入即使满足时长，也由同一数据准入策略拒绝。@return Unit。Callers: JUnit。 */
    @Test fun offlineInsertionCannotPassByMeetingDuration() {
        val source = listOf(FeedEntry(durationMs = 45000, offline = true), FeedEntry(durationMs = 45000))
        val rules = FeedFilterPolicy(removeOffline = true, duration = FeedRange(40000, 50000))
        assertEquals(listOf(source[1]), rules.filter(source, FeedScope.FOR_YOU) { it })
        assertEquals(source, rules.copy(removeOffline = false).filter(source, FeedScope.FOR_YOU) { it })
    }

    /** 验证窄区间整批拒绝时不恢复缓存内容，关闭区间后允许后续新批次。@return Unit。Callers: JUnit。 */
    @Test fun emptyNarrowRangeBatchIsNotRepopulated() {
        val source = listOf(FeedEntry(durationMs = 39999), FeedEntry(durationMs = 50001))
        assertTrue(FeedFilterPolicy(duration = FeedRange(40000, 50000))
            .filter(source, FeedScope.FOR_YOU) { it }.isEmpty())
        assertEquals(source, FeedFilterPolicy().filter(source, FeedScope.FOR_YOU) { it })
    }

    /** 验证完整文案同样参与文案过滤。@return Unit。Callers: JUnit。 */
    @Test fun contentDescriptionIsFiltered() {
        val rules = FeedFilterPolicy(keywords = KeywordFilterConfig(desc = listOf("Minecraft")))
        assertEquals("文案关键词", rules.reason(FeedEntry(contentDescription = "new MINECRAFT"), FeedScope.FOR_YOU))
        assertNull(rules.reason(FeedEntry(tags = listOf("Minecraft")), FeedScope.FOR_YOU))
    }

    /** 验证时长诊断对应真正越界的规则，闭区间端点仍允许。@return Unit。Callers: JUnit。 */
    @Test fun durationReasonMatchesBounds() {
        val rules = FeedFilterPolicy(duration = FeedRange(30000, 60000))
        for (value in listOf(29999L, 60001L, 1211134L)) {
            assertEquals("视频时长", rules.reason(FeedEntry(durationMs = value), FeedScope.FOR_YOU))
        }
        for (value in listOf(30000L, 60000L)) assertNull(rules.reason(FeedEntry(durationMs = value), FeedScope.FOR_YOU))
        assertNull(rules.reason(FeedEntry(durationMs = 1211134L), FeedScope.OTHER_FEED))
    }

    /** 验证缺少时长的视频不能被判定满足已启用区间。@return Unit。Callers: JUnit。 */
    @Test fun missingDurationIsNotAssumedToBeWithinRange() {
        assertEquals("时长未提供", FeedFilterPolicy(duration = FeedRange(max = 60000))
            .reason(FeedEntry(), FeedScope.FOR_YOU))
        assertNull(FeedFilterPolicy().reason(FeedEntry(), FeedScope.FOR_YOU))
    }

    /** 验证配置变更使用新策略可筛除已读取的目标。@return Unit。Callers: JUnit。 */
    @Test fun changedPolicyRescreensSource() {
        val source = listOf(FeedEntry(durationMs = 45000), FeedEntry(durationMs = 60001))
        val initial = FeedFilterPolicy().filter(source, FeedScope.FOR_YOU) { it }
        val updated = FeedFilterPolicy(duration = FeedRange(30000, 60000)).filter(initial, FeedScope.FOR_YOU) { it }
        assertEquals(listOf(source[0]), updated)
        assertEquals(2, initial.size)
    }
    /** 验证关注流不进入推荐规则。@return Unit。Callers: JUnit。 */
    @Test fun followIsNotForYou() {
        assertEquals(FeedScope.FOR_YOU, FeedScope.fromFeedType(0))
        assertEquals(FeedScope.OTHER_FEED, FeedScope.fromFeedType(1))
        assertFalse(FeedFilterPolicy(removeLive = true).reject(FeedEntry(live = true), FeedScope.fromFeedType(1)))
    }

    /**
     * 验证广告过滤覆盖作者主页、已识别页面及未识别页面，并保持开关独立。
     * 无入参；页面分类只控制推荐内容规则，不豁免广告。
     * @return Unit；作用范围或开关行为不符合要求时由 JUnit 报告失败。
     * Callers: JUnit 测试运行器。
     */
    @Test fun adsApplyIndependentlyOfPageClassification() {
        val enabled = FeedFilterPolicy(removeAds = true)
        val disabled = enabled.copy(removeAds = false)
        for (page in listOf("homepage_hot", "homepage_follow", "homepage_friends", "general_search",
            "challenge_from", "personal_homepage", "unknown", "", null)) {
            val scope = FeedScope.fromEventType(page)
            assertTrue("广告规则未启用: $page", enabled.applies(scope))
            assertEquals("广告", enabled.reason(FeedEntry(ad = true), scope))
            assertNull(enabled.reason(FeedEntry(), scope))
            assertFalse(disabled.applies(scope))
            assertNull(disabled.reason(FeedEntry(ad = true), scope))
        }
    }

    /**
     * 验证每项推荐内容规则在广告开关启用或禁用时都不影响作者主页等其他页面。
     * 无入参；各内容条目在推荐页必须命中，在其他页面只有广告可以被拒绝。
     * @return Unit；内容规则的范围发生扩展时由 JUnit 报告失败。
     * Callers: JUnit 测试运行器。
     */
    @Test fun recommendationRulesStayScopedWhenAdsAreGlobal() {
        val cases = listOf(
            FeedFilterPolicy(removeLive = true) to FeedEntry(live = true),
            FeedFilterPolicy(removePhoto = true) to FeedEntry(photo = true),
            FeedFilterPolicy(removeOffline = true) to FeedEntry(offline = true),
            FeedFilterPolicy(removeAiGenerated = true) to FeedEntry(aiLabelType = 1, photo = true),
            FeedFilterPolicy(removeTopicRecommendations = true) to FeedEntry(awemeType = 105, cardType = 38),
            FeedFilterPolicy(removeCreatorRecommendations = true) to FeedEntry(awemeType = 4004),
            FeedFilterPolicy(views = FeedRange(min = 100)) to FeedEntry(views = 10),
            FeedFilterPolicy(likes = FeedRange(min = 100)) to FeedEntry(likes = 10),
            FeedFilterPolicy(duration = FeedRange(40000, 50000)) to FeedEntry(durationMs = 60000),
            FeedFilterPolicy(keywords = KeywordFilterConfig(desc = listOf("blocked"))) to
                FeedEntry(description = "blocked"),
            FeedFilterPolicy(keywords = KeywordFilterConfig(tag = listOf("blocked"))) to
                FeedEntry(tags = listOf("blocked")),
            FeedFilterPolicy(keywords = KeywordFilterConfig(author = listOf("blocked"))) to
                FeedEntry(authors = listOf("blocked"))
        )
        for ((contentRules, entry) in cases) {
            for (removeAds in listOf(false, true)) {
                val rules = contentRules.copy(removeAds = removeAds)
                assertTrue(rules.applies(FeedScope.FOR_YOU))
                assertTrue(rules.reject(entry, FeedScope.FOR_YOU))
                for (scope in listOf(FeedScope.OTHER_FEED, FeedScope.OUTSIDE_FEED)) {
                    assertEquals(removeAds, rules.applies(scope))
                    assertNull(rules.reason(entry, scope))
                    assertEquals(removeAds, rules.reject(entry.copy(ad = true), scope))
                }
            }
        }
    }

    /**
     * 验证作者主页开关重筛、后续广告批次和单条插入共用跨页面广告决策。
     * 无入参；保留普通内容的顺序与身份，输入列表不可修改，整批广告允许清空。
     * @return Unit；重筛或后续准入不符合要求时由 JUnit 报告失败。
     * Callers: JUnit 测试运行器。
     */
    @Test fun profileAdsAreRejectedOnRescreenAndSubsequentAdmission() {
        val scope = FeedScope.fromEventType("personal_homepage")
        val first = FeedEntry(durationMs = 60000)
        val ad = FeedEntry(ad = true)
        val last = FeedEntry(photo = true, aiLabelType = 1, description = "blocked")
        val source = Collections.unmodifiableList(listOf(first, ad, last))
        val disabled = FeedFilterPolicy(removePhoto = true, removeAiGenerated = true,
            duration = FeedRange(40000, 50000), keywords = KeywordFilterConfig(desc = listOf("blocked")))
        assertFalse(disabled.applies(scope))
        assertEquals(source, disabled.filter(source, scope) { it })
        val enabled = disabled.copy(removeAds = true)
        assertTrue(enabled.applies(scope))
        val accepted = enabled.filter(source, scope) { it }
        assertEquals(listOf(first, last), accepted)
        assertSame(first, accepted[0])
        assertSame(last, accepted[1])
        assertEquals(3, source.size)
        assertTrue(enabled.reject(ad, scope))
        assertTrue(enabled.filter(listOf(ad, ad.copy(photo = true)), scope) { it }.isEmpty())
        assertEquals(accepted, enabled.filter(accepted, scope) { it })
        assertEquals(source, disabled.filter(source, scope) { it })
    }

    /** 验证刚超过秒数上限的毫秒值被拒绝。@return Unit。Callers: JUnit。 */
    @Test fun durationDoesNotTruncateMilliseconds() {
        val policy = FeedFilterPolicy(duration = FeedRange(max = FeedRange.milliseconds(600)))
        assertFalse(policy.reject(FeedEntry(durationMs = 600000), FeedScope.FOR_YOU))
        assertTrue(policy.reject(FeedEntry(durationMs = 600001), FeedScope.FOR_YOU))
        assertTrue(policy.reject(FeedEntry(durationMs = 600999), FeedScope.FOR_YOU))
    }

    /** 验证下限按毫秒精确比较。@return Unit。Callers: JUnit。 */
    @Test fun durationMinimumIsInclusive() {
        val policy = FeedFilterPolicy(duration = FeedRange(min = 1000))
        assertTrue(policy.reject(FeedEntry(durationMs = 999), FeedScope.FOR_YOU))
        assertFalse(policy.reject(FeedEntry(durationMs = 1000), FeedScope.FOR_YOU))
    }

    /** 验证时长转换不会因整数溢出而漏滤。@return Unit。Callers: JUnit。 */
    @Test fun durationConversionCannotOverflow() {
        assertEquals(Long.MAX_VALUE, FeedRange.milliseconds(Long.MAX_VALUE))
        assertEquals(0L, FeedRange.milliseconds(0))
    }

    /** 验证图文不被视频时长规则删除。@return Unit。Callers: JUnit。 */
    @Test fun photosDoNotHaveVideoDurationRequirements() {
        val policy = FeedFilterPolicy(duration = FeedRange(min = 10000))
        assertFalse(policy.reject(FeedEntry(photo = true), FeedScope.FOR_YOU))
    }

    /** 验证直播规则只读取条目事实，不关联作者状态。@return Unit。Callers: JUnit。 */
    @Test fun ordinaryVideoIsNotLive() {
        val policy = FeedFilterPolicy(removeLive = true)
        assertFalse(policy.reject(FeedEntry(live = false), FeedScope.FOR_YOU))
        assertTrue(policy.reject(FeedEntry(live = true), FeedScope.FOR_YOU))
    }

    /** 验证浏览数和点赞数的闭区间语义。@return Unit。Callers: JUnit。 */
    @Test fun countBoundsAreInclusive() {
        val policy = FeedFilterPolicy(views = FeedRange(10, 20), likes = FeedRange(1, 3))
        assertFalse(policy.reject(FeedEntry(views = 10, likes = 3), FeedScope.FOR_YOU))
        assertTrue(policy.reject(FeedEntry(views = 9, likes = 3), FeedScope.FOR_YOU))
        assertTrue(policy.reject(FeedEntry(views = 20, likes = 4), FeedScope.FOR_YOU))
    }

    /** 验证关键词分类不会互相匹配。@return Unit。Callers: JUnit。 */
    @Test fun keywordCategoriesAreIndependent() {
        val policy = FeedFilterPolicy(keywords = KeywordFilterConfig(tag = listOf("#Demo")))
        assertFalse(policy.reject(FeedEntry(description = "demo"), FeedScope.FOR_YOU))
        assertTrue(policy.reject(FeedEntry(tags = listOf("DEMO")), FeedScope.FOR_YOU))
    }

    /** 验证作者前缀、空词条和不区分大小写的匹配。@return Unit。Callers: JUnit。 */
    @Test fun authorPrefixAndEmptyKeywordsAreNormalized() {
        val policy = FeedFilterPolicy(keywords = KeywordFilterConfig(author = listOf(" @Creator ", "@")))
        assertTrue(policy.reject(FeedEntry(authors = listOf("creator")), FeedScope.FOR_YOU))
        assertFalse(policy.reject(FeedEntry(authors = listOf("other")), FeedScope.FOR_YOU))
    }

    /** 验证不可修改列表不被原地操作，输出维持顺序和对象身份。@return Unit。Callers: JUnit。 */
    @Test fun immutableInputRemainsUntouched() {
        val first = FeedEntry(views = 10)
        val blocked = FeedEntry(ad = true)
        val last = FeedEntry(views = 20)
        val source = Collections.unmodifiableList(listOf(first, blocked, last))
        val result = FeedFilterPolicy(removeAds = true).filter(source, FeedScope.FOR_YOU) { it }
        assertEquals(3, source.size)
        assertEquals(2, result.size)
        assertSame(first, result[0])
        assertSame(last, result[1])
    }

    /** 验证整批被拒绝时结果为空，不回填目标内容。@return Unit。Callers: JUnit。 */
    @Test fun fullyRejectedPageStaysEmpty() {
        val source = listOf(FeedEntry(ad = true), FeedEntry(ad = true))
        val result = FeedFilterPolicy(removeAds = true).filter(source, FeedScope.FOR_YOU) { it }
        assertTrue(result.isEmpty())
        assertEquals(2, source.size)
    }

    /** 验证离线来源规则只作用于推荐页。@return Unit。Callers: JUnit。 */
    @Test fun offlineItemsAreRejectedOnlyInForYou() {
        val policy = FeedFilterPolicy(removeOffline = true)
        assertTrue(policy.reject(FeedEntry(offline = true), FeedScope.FOR_YOU))
        assertFalse(policy.reject(FeedEntry(offline = true), FeedScope.OTHER_FEED))
    }

    /** 验证重复过滤结果稳定。@return Unit。Callers: JUnit。 */
    @Test fun filteringIsIdempotent() {
        val policy = FeedFilterPolicy(removeAds = true, removeLive = true)
        val once = policy.filter(listOf(FeedEntry(), FeedEntry(ad = true)), FeedScope.FOR_YOU) { it }
        assertEquals(once, policy.filter(once, FeedScope.FOR_YOU) { it })
    }
}
