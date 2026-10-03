package io.github.meiyongai.toki.hook

import android.graphics.Rect
import android.widget.ImageView
import com.ss.android.ugc.aweme.ui.layout.FeedContentLayoutInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/** 验证宿主策略契约与横图、竖图、方图及未完成测量时的几何约束。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ImmersivePhotoLayoutTest {
    class Context
    class Result(val mode: ImageView.ScaleType, val percent: Float, val height: Int, val bottom: Int,
        val bounds: Rect, val scale: Float, val peer: Rect?, val translation: Float, val top: Float,
        val category: Int, val flag: Int, val debug: String, val previous: Result?, val metadata: Map<*, *>?)
    class Dispatcher {
        companion object {
            @JvmField val hostState: Any = error("宿主 Application 尚未初始化")
            /** @param info 文案布局。@param iw 图片宽。@param ih 图片高。@param w 视口宽。@param h 视口高。@param tab 标签高。@param status 顶栏高。@param context 上下文。@return 原生结果。Callers: 框架反射定位，不执行。 */
            @JvmStatic fun plan(info: FeedContentLayoutInfo, iw: Int, ih: Int, w: Int, h: Int,
                tab: Int, status: Int, context: Context): Result = error("host dispatcher")
        }
    }
    class InstanceDispatcher {
            /** @param info 文案布局。@param iw 图片宽。@param ih 图片高。@param w 视口宽。@param h 视口高。@param tab 标签高。@param status 顶栏高。@param context 上下文。@return 原生结果。Callers: 框架反射定位，不执行。 */
            fun plan(info: FeedContentLayoutInfo, iw: Int, ih: Int, w: Int, h: Int,
                tab: Int, status: Int, context: Context): Result = error("host dispatcher")
    }

    /** 各种比例完整呈现，中心误差至多一个像素且不产生拉伸。无参数、无返回。Callers: JUnit。 */
    @Test fun centersWholeImageForPortraitLandscapeSquareAndTallSources() {
        val layout = ImmersivePhotoLayout(Dispatcher::class.java, "plan")
        for ((iw, ih) in listOf(1080 to 1920, 1920 to 1080, 1024 to 1024, 600 to 4000)) {
            for ((w, h) in listOf(1080 to 2356, 1081 to 2357, 1080 to 1100)) {
                val result = layout.centered(iw, ih, w, h) as Result
                assertEquals(ImageView.ScaleType.FIT_CENTER, result.mode)
                assertEquals(1f, result.percent)
                assertEquals(h, result.height)
                assertEquals(h, result.bottom)
                assertEquals(0f, result.top)
                assertEquals(0f, result.translation)
                assertTrue(abs(result.bounds.left + result.bounds.right - w) <= 1)
                assertTrue(abs(result.bounds.top + result.bounds.bottom - h) <= 1)
                assertTrue(Rect(0, 0, w, h).contains(result.bounds))
                assertTrue(result.bounds.width() == w || result.bounds.height() == h)
                assertTrue(abs(result.bounds.width().toLong() * ih - result.bounds.height().toLong() * iw) <= maxOf(iw, ih))
            }
        }
    }

    /** 注册仅检查类型，不能提前初始化宿主静态状态。无参数、无返回。Callers: JUnit。 */
    @Test fun resolvesDispatcherWithoutInitializingHostState() {
        val layout = ImmersivePhotoLayout(Dispatcher::class.java, "plan")
        assertEquals(Dispatcher::class.java, layout.calculate.declaringClass)
        assertEquals("plan", layout.calculate.name)
    }

    /** 错误的实例入口必须明确拒绝。无参数、无返回。Callers: JUnit。 */
    @Test fun rejectsInstanceDispatcherBeforeRegistration() {
        assertThrows(IllegalStateException::class.java) {
            ImmersivePhotoLayout(InstanceDispatcher::class.java, "plan")
        }
    }

    /** 未测量尺寸不能创建无效宿主结果。无参数、无返回。Callers: JUnit。 */
    @Test fun rejectsUnmeasuredDimensions() {
        val layout = ImmersivePhotoLayout(Dispatcher::class.java, "plan")
        assertThrows(IllegalArgumentException::class.java) { layout.centered(0, 1920, 1080, 2356) }
        assertThrows(IllegalArgumentException::class.java) { layout.centered(1080, 1920, 1080, 0) }
    }
}
