package io.github.meiyongai.toki.hook

import io.github.libxposed.api.XposedModule
import io.github.meiyongai.toki.provider.ConfigClient
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** 按宿主页面生命周期选择原生控制器；状态不持有页面或控制器的强引用。 */
internal class AutoScrollPageState {
    private data class State(var resumed: Boolean, var visible: Boolean, var order: Long)
    private val states = WeakHashMap<Any, State>()
    private var sequence = 0L

    /** @param controller 原生控制器。@param resumed 页面已恢复。@param visible 分页可见。@return Unit。Callers: AutoScrollPages.install、测试。 */
    @Synchronized fun attach(controller: Any, resumed: Boolean, visible: Boolean) {
        states[controller] = State(resumed, visible, ++sequence)
    }

    /** @param controller 原生控制器。@param resumed 恢复状态，null 表示不变。@param visible 分页状态，null 表示不变。@return Unit。Callers: AutoScrollPages.install、测试。 */
    @Synchronized fun update(controller: Any, resumed: Boolean? = null, visible: Boolean? = null) {
        val state = states[controller] ?: return // onViewCreated 前没有可操作的播放器。
        resumed?.let { state.resumed = it }
        visible?.let { state.visible = it }
        if (state.resumed && state.visible) state.order = ++sequence
    }

    /** @param controller 已销毁的控制器。@return Unit。Callers: AutoScrollPages.install、测试。 */
    @Synchronized fun remove(controller: Any) { states.remove(controller) }

    /** @return Unit。无参数。Callers: AutoScrollPages.install 的卸载回调。 */
    @Synchronized fun clear() { states.clear() }

    /** @param matches 当前宿主窗口及菜单页面的约束。@return 最近恢复且符合约束的控制器；没有可见视频页时为 null。Callers: AutoScrollPages.install、测试。 */
    @Synchronized fun current(matches: (Any) -> Boolean): Any? = states.entries
        .filter { it.value.resumed && it.value.visible && matches(it.key) }
        .maxByOrNull { it.value.order }?.key
}

/** 连接各视频页已经提供的原生自动滚动组件，菜单作用域不修改页面来源或宿主字段。 */
internal class AutoScrollPages(loader: ClassLoader, private val controller: Class<*>, menuOwner: Class<*>) {
    private val rootPanel = loader.loadClass("com.ss.android.ugc.feed.platform.panel.RootPanelComponent")
    private val commonConfiguration = loader.loadClass("com.ss.android.ugc.feed.platform.panel.CommonPanelConfiguration")
    private val configure = commonConfiguration.declaredMethods.single {
        !Modifier.isAbstract(it.modifiers) && !Modifier.isStatic(it.modifiers) &&
            it.parameterCount == 1 && it.returnType == Void.TYPE && it.parameterTypes[0].isAssignableFrom(rootPanel)
    }.apply { isAccessible = true }
    private val declaration = HostSymbols.resolve(loader, HostSymbol.AUTO_SCROLL_REGISTRATION)
        .getDeclaredConstructor().apply { isAccessible = true }
    private val register = HostSymbols.resolve(loader, HostSymbol.AUTO_SCROLL_REGISTER).getDeclaredMethod(
        HostSymbols.member(HostSymbol.AUTO_SCROLL_REGISTER, "register"), rootPanel,
        loader.loadClass("kotlin.jvm.functions.Function1")
    ).apply {
        check(Modifier.isStatic(modifiers) && returnType == Void.TYPE &&
            parameterTypes[1].isAssignableFrom(declaration.declaringClass))
        isAccessible = true
    }
    private val fragment = loader.loadClass("androidx.fragment.app.Fragment")
    private val menuPage = menuOwner.declaredFields.single { it.type == fragment && !Modifier.isStatic(it.modifiers) }
        .apply { isAccessible = true }
    private val panelContext = controller.getMethod("getPanelContext")
    private val panelPage = panelContext.returnType.declaredFields.single { it.type == fragment && !Modifier.isStatic(it.modifiers) }
        .apply { isAccessible = true }
    private val activity = fragment.getMethod("getActivity")
    private val resumed = fragment.getMethod("isResumed")
    private val visible = fragment.getMethod("isVisible")
    private val pageVisible = fragment.getMethod("getUserVisibleHint")
    private val utility: Class<*> = HostSymbols.resolve(loader, HostSymbol.AUTO_SCROLL_CONTEXT)
    private val resolve: java.lang.reflect.Method = utility.declaredMethods.single {
        it.name == HostSymbols.member(HostSymbol.AUTO_SCROLL_CONTEXT, "resolve")
    }.apply {
        check(Modifier.isStatic(modifiers) && parameterCount == 1 &&
            returnType.name == "com.ss.android.ugc.feed.platform.panel.autoscroll.IAutoAScrollAbility")
        isAccessible = true
    }
    private val page = gate("page")
    private val search = gate("search")
    private val created = controller.getDeclaredMethod("onViewCreated", android.view.View::class.java)
    private val lifecycle = listOf("onResume", "onPause", "onDestroy").associateWith { controller.getDeclaredMethod(it) }
    private val paging = listOf("onPageResume", "onPagePause").associateWith {
        controller.getDeclaredMethod(it, Int::class.javaPrimitiveType)
    }
    private val menuContext = ThreadLocal<Any?>()
    private val state = AutoScrollPageState()

