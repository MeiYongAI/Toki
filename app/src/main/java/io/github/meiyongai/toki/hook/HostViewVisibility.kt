package io.github.meiyongai.toki.hook

import android.view.View
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/** 净屏和布局净化共用一份宿主显示意图，避免两个 Hook 将彼此的隐藏结果当作原始状态。 */
internal object HostViewVisibility {
    val gate = CleanViewGate()
    private val users = mutableSetOf<String>()
    private var handle: XposedInterface.HookHandle? = null

    /**
     * 按功能引用计数安装唯一可见性拦截，最后一个功能撤销后释放。
     * @param module 模块实例。
     * @param feature 功能安装事务标识。
     * @return Unit。
     * Callers: AutoCleanModeHook.installViewHooks、LayoutCleanupHook.init。
     */
    fun acquire(module: XposedModule, feature: String) {
        check(users.add(feature)) { "可见性规则重复安装：$feature" }
        if (handle == null) {
            handle = module.hook(View::class.java.getMethod("setVisibility", Int::class.javaPrimitiveType))
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept { chain ->
                    val requested = chain.args[0] as Int
                    val effective = gate.visibility(chain.thisObject as View, requested)
                    if (effective == requested) chain.proceed() else chain.proceed(arrayOf(effective))
                }
        }
        HookRuntime.onDispose(feature) {
            users.remove(feature)
            if (users.isEmpty()) { handle?.unhook(); handle = null }
        }
    }
}
