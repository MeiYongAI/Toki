package io.github.meiyongai.toki.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 验证诊断只响应管理端授权查询，不创建后台推送或启动其它应用。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class HookDiagnosticsTest {
    /** 注册必须携带签名权限，注册本身不能生成或发送报告。@return Unit。Callers: JUnit。 */
    @Test fun registrationIsPermissionProtectedAndDoesNotPublish() {
        val app = RuntimeEnvironment.getApplication()
        var snapshots = 0
        val receiver = HookDiagnostics.register(app) { snapshots++; Bundle() }
        val registration = shadowOf(app).registeredReceivers.single { it.broadcastReceiver === receiver }
        assertEquals(HookDiagnostics.PERMISSION, registration.broadcastPermission)
        assertTrue(registration.intentFilter.hasAction(HookDiagnostics.ACTION))
        assertEquals(ContextCompat.RECEIVER_EXPORTED, registration.flags and ContextCompat.RECEIVER_EXPORTED)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, snapshots)
        assertTrue(shadowOf(app).broadcastIntents.isEmpty())
        app.unregisterReceiver(receiver)
    }

    /** 普通广播和非协议广播不能触发报告生成。@return Unit。Callers: JUnit。 */
    @Test fun unrelatedOrUnorderedBroadcastCannotRequestSnapshot() {
        val app = RuntimeEnvironment.getApplication()
        var snapshots = 0
        val receiver = HookDiagnostics.register(app) { snapshots++; Bundle() }
        receiver.onReceive(app, Intent("unrelated"))
        receiver.onReceive(app, Intent(HookDiagnostics.ACTION))
        assertEquals(0, snapshots)
        app.unregisterReceiver(receiver)
    }

    /** 受权限保护的有序查询得到本次报告且不启动 Activity 或 Service。@return Unit。Callers: JUnit。 */
    @Test fun orderedRequestReturnsSnapshotWithoutStartingComponents() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(HookDiagnostics.PERMISSION)
        var snapshots = 0
        val receiver = HookDiagnostics.register(app) {
            snapshots++
            Bundle().apply { putLong("configRevision", 75) }
        }
        var reply: Bundle? = null
        val completion = object : BroadcastReceiver() {
            /** 保存查询结果。@param context 测试上下文。@param intent 查询。@return Unit。Callers: Android。 */
            override fun onReceive(context: Context, intent: Intent) { reply = getResultExtras(false) }
        }
        app.sendOrderedBroadcast(Intent(HookDiagnostics.ACTION).setPackage(app.packageName)
            .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY), null, completion, null, 0, null, null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, snapshots)
        assertEquals(75L, reply?.getBundle(app.packageName)?.getLong("configRevision"))
        assertNull(shadowOf(app).nextStartedActivity)
        assertNull(shadowOf(app).nextStartedService)
        app.unregisterReceiver(receiver)
    }
}
