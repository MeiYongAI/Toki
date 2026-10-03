package io.github.meiyongai.toki.hook

import io.github.meiyongai.toki.model.KeywordFilterConfig
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

/** 验证创作者推荐卡的宿主协议、页面边界与统一过滤策略。 */
class FeedCreatorRecommendationTest {
    /**
     * 本地生成的独立推荐条目不要求携带服务端卡片载荷。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun locallyCreatedCardsAreRejectedWithoutPayload() {
        val policy = FeedFilterPolicy(removeCreatorRecommendations = true)
        for (cardType in listOf(null, 0, 49)) {
            assertEquals("创作者推荐", policy.reason(FeedEntry(awemeType = 4004, cardType = cardType), FeedScope.FOR_YOU))
        }
    }

    /**
     * 服务端用户推荐卡按卡片协议识别，与宿主选择器一致，不受条目类型限制。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun serverCardsMatchTheIndependentCardProtocol() {
        val policy = FeedFilterPolicy(removeCreatorRecommendations = true)
        for (awemeType in listOf(0, 104, 105, 4004)) {
            assertEquals("创作者推荐", policy.reason(FeedEntry(awemeType = awemeType, cardType = 49), FeedScope.FOR_YOU))
        }
    }

    /**
     * 新开关默认关闭，仅推荐页启用，关注、搜索及作者主页均保持原内容。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun creatorRuleIsOptInAndForYouOnly() {
        val policy = FeedFilterPolicy(removeCreatorRecommendations = true)
        val entries = listOf(FeedEntry(awemeType = 4004), FeedEntry(awemeType = 105, cardType = 49))
        assertFalse(FeedFilterPolicy().applies(FeedScope.FOR_YOU))
        assertTrue(policy.applies(FeedScope.FOR_YOU))
        for (entry in entries) {
            assertFalse(FeedFilterPolicy().reject(entry, FeedScope.FOR_YOU))
            for (event in listOf("homepage_follow", "homepage_friends", "general_search", "others_homepage")) {
                val scope = FeedScope.fromEventType(event)
                assertFalse(policy.applies(scope))
                assertFalse(policy.reject(entry, scope))
            }
        }
    }

    /**
     * 作者、文案、标签和相邻编号不能代替明确的独立推荐卡标记。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun ordinaryContentAndUnrelatedCardsArePreserved() {
        val policy = FeedFilterPolicy(removeCreatorRecommendations = true)
        for (awemeType in listOf(0, 101, 104, 105, 150, 601, 4003, 4005)) {
            for (cardType in listOf(null, -1, 0, 34, 35, 38, 48, 50)) {
                assertFalse(policy.reject(FeedEntry(awemeType = awemeType, cardType = cardType,
                    description = "创作者推荐 Suggested creators", authors = listOf("Suggested creators"),
                    tags = listOf("creator", "follow")), FeedScope.FOR_YOU))
            }
        }
    }

    /**
     * 两种推荐开关独立判断，同开时分别提供准确的拒绝原因。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun topicAndCreatorSwitchesAreIndependent() {
        val creator = FeedEntry(awemeType = 4004)
        val serverCreator = FeedEntry(awemeType = 105, cardType = 49)
        val topic = FeedEntry(awemeType = 105, cardType = 38)
        for (topicsEnabled in listOf(false, true)) {
            for (creatorsEnabled in listOf(false, true)) {
                val policy = FeedFilterPolicy(removeTopicRecommendations = topicsEnabled,
                    removeCreatorRecommendations = creatorsEnabled)
                assertEquals(topicsEnabled, policy.reject(topic, FeedScope.FOR_YOU))
                assertEquals(creatorsEnabled, policy.reject(creator, FeedScope.FOR_YOU))
                assertEquals(creatorsEnabled, policy.reject(serverCreator, FeedScope.FOR_YOU))
            }
        }
    }

    /**
     * 配置变更可重筛已加载条目，保持输入不变、保留项身份及顺序；关闭后接受新批次。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun togglingRechecksLoadedItemsWithoutMutatingSource() {
        val first = FeedEntry(durationMs = 45000)
        val last = FeedEntry(durationMs = 48000)
        val source = Collections.unmodifiableList(listOf(first, FeedEntry(awemeType = 4004),
            FeedEntry(awemeType = 105, cardType = 49), last))
        val disabled = FeedFilterPolicy()
        val enabled = disabled.copy(removeCreatorRecommendations = true)
        val accepted = enabled.filter(disabled.filter(source, FeedScope.FOR_YOU) { it }, FeedScope.FOR_YOU) { it }
        assertEquals(listOf(first, last), accepted)
        assertSame(first, accepted[0])
        assertSame(last, accepted[1])
        assertEquals(4, source.size)
        assertEquals(source, disabled.filter(source, FeedScope.FOR_YOU) { it })
        assertEquals(accepted, enabled.filter(accepted, FeedScope.FOR_YOU) { it })
    }

    /**
     * 全部为目标卡片的批次允许为空，不恢复已拒绝条目。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun allCreatorBatchCanBecomeEmpty() {
        val source = listOf(FeedEntry(awemeType = 4004), FeedEntry(awemeType = 105, cardType = 49))
        assertTrue(FeedFilterPolicy(removeCreatorRecommendations = true).filter(source, FeedScope.FOR_YOU) { it }.isEmpty())
    }

    /**
     * 创作者卡片判定不依赖时长与关键词，其他信息流规则继续独立生效。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun creatorRuleComposesWithOtherFilters() {
        val policy = FeedFilterPolicy(removeCreatorRecommendations = true, removeTopicRecommendations = true,
            removeOffline = true, duration = FeedRange(40000, 50000), keywords = KeywordFilterConfig(desc = listOf("blocked")))
        assertEquals("创作者推荐", policy.reason(FeedEntry(awemeType = 4004), FeedScope.FOR_YOU))
        assertEquals("话题推荐", policy.reason(FeedEntry(awemeType = 105, cardType = 38), FeedScope.FOR_YOU))
        assertEquals("离线", policy.reason(FeedEntry(offline = true, durationMs = 45000), FeedScope.FOR_YOU))
        assertEquals("视频时长", policy.reason(FeedEntry(durationMs = 51000), FeedScope.FOR_YOU))
        assertEquals("文案关键词", policy.reason(FeedEntry(durationMs = 45000, description = "blocked"), FeedScope.FOR_YOU))
        assertNull(policy.reason(FeedEntry(durationMs = 45000), FeedScope.FOR_YOU))
    }
}
