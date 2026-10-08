package io.github.meiyongai.toki.hook

import io.github.libxposed.api.XposedModule
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 状态栏入口缺失必须中止注册，不继续安装其它入口并覆盖首个错误。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class StatusBarRegistrationTest {
    /** 缺少首个宿主类型时原样报告，后续类型不再访问。@return Unit；无参数。Callers: JUnit。 */
    @Test fun missingPlaybackClassStopsRegistrationAtOriginalFailure() {
        val failure = ClassNotFoundException("missing playback class")
        val requested = mutableListOf<String>()
        val loader = object : ClassLoader(javaClass.classLoader) {
            /** 模拟缺失宿主入口。@param name 类型名。@return 不返回，抛出原始错误。Callers: StatusBarHook.init。 */
            override fun loadClass(name: String): Class<*> {
                requested.add(name)
                throw failure
            }
        }
        assertSame(failure, assertThrows(ClassNotFoundException::class.java) {
            StatusBarHook.init(object : XposedModule() {}, loader)
        })
        assertEquals(listOf("com.ss.android.ugc.aweme.feed.adapter.VideoViewCell"), requested)
    }
}