    /** @param role 行为规则角色。@return 已验证的页面判断入口。Callers: 初始化。 */
    private fun gate(role: String) = utility.getDeclaredMethod(
        HostSymbols.member(HostSymbol.AUTO_SCROLL_CONTEXT, role), String::class.java
    ).apply {
        check(Modifier.isStatic(modifiers) && returnType == Boolean::class.javaPrimitiveType)
        isAccessible = true
    }

    /** @param instance 原生控制器。@return 所属视频 Fragment。Callers: install。 */
    private fun pageOf(instance: Any): Any? = panelPage.get(panelContext.invoke(instance))

    /** @return 当前解锁开关。无参数。Callers: install。 */
    private fun enabled() = ConfigClient.getBoolean(AutoScrollHook.KEY_AUTO_SCROLL_UNLOCK)

    /** @param owner 长按菜单工厂。@param block 原生自动滚动菜单构建。@return 原生结果。Callers: AutoScrollHook.init。 */
    fun <T> building(owner: Any, block: () -> T): T {
        val previous = menuContext.get()
        menuContext.set(menuPage.get(owner))
        return try { block() } finally {
            if (previous == null) menuContext.remove() else menuContext.set(previous)
        }
    }

    /** @param module LSPosed 模块。@return Unit。Callers: AutoScrollHook.init，全部反射契约验证后安装。 */
    fun install(module: XposedModule) {
        HookRuntime.onDispose("AutoScrollHook") { state.clear() }
        module.trackHook("AutoScrollHook", configure).intercept { chain ->
            val panel = chain.args[0]
            if (enabled() && rootPanel.isInstance(panel)) {
                register.invoke(null, panel, declaration.newInstance())
            }
            chain.proceed()
        }
        module.trackHook("AutoScrollHook", created).intercept { chain ->
            val result = chain.proceed()
            val instance = checkNotNull(chain.thisObject)
            val owner = pageOf(instance)
            state.attach(instance, owner != null && resumed.invoke(owner) as Boolean,
                owner != null && pageVisible.invoke(owner) as Boolean)
            result
        }
        for ((name, method) in lifecycle + paging) {
            module.trackHook("AutoScrollHook", method).intercept { chain ->
                val instance = checkNotNull(chain.thisObject)
                when (name) {
                    "onResume" -> state.update(instance, resumed = true)
                    "onPause" -> state.update(instance, resumed = false)
                    "onPageResume" -> state.update(instance, visible = true)
                    "onPagePause" -> state.update(instance, visible = false)
                    "onDestroy" -> state.remove(instance)
                }
                chain.proceed()
            }
        }
        module.trackHook("AutoScrollHook", resolve).intercept { chain ->
            if (!enabled()) return@intercept chain.proceed()
            val expectedPage = menuContext.get()
            val selected = state.current { candidate ->
                val owner = pageOf(candidate)
                owner != null && (expectedPage == null || owner === expectedPage) &&
                    (chain.args[0] == null || activity.invoke(owner) === chain.args[0]) && visible.invoke(owner) as Boolean
            }
            selected
        }
        module.trackHook("AutoScrollHook", page).intercept { chain ->
            if (enabled() && !(chain.args[0] as String?).isNullOrEmpty()) true else chain.proceed()
        }
        module.trackHook("AutoScrollHook", search).intercept { chain ->
            if (enabled() && menuContext.get() != null) true else chain.proceed()
        }
    }
}
