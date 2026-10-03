package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 核对宿主类型发生变化时沉浸注册明确失败，避免部分挂载。 */
class ImmersiveLayoutContractTest {
    class Screen(val topSpaceHeight: Int, val bottomSpaceHeight: Int, val height: Int,
        val width: Int, val status: Int, val navigation: Int, val virtual: Int?) {
        val containerHeight: Int get() = height - topSpaceHeight - bottomSpaceHeight
    }
    interface Context {
        /** @return 状态栏高度。无参数。Callers: 反射契约检查。 */
        fun getStatusBarHeight(): Float
        /** @return 顶部标签高度。无参数。Callers: 反射契约检查。 */
        fun getTopTabHeight(): Float
        /** @return 底部标签高度。无参数。Callers: 反射契约检查。 */
        fun getBottomTabHeight(): Float
        /** @return 底部横幅高度。无参数。Callers: 反射契约检查。 */
        fun getBottomBannerHeight(): Float
    }
    class Area {
        companion object {
            /** @param areas 区域。@param mask 显示掩码。@param context 布局上下文。@return 示例高度。Callers: 反射测试。 */
            @JvmStatic fun renamed(areas: List<*>, mask: Int, context: Context): Float = 12f
        }
    }
    class InvalidArea {
        companion object {
            /** @param areas 区域。@param mask 掩码。@param context 错误上下文。@return 示例高度。Callers: 反射测试。 */
            @JvmStatic fun renamed(areas: List<*>, mask: Int, context: String): Float = 12f
        }
    }
    class InstanceArea {
        /** @param areas 区域。@param mask 掩码。@param context 上下文。@return 示例高度。Callers: 反射测试。 */
        fun renamed(areas: List<*>, mask: Int, context: Context): Float = 12f
    }

    /** 接受实际类型契约，布局计算由宿主构造器完成。无参数，无返回。Callers: JUnit。 */
    @Test fun acceptsRenamedResolverAndPreservesScreenDimensions() {
        val contract = ImmersiveFullScreenHook.LayoutContract(Screen::class.java, Area::class.java, "renamed")
        val screen = contract.screenConstructor.newInstance(0, 0, 2400, 1080, 80, 120, 0) as Screen
        assertEquals(2400, screen.containerHeight)
        assertEquals(80, screen.status)
        assertEquals("renamed", contract.reserve.name)
    }
    /** 无区域 getter 的同形入口不可注册。无参数，无返回。Callers: JUnit。 */
    @Test fun rejectsUnrelatedContext() {
        assertThrows(NoSuchMethodException::class.java) {
            ImmersiveFullScreenHook.LayoutContract(Screen::class.java, InvalidArea::class.java, "renamed")
        }
    }
    /** 静态调用约束不可放宽为实例。无参数，无返回。Callers: JUnit。 */
    @Test fun rejectsInstanceResolver() {
        assertThrows(IllegalStateException::class.java) {
            ImmersiveFullScreenHook.LayoutContract(Screen::class.java, InstanceArea::class.java, "renamed")
        }
    }
}
