package io.github.meiyongai.toki.hook

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 验证模板路由解析与整类过滤，不从地址中的任意文本推断推荐内容。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class FeedCardTemplateTest {
    private val channel = "tiktok_live_interaction_game_fyp"
    private val bundle = "pages/interest_card/template.js"
    private val resource = "https://template.example/musically/fe/$channel/$bundle"

    /** 直接模板地址按资源路径识别，查询参数不进入策略数据。@return Unit；无入参。Callers: JUnit。 */
    @Test fun directResourceDropsQueriesAndFragments() {
        val template = FeedCardTemplate.read(channel, "$resource?game=anything&token=private#section")!!
        assertTrue(template.isGameInterest())
        assertEquals("musically/fe/$channel/$bundle", template.path)
        assertFalse(template.toString().contains("private"))
        assertTrue(FeedCardTemplate.read(null, resource)!!.isGameInterest())
    }

    /** 业务通道与相对 bundle 共同确认身份，单独通道不足以删除整类直播内容。@return Unit；无入参。Callers: JUnit。 */
    @Test fun relativeResourceRequiresMatchingChannel() {
        assertTrue(FeedCardTemplate.read(channel, bundle)!!.isGameInterest())
        assertFalse(FeedCardTemplate.read("other_channel", bundle)!!.isGameInterest())
        assertFalse(FeedCardTemplate.read(null, bundle)!!.isGameInterest())
        assertNull(FeedCardTemplate.read(channel, null))
        assertNull(FeedCardTemplate.read(channel, ""))
    }

    /** 编码 url 路由只解析其明确模板参数，不依赖域名或游戏项目。@return Unit；无入参。Callers: JUnit。 */
    @Test fun encodedTemplateUrlIsRecognized() {
        val route = "sslocal://webcast_lynxview?url=${Uri.encode("$resource?game=one&token=private")}&extra=ignored"
        val template = FeedCardTemplate.read(channel, route)!!
        assertTrue(template.isGameInterest())
        assertEquals("musically/fe/$channel/$bundle", template.path)
    }

    /** channel/bundle 路由与直接资源具有相同分类。@return Unit；无入参。Callers: JUnit。 */
    @Test fun channelBundleRouteIsRecognized() {
        val route = "sslocal://lynxview?channel=$channel&bundle=${Uri.encode(bundle)}"
        assertTrue(FeedCardTemplate.read(null, route)!!.isGameInterest())
        assertTrue(FeedCardTemplate.read(channel, route)!!.isGameInterest())
        assertNull(FeedCardTemplate.read("different_channel", route))
    }

    /** 未知参数或路径中的局部文字不构成模板身份，避免误删其它卡片。@return Unit；无入参。Callers: JUnit。 */
    @Test fun arbitraryQueryTextAndNearMatchesAreNotTopics() {
        val other = "https://template.example/another/template.js"
        for (address in listOf(
            "$other?description=${Uri.encode(resource)}",
            "$other?biz_data=${Uri.encode(resource)}",
            "$resource.backup",
            "$resource/other",
            "https://template.example/prefix_$channel/$bundle"
        )) {
            assertFalse(address, FeedCardTemplate.read(null, address)!!.isGameInterest())
        }
    }

    /** 空、非分层和没有资源路径的 URI 不产生模板身份，不抛出解析异常。@return Unit；无入参。Callers: JUnit。 */
    @Test fun missingAndOpaqueResourcesHaveNoIdentity() {
        for (address in listOf(null, "", " ", "mailto:someone", "https://template.example", "sslocal://lynxview?url=")) {
            assertNull(FeedCardTemplate.read(channel, address))
        }
    }

    /** 主模板为通用容器时，业务模板仍可独立指明游戏话题类型。@return Unit；无入参。Callers: JUnit。 */
    @Test fun businessTemplateIdentifiesCardInsideGenericEntrance() {
        val templates = listOfNotNull(
            FeedCardTemplate.read("feed_shell", "https://template.example/feed_shell/template.js"),
            FeedCardTemplate.read(channel, resource)
        )
        val entry = FeedEntry(awemeType = 105, cardTemplates = templates)
        assertEquals(FeedTopicKind.GAME_INTEREST, FeedTopicKind.classify(entry))
        assertEquals("话题推荐", FeedFilterPolicy(removeTopicRecommendations = true).reason(entry, FeedScope.FOR_YOU))
    }
}
