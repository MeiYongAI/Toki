package io.github.meiyongai.toki.hook

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.provider.ConfigSnapshot
import io.github.meiyongai.toki.provider.ConfigStore
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 驱动真实清屏提交和 Android 视图树，配置事件拥有页面监听的生命周期。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [35])
class CleanConfigurationLifecycleTest {
    class SeekBarModeRecorder {
        var mode = -1
        fun setSeekBarShowType(value: Int) { mode = value }
    }
    private fun field(target: Class<*>, name: String) = target.getDeclaredField(name).apply { isAccessible = true }
    private fun dispose() = AutoCleanModeHook::class.java.getDeclaredMethod("dispose")
        .apply { isAccessible = true }.invoke(AutoCleanModeHook)

    @Before @After fun reset() {
        dispose()
        val store = field(ConfigClient::class.java, "store").get(null) as ConfigStore
        field(ConfigStore::class.java, "isReady").set(store, false)
        field(ConfigStore::class.java, "current").set(store, null)
        field(ProgressBarHook::class.java, "activeSeekBar").set(null, null)
        field(ProgressBarHook::class.java, "seekBarShowTypeMethod").set(null, null)
        HookRuntime.start("clean-test") { _, _ -> }
    }

    private fun applyConfiguration(snapshot: ConfigSnapshot?) = AutoCleanModeHook::class.java
        .getDeclaredMethod("configurationChanged", ConfigSnapshot::class.java)
        .apply { isAccessible = true }.invoke(AutoCleanModeHook, snapshot)

    @Test fun preDrawUsesAppliedSnapshotAndConfigLossReleasesItsCallbacks() {
        val context = RuntimeEnvironment.getApplication()
        val root = FrameLayout(context)
        val controls = View(context).also(root::addView)
        val fragment = Any()
        val pageType = AutoCleanModeHook::class.java.declaredClasses.single { it.simpleName == "Page" }
        val page = pageType.getDeclaredConstructor(Any::class.java).apply { isAccessible = true }.newInstance(fragment)
        val state = field(pageType, "state").get(page) as CleanPlaybackState
        val owner = field(pageType, "owner").get(page) as CleanViewGate.Owner
        val gate = field(AutoCleanModeHook::class.java, "views").get(null) as CleanViewGate
        @Suppress("UNCHECKED_CAST")
        val pages = field(AutoCleanModeHook::class.java, "pages").get(null) as MutableMap<Any, Any>
        pages[fragment] = page
        state.visible = true
        gate.bind(owner, controls)
        var commits = 0
        val commit = AutoCleanModeHook::class.java.getDeclaredMethod("commit", String::class.java).apply { isAccessible = true }
        val binding = CleanSceneBinding(root, null, gate, owner) {
            commits++
            commit.invoke(AutoCleanModeHook, "frame")
        }
        field(pageType, "binding").set(page, binding)
        val seekBar = SeekBarModeRecorder()
        field(ProgressBarHook::class.java, "activeSeekBar").set(null, WeakReference<Any>(seekBar))
        field(ProgressBarHook::class.java, "seekBarShowTypeMethod").set(null,
            SeekBarModeRecorder::class.java.getMethod("setSeekBarShowType", Int::class.javaPrimitiveType))

        // 存储不可用时不会由绘制回调重新读取它，显示决策来自已验证的配置事件。
        assertThrows(IllegalStateException::class.java) { ConfigClient.snapshot() }
        applyConfiguration(ConfigSnapshot(mapOf("clean_mode_on_play" to true, "clean_mode_show_progress_bar" to true), 1))
        assertEquals(0, seekBar.mode)
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(View.INVISIBLE, controls.visibility)
        assertEquals(1, commits)

        applyConfiguration(null)
        assertEquals(View.VISIBLE, controls.visibility)
        assertFalse(owner.clean)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, commits)

        // 暂停监听期间宿主可更改自己的属性；恢复后不覆盖这些原生意图。
        controls.visibility = gate.visibility(controls, View.GONE)
        controls.alpha = 0.4f
        applyConfiguration(ConfigSnapshot(mapOf("clean_mode_on_play" to true), 2))
        assertEquals(4, seekBar.mode)
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(2, commits)
        assertEquals(View.GONE, controls.visibility)
        applyConfiguration(null)
        assertEquals(View.GONE, controls.visibility)
        assertEquals(0.4f, controls.alpha)
    }

}
