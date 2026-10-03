package io.github.meiyongai.toki.hook

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import io.github.meiyongai.toki.provider.ConfigSnapshot
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

/** 页面内容、导航选择和播放阶段共同决定清屏；显示范围在视图创建时确定。 */
object AutoCleanModeHook {
    const val KEY_CLEAN_MODE_ON_PLAY = "clean_mode_on_play"
    private val pages = WeakHashMap<Any, Page>()
    private val components = WeakHashMap<Any, Component>()
    private val pagers = WeakHashMap<Any, Pager>()
    private val shells = WeakHashMap<Any, Shell>()
    private val views = CleanViewGate()
    private var configuration: ConfigSnapshot? = null
    private lateinit var panelContext: Method
    private lateinit var panelFragment: Field
    private lateinit var cellFragment: Field
    private lateinit var currentHolder: Method
    private lateinit var listFragment: Method
    private lateinit var cellAweme: Method
    private lateinit var videoCell: Class<*>
    private lateinit var feedFragment: Class<*>
    private lateinit var mainFragment: Class<*>

    /** @param fragment 视频页所属 Fragment；仅弱引用宿主。 */
    private class Page(fragment: Any) {
        val fragment = WeakReference(fragment)
        val state = CleanPlaybackState()
        val owner = CleanViewGate.Owner()
        var cell = WeakReference<Any>(null)
        var list = WeakReference<Any>(null)
        var binding: CleanSceneBinding? = null
        var reported = ""
    }
    /** @param page 卡片所属页面。 */
    private class Component(val page: Page) { val owner = CleanViewGate.Owner() }
    /** @param page 翻页器所属页面。 */
    private class Pager(val page: Page) { var origin: WeakReference<Any>? = null }
    /** @param binding 导航区域。@param current 读取已选子 Fragment，不持有宿主强引用。 */
    private class Shell(val binding: CleanSceneBinding, val current: () -> Any?)

    /** 当前可见视频页是否清屏。@return 清屏状态；无入参。Callers: ProgressBarHook。 */
    val isCleanActive: Boolean get() = pages.values.any { it.owner.clean }

    /** 读取组件所属 Fragment。@param component 页面组件。@return Fragment 或 null。Callers: init。 */
    private fun owner(component: Any): Any? = panelFragment.get(panelContext.invoke(component))

    /** 取得同一个 Fragment 的唯一会话。@param fragment 页面。@return 会话。Callers: 页面、组件和列表回调。 */
    private fun pageFor(fragment: Any): Page = pages.getOrPut(fragment) { Page(fragment) }

    /** 读取卡片所属页面。@param cell 视频卡片。@return 页面或 null。Callers: 卡片回调。 */
    private fun page(cell: Any): Page? = cellFragment.get(cell)?.let(::pageFor)

    /** 关联列表与页面。@param panel 实际列表面板。@return 页面或 null。Callers: init、playbackStopped。 */
    private fun listPage(panel: Any): Page? = listFragment.invoke(panel)?.let {
        pageFor(it).also { page -> page.list = WeakReference(panel) }
    }

    /**
     * 从实际选中的导航分支解析显示策略；尚未创建内容与已确认非视频是不同状态。
     * @param fragment 当前选中的 Fragment；null 表示该导航容器尚未完成初始选择。
     * @return 是否隐藏该分支外的页面装饰。
     * Callers: commit。
     */
    private fun resolve(fragment: Any?): Boolean {
        if (fragment == null) return true
        shells[fragment]?.let { return resolve(it.current()) }
        pages[fragment]?.let { return it.state.hidesControls }
        return mainFragment.isInstance(fragment) || feedFragment.isInstance(fragment)
    }

