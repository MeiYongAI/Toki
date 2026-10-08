package io.github.meiyongai.toki.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.github.meiyongai.toki.model.LayoutElement

class LayoutComponentSelectorTest {
    private open class Banner
    private open class Playlist : Banner()
    private class PlaylistVariant : Playlist()

    /** 验证顶部业务入口与视频开关统一，活动和奖励分别管理。无参数，返回 Unit。Callers: JUnit。 */
    @Test fun toolbarUsesVerifiedSemanticTags() {
        assertEquals(LayoutElement.TAKO, LayoutElement.toolbar("tako"))
        assertEquals(LayoutElement.REWARDS, LayoutElement.toolbar("coin"))
        assertEquals(LayoutElement.QUICK_MESSAGE, LayoutElement.toolbar("dm_notice"))
        assertEquals(LayoutElement.CREATION_BUTTONS, LayoutElement.toolbar("story_camera"))
        assertEquals(LayoutElement.TOP_SEARCH, LayoutElement.toolbar("search"))
        assertEquals(LayoutElement.ACTIVITIES, LayoutElement.toolbar("special_event"))
        assertNull(LayoutElement.toolbar("unknown"))
    }

    /** 验证父子 Hook 共用入口时具体语义稳定。无参数，返回 Unit。Callers: JUnit。 */
    @Test fun nearestOwnerWinsRegardlessOfRegistrationOrder() {
        val entries = listOf(Banner::class.java to "banner", Playlist::class.java to "playlist")
        assertEquals("playlist", LayoutComponentSelector.select(PlaylistVariant(), entries))
        assertEquals("playlist", LayoutComponentSelector.select(PlaylistVariant(), entries.reversed()))
        assertEquals("banner", LayoutComponentSelector.select(Banner(), entries))
        assertNull(LayoutComponentSelector.select(Any(), entries))
    }
}
