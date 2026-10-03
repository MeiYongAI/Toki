package io.github.meiyongai.toki.hook

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/** 在宿主前台活动中显示独立 Material 3 窗口，使用模块资源和独立生命周期。 */
internal class HostScanController private constructor(
    private val moduleResources: ModuleResources,
) : Application.ActivityLifecycleCallbacks {
    companion object {
        val session = HostScanSession()
        private var controller: HostScanController? = null

        /**
         * 在宿主主进程订阅活动生命周期，窗口随前台活动释放。
         * @param app 当前宿主Application。
         * @param moduleResources 框架提供的模块 APK 资源来源。
         * @return Unit。
         * Callers: TokiModule.hookApplication。
         */
        fun attach(app: Application, moduleResources: ModuleResources) {
            if (controller != null) return
            controller = HostScanController(moduleResources).also {
                app.registerActivityLifecycleCallbacks(it)
            }
        }

        /** 扫描结束后通知主线程更新结果提示。@return Unit。Callers: HostSymbols 扫描线程。 */
        fun onScanFinished() {
            Handler(Looper.getMainLooper()).post { controller?.render() }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var foreground = WeakReference<Activity>(null)
    private var dialog: HostScanDialog? = null
    private val refresh = object : Runnable {
        /** 更新可见窗口；无前台活动时停止刷新。@return Unit。Callers: 主线程Handler。 */
        override fun run() {
            render()
            val phase = session.status.phase
            if (dialog != null && (phase == HostScanPhase.SCANNING || phase == HostScanPhase.SAVING)) {
                main.postDelayed(this, 150)
            }
        }
    }

    /**
     * 更新当前活动的模态窗口，后台扫描不会抢占其他应用前台。
     * @return Unit。
     * Callers: refresh、onActivityResumed。
     */
    private fun render() {
        val activity = foreground.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val state = session.status
        if (state.phase == HostScanPhase.IDLE) { dismissDialog(); return }
        if (dialog == null) {
            dialog = HostScanDialog(activity, moduleResources) { session.dismissResult(); dismissDialog() }
                .also { it.render(state); it.show() }
        }
        dialog!!.render(state)
    }

    /** 释放窗口，保留未确认的扫描结果。@return Unit。Callers: 生命周期、结果确认、render。 */
    private fun dismissDialog() {
        main.removeCallbacks(refresh)
        dialog?.dismiss()
        dialog = null
    }

    /** 活动进入前台后恢复窗口。@param activity 宿主活动。@return Unit。Callers: Android Application。 */
    override fun onActivityResumed(activity: Activity) {
        dismissDialog()
        foreground = WeakReference(activity)
        main.post(refresh)
    }

    /** 活动离开前台时释放窗口。@param activity 宿主活动。@return Unit。Callers: Android Application。 */
    override fun onActivityPaused(activity: Activity) {
        if (foreground.get() === activity) { dismissDialog(); foreground.clear() }
    }

    /** 销毁活动时清理引用。@param activity 宿主活动。@return Unit。Callers: Android Application。 */
    override fun onActivityDestroyed(activity: Activity) {
        if (foreground.get() === activity) { dismissDialog(); foreground.clear() }
    }

    /** 创建阶段不显示窗口。@param activity 活动。@param savedInstanceState 保存状态。@return Unit。Callers: Android Application。 */
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    /** 启动阶段等待恢复回调。@param activity 活动。@return Unit。Callers: Android Application。 */
    override fun onActivityStarted(activity: Activity) = Unit
    /** 暂停阶段已完成窗口释放。@param activity 活动。@return Unit。Callers: Android Application。 */
    override fun onActivityStopped(activity: Activity) = Unit
    /** 扫描状态属于进程，不写入活动状态。@param activity 活动。@param outState 保存状态。@return Unit。Callers: Android Application。 */
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
