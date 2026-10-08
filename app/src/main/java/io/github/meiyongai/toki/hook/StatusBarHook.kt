package io.github.meiyongai.toki.hook

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * 系统状态栏隐藏控制管理系统。
 *
 * 依据宿主 TikTok 的页面架构与播放生命周期，实现“视频播放处隐藏状态栏，其它地方则不隐藏”的核心逻辑。
 *
 * 信号调度模型：
 * 1. 播放流激活（[com.ss.android.ugc.aweme.feed.adapter.VideoViewCell] 首帧渲染与切页选中）：
 *    在视频卡片处于可视视口并渲染出首帧或被选中时触发隐藏（需同时满足未展开作者主页且处于播放态）。
 * 2. 详情播放页（[com.ss.android.ugc.aweme.detail.ui.DetailActivity] 前台生命周期）：
 *    独立的全屏视频详情播放窗口触发隐藏。
 * 3. 导航栏 Tab 切换（[com.ss.android.ugc.aweme.ui.FragmentTabHost.onTabChanged]）：
 *    离开首页视频流切换到个人主页、收件箱、商城等非视频页面时立即恢复显示状态栏；切回首页流时隐藏。
 * 4. 个人主页视口展示与回退（[com.ss.android.ugc.profile.platform.framework.aweme.profile.ui.I18nAbsProfileFragmentV2.setUserVisibleHint]）：
 *    由首页向左横划呼出作者主页时立即恢复显示状态栏并标记处于作者主页；向右划回推荐流时重置标记并即刻隐藏状态栏。
 * 5. 视频播放与暂停联动（[com.ss.android.ugc.aweme.feed.adapter.VideoViewCell.onPausePlay] / [onResumePlay]）：
 *    当前卡片主动暂停时立即恢复显示状态栏；恢复播放时重新隐藏状态栏；切页切流时通过活跃卡片对齐避免闪烁。
 * 6. 窗口前台求值（[Activity.onResume]）：
 *    由视频详情播放页返回主界面时，若仍停留于作者主页或处于暂停态则保持状态栏显示，处于推荐视频流且播放中时隐藏状态栏，常规 Activity 统一显示状态栏。
 * 7. 直播观看页（LivePlayActivity）：独立按功能开关隐藏，在恢复前台、窗口焦点和配置变化后重新求值。
 */
object StatusBarHook {

    private const val TAG = "TokiStatusBar"

    /** 隐藏系统状态栏功能配置项键名 */
    const val KEY_STATUS_BAR_HIDDEN = "status_bar_hidden"

    /** 主界面底部导航首页标识 */
    private const val TAB_HOME = "HOME"
    private const val LIVE_PLAY_ACTIVITY = "com.ss.android.ugc.aweme.live.LivePlayActivity"

    /** 记录主界面当前活动的导航 Tab 标签，默认初始化为首页 */
    @Volatile
    private var currentMainTab: String = TAB_HOME

    /** 记录作者主页或个人主页等非视频侧滑页面当前是否处于视口可见状态 */
    @Volatile
    private var isProfileVisible: Boolean = false

    /** 活跃视频播放卡片弱引用，用于区分当前视频主动暂停与切流离屏停播 */
    @Volatile
    private var activeCell: WeakReference<Any>? = null

    /** 记录当前视频是否处于暂停播放状态 */
    @Volatile
    private var isVideoPaused: Boolean = false

    /** 记录当前视频流是否正处于切页翻页滑动状态中 */
    @Volatile
    private var isPageScrolling: Boolean = false

    /**
     * 查询系统状态栏隐藏功能是否已在配置中心启用。
     *
     * Returns:
     *     Boolean: true 为已启用隐藏状态栏，false 为未启用。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.evaluate`: 窗口回到前台求值时。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.handleVideoPlay`: 视频流渲染或选中时。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.handleTabChange`: 底部导航切换时。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.hookProfileVisibility`: 个人主页视口变化时。
     */
    private fun hiddenEnabled(): Boolean = ConfigClient.getBoolean(KEY_STATUS_BAR_HIDDEN)

