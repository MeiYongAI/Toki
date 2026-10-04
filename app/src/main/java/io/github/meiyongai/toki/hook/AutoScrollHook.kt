package io.github.meiyongai.toki.hook

import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/** 解锁视频页与广告的原生自动滚动，保留宿主菜单操作、暂停和播放完成流程。 */
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
     * 完整校验实验、菜单、翻页资格与页面路由，再注册可观测 Hook。
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
        val aweme = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val adTraffic = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.AwemeExtKt")
            .getDeclaredMethod("isAdTraffic", aweme).apply {
                check(Modifier.isStatic(modifiers) && returnType == Boolean::class.javaPrimitiveType)
                isAccessible = true
            }
        val menuOwner = HostSymbols.resolve(classLoader, HostSymbol.AUTO_SCROLL_MENU)
        val menu = menuOwner.declaredMethods.single {
            it.name == HostSymbols.member(HostSymbol.AUTO_SCROLL_MENU, "build")
        }.apply {
            check(!Modifier.isStatic(modifiers))
            check(parameterCount == 1 && !returnType.isPrimitive)
            isAccessible = true
        }
        val model = menuOwner.declaredFields.single { it.type == aweme && !Modifier.isStatic(it.modifiers) }
            .apply { isAccessible = true }
        val controller = HostSymbols.resolve(classLoader, HostSymbol.AUTO_SCROLL_PLAYBACK)
        val canAdvance = controller.getDeclaredMethod(
            HostSymbols.member(HostSymbol.AUTO_SCROLL_PLAYBACK, "canAdvance"), Boolean::class.javaPrimitiveType
        ).apply {
            check(!Modifier.isStatic(modifiers) && returnType == Boolean::class.javaPrimitiveType)
            isAccessible = true
        }
        val currentVideo = controller.declaredMethods.single {
            !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == aweme
        }.apply { isAccessible = true }
        val pages = AutoScrollPages(classLoader, controller, menuOwner)
        val adScope = AutoScrollAdScope()
        pages.install(module)
        module.trackHook("AutoScrollHook", contract.reader).intercept { chain ->
            if (unlocks(ConfigClient.getBoolean(KEY_AUTO_SCROLL_UNLOCK), chain.args[2])) 1 else chain.proceed()
        }
        module.trackHook("AutoScrollHook", contract.gate).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_AUTO_SCROLL_UNLOCK)) true else chain.proceed()
        }
        module.trackHook("AutoScrollHook", adTraffic).intercept { chain ->
            val original = chain.proceed() as Boolean
            original && !adScope.contains(chain.args[0])
        }
        module.trackHook("AutoScrollHook", menu).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_AUTO_SCROLL_UNLOCK)) {
                pages.building(checkNotNull(chain.thisObject)) {
                    adScope.checking(model.get(chain.thisObject)) { chain.proceed() }
                }
            } else chain.proceed()
        }
        module.trackHook("AutoScrollHook", canAdvance).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_AUTO_SCROLL_UNLOCK)) {
                adScope.checking(currentVideo.invoke(chain.thisObject)) { chain.proceed() }
            } else chain.proceed()
        }
        Log.i("TokiAutoScroll", "自动滚动入口已注册：推荐=${contract.reader.declaringClass.name}.${contract.reader.name}；搜索=${contract.gate.declaringClass.name}.${contract.gate.name}；菜单=${menuOwner.name}.${menu.name}")
    }
}
