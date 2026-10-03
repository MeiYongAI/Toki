package io.github.meiyongai.toki.hook

import android.app.Activity
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowProcess
import java.time.Duration

/** 使用 Android 主线程调度验证扫描结果等待用户确认，不自动结束宿主。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class HostScanControllerTest {
    private val events = mutableListOf<String>()

    private fun attach() {
        val app = RuntimeEnvironment.getApplication()
        HostScanController.attach(app, ModuleResources(app.applicationInfo, javaClass.classLoader!!))
    }

    /** 初始化框架日志接收器和模拟进程，隔离测试进程的静态会话。@return Unit；无入参。Callers: JUnit。 */
    @Before fun prepare() {
        HostScanController::class.java.getDeclaredField("controller").apply { isAccessible = true }.set(null, null)
        HostScanController.session.fail()
        HostScanController.session.dismissResult()
        HookRuntime.start("test.host") { tag, message -> events.add("$tag:$message") }
        ShadowProcess.setPid(4703)
    }

    /** 完成一次模拟缓存提交，不创建窗口。@return Unit；无入参。Callers: 本类测试。 */
    private fun commitScan() {
        HostScanController.session.apply {
            begin()
                progress(1, 1)
            saving()
            ready()
        }
    }

    /**
     * 验证完成后 Activity 暂停、销毁或重复通知都不会结束宿主，结果仍等待用户确认。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun completedScanDoesNotExitAfterActivityPauseOrRepeatedNotification() {
        attach()
        commitScan()
        HostScanController.onScanFinished()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(HostScanPhase.READY, HostScanController.session.status.phase)
        val activity = Robolectric.buildActivity(Activity::class.java).create().start()
        activity.pause()
        activity.stop().destroy()
        HostScanController.onScanFinished()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertFalse(ShadowProcess.wasKilled(4703))
        assertEquals(HostScanPhase.READY, HostScanController.session.status.phase)
        assertTrue(events.isEmpty())
    }

    /**
     * 验证扫描早于 Application 接入时，接入后仍等待用户重启。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun committedBeforeApplicationAttachmentStillWaitsForUser() {
        commitScan()
        HostScanController.onScanFinished()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(HostScanPhase.READY, HostScanController.session.status.phase)
        attach()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertFalse(ShadowProcess.wasKilled(4703))
        assertEquals(HostScanPhase.READY, HostScanController.session.status.phase)
        assertTrue(events.isEmpty())
    }

    /** 验证保存未完成或失败不能结束进程。@return Unit；无入参。Callers: JUnit。 */
    @Test fun failedScanDoesNotExit() {
        attach()
        HostScanController.session.begin()
        HostScanController.session.fail()
        HostScanController.onScanFinished()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertFalse(ShadowProcess.wasKilled(4703))
        assertTrue(events.isEmpty())
    }

    /** 确认完成提示后不会因迟到通知重新打开窗口或结束宿主。@return Unit。Callers: JUnit。 */
    @Test fun acknowledgingResultDoesNotExitOrRestorePendingPrompt() {
        attach()
        commitScan()
        HostScanController.session.dismissResult()
        HostScanController.onScanFinished()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertFalse(ShadowProcess.wasKilled(4703))
        assertEquals(HostScanPhase.IDLE, HostScanController.session.status.phase)
        assertTrue(events.isEmpty())
    }
}
