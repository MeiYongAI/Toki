package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 根据实机生命周期顺序验证显示阶段，不把播放通知时间当作页面首绘时间。 */
class CleanPlaybackStateTest {
    /** 创建可见且启用的页面。@return 页面状态；无入参。Callers: 本类测试。 */
    private fun page() = CleanPlaybackState().apply { enabled = true; visible = true }

    /** 首绘早于选页与播放准备，整个加载过程保持清屏。@return Unit；无入参。Callers: JUnit。 */
    @Test fun coldStartDrawPrecedesEveryPlaybackCallback() {
        val state = page()
        assertFalse(state.selected)
        assertTrue(state.shouldClean)
        state.select()
        assertTrue(state.shouldClean)
        state.prepare()
        assertTrue(state.shouldClean)
        state.play()
        assertTrue(state.shouldClean)
        state.release()
        assertTrue(state.shouldClean)
        state.select()
        state.play()
        assertTrue(state.shouldClean)
    }

    /** 暂停是明确显示阶段，恢复播放立即重新隐藏。@return Unit；无入参。Callers: JUnit。 */
    @Test fun actualPauseAndResume() {
        val state = page()
        state.select(true)
        state.stop()
        assertFalse(state.shouldClean)
        state.play()
        assertTrue(state.shouldClean)
    }

    /** 首帧前的明确暂停与失败恢复控件。@return Unit；无入参。Callers: JUnit。 */
    @Test fun failureOrExplicitPauseDoesNotWaitForFirstFrame() {
        val state = page()
        state.select()
        state.stop()
        assertFalse(state.shouldClean)
        state.prepare()
        assertFalse(state.shouldClean)
        state.play()
        assertTrue(state.shouldClean)
    }

    /** 预加载页面不能接管可见页面。@return Unit；无入参。Callers: JUnit。 */
    @Test fun offscreenPageHasNoOwnership() {
        val state = page()
        state.visible = false
        state.select(true)
        assertFalse(state.shouldClean)
    }

    /** 切到其它页面立即解除所有权，返回仍读取该页实际阶段。@return Unit；无入参。Callers: JUnit。 */
    @Test fun pageVisibilityAndPlaybackAreIndependent() {
        val state = page()
        state.select(true)
        state.visible = false
        assertFalse(state.shouldClean)
        state.stop()
        state.visible = true
        assertFalse(state.shouldClean)
        state.prepare()
        assertFalse(state.shouldClean)
        state.play()
        assertTrue(state.shouldClean)
    }

    /** 翻页中的停播与新内容加载不经过显示控件阶段。@return Unit；无入参。Callers: JUnit。 */
    @Test fun switchingVideosHasNoVisibleIntermediatePhase() {
        val state = page()
        state.select(true)
        state.release()
        assertTrue(state.shouldClean)
        state.select()
        assertTrue(state.shouldClean)
        state.prepare()
        state.play()
        assertTrue(state.shouldClean)
    }

    /** 明确暂停后保持显示控件。@return Unit；无入参。Callers: JUnit。 */
    @Test fun explicitPauseRetainsControls() {
        val state = page()
        state.select(true)
        state.stop()
        assertFalse(state.shouldClean)
    }

    /** 暂停后恢复播放，不显示控件。@return Unit；无入参。Callers: JUnit。 */
    @Test fun resumeAfterExplicitPauseHidesControls() {
        val state = page()
        state.select(true)
        state.stop()
        state.play()
        assertTrue(state.shouldClean)
    }

    /** 明确非视频或空列表恢复操作入口。@return Unit；无入参。Callers: JUnit。 */
    @Test fun nonVideoAndEmptyContentEndLoading() {
        val state = page()
        state.clear()
        assertFalse(state.shouldClean)
        state.select()
        assertTrue(state.shouldClean)
    }

    /** 网络失败恢复导航，重试请求重新进入加载，空结果再次恢复。@return Unit；无入参。Callers: JUnit。 */
    @Test fun networkErrorRetryAndEmptyResultHaveExplicitPhases() {
        val state = page()
        state.clear()
        assertFalse(state.shouldClean)
        state.loading()
        assertTrue(state.shouldClean)
        state.clear()
        assertFalse(state.shouldClean)
        state.select()
        state.play()
        assertTrue(state.shouldClean)
    }

    /** 关闭及重新开启功能不改变实际播放阶段。@return Unit；无入参。Callers: JUnit。 */
    @Test fun configurationOnlyControlsOwnership() {
        val state = page()
        state.select(true)
        state.enabled = false
        assertFalse(state.shouldClean)
        state.enabled = true
        assertTrue(state.shouldClean)
        state.stop()
        state.enabled = false
        state.enabled = true
        assertFalse(state.shouldClean)
    }
}
