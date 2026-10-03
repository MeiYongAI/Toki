package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

class StatusBarHookTest {
    private val live = "com.ss.android.ugc.aweme.live.LivePlayActivity"
    private fun hide(name: String, enabled: Boolean = true, tab: String = "HOME",
                     profile: Boolean = false, paused: Boolean = false) =
        StatusBarHook.shouldHideStatusBar(name, enabled, tab, profile, paused)

    @Test fun liveHidesRegardlessOfBackgroundFeedState() {
        for (tab in listOf("HOME", "USER", "NOTIFICATION")) {
            for (profile in listOf(false, true)) for (paused in listOf(false, true)) {
                assertTrue(hide(live, tab = tab, profile = profile, paused = paused))
            }
        }
    }

    @Test fun disabledOptionShowsStatusBarOnLiveAndFeed() {
        for (name in listOf(live, "com.ss.android.ugc.aweme.main.MainActivity", "com.ss.android.ugc.aweme.detail.ui.DetailActivity")) {
            assertFalse(hide(name, enabled = false))
        }
    }

    @Test fun returningFromLivePreservesFeedPauseAndProfilePolicy() {
        val main = "com.ss.android.ugc.aweme.main.MainActivity"
        assertTrue(hide(main))
        assertFalse(hide(main, paused = true))
        assertFalse(hide(main, profile = true))
        assertFalse(hide(main, tab = "USER"))
        assertTrue(hide("com.ss.android.ugc.aweme.detail.ui.DetailActivity"))
        assertFalse(hide("com.ss.android.ugc.aweme.detail.ui.DetailActivity", paused = true))
    }

    @Test fun liveSettingsAndUnrelatedLivePagesRemainVisible() {
        for (name in listOf("com.ss.android.ugc.aweme.live.LiveSettingActivity",
            "com.ss.android.ugc.aweme.live.LiveContainerActivity",
            "com.ss.android.ugc.aweme.live.LiveBroadcastActivity", "example.LivePlayActivity")) {
            assertFalse(hide(name))
        }
    }
}