    /**
     * 在一次提交中先计算所有区域，再更新视图，避免共享容器出现中间恢复状态。
     * @param event 固定事件名称，不包含用户内容。
     * @return Unit；绘制监听始终放行，不依赖播放器通知才能绘制视频。
     * Callers: 页面生命周期、播放事件、配置和 CleanSceneBinding。
     */
    private fun commit(event: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "清屏显示状态只能在主线程提交" }
        val enabled = configuration?.boolean(KEY_CLEAN_MODE_ON_PLAY) == true
        for (page in pages.values) {
            page.state.enabled = enabled
            page.owner.clean = page.state.shouldClean
        }
        for (component in components.values) component.owner.clean = component.page.owner.clean
        for (shell in shells.values) shell.binding.owner.clean = enabled && resolve(shell.current())
        views.refresh()
        for (page in pages.values) {
            val snapshot = "phase=${page.state.phase} visible=${page.state.visible} clean=${page.owner.clean}"
            if (snapshot != page.reported) {
                page.reported = snapshot
                HookRuntime.event("TokiAutoClean", "event=$event page=${System.identityHashCode(page)} $snapshot")
                HookRuntime.count("AutoCleanModeHook", if (page.owner.clean) "进入清屏" else "退出清屏")
            }
        }
        if (event != "frame" && isCleanActive) ProgressBarHook.assertPlayingSeekBarMode(
            checkNotNull(configuration).boolean(ProgressBarHook.KEY_CLEAN_SHOW_PROGRESS_BAR))
    }

    /**
     * 按列表当前 Holder 选择卡片；尚无 Holder 保持加载状态，非视频明确解除清屏。
     * @param page 所属页面。
     * @return 当前视频卡片；尚未创建或为其它内容时返回 null。
     * Callers: startPlayback、pause、settle、选页与暂停命令。
     */
    private fun selectCurrent(page: Page): Any? {
        val panel = page.list.get() ?: return null
        val cell = currentHolder.invoke(panel) ?: return null
        if (!videoCell.isInstance(cell)) {
            page.state.clear()
            page.cell.clear()
            return null
        }
        if (page.cell.get() !== cell) {
            page.state.select()
            page.cell = WeakReference(cell)
        }
        return cell
    }

    /** 核对播放通知归属。@param page 页面。@param cell 通知卡片。@return 是否为当前卡片。Callers: startPlayback、pause。 */
    private fun isCurrent(page: Page, cell: Any): Boolean =
        page.list.get()?.let { currentHolder.invoke(it) === cell } == true

    /** 更新实际播放阶段。@param cell 当前卡片。@param preparing 是否准备通知。@return Unit。Callers: init 的播放回调。 */
    private fun startPlayback(cell: Any, preparing: Boolean) {
        val page = page(cell) ?: return
        if (!isCurrent(page, cell)) return
        selectCurrent(page)
        if (preparing) page.state.prepare() else {
            page.state.play()
        }
        commit(if (preparing) "prepare" else "play")
    }

    /** 处理当前卡片停播或失败。@param cell 当前卡片。@param failed 是否失败。@return Unit。Callers: init 的停止回调。 */
    private fun pause(cell: Any, failed: Boolean) {
        val page = page(cell) ?: return
        if (!isCurrent(page, cell)) return
        selectCurrent(page)
        if (failed) page.state.stop() else page.state.pause()
        commit(if (failed) "failed" else "pause")
    }

    /**
     * 明确暂停交互完成后读取实际暂停状态，拒绝其它视频的迟到事件。
     * @param contract 已核实的暂停接口。
     * @param controller 控制器。
     * @param aweme 交互视频；null 表示控制器暂停命令。
     * @return Unit。
     * Callers: init。
     */
    private fun playbackStopped(contract: CleanPlaybackContract, controller: Any, aweme: Any?) {
        if (!contract.isPaused(controller)) return
        val page = contract.panel.invoke(controller)?.let(::listPage) ?: return
        val cell = page.list.get()?.let { currentHolder.invoke(it) } ?: return
        if (!videoCell.isInstance(cell) || (aweme != null && cellAweme.invoke(cell) !== aweme)) return
        selectCurrent(page)
        page.state.stop()
        commit("pauseCommand")
    }

    /** 释放身份并保持页面交接。@param cell 解除绑定的卡片。@return Unit。Callers: init。 */
    private fun releaseCell(cell: Any) {
        val page = page(cell) ?: return
        if (page.cell.get() !== cell) return
        page.state.release()
        page.cell.clear()
        commit("release")
    }

    /** 开始翻页事务。@param binding 翻页器。@return Unit。Callers: init。 */
    private fun beginTransition(binding: Pager) {
        if (binding.origin == null) binding.origin = WeakReference(binding.page.cell.get())
        binding.page.state.beginTransition()
    }

    /** 完成翻页并提交当前内容。@param binding 翻页器。@return Unit。Callers: init。 */
    private fun settle(binding: Pager) {
        val selected = selectCurrent(binding.page)
        binding.page.state.settle(binding.origin?.get() !== selected)
        binding.origin = null
        commit("settle")
    }

    /**
     * 注册一个导航页面，保留内容容器及其祖先，将旁支统一视作页面装饰区域。
     * @param fragment 页面身份。
     * @param root 页面根视图。
     * @param contentId 宿主用于挂载页面的内容 ID，由页面根视图按相同规则解析。
     * @param current 实际选中子 Fragment 的读取函数。
     * @param preserve 页面级清屏之外仍由专用状态机管理的视图判定。
     * @return Unit。
     * Callers: init 中两个导航页面的 onViewCreated。
     */
    private fun bindShell(fragment: Any, root: View, contentId: Int, current: () -> Any?,
        preserve: (View) -> Boolean) {
        shells.remove(fragment)?.binding?.close()
        val owner = CleanViewGate.Owner()
        val kind = fragment.javaClass.simpleName
        var firstDraw = true
        val binding = CleanSceneBinding(root, contentId, views, owner, preserve) {
            commit("frame")
            if (firstDraw) {
                firstDraw = false
                HookRuntime.event("TokiAutoClean", "sceneFirstDraw type=$kind clean=${owner.clean}")
            }
        }
        binding.setActive(configuration != null)
        shells[fragment] = Shell(binding, current)
        commit("sceneCreated")
        HookRuntime.event("TokiAutoClean", "sceneCreated type=${fragment.javaClass.simpleName} clean=${binding.owner.clean}")
    }

    /**
     * 注册生命周期、原生容器声明及页面绘制提交；不调用原生清屏开关。
     * @param module libxposed 模块。
     * @param classLoader 宿主类加载器。
     * @return Unit；类型契约缺失由功能注册事务报告。
     * Callers: TokiModule.onPackageReady。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        HookRuntime.onDispose("AutoCleanModeHook", ::dispose)
        val fragment = classLoader.loadClass("androidx.fragment.app.Fragment")
        mainFragment = classLoader.loadClass("com.ss.android.ugc.aweme.main.MainFragment")
        feedFragment = classLoader.loadClass("com.ss.android.ugc.aweme.feed.ui.BaseFeedListFragment")
        val mainPage = classLoader.loadClass("com.ss.android.ugc.aweme.main.MainPageFragment")
        val tabHost = classLoader.loadClass("com.ss.android.ugc.aweme.ui.FragmentTabHost")
        val tabContent = tabHost.declaredFields.single { it.type == FrameLayout::class.java }.apply { isAccessible = true }
        val tabCurrent = tabHost.getMethod("getCurrentFragment")
        val seekBar = HostSymbols.resolve(classLoader, HostSymbol.SEEK_BAR)
        val preserve = { view: View -> CleanSceneBinding.containsInstance(view, seekBar) }
        val homeCurrent = mainFragment.getMethod("getCurrentFragment")
        videoCell = HostSymbols.resolve(classLoader, HostSymbol.VIDEO_CELL)
        val base = classLoader.loadClass("com.ss.android.ugc.aweme.feed.adapter.VideoBaseCell")
        val panel = classLoader.loadClass("com.ss.android.ugc.feed.platform.panel.clean.FeedCleanComponent")
        val component = classLoader.loadClass("com.ss.android.ugc.feed.platform.cell.clean.CellCleanComponent")
        val root = classLoader.loadClass("com.ss.android.ugc.feed.platform.panel.RootPanelComponent")
        panelContext = panel.getMethod("getPanelContext")
        panelFragment = panelContext.returnType.declaredFields.single { it.type == fragment }.apply { isAccessible = true }
        cellFragment = base.declaredFields.single { it.type == fragment }.apply { isAccessible = true }
        cellAweme = videoCell.getMethod("getAweme")
        val pageParams = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.BaseFeedPageParams")
        val componentBase = classLoader.loadClass("com.ss.android.ugc.feed.platform.cell.BaseCellContentComponent")
        val componentContext = componentBase.declaredMethods.single {
            it.parameterCount == 0 && it.returnType.declaredFields.any { field -> field.type == pageParams }
        }
        val componentPanel = componentContext.returnType.declaredMethods.single {
            it.parameterCount == 0 && it.returnType == panelContext.returnType
        }
        val componentViews = component.declaredFields.filter { View::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        check(componentViews.isNotEmpty()) { "原生卡片清屏组件未声明控件容器" }
        val registerView = panel.declaredMethods.single {
            it.returnType == Void.TYPE && it.parameterCount == 2 && it.parameterTypes[0] == View::class.java &&
                it.parameterTypes[1].isEnum
        }

        for (type in listOf(mainFragment, mainPage)) {
            module.trackScene(type.getMethod("onViewCreated", View::class.java, Bundle::class.java)).intercept { chain ->
                val result = chain.proceed()
                val instance = chain.thisObject!!
                val view = chain.args[0] as View
                if (type == mainFragment) {
                    val id = view.resources.getIdentifier("viewpager", "id", view.context.packageName)
                    val reference = WeakReference(instance)
                    bindShell(instance, view, id, { reference.get()?.let { homeCurrent.invoke(it) } }, preserve)
                } else {
                    check(tabHost.isInstance(view)) { "首页导航根视图不符合 FragmentTabHost 契约" }
                    val content = checkNotNull(tabContent.get(view) as? View) { "首页导航尚未设置内容容器" }
                    val reference = WeakReference(view)
                    bindShell(instance, view, content.id, { reference.get()?.let { tabCurrent.invoke(it) } }, preserve)
                }
                result
            }
            module.trackScene(type.getMethod("onDestroyView")).intercept { chain ->
                shells.remove(chain.thisObject)?.binding?.close()
                chain.proceed()
            }
        }
        module.trackScene(root.getDeclaredMethod("setUserVisibleHint", Boolean::class.javaPrimitiveType)).intercept { chain ->
            owner(chain.thisObject!!)?.let { pageFor(it).state.visible = chain.args[0] as Boolean }
            val result = chain.proceed()
            commit("visible")
            result
        }
        module.trackScene(registerView).intercept { chain ->
            val result = chain.proceed()
            val view = chain.args[0] as? View
            if (view != null) owner(chain.thisObject!!)?.let { views.bind(pageFor(it).owner, view) }
            result
        }
        module.trackScene(panel.getMethod("onViewCreated", View::class.java)).intercept { chain ->
            val result = chain.proceed()
            owner(chain.thisObject!!)?.let {
                val page = pageFor(it)
                page.binding?.close()
                page.binding = CleanSceneBinding(chain.args[0] as View, null, views, page.owner) { commit("frame") }
                    .apply { setActive(configuration != null) }
                commit("pageCreated")
            }
            result
        }
        module.trackScene(component.getMethod("onViewCreated", View::class.java)).intercept { chain ->
            val result = chain.proceed()
            val instance = chain.thisObject!!
            val context = componentPanel.invoke(componentContext.invoke(instance))
            if (context != null) panelFragment.get(context)?.let {
                components.remove(instance)?.let { old -> views.detach(old.owner) }
                val binding = Component(pageFor(it))
                components[instance] = binding
                views.replace(binding.owner, componentViews.mapNotNull { field -> field.get(instance) as? View }.toSet())
                commit("cellCreated")
            }
            result
        }
        module.trackScene(panel.getMethod("onDestroy")).intercept { chain ->
            if (panel.isInstance(chain.thisObject)) owner(chain.thisObject!!)?.let {
                pages.remove(it)?.let { page ->
                    page.binding?.close()
                    views.detach(page.owner)
                    components.entries.removeAll { binding ->
                        if (binding.value.page !== page) false else { views.detach(binding.value.owner); true }
                    }
                    pagers.entries.removeAll { binding -> binding.value.page === page }
                }
            }
            chain.proceed()
        }
        installViewHooks(module)

        val pager = classLoader.loadClass("com.ss.android.ugc.aweme.common.widget.VerticalViewPager")
        val listPanel = classLoader.loadClass("com.ss.android.ugc.aweme.feed.panel.BaseListFragmentPanel")
        listFragment = listPanel.getMethod("getFragment")
        val pagerField = listPanel.declaredFields.single { it.type == pager }.apply { isAccessible = true }
        val scrollState = pager.getMethod("getScrollState")
        val currentItem = pager.getMethod("getCurrentItem")
        val selectItem = pager.declaredMethods.single {
            it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(
                Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        }
        val listPanelType = classLoader.loadClass("com.ss.android.ugc.aweme.feed.panel.IBaseListFragmentPanel")
        currentHolder = listPanelType.declaredMethods.single {
            it.parameterCount == 0 && it.returnType.isInterface && it.returnType.isAssignableFrom(videoCell) &&
                it.returnType.methods.any { method -> method.name == "onPageSelected" &&
                    method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) }
        }
        installPageStatusHooks(module, classLoader)
        val aweme = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val playback = CleanPlaybackContract(HostSymbols.resolve(classLoader, HostSymbol.PLAYER_CONTROLLER), listPanelType, aweme)
        for (method in listOf(playback.handle, playback.pause)) {
            module.trackScene(method).intercept { chain ->
                val result = chain.proceed()
                playbackStopped(playback, chain.thisObject!!, chain.args.firstOrNull())
                result
            }
        }
        module.trackScene(listPanel.getMethod("onViewCreated", View::class.java, Bundle::class.java)).intercept { chain ->
            val instance = chain.thisObject!!
            val page = listPage(instance)
            val result = chain.proceed()
            if (page != null) pagerField.get(instance)?.let { pagers[it] = Pager(page) }
            commit("listCreated")
            result
        }
        module.trackScene(pager.getMethod("setScrollState", Int::class.javaPrimitiveType)).intercept { chain ->
            val binding = pagers[chain.thisObject]
            val moving = chain.args[0] != 0
            if (moving && binding != null) beginTransition(binding)
            val result = chain.proceed()
            if (!moving && binding != null) settle(binding)
            result
        }
        module.trackScene(selectItem).intercept { chain ->
            val binding = pagers[chain.thisObject]
            if (binding != null && chain.args[0] != currentItem.invoke(chain.thisObject)) beginTransition(binding)
            val result = chain.proceed()
            if (binding != null && scrollState.invoke(chain.thisObject) == 0) settle(binding)
            result
        }
        module.trackScene(videoCell.getMethod("onPageSelected", Int::class.javaPrimitiveType)).intercept { chain ->
            page(chain.thisObject!!)?.let { selectCurrent(it) }
            commit("selected")
            chain.proceed()
        }
        for (method in listOf(videoCell.getMethod(HostSymbols.member(HostSymbol.VIDEO_CELL, "unselect"), Boolean::class.javaPrimitiveType),
            videoCell.getMethod("unBind"), videoCell.getMethod(HostSymbols.member(HostSymbol.VIDEO_CELL, "bind"), aweme))) {
            module.trackScene(method).intercept { chain ->
                releaseCell(chain.thisObject!!)
                chain.proceed()
            }
        }
        for (method in listOf(videoCell.getMethod("onPausePlay", String::class.java)) +
            videoCell.declaredMethods.filter { it.name == "onPlayFailed" && !it.isSynthetic }) {
            module.trackScene(method).intercept { chain ->
                pause(chain.thisObject!!, method.name == "onPlayFailed")
                chain.proceed()
            }
        }
        val playing = mutableSetOf(videoCell.getMethod("onResumePlay", String::class.java))
        for (type in listOf(videoCell, classLoader.loadClass("com.ss.android.ugc.aweme.feed.adapter.FullFeedVideoViewHolder"))) {
            playing.add(type.getMethod("onPreparePlay", String::class.java))
            playing.addAll(type.declaredMethods.filter { it.name == "onRenderFirstFrame" && !it.isSynthetic })
        }
        for (method in playing) {
            module.trackScene(method).intercept { chain ->
                startPlayback(chain.thisObject!!, method.name == "onPreparePlay")
                chain.proceed()
            }
        }
        HookRuntime.subscribe("AutoCleanModeHook", ::configurationChanged)
    }

    /** 已验证的配置事件负责显示状态；绘制提交不再重新读取可能失效的存储。 */
    private fun configurationChanged(snapshot: ConfigSnapshot?) {
        configuration = snapshot
        if (snapshot == null) {
            commit("configurationUnavailable")
            pages.values.forEach { it.binding?.setActive(false) }
            shells.values.forEach { it.binding.setActive(false) }
        } else {
            pages.values.forEach { it.binding?.setActive(true) }
            shells.values.forEach { it.binding.setActive(true) }
            commit("configuration")
            HookRuntime.appliedConfiguration("AutoCleanModeHook", snapshot, snapshot.boolean(KEY_CLEAN_MODE_ON_PLAY))
        }
    }

    /** 撤销功能安装同时释放页面监听、显示所有权及播放身份。 */
    private fun dispose() {
        configuration = null
        pages.values.forEach { it.binding?.close(); views.detach(it.owner) }
        shells.values.forEach { it.binding.close() }
        components.values.forEach { views.detach(it.owner) }
        pages.clear()
        shells.clear()
        components.clear()
        pagers.clear()
    }

    /** 生命周期与播放身份继续跟踪，配置事件统一决定是否有显示修改。 */
    private fun XposedModule.trackScene(executable: Executable): TrackedHook =
        trackHook("AutoCleanModeHook", executable, requiresConfiguration = false)

    /**
     * 仅约束已登记容器，普通视图和视频表面不进入管理表。
     * @param module libxposed 模块。
     * @return Unit。
     * Callers: init。
     */
    private fun installViewHooks(module: XposedModule) {
            val handle = module.hook(View::class.java.getMethod("setVisibility", Int::class.javaPrimitiveType))
                .setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept { chain ->
                    if (Looper.myLooper() != Looper.getMainLooper()) return@intercept chain.proceed()
                    val requested = chain.args[0] as Int
                    val view = chain.thisObject as View
                    val effective = views.visibility(view, requested)
                    if (effective != requested) chain.proceed(arrayOf(effective)) else chain.proceed()
                }
            HookRuntime.registered("AutoCleanModeHook", handle)
    }

    /**
     * 消费宿主空列表的加载、空内容和网络失败决策，状态界面使用宿主自己的显示流程。
     * @param module libxposed 模块。
     * @param loader 宿主类加载器。
     * @return Unit；有当前内容时不把追加加载失败误认为整页失败。
     * Callers: init。
     */
    private fun installPageStatusHooks(module: XposedModule, loader: ClassLoader) {
        val type = loader.loadClass("com.ss.android.ugc.feed.platform.panel.pagestate.PageStateCommonComponent")
        val ability = loader.loadClass("com.ss.android.ugc.feed.platform.panel.pagestate.IPageStateAbility")
        val getter = ability.declaredMethods.single {
            it.parameterCount == 0 && View::class.java.isAssignableFrom(it.returnType) &&
                it.returnType.declaredMethods.any { method -> method.name == "setStatus" &&
                    method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) }
        }
        val statuses = WeakHashMap<Any, Page>()
        module.trackScene(type.getMethod(getter.name)).intercept { chain ->
            val result = chain.proceed()
            if (result != null) owner(chain.thisObject!!)?.let { statuses[result] = pageFor(it) }
            result
        }
        module.trackScene(getter.returnType.getMethod("setStatus", Int::class.javaPrimitiveType)).intercept { chain ->
            val result = chain.proceed()
            statuses[chain.thisObject]?.let { page ->
                if (page.list.get()?.let { currentHolder.invoke(it) } == null) {
                    val status = chain.args[0] as Int
                    if (status == 0) page.state.loading() else if (status in 1..4) page.state.clear()
                    commit("contentStatus")
                }
            }
            result
        }
        val error = type.declaredMethods.single {
            it.returnType == Void.TYPE && it.parameterCount == 2 && it.parameterTypes[0].isEnum &&
                it.parameterTypes[1] == Exception::class.java
        }
        module.trackScene(error).intercept { chain ->
            owner(chain.thisObject!!)?.let {
                val page = pageFor(it)
                if (page.list.get()?.let { panel -> currentHolder.invoke(panel) } == null) page.state.clear()
            }
            val result = chain.proceed()
            commit("contentError")
            result
        }
    }
}
