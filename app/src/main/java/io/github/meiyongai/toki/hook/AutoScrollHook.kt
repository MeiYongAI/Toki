package io.github.meiyongai.toki.hook

import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/** 放行推荐页和搜索页的自动滚动实验开关，保留宿主菜单、播放与场景限制。 */
object AutoScrollHook {
    const val KEY_AUTO_SCROLL_UNLOCK = "auto_scroll_unlock"

    /**
     * 注册前完整验证两路入口，不读取宿主静态实例或提前初始化实验配置。
     * @param settings 整型实验读取类。
     * @param readerName 行为规则定位的读取方法名。
     * @param search 搜索自动滚动开关类。
     * @param gateName 行为规则定位的开关方法名。
     * Callers: init；AutoScrollContractTest。
     */
    internal class Contract(settings: Class<*>, readerName: String, search: Class<*>, gateName: String) {
        val reader = settings.getDeclaredMethod(readerName, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, String::class.java, Boolean::class.javaPrimitiveType).apply {
            check(returnType == Int::class.javaPrimitiveType && !Modifier.isStatic(modifiers)) {
                "自动滚动实验读取入口不符合契约"
            }
            isAccessible = true
        }
        val gate = search.getDeclaredMethod(gateName).apply {
            check(returnType == Boolean::class.javaPrimitiveType && !Modifier.isStatic(modifiers)) {
                "搜索自动滚动开关不符合契约"
            }
            isAccessible = true
        }
    }

    /**
     * 限定实验放行范围，不修改同组或同名之外的设置。
     * @param enabled 当前会话是否启用解锁。
     * @param key 实验键名。
     * @return 仅启用且精确命中推荐页自动滚动时返回 true。
     * Callers: init 的实验读取 Hook；AutoScrollContractTest。
     */
    internal fun unlocks(enabled: Boolean, key: Any?): Boolean = enabled && key == "fyp_auto_scroll"

    /**
     * 同时校验推荐页与搜索页入口，然后注册可观测 Hook。
     * @param module LSPosed 模块上下文。
     * @param classLoader 官方宿主类加载器。
     * @return Unit；契约错误交由统一功能注册事务报告。
     * Callers: TokiModule.installHost。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        val contract = Contract(HostSymbols.resolve(classLoader, HostSymbol.SETTINGS),
            HostSymbols.member(HostSymbol.SETTINGS, "readInt"),
            HostSymbols.resolve(classLoader, HostSymbol.SEARCH_AUTO_SCROLL),
            HostSymbols.member(HostSymbol.SEARCH_AUTO_SCROLL, "gate"))
        module.trackHook("AutoScrollHook", contract.reader).intercept { chain ->
            if (unlocks(ConfigClient.getBoolean(KEY_AUTO_SCROLL_UNLOCK), chain.args[2])) 1 else chain.proceed()
        }
        module.trackHook("AutoScrollHook", contract.gate).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_AUTO_SCROLL_UNLOCK)) true else chain.proceed()
        }
        Log.i("TokiAutoScroll", "自动滚动入口已注册：推荐=${contract.reader.declaringClass.name}.${contract.reader.name}；搜索=${contract.gate.declaringClass.name}.${contract.gate.name}")
    }
}
