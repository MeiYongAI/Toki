package io.github.meiyongai.toki.hook

import io.github.meiyongai.toki.model.KeywordFilterConfig
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

/** 验证推荐插卡协议的精确匹配，不以视频标签、作者或显示文案推断话题推荐。 */
class FeedTopicRecommendationTest {
    /**
     * 原生和 Lynx 卡片中的热门话题、兴趣话题使用同一拒绝原因。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun explicitTopicCardsAreRejected() {
        val policy = FeedFilterPolicy(removeTopicRecommendations = true)
        for (awemeType in listOf(104, 105)) {
            for (cardType in listOf(34, 35, 38)) {
                assertEquals("话题推荐", policy.reason(FeedEntry(awemeType = awemeType, cardType = cardType), FeedScope.FOR_YOU))
            }
        }
    }

    /**
     * 未启用、关注、搜索和作者主页不应用话题推荐规则。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun topicRuleIsOptInAndForYouOnly() {
        val entry = FeedEntry(awemeType = 105, cardType = 38)
        val policy = FeedFilterPolicy(removeTopicRecommendations = true)
        assertFalse(FeedFilterPolicy().applies(FeedScope.FOR_YOU))
        assertFalse(FeedFilterPolicy().reject(entry, FeedScope.FOR_YOU))
        assertTrue(policy.applies(FeedScope.FOR_YOU))
        for (scope in listOf(FeedScope.OTHER_FEED, FeedScope.OUTSIDE_FEED)) {
            assertFalse(policy.applies(scope))
            assertFalse(policy.reject(entry, scope))
        }
    }

    /**
     * 普通视频、图文和直播不会仅因标题或标签包含话题词汇被过滤。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun ordinaryContentAndTagsArePreserved() {
        val policy = FeedFilterPolicy(removeTopicRecommendations = true)
        for (awemeType in listOf(0, 101, 106, 150)) {
            val entry = FeedEntry(awemeType = awemeType, cardType = 34, description = "话题推荐 Suggested topics",
                tags = listOf("trending", "topic", "热门话题"))
            assertFalse(policy.reject(entry, FeedScope.FOR_YOU))
        }
    }

    /**
     * 卡片载荷缺失、未知类别和其他业务卡片均不扩大过滤范围。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun unrelatedAndUnknownCardsArePreserved() {
        val policy = FeedFilterPolicy(removeTopicRecommendations = true)
        for (cardType in listOf(null, -1, 0, 10, 13, 33, 36, 37, 39, 49, 120, 122)) {
            for (awemeType in listOf(104, 105)) {
                assertFalse(policy.reject(FeedEntry(awemeType = awemeType, cardType = cardType), FeedScope.FOR_YOU))
            }
        }
    }

    /**
     * 开启后可重筛已加载条目，关闭后允许新批次；不修改来源列表或普通条目身份。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun togglingRechecksLoadedItemsWithoutMutatingSource() {
        val first = FeedEntry(durationMs = 45000)
        val card = FeedEntry(awemeType = 105, cardType = 35)
        val last = FeedEntry(durationMs = 48000)
        val source = Collections.unmodifiableList(listOf(first, card, last))
        val disabled = FeedFilterPolicy()
        val enabled = disabled.copy(removeTopicRecommendations = true)
        val accepted = enabled.filter(disabled.filter(source, FeedScope.FOR_YOU) { it }, FeedScope.FOR_YOU) { it }
        assertEquals(listOf(first, last), accepted)
        assertSame(first, accepted[0])
        assertSame(last, accepted[1])
        assertEquals(3, source.size)
        assertEquals(source, disabled.filter(source, FeedScope.FOR_YOU) { it })
        assertEquals(accepted, enabled.filter(accepted, FeedScope.FOR_YOU) { it })
    }

    /**
     * 只有话题卡的输入允许全部移除，不重新添加被拒绝的内容。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun allTopicBatchCanBecomeEmpty() {
        val source = listOf(34, 35, 38).map { FeedEntry(awemeType = 105, cardType = it) } +
            FeedEntry(awemeType = 601) + FeedEntry(cardTemplates = listOf(
                FeedCardTemplate("tiktok_live_interaction_game_fyp", "pages/interest_card/template.js")))
        assertTrue(FeedFilterPolicy(removeTopicRecommendations = true).filter(source, FeedScope.FOR_YOU) { it }.isEmpty())
    }

    /** 游戏话题按模板识别，不依赖卡片编号、所列游戏或语言。@return Unit；无入参。Callers: JUnit。 */
    @Test fun gameInterestUsesTemplateInsteadOfContentBlacklist() {
        val policy = FeedFilterPolicy(removeTopicRecommendations = true)
        val template = FeedCardTemplate("tiktok_live_interaction_game_fyp", "pages/interest_card/template.js")
        for (name in listOf("Minecraft", "Genshin Impact", "新游戏", "새로운 게임", "")) {
            val card = FeedEntry(cardTemplates = listOf(template), description = name)
            assertEquals(FeedTopicKind.GAME_INTEREST, FeedTopicKind.classify(card))
            assertEquals("话题推荐", policy.reason(card, FeedScope.FOR_YOU))
            assertFalse(FeedFilterPolicy().reject(card, FeedScope.FOR_YOU))
            assertFalse(policy.reject(card, FeedScope.OTHER_FEED))
            assertFalse(policy.reject(card, FeedScope.OUTSIDE_FEED))
            assertFalse(policy.reject(card.copy(cardTemplates = emptyList()), FeedScope.FOR_YOU))
        }
    }

