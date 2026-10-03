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
