package io.github.meiyongai.toki.hook

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule

/**
 * 自动滚动解锁 Hook 处理器（For You 流与搜索结果页）。
 *
 * TikTok 的自动滚动（自动播放下一个视频）由服务端实验投放控制：判定链
 * `oad.n1.LIZ()` 读取实验组 31744 的 `fyp_auto_scroll` 参数，搜索流另有
 * 独立闸门 `X.03cj.L()`（经独立读取链读 `search_auto_scroll`），客户端
 * 默认均为关闭。未投放地区的账号拿不到开启值，长按菜单中不会出现自动
 * 滚动入口，功能整体不可见；base 包内不存在任何客户端侧国家/地区检查，
 * 风控完全发生在服务端下发环节。
 *
 * 解锁拦截分两路，与宿主读取链一一对应：
 * 1. 实验门面 [X.033O.LJIIJJI]：仅对 `fyp_auto_scroll` 键返回 1，其余键
 *    原样放行——`oad.n1.LIZ` 内部经由该门面读取，此路即覆盖 For You 流
 *    总闸，且完整保留宿主其余判定条件（特殊运营活动过滤、平板分流）；
 * 2. 搜索闸门 [X.03cj.L]：搜索流读取链不经实验门面，直接强制放行。
 *
 * 使未投放地区用户获得与已投放地区一致的官方体验（官方入口、官方暂停
 * 交互、广告不自动跳过）；对已投放地区的用户两路拦截均为语义空转。
 */
object AutoScrollHook {

    private const val TAG = "TokiAutoScroll"

    /** 自动滚动解锁功能配置项键名 */
    const val KEY_AUTO_SCROLL_UNLOCK = "auto_scroll_unlock"

    /** 宿主实验参数键名：For You 流自动滚动总开关（实验组 31744，默认 false） */
    private const val FYP_AUTO_SCROLL_KEY = "fyp_auto_scroll"

    private fun unlockEnabled(): Boolean = ConfigClient.getBoolean(KEY_AUTO_SCROLL_UNLOCK)

    /**
     * 初始化挂载自动滚动解锁核心 Hook。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标应用包完成加载时触发注册。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        hookFypAutoScrollSwitch(module, classLoader)
        hookSearchAutoScrollGate(module, classLoader)
        Log.i(TAG, "自动滚动解锁 Hook 初始化挂载就绪")
    }

    /**
     * 目标应用 Application 上下文下的配置刷新同步入口。
     *
     * Args:
     *     context (Context): 目标应用上下文环境。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主冷启动完成时分发。
     */
    fun refreshConfig(context: Context) {
        // 配置由 ConfigClient 统一管理并缓存
    }

    /**
     * 挂载 fyp_auto_scroll 实验读取解锁拦截。
     *
     * 拦截 [X.033O] 的 `LJIIJJI(int, int, String, boolean)`（整型实验参数读取，
     * oad.n1 等经由该门面读取实验组 31744 的参数值）：解锁开启且键名命中
     * `fyp_auto_scroll` 时返回 1，其余调用原样放行。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.AutoScrollHook.init`: 模块初始化阶段挂载。
     */
    private fun hookFypAutoScrollSwitch(module: XposedModule, classLoader: ClassLoader) {
        val settingsFacadeClass = HostSymbols.resolve(classLoader, HostSymbol.SETTINGS)
        val experimentReadMethod = settingsFacadeClass.declaredMethods.firstOrNull {
            it.name == "LJIIJJI" && it.parameterCount == 4 &&
                it.parameterTypes[0] == java.lang.Integer.TYPE &&
                it.parameterTypes[1] == java.lang.Integer.TYPE &&
                it.parameterTypes[2] == String::class.java &&
                it.parameterTypes[3] == java.lang.Boolean.TYPE &&
                it.returnType == java.lang.Integer.TYPE
        }
        if (experimentReadMethod == null) {
            Log.w(TAG, "X.033O.LJIIJJI 未匹配，自动滚动解锁未挂载")
            return
        }
        experimentReadMethod.isAccessible = true
        module.trackHook("AutoScrollHook", experimentReadMethod).intercept { chain ->
            if (chain.args[2] == FYP_AUTO_SCROLL_KEY && unlockEnabled()) {
                1
            } else {
                chain.proceed()
            }
        }
        Log.i(TAG, "已成功挂载 fyp_auto_scroll 实验读取解锁拦截")
    }

    /**
     * 挂载搜索结果页自动滚动闸门解锁拦截。
     *
     * 搜索流的可用性闸门 [X.03cj.L] 经独立读取链（非实验门面）读取
     * `search_auto_scroll`，无法由门面键过滤传递覆盖，直接强制放行。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.AutoScrollHook.init`: 模块初始化阶段挂载。
     */
    private fun hookSearchAutoScrollGate(module: XposedModule, classLoader: ClassLoader) {
        val searchGateClass = HostSymbols.resolve(classLoader, HostSymbol.SEARCH_AUTO_SCROLL)
        val gateMethod = searchGateClass.declaredMethods.firstOrNull {
            it.name == HostSymbols.member(HostSymbol.SEARCH_AUTO_SCROLL, "gate") && it.parameterCount == 0 && it.returnType == java.lang.Boolean.TYPE
        }
        if (gateMethod == null) {
            Log.w(TAG, "X.03cj.L 未匹配，搜索流自动滚动解锁未挂载")
            return
        }
        gateMethod.isAccessible = true
        module.trackHook("AutoScrollHook", gateMethod).intercept { chain ->
            if (unlockEnabled()) true else chain.proceed()
        }
        Log.i(TAG, "已成功挂载 X.03cj.L 搜索流自动滚动闸门解锁拦截")
    }
}
