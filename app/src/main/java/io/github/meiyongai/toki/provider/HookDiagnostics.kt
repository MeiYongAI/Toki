package io.github.meiyongai.toki.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 仅由管理端主动查询的诊断协议；不启动宿主，也不要求宿主唤起管理端服务。 */
internal object HookDiagnostics {
    const val ACTION = "io.github.meiyongai.toki.REQUEST_DIAGNOSTICS"
    const val PERMISSION = "io.github.meiyongai.toki.permission.READ_DIAGNOSTICS"
    val hosts = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    /**
     * 在宿主主进程注册受 Toki 签名权限保护的动态接收器。
     * @param context 宿主应用上下文。
     * @param snapshot 生成无用户内容的当前会话报告。
     * @return 已注册接收器，由进程持有至退出。
     * Callers: HookRuntime.attach、HookDiagnosticsTest。
     */
    fun register(context: Context, snapshot: () -> Bundle): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            /**
             * 回答管理端发起的有序查询，不进行配置读写或后台发布。
             * @param context 当前宿主上下文。
             * @param intent 固定协议查询。
             * @return Unit。
             * Callers: Android 广播分发。
             */
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION || !isOrderedBroadcast) return
                setResultExtras(Bundle().apply { putBundle(context.packageName, snapshot()) })
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), PERMISSION, null,
            ContextCompat.RECEIVER_EXPORTED)
        return receiver
    }

    /**
     * 查询当前仍运行的宿主主进程；无动态接收器时返回空报告，不启动应用。
     * @param context Toki 上下文。
     * @return 包名到当前会话的映射。
     * Callers: HookStatusSection。
     */
    suspend fun request(context: Context): Bundle {
        val result = Bundle()
        for (host in hosts) result.putAll(requestHost(context, host))
        return result
    }

    /**
     * 向一个明确宿主发送签名权限受控的有序广播，并等待最终回复。
     * @param context Toki 上下文。
     * @param host 已声明的目标包名。
     * @return 当前进程报告；未运行时为空。
     * Callers: request。
     */
    private suspend fun requestHost(context: Context, host: String): Bundle = suspendCancellableCoroutine { continuation ->
        val resultReceiver = object : BroadcastReceiver() {
            /** 接收本次查询结果。@param context Toki 上下文。@param intent 查询 Intent。@return Unit。Callers: Android。 */
            override fun onReceive(context: Context, intent: Intent) {
                val result = getResultExtras(false) ?: Bundle()
                if (continuation.isActive) continuation.resume(result)
            }
        }
        context.sendOrderedBroadcast(Intent(ACTION).setPackage(host).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY),
            null, resultReceiver, null, 0, null, null)
    }
}
