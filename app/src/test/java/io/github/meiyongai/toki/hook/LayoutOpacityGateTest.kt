package io.github.meiyongai.toki.hook

import android.view.View
import android.widget.FrameLayout
import io.github.meiyongai.toki.model.LayoutGroup
import io.github.meiyongai.toki.provider.ConfigSchema
import io.github.meiyongai.toki.provider.ConfigSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 验证透明度与原生动画、嵌套控件及功能安装契约。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LayoutOpacityGateTest {
    /** 获取真实过渡属性。@param view 控件。@return Float。Callers: 本类测试。 */
    private fun transition(view: View) = View::class.java.getMethod("getTransitionAlpha").invoke(view) as Float
    /** 创建分区配置。@param percent 视频页乘数。@return 完整配置。Callers: 本类测试。 */
    private fun factors(percent: Float) = LayoutGroup.entries.associateWith { if (it == LayoutGroup.SIDE) percent else 1f }

    /** 宿主淡入不会被配置刷新重置，隐藏也不被解除。无参数、无返回。Callers: JUnit。 */
    @Test fun nativeAnimationAndVisibilityRemainIndependent() {
        val view = View(RuntimeEnvironment.getApplication())
        val gate = LayoutOpacityGate()
        gate.bind(view, LayoutGroup.SIDE)
        gate.configure(factors(0.4f))
        for (alpha in listOf(0f, 0.3f, 0.8f, 1f)) {
            view.alpha = alpha
            gate.configure(factors(0.4f))
            assertEquals(alpha, view.alpha, 0.001f)
            assertEquals(0.4f, transition(view), 0.001f)
        }
        view.visibility = View.GONE
        gate.configure(factors(1f))
        assertEquals(View.GONE, view.visibility)
        assertEquals(1f, transition(view), 0.001f)
    }

    /** 原生过渡动画的最新值在关闭功能时恢复。无参数、无返回。Callers: JUnit。 */
    @Test fun transitionUpdatesAndDisposalPreserveNativeIntent() {
        val view = View(RuntimeEnvironment.getApplication())
        val gate = LayoutOpacityGate()
        gate.bind(view, LayoutGroup.SIDE)
        gate.configure(factors(0.5f))
        gate.setter.invoke(view, gate.alpha(view, 0.6f))
        assertEquals(0.3f, transition(view), 0.001f)
        gate.configure(factors(0f))
        assertEquals(0f, transition(view), 0.001f)
        gate.close()
        assertEquals(0.6f, transition(view), 0.001f)
    }

    /** 头像和关注按钮等嵌套目标只衰减一次，并支持不同注册顺序。无参数、无返回。Callers: JUnit。 */
    @Test fun nestedControlsAndRebindingDoNotMultiplyTwice() {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val child = View(context)
        parent.addView(child)
        val gate = LayoutOpacityGate()
        gate.configure(factors(0.5f))
        gate.bind(child, LayoutGroup.SIDE)
        assertEquals(0.5f, transition(child), 0.001f)
        gate.bind(parent, LayoutGroup.SIDE)
        assertEquals(0.5f, transition(parent), 0.001f)
        assertEquals(1f, transition(child), 0.001f)
        parent.removeView(child)
        gate.bind(child, LayoutGroup.SIDE)
        assertEquals(0.5f, transition(child), 0.001f)
        gate.bind(child, LayoutGroup.TOP)
        assertEquals(1f, transition(child), 0.001f)
    }

    /** 单独调节透明度也能安装布局功能；非法配置不得进入运行时。无参数、无返回。Callers: JUnit。 */
    @Test fun opacityAloneActivatesLayoutAndValidatesRange() {
        for (group in LayoutGroup.entries) {
            val config = mapOf(group.opacityKey to "50", group.opacityEnabledKey to true)
            ConfigSchema.validate(config)
            val snapshot = ConfigSnapshot(config)
            assertTrue(snapshot.hasEnabledFeatures())
            assertTrue("LayoutCleanupHook" in HostFeaturePlan(snapshot).features)
            for (invalid in listOf("-1", "101", "NaN", "50.5")) {
                assertThrows(IllegalArgumentException::class.java) {
                    ConfigSchema.validate(mapOf(group.opacityKey to invalid))
                }
            }
        }
    }
}
