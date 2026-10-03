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
    private fun alpha(view: View, value: Float) { view.alpha = gate.alpha(view, value) }

    /** 创建时登记使首次显示不依赖一次原生隐藏调用。@return Unit；无入参。Callers: JUnit。 */
    @Test fun creationBindingDoesNotNeedNativeClean() {
        val owner = CleanViewGate.Owner(true)
        val controls = view()
        gate.bind(owner, controls)
        gate.refresh()
        assertEquals(View.INVISIBLE, controls.visibility)
        assertEquals(0f, controls.alpha)
        visibility(controls, View.VISIBLE)
        alpha(controls, 1f)
        assertEquals(View.INVISIBLE, controls.visibility)
        assertEquals(0f, controls.alpha)
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
            /** 验证异常传播。@param value 新透明度。@return Unit。Callers: CleanViewGate。 */
            override fun setAlpha(value: Float) {
                if (failing) throw failure
                super.setAlpha(value)
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