    /**
     * 挂载系统状态栏精准控制 Hook 系统。
     *
     * 分别注册视频播放卡片生命周期、视频流滑动状态、底部导航切换生命周期、作者主页滑入监听以及 Activity 回到前台的联动通道。
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
        hookVideoCellPlayback(module, classLoader)
        hookPageScrollState(module, classLoader)
        hookFragmentTabHost(module, classLoader)
        hookProfileVisibility(module, classLoader)
        hookActivityResume(module, classLoader)
        hookLivePlayback(module, classLoader)
    }

    /** 直播不经过 VideoViewCell；在直播自身完成窗口生命周期处理后应用同一显隐规则。 */
    private fun hookLivePlayback(module: XposedModule, classLoader: ClassLoader) {
        val liveClass = classLoader.loadClass(LIVE_PLAY_ACTIVITY)
        val methods = listOf(
            liveClass.getDeclaredMethod("onResume"),
            liveClass.getDeclaredMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType),
            liveClass.getDeclaredMethod("onConfigurationChanged", android.content.res.Configuration::class.java)
        )
        for (method in methods) {
            module.trackHook("StatusBarHook", method).intercept { chain ->
                val result = chain.proceed()
                if (method.name != "onWindowFocusChanged" || chain.args[0] == true) {
                    evaluate(chain.thisObject as Activity)
                }
                result
            }
        }
    }

    /** 视频卡片根视图获取方法引用 */
    private lateinit var cellGetRootViewMethod: Method

    /**
     * 挂载视频播放卡片生命周期监听。
     *
     * 针对 [com.ss.android.ugc.aweme.feed.adapter.VideoViewCell] 的首帧渲染、选卡、暂停与恢复方法进行拦截，
     * 确保当视频实际进入视口播放时隐藏系统状态栏，暂停时恢复显示。
     *
     * Args:
     *     module (XposedModule): LSPosed 注入上下文实例。
     *     classLoader (ClassLoader): 宿主类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.init`: 模块初始化时调用。
     */
    private fun hookVideoCellPlayback(module: XposedModule, classLoader: ClassLoader) {
        val cellClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.adapter.VideoViewCell")

        cellGetRootViewMethod = cellClass.getMethod("getRootView")

        val renderMethods = cellClass.declaredMethods.filter { method ->
            method.name == "onRenderFirstFrame" && method.parameterTypes.size == 1
        }
        check(renderMethods.isNotEmpty()) { "视频卡片缺少首帧通知入口" }
        for (method in renderMethods) {
            method.isAccessible = true
            module.trackHook("StatusBarHook", method).intercept { chain ->
                val result = chain.proceed()
                handleVideoPlay(chain.thisObject)
                result
            }
        }

        cellClass.getDeclaredMethod("onPageSelected", Int::class.javaPrimitiveType).let { method ->
            method.isAccessible = true
            module.trackHook("StatusBarHook", method).intercept { chain ->
                val result = chain.proceed()
                handleVideoPlay(chain.thisObject)
                result
            }
        }

        cellClass.getDeclaredMethod("onPausePlay", String::class.java).let { method ->
            method.isAccessible = true
            module.trackHook("StatusBarHook", method).intercept { chain ->
                val result = chain.proceed()
                handleVideoPause(chain.thisObject)
                result
            }
        }

        cellClass.getDeclaredMethod("onResumePlay", String::class.java).let { method ->
            method.isAccessible = true
            module.trackHook("StatusBarHook", method).intercept { chain ->
                val result = chain.proceed()
                handleVideoResume(chain.thisObject)
                result
            }
        }

        Log.i(TAG, "视频卡片播放与暂停生命周期 Hook 挂载完成")
    }

    /**
     * 挂载视频流切页翻页滑动状态生命周期监听。
     *
     * 针对 [com.ss.android.ugc.aweme.feed.controller.PlayerController.onPageScrollStateChanged] 进行拦截，
     * 实时跟踪当前视频流是否处于用户手指拖动或惯性滚动切页中，从源头上区分切流停播与主动暂停。
     * 卡片互动区域的可见性参数不是滚动状态，不用于判断翻页。
     *
     * Args:
     *     module (XposedModule): LSPosed 注入上下文实例。
     *     classLoader (ClassLoader): 宿主类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.init`: 模块初始化时调用。
     */
    private fun hookPageScrollState(module: XposedModule, classLoader: ClassLoader) {
        val playerControllerClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.controller.PlayerController")
        val method = playerControllerClass.getDeclaredMethod("onPageScrollStateChanged", Int::class.javaPrimitiveType)
        method.isAccessible = true
        module.trackHook("StatusBarHook", method).intercept { chain ->
            val result = chain.proceed()
            isPageScrolling = (chain.args[0] as Int) != 0
            result
        }
        Log.i(TAG, "PlayerController.onPageScrollStateChanged Hook 挂载完成")
    }

    /**
     * 挂载底部导航切换生命周期监听。
     *
     * 针对 [com.ss.android.ugc.aweme.ui.FragmentTabHost.onTabChanged] 进行拦截，
     * 当用户离开首页视频流切换至个人主页、收件箱、商城等非视频区域时，立即恢复状态栏显示。
     *
     * Args:
     *     module (XposedModule): LSPosed 注入上下文实例。
     *     classLoader (ClassLoader): 宿主类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.init`: 模块初始化时调用。
     */
    private fun hookFragmentTabHost(module: XposedModule, classLoader: ClassLoader) {
        val tabHostClass = classLoader.loadClass("com.ss.android.ugc.aweme.ui.FragmentTabHost")

        val onTabChangedMethod = tabHostClass.getDeclaredMethod("onTabChanged", String::class.java)
        onTabChangedMethod.isAccessible = true

        module.trackHook("StatusBarHook", onTabChangedMethod).intercept { chain ->
            val result = chain.proceed()
            val tabId = chain.args[0] as? String ?: return@intercept result
            val view = chain.thisObject as? View ?: return@intercept result
            val activity = activityOf(view.context) ?: return@intercept result
            handleTabChange(activity, tabId)
            result
        }

        Log.i(TAG, "底部导航切换 Hook 挂载完成")
    }

    /**
     * 挂载个人主页视口可见性监听。
     *
     * 针对 [com.ss.android.ugc.profile.platform.framework.aweme.profile.ui.I18nAbsProfileFragmentV2.setUserVisibleHint] 进行拦截，
     * 当从视频流侧滑呼出作者主页或切换至个人主页时，保证状态栏立即显示。
     *
     * Args:
     *     module (XposedModule): LSPosed 注入上下文实例。
     *     classLoader (ClassLoader): 宿主类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.init`: 模块初始化时调用。
     */
    private fun hookProfileVisibility(module: XposedModule, classLoader: ClassLoader) {
        val profileClass = classLoader.loadClass("com.ss.android.ugc.profile.platform.framework.aweme.profile.ui.I18nAbsProfileFragmentV2")

        val setUserVisibleHintMethod = profileClass.getDeclaredMethod("setUserVisibleHint", Boolean::class.javaPrimitiveType)
        setUserVisibleHintMethod.isAccessible = true

        val getActivityMethod = profileClass.getMethod("getActivity")
        getActivityMethod.isAccessible = true

        module.trackHook("StatusBarHook", setUserVisibleHintMethod).intercept { chain ->
            val result = chain.proceed()
            val isVisibleToUser = chain.args[0] as? Boolean ?: return@intercept result
            isProfileVisible = isVisibleToUser
            val fragment = chain.thisObject
            val activity = getActivityMethod.invoke(fragment) as? Activity
            if (activity != null) {
                if (isVisibleToUser) {
                    sync(activity, false)
                } else {
                    if (isMainActivity(activity) && currentMainTab == TAB_HOME) {
                        val shouldHide = hiddenEnabled() && !isVideoPaused
                        sync(activity, shouldHide)
                    }
                }
            }
            result
        }

        Log.i(TAG, "个人主页可见性 Hook 挂载完成")
    }

    /**
     * 挂载 Activity 回到前台生命周期监听。
     *
     * 在窗口回到前台时，区分全屏详情播放页、主界面及普通非播放页，统一精准求值。
     *
     * Args:
     *     module (XposedModule): LSPosed 注入上下文实例。
     *     classLoader (ClassLoader): 宿主类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.init`: 模块初始化时调用。
     */
    private fun hookActivityResume(module: XposedModule, classLoader: ClassLoader) {
        val activityClass = classLoader.loadClass("android.app.Activity")

        val onResumeMethod = activityClass.getDeclaredMethod("onResume")
        onResumeMethod.isAccessible = true

        module.trackHook("StatusBarHook", onResumeMethod).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity ?: return@intercept result
            evaluate(activity)
            result
        }

        Log.i(TAG, "窗口前台生命周期 Hook 挂载完成")
    }

    /**
     * 处理视频播放卡片进入播放渲染态的求值。
     *
     * 标记当前视频卡片为活跃卡片，重置暂停标志，若处于视频播放页面（详情播放页或主界面首页推荐流且未展示作者主页），则隐藏系统状态栏。
     *
     * Args:
     *     cellObject (Any): [com.ss.android.ugc.aweme.feed.adapter.VideoViewCell] 实例对象。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.hookVideoCellPlayback`: 首帧渲染与选卡拦截触发。
     */
    private fun handleVideoPlay(cellObject: Any) {
        activeCell = WeakReference(cellObject)
        isVideoPaused = false
        val activity = resolveActivityFromCell(cellObject) ?: return
        if (isDetailActivity(activity)) {
            sync(activity, hiddenEnabled())
        } else if (isMainActivity(activity)) {
            if (currentMainTab == TAB_HOME && !isProfileVisible) {
                sync(activity, hiddenEnabled())
            }
        }
    }

    /**
     * 处理视频播放卡片暂停播放事件。
     *
     * 若当前处于翻页滑动切流中（[isPageScrolling] 为 true），则本次停播属于卡片滑出视口的切流过渡，直接忽略；
     * 仅在视口静止（用户主动暂停播放或展开弹窗停播）且暂停卡片为当前活跃卡片时，更新暂停状态并恢复显示系统状态栏。
     *
     * Args:
     *     cellObject (Any): [com.ss.android.ugc.aweme.feed.adapter.VideoViewCell] 实例对象。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.hookVideoCellPlayback`: onPausePlay 拦截触发。
     */
    private fun handleVideoPause(cellObject: Any) {
        if (isPageScrolling) {
            return
        }
        val currentActive = activeCell?.get()
        if (currentActive == null || currentActive === cellObject) {
            isVideoPaused = true
            val activity = resolveActivityFromCell(cellObject) ?: return
            if (isDetailActivity(activity) || (isMainActivity(activity) && currentMainTab == TAB_HOME)) {
                sync(activity, false)
            }
        }
    }

    /**
     * 处理视频播放卡片恢复播放事件。
     *
     * 标记活跃卡片并解除暂停状态，重新执行状态栏隐藏逻辑。
     *
     * Args:
     *     cellObject (Any): [com.ss.android.ugc.aweme.feed.adapter.VideoViewCell] 实例对象。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.hookVideoCellPlayback`: onResumePlay 拦截触发。
     */
    private fun handleVideoResume(cellObject: Any) {
        activeCell = WeakReference(cellObject)
        isVideoPaused = false
        val activity = resolveActivityFromCell(cellObject) ?: return
        if (isDetailActivity(activity)) {
            sync(activity, hiddenEnabled())
        } else if (isMainActivity(activity)) {
            if (currentMainTab == TAB_HOME && !isProfileVisible) {
                sync(activity, hiddenEnabled())
            }
        }
    }

    /**
     * 处理底部导航 Tab 切换事件。
     *
     * Args:
     *     activity (Activity): 主界面所属 Activity。
     *     tabId (String): 目标 Tab 标识（如 "HOME", "USER", "NOTIFICATION", "MALL" 等）。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.hookFragmentTabHost`: 导航切换拦截触发。
     */
    private fun handleTabChange(activity: Activity, tabId: String) {
        currentMainTab = tabId
        if (tabId == TAB_HOME) {
            if (!isProfileVisible && !isVideoPaused) {
                sync(activity, hiddenEnabled())
            } else {
                sync(activity, false)
            }
        } else {
            isProfileVisible = false
            sync(activity, false)
        }
    }

    /**
     * 目标应用 Application 上下文下的配置刷新同步入口。
     *
     * 配置由 [ConfigClient] 统一管理并热更新，所有通道实时读取最新配置。
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
        // 配置由 ConfigClient 统一管理与跨进程分发
    }

    /**
     * 对回到前台的目标窗口进行状态栏显隐状态的精准求值。
     *
     * 判定逻辑：
     * 1. 若为全屏沉浸播放页（DetailActivity），未暂停时按配置开关执行隐藏，暂停时恢复显示；
     * 2. 若为主界面（MainActivity），仅在活动 Tab 为首页（HOME）、未展示作者主页且未暂停时按开关执行隐藏，其它状态强制恢复显示；
     * 3. 直播观看页独立按开关隐藏，不继承后台短视频暂停状态；其它常规页面恢复显示。
     *
     * Args:
     *     activity (Activity): 回到前台的窗口所属 Activity。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.hookActivityResume`: Activity.onResume 触发时。
     */
    private fun evaluate(activity: Activity) {
        sync(activity, shouldHideStatusBar(activity.javaClass.name, hiddenEnabled(),
            currentMainTab, isProfileVisible, isVideoPaused))
    }

    /** 直播窗口不继承后台短视频卡片的暂停、Tab 或作者主页状态。 */
    internal fun shouldHideStatusBar(
        activityName: String, enabled: Boolean, tab: String, profileVisible: Boolean, videoPaused: Boolean
    ): Boolean = enabled && when {
        activityName == LIVE_PLAY_ACTIVITY -> true
        activityName.endsWith(".DetailActivity") -> !videoPaused
        activityName.endsWith(".MainActivity") -> tab == TAB_HOME && !profileVisible && !videoPaused
        else -> false
    }

    /**
     * 判定目标 Activity 是否为视频详情播放页。
     *
     * Args:
     *     activity (Activity): 待判定的 Activity 实例。
     *
     * Returns:
     *     Boolean: true 为视频详情播放页，false 为其它页面。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.handleVideoPlay`: 视频播放判定。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.evaluate`: 窗口前台求值判定。
     */
    private fun isDetailActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name == "com.ss.android.ugc.aweme.detail.ui.DetailActivity" ||
            name.endsWith(".DetailActivity")
    }

    /**
     * 判定目标 Activity 是否为宿主主界面。
     *
     * Args:
     *     activity (Activity): 待判定的 Activity 实例。
     *
     * Returns:
     *     Boolean: true 为宿主主界面，false 为其它页面。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.handleVideoPlay`: 视频播放判定。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.evaluate`: 窗口前台求值判定。
     */
    private fun isMainActivity(activity: Activity): Boolean {
        val name = activity.javaClass.name
        return name == "com.ss.android.ugc.aweme.main.MainActivity" ||
            name.endsWith(".MainActivity")
    }

    /**
     * 从 VideoViewCell 实例对象中解析宿主 Activity。
     *
     * Args:
     *     cellObject (Any): 视频卡片实例。
     *
     * Returns:
     *     Activity?: 宿主 Activity，无法解析时返回 null。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.handleVideoPlay`: 提取所属 Activity 时。
     */
    private fun resolveActivityFromCell(cellObject: Any): Activity? {
        val method = cellGetRootViewMethod
        val view = method.invoke(cellObject) as? View ?: return null
        return activityOf(view.context)
    }

    /**
     * 从 Context 链递归解析宿主 Activity。
     *
     * Args:
     *     context (Context): 视图或组件持有的上下文环境。
     *
     * Returns:
     *     Activity?: 宿主 Activity，上下文链中不存在 Activity 时返回 null。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.hookFragmentTabHost`: 导航宿主解析。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.resolveActivityFromCell`: 卡片宿主解析。
     */
    private fun activityOf(context: Context): Activity? {
        var ctx: Context = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * 将目标 Activity 窗口的系统状态栏同步为目标显隐状态。
     *
     * 在 Android 11 (API 30) 及以上版本使用 [WindowInsetsController] 控制状态栏层显隐，
     * 并配置 [WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE] 保留下滑手势临时呼出特性；
     * 在低版本系统中使用标准沉浸式 systemUiVisibility 旗标进行控制。
     *
     * Args:
     *     activity (Activity): 目标窗口所属 Activity。
     *     hide (Boolean): true 隐藏状态栏，false 恢复显示状态栏。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.handleVideoPlay`: 视频播放时同步。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.handleTabChange`: 导航切换时同步。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.hookProfileVisibility`: 个人主页展示时同步。
     *     - `io.github.meiyongai.toki.hook.StatusBarHook.evaluate`: 窗口回到前台时同步。
     */
    private fun sync(activity: Activity, hide: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }
        val window = activity.window ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hide) {
                controller.hide(WindowInsets.Type.statusBars())
            } else {
                controller.show(WindowInsets.Type.statusBars())
            }
        } else {
            val decorView = window.decorView
            val fullscreen = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility = if (hide) {
                decorView.systemUiVisibility or fullscreen
            } else {
                decorView.systemUiVisibility and fullscreen.inv()
            }
        }
    }
}