    /** 同直播业务的其它模板、无关兴趣卡不被扩大删除。@return Unit；无入参。Callers: JUnit。 */
    @Test fun liveAndUnrelatedLynxTemplatesArePreserved() {
        val policy = FeedFilterPolicy(removeTopicRecommendations = true)
        for (template in listOf(
            FeedCardTemplate("tiktok_live_interaction_game_fyp", "pages/live_card/template.js"),
            FeedCardTemplate("unrelated_business", "pages/interest_card/template.js"),
            FeedCardTemplate("", "prefix_tiktok_live_interaction_game_fyp/pages/interest_card/template.js"),
            FeedCardTemplate("", "tiktok_live_interaction_game_fyp/pages/interest_card/template.js/other")
        )) {
            assertFalse(policy.reject(FeedEntry(awemeType = 105, cardType = 122, cardTemplates = listOf(template)), FeedScope.FOR_YOU))
        }
    }

    /** 本地兴趣选择插卡只在推荐流被移除，不影响其它页面的兴趣管理。@return Unit；无入参。Callers: JUnit。 */
    @Test fun localInterestSelectionIsForYouOnly() {
        val card = FeedEntry(awemeType = 601)
        val policy = FeedFilterPolicy(removeTopicRecommendations = true)
        assertEquals(FeedTopicKind.INTEREST_SELECTION, FeedTopicKind.classify(card))
        assertTrue(policy.reject(card, FeedScope.FOR_YOU))
        assertFalse(FeedFilterPolicy().reject(card, FeedScope.FOR_YOU))
        assertFalse(policy.reject(card, FeedScope.OTHER_FEED))
        assertFalse(policy.reject(card, FeedScope.OUTSIDE_FEED))
    }

    /**
     * 话题判定不依赖时长或关键词，且其余过滤规则仍独立生效。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun topicRuleComposesWithExistingFilters() {
        val policy = FeedFilterPolicy(removeTopicRecommendations = true, removeOffline = true,
            duration = FeedRange(40000, 50000), keywords = KeywordFilterConfig(desc = listOf("blocked")))
        assertEquals("话题推荐", policy.reason(FeedEntry(awemeType = 105, cardType = 38), FeedScope.FOR_YOU))
        assertEquals("离线", policy.reason(FeedEntry(offline = true, durationMs = 45000), FeedScope.FOR_YOU))
        assertEquals("视频时长", policy.reason(FeedEntry(durationMs = 51000), FeedScope.FOR_YOU))
        assertEquals("文案关键词", policy.reason(FeedEntry(durationMs = 45000, description = "blocked"), FeedScope.FOR_YOU))
        assertNull(policy.reason(FeedEntry(durationMs = 45000, tags = listOf("topic")), FeedScope.FOR_YOU))
    }
}
