package io.github.meiyongai.toki.hook

import android.app.Application
import io.github.libxposed.api.XposedModule
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 缺失的符号必须在注册前报告，不能潜伏到全局 View.setAlpha 回调中。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [35])
class ProgressBarRegistrationTest {
    class Seek(context: android.content.Context) : android.view.View(context) {
        /** @param mode 显示模式。@return Unit。Callers: 反射契约测试。 */
        fun renamedApply(mode: Int) {}
    }
    class Mask(context: android.content.Context) : android.view.View(context) {
        /** @param canvas 绘制画布。@return Unit。Callers: Android、反射契约测试。 */
        override fun onDraw(canvas: android.graphics.Canvas) {}
    }
    class Controller {
        @JvmField var renamedView: Seek? = null
        @JvmField var renamedDuration: android.view.ViewGroup? = null
        /** @param show 请求显隐。@return 模式。Callers: 反射契约测试。 */
        fun renamedDecision(show: Boolean): Int = 0
    }
    class AmbiguousController {
        @JvmField var first: Seek? = null
        @JvmField var second: Seek? = null
    }
    class MissingController

    /** 控件字段和方法改名仍按实际类型关联解析。无参数，无返回。Callers: JUnit。 */
    @Test fun renamedMembersResolveThroughViewRelationship() {
        val contract = ProgressBarHook.ViewContract(Seek::class.java, Controller::class.java, Mask::class.java,
            "renamedDecision", "renamedApply")
        assertEquals("renamedView", contract.field.name)
        assertEquals("renamedDuration", contract.duration.name)
        assertEquals("renamedDecision", contract.decide.name)
        assertEquals("renamedApply", contract.apply.name)
    }

    /** 缺失或重复控件字段及不存在的方法不能留下部分契约。无参数，无返回。Callers: JUnit。 */
    @Test fun incompleteAndAmbiguousViewRelationshipsAreRejected() {
        for (type in listOf(MissingController::class.java, AmbiguousController::class.java)) {
            assertThrows(RuntimeException::class.java) {
                ProgressBarHook.ViewContract(Seek::class.java, type, Mask::class.java, "renamedDecision", "renamedApply")
            }
        }
        assertThrows(NoSuchMethodException::class.java) {
            ProgressBarHook.ViewContract(Seek::class.java, Controller::class.java, Mask::class.java, "missing", "renamedApply")
        }
    }

    /** 净屏的保留开关只影响播放态，暂停和拖拽不被改写。无参数，无返回。Callers: JUnit。 */
    @Test fun cleanModePreservesPauseAndDragStates() {
        for (keep in listOf(false, true)) {
            for (mode in listOf(0, 4)) assertEquals(if (keep) 0 else 4, ProgressBarHook.cleanMode(mode, keep))
            for (mode in listOf(1, 2, 100, 101)) assertEquals(mode, ProgressBarHook.cleanMode(mode, keep))
        }
    }

    @Test fun missingSeekBarIsRejectedBeforeAccessingAnyHostMethods() {
        val field = HostSymbols::class.java.getDeclaredField("resolved").apply { isAccessible = true }
        val previous = field.get(null)
        field.set(null, Properties().apply {
            setProperty("DARK_LAYER", "android.view.View|test")
            setProperty("error.SEEK_BAR", "候选数量=0")
        })
        try {
            val failure = assertThrows(IllegalStateException::class.java) {
                ProgressBarHook.init(object : XposedModule() {}, javaClass.classLoader!!)
            }
            assertTrue(failure.message.orEmpty().contains("SEEK_BAR"))
        } finally {
            field.set(null, previous)
        }
    }
}
