package io.github.meiyongai.toki.hook

import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 使用 Android View 验证独立所有权与原生属性恢复。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class CleanViewGateTest {
    private val gate = CleanViewGate()
    /** 创建视图。@return View；无入参。Callers: 本类测试。 */
    private fun view() = View(RuntimeEnvironment.getApplication())
    /** 模拟平台属性拦截。@param view 目标。@param value 可见性。@return Unit。Callers: 本类测试。 */
    private fun visibility(view: View, value: Int) { view.visibility = gate.visibility(view, value) }
    /** 模拟平台属性拦截。@param view 目标。@param value 透明度。@return Unit。Callers: 本类测试。 */
    private fun alpha(view: View, value: Float) { view.alpha = value }

    /** 暂停退出净屏不能恢复被布局净化隐藏的控件。无参数、无返回。Callers: JUnit。 */
    @Test fun layoutHidingSurvivesPauseAndRepeatedNativeShow() {
        val playback = CleanViewGate.Owner(true)
        val layout = CleanViewGate.Owner(true, View.GONE)
        val controls = view()
        gate.bind(playback, controls)
        gate.refresh()
        gate.bind(layout, controls)
        gate.refresh()
        repeat(3) {
            visibility(controls, View.VISIBLE)
            playback.clean = false
            gate.refresh()
            assertEquals(View.GONE, controls.visibility)
            playback.clean = true
        }
        layout.clean = false
        gate.refresh()
        assertEquals(View.INVISIBLE, controls.visibility)
        playback.clean = false
        gate.refresh()
        assertEquals(View.VISIBLE, controls.visibility)
    }

    /** 导航复用解绑不得释放同一视图上的净屏所有权。无参数、无返回。Callers: JUnit。 */
    @Test fun recycledNavigationRetainsOtherOwnersAndNativeIntent() {
        val playback = CleanViewGate.Owner(true)
        val layout = CleanViewGate.Owner(true, View.GONE)
        val controls = view()
        gate.bind(playback, controls)
        gate.bind(layout, controls)
        visibility(controls, View.INVISIBLE)
        gate.unbind(layout, controls)
        assertEquals(View.INVISIBLE, controls.visibility)
        playback.clean = false
        gate.refresh()
        assertEquals(View.INVISIBLE, controls.visibility)
        visibility(controls, View.VISIBLE)
        assertEquals(View.VISIBLE, controls.visibility)
    }

    /** 原生重建控件绑定相同配置后立即隐藏，关闭时各自恢复原生状态。无参数、无返回。Callers: JUnit。 */
    @Test fun newVideoControlsInheritHidingWithoutLosingNativeVisibility() {
        val layout = CleanViewGate.Owner(true, View.GONE)
        val first = view()
        val next = view().apply { visibility = View.INVISIBLE }
        gate.bind(layout, first)
        gate.bind(layout, next)
        gate.refresh()
        assertEquals(View.GONE, first.visibility)
        assertEquals(View.GONE, next.visibility)
        gate.detach(layout)
        assertEquals(View.VISIBLE, first.visibility)
        assertEquals(View.INVISIBLE, next.visibility)
    }

    /** 后台预加载只提交自身控件，前台所有者随后更新仍保持同一原生状态。无参数、无返回。Callers: JUnit。 */
    @Test fun preloadedControlsShareVisibilityIntentAcrossThreads() {
        val layout = CleanViewGate.Owner(true, View.GONE)
        val controls = view()
        val worker = Thread {
            gate.bind(layout, controls)
            gate.refresh(controls)
            visibility(controls, View.VISIBLE)
        }
        worker.start()
        worker.join()
        assertEquals(View.GONE, controls.visibility)
        layout.clean = false
        gate.refresh()
        assertEquals(View.VISIBLE, controls.visibility)
    }

    /** 淡入在暂停后逐帧推进时，绘制提交不得回写动画起始值。无参数、无返回。Callers: JUnit。 */
    @Test fun pauseFadeInIsNotOverwrittenByFrameCommits() {
        val owner = CleanViewGate.Owner(true)
        val controls = view().apply { alpha = 0f }
        gate.bind(owner, controls)
        gate.refresh()
        owner.clean = false
        visibility(controls, View.VISIBLE)
        for (value in listOf(0f, 0.25f, 0.7f, 1f)) {
            controls.alpha = value
            gate.refresh()
            assertEquals(View.VISIBLE, controls.visibility)
            assertEquals(value, controls.alpha)
        }
    }

    /** 隐藏期间动画仍可推进，解除净屏不能恢复过期透明度。无参数、无返回。Callers: JUnit。 */
    @Test fun animationWhileCleanRemainsHostOwned() {
        val owner = CleanViewGate.Owner(true)
        val controls = view().apply { alpha = 0f }
        gate.bind(owner, controls)
        gate.refresh()
        controls.alpha = 0.8f
        gate.refresh()
        assertEquals(View.INVISIBLE, controls.visibility)
        owner.clean = false
        gate.refresh()
        assertEquals(View.VISIBLE, controls.visibility)
        assertEquals(0.8f, controls.alpha)
    }

    /** 创建时登记使首次显示不依赖一次原生隐藏调用。@return Unit；无入参。Callers: JUnit。 */
    @Test fun creationBindingDoesNotNeedNativeClean() {
        val owner = CleanViewGate.Owner(true)
        val controls = view()
        gate.bind(owner, controls)
        gate.refresh()
        assertEquals(View.INVISIBLE, controls.visibility)
        assertEquals(1f, controls.alpha)
        visibility(controls, View.VISIBLE)
        alpha(controls, 1f)
        assertEquals(View.INVISIBLE, controls.visibility)
        assertEquals(1f, controls.alpha)
    }

    /** 原生退出清屏不能覆盖自动清屏，自动暂停后恢复原生意图。@return Unit；无入参。Callers: JUnit。 */
    @Test fun nativeExitAndAutomaticPauseHaveIndependentOwnership() {
        val owner = CleanViewGate.Owner(true)
        val controls = view()
        gate.bind(owner, controls)
        gate.refresh()
        repeat(3) { visibility(controls, View.VISIBLE); alpha(controls, 1f) }
        assertEquals(View.INVISIBLE, controls.visibility)
        owner.clean = false
        gate.refresh()
        assertEquals(View.VISIBLE, controls.visibility)
        assertEquals(1f, controls.alpha)
    }

    /** 原生清屏未退出时，关闭自动清屏不显示原生隐藏的控件。@return Unit；无入参。Callers: JUnit。 */
    @Test fun nativeCleanSurvivesAutomaticRelease() {
        val owner = CleanViewGate.Owner(true)
        val controls = view()
        gate.bind(owner, controls)
        gate.refresh()
        visibility(controls, View.INVISIBLE)
        alpha(controls, 0f)
        owner.clean = false
        gate.refresh()
        assertEquals(View.INVISIBLE, controls.visibility)
        assertEquals(0f, controls.alpha)
        visibility(controls, View.VISIBLE)
        alpha(controls, 1f)
        assertEquals(View.VISIBLE, controls.visibility)
    }

    /** 宿主删除和部分透明请求准确恢复。@return Unit；无入参。Callers: JUnit。 */
    @Test fun goneAndPartialAlphaRemainHostOwned() {
        val owner = CleanViewGate.Owner(true)
        val controls = view()
        gate.bind(owner, controls)
        gate.refresh()
        visibility(controls, View.GONE)
        alpha(controls, 0.4f)
        owner.clean = false
        gate.refresh()
        assertEquals(View.GONE, controls.visibility)
        assertEquals(0.4f, controls.alpha)
    }

    /** 不登记视频表面及无关窗口。@return Unit；无入参。Callers: JUnit。 */
    @Test fun unregisteredViewIsUntouched() {
        val video = view()
        visibility(video, View.VISIBLE)
        alpha(video, 0.6f)
        gate.refresh()
        assertEquals(View.VISIBLE, video.visibility)
        assertEquals(0.6f, video.alpha)
    }

    /** 共享区域在全部所有者释放前不显示。@return Unit；无入参。Callers: JUnit。 */
    @Test fun sharedOwnershipIsMergedBeforeCommit() {
        val first = CleanViewGate.Owner(true)
        val second = CleanViewGate.Owner(true)
        val controls = view()
        gate.bind(first, controls)
        gate.bind(second, controls)
        gate.refresh()
        gate.detach(first)
        assertEquals(View.INVISIBLE, controls.visibility)
        gate.detach(second)
        assertEquals(View.VISIBLE, controls.visibility)
    }

    /** 区域内容变更只恢复被移出的容器。@return Unit；无入参。Callers: JUnit。 */
    @Test fun replacingTargetsRestoresRemovedContainers() {
        val owner = CleanViewGate.Owner(true)
        val first = view()
        val second = view()
        gate.replace(owner, setOf(first, second))
        gate.refresh()
        gate.replace(owner, setOf(second))
        assertEquals(View.VISIBLE, first.visibility)
        assertEquals(View.INVISIBLE, second.visibility)
    }

    /** 重复登记不能把模块隐藏值保存为原生状态。@return Unit；无入参。Callers: JUnit。 */
    @Test fun repeatedBindingDoesNotOverwriteOriginalValues() {
        val owner = CleanViewGate.Owner(true)
        val controls = view()
        repeat(5) { gate.bind(owner, controls); gate.refresh() }
        owner.clean = false
        gate.refresh()
        assertEquals(View.VISIBLE, controls.visibility)
        assertEquals(1f, controls.alpha)
    }

    /** 模块属性写入发生异常仍原样抛出，后续宿主意图正常记录。@return Unit；无入参。Callers: JUnit。 */
    @Test fun propertyFailureIsPropagatedAndContextRestored() {
        val failure = IllegalStateException("view-property-failure")
        val controls = object : View(RuntimeEnvironment.getApplication()) {
            var failing = true
            /** 验证异常传播。@param value 新可见性。@return Unit。Callers: CleanViewGate。 */
            override fun setVisibility(value: Int) {
                if (failing) throw failure
                super.setVisibility(value)
            }
        }
        val owner = CleanViewGate.Owner(true)
        gate.bind(owner, controls)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { gate.refresh() })
        controls.failing = false
        visibility(controls, View.GONE)
        alpha(controls, 0.3f)
        owner.clean = false
        gate.refresh()
        assertEquals(View.GONE, controls.visibility)
        assertEquals(0.3f, controls.alpha)
    }
}
