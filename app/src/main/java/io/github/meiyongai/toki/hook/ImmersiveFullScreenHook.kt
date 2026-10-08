package io.github.meiyongai.toki.hook

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import io.github.libxposed.api.XposedModule
import io.github.meiyongai.toki.provider.ConfigClient
import java.lang.reflect.Modifier

/** 扩展宿主显示区域；居中扩展保留原适配，智能铺满按比例限制裁切。 */
object ImmersiveFullScreenHook {
    private const val TAG = "TokiImmersiveFullScreen"
    const val KEY_IMMERSIVE_FULL_SCREEN = "immersive_full_screen"
    const val KEY_VIDEO_FIT_MODE = "video_fit_mode"
    private const val SCREEN = "com.ss.android.ugc.aweme.screenadaption.adaptionparams.ScreenAdaptionResult"
    private const val FEED = "com.ss.android.ugc.feed.platform.cell.component.adaption.FeedCellAdaptionComponentV2"
    private const val PADDING = "com.ss.android.ugc.aweme.videoadaption.adaptionparams.AdaptionPaddingValues"
    private const val BOTTOM = "com.ss.android.ugc.aweme.main.assems.mainfragment.BottomTabAssemBase"
    private const val MAIN_ACTIVITY = "com.ss.android.ugc.aweme.main.MainActivity"

    /**
     * 在注册前核对屏幕预留和视频区域解析器的完整类型契约。
     * @param screen 屏幕布局结果类。
     * @param area 行为规则唯一定位的区域解析类。
     * @param areaName 行为规则定位的方法名。
     * Callers: init；ImmersiveLayoutContractTest。
     */
    internal class LayoutContract(screen: Class<*>, area: Class<*>, areaName: String) {
        val screenConstructor = screen.getDeclaredConstructor(
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Integer::class.java
        ).apply { isAccessible = true }
        val reserve = area.declaredMethods.filter { it.name == areaName }.single().apply {
            check(Modifier.isStatic(modifiers) && returnType == Float::class.javaPrimitiveType &&
                parameterTypes.size == 3 && parameterTypes[0] == List::class.java &&
                parameterTypes[1] == Int::class.javaPrimitiveType) { "沉浸区域解析器签名不符合契约" }
            isAccessible = true
        }

        init {
            for (name in listOf("getTopSpaceHeight", "getBottomSpaceHeight", "getContainerHeight")) {
                val getter = screen.getMethod(name)
                check(!Modifier.isStatic(getter.modifiers) && getter.returnType == Int::class.javaPrimitiveType) {
                    "屏幕布局 getter 不符合契约：$name"
                }
            }
            for (name in listOf("getStatusBarHeight", "getTopTabHeight", "getBottomTabHeight", "getBottomBannerHeight")) {
                val getter = reserve.parameterTypes[2].getMethod(name)
                check(!Modifier.isStatic(getter.modifiers) && getter.returnType == Float::class.javaPrimitiveType) {
                    "视频区域上下文不符合契约：$name"
                }
            }
        }
    }

    /**
     * 一次核对宿主卡片内边距和底栏视图入口，不依赖混淆方法名或资源 ID。
     * @param feed 卡片布局组件。
     * @param padding 内边距值类型。
     * @param bottom 底栏装配组件。
     * @param paddingNames 已按行为验证的两个布局输入方法名。
     * Callers: init。
     */
    internal class ViewContract(feed: Class<*>, padding: Class<*>, bottom: Class<*>, paddingNames: List<String>) {
        val paddingConstructor = padding.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        val paddingInputs = paddingNames.also { check(it.size == 2 && it.distinct().size == 2) }
            .map { name -> feed.getDeclaredMethod(name).apply {
                check(returnType == padding && !Modifier.isStatic(modifiers)) { "卡片布局输入类型不符合契约：$name" }
                isAccessible = true
            } }
        val bottomView = bottom.declaredMethods.filter {
            it.parameterCount == 0 && it.returnType == ViewGroup::class.java && !Modifier.isStatic(it.modifiers)
        }.single().apply { isAccessible = true }
    }

    /** @return 当前会话的沉浸开关。无参数。Callers: init 中注册的 Hook。 */
    private fun enabled(): Boolean = ConfigClient.getBoolean(KEY_IMMERSIVE_FULL_SCREEN)

    /**
     * 完成全部契约检查后注册输入修正和首页底栏/窗口处理。
     * @param module 模块 Hook 上下文。
     * @param classLoader 宿主类加载器。
     * @return Unit。
     * Callers: TokiModule.installHost。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        val layout = LayoutContract(classLoader.loadClass(SCREEN),
            HostSymbols.resolve(classLoader, HostSymbol.RESERVED_AREA),
            HostSymbols.member(HostSymbol.RESERVED_AREA, "resolve"))
        val feed = HostSymbols.resolve(classLoader, HostSymbol.FEED_ADAPTION)
        check(feed.name == FEED) { "卡片布局输入所属类不符合契约" }
        val views = ViewContract(feed, classLoader.loadClass(PADDING), classLoader.loadClass(BOTTOM),
            listOf(HostSymbols.member(HostSymbol.FEED_ADAPTION, "paddingContent"),
                HostSymbols.member(HostSymbol.FEED_ADAPTION, "paddingViewport")))
        val mainActivity = classLoader.loadClass(MAIN_ACTIVITY)
        check(Activity::class.java.isAssignableFrom(mainActivity)) { "首页窗口所属类型不符合契约" }
        val resume = Activity::class.java.getDeclaredMethod("onPostResume").apply { isAccessible = true }
        val photos = ImmersivePhotoLayout(HostSymbols.resolve(classLoader, HostSymbol.PHOTO_LAYOUT),
            HostSymbols.member(HostSymbol.PHOTO_LAYOUT, "plan"))
        val video = if (ConfigClient.getString(KEY_VIDEO_FIT_MODE, "center") == "smart") SmartVideoLayout(feed,
            classLoader.loadClass("com.ss.android.ugc.aweme.videoadaption.adaptionparams.VideoAdaptionResult"),
            classLoader.loadClass("com.ss.android.ugc.aweme.videoadaption.adaptionparams.resultoperator.MultiContainerThresholdResultOperator")) else null

        if (video != null) module.trackHook("ImmersiveFullScreenHook", video.apply).intercept { chain ->
            if (enabled()) {
                val args = chain.args.toTypedArray()
                val previous = video.lastGeometry
                args[0] = video.replace(args[0])
                if (previous != video.lastGeometry) {
                    HookRuntime.detail("ImmersiveFullScreenHook", video.lastGeometry)
                    HookRuntime.event(TAG, video.lastGeometry)
                }
                chain.proceed(args)
            } else chain.proceed()
        }

        module.trackHook("ImmersiveFullScreenHook", layout.screenConstructor).intercept { chain ->
            if (enabled()) {
                val args = chain.args.toTypedArray()
                args[0] = 0
                args[1] = 0
                chain.proceed(args)
            } else chain.proceed()
        }
        module.trackHook("ImmersiveFullScreenHook", layout.reserve).intercept { chain ->
            if (enabled()) 0.0f else chain.proceed()
        }
        for (input in views.paddingInputs) {
            module.trackHook("ImmersiveFullScreenHook", input).intercept { chain ->
                if (enabled()) views.paddingConstructor.newInstance(0, 0, 0, 0) else chain.proceed()
            }
        }
        module.trackHook("ImmersiveFullScreenHook", photos.calculate).intercept { chain ->
            if (enabled() && (1..4).all { (chain.args[it] as Int) > 0 }) {
                photos.centered(chain.args[1] as Int, chain.args[2] as Int,
                    chain.args[3] as Int, chain.args[4] as Int)
            } else chain.proceed()
        }
        module.trackHook("ImmersiveFullScreenHook", views.bottomView).intercept { chain ->
            val view = chain.proceed() as? ViewGroup
            if (view != null && enabled()) view.background = null
            view
        }
        module.trackHook("ImmersiveFullScreenHook", resume).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as Activity
            if (mainActivity.isInstance(activity) && enabled()) applyEdgeToEdge(activity)
            result
        }
        Log.i(TAG, "沉浸布局入口已注册：${layout.reserve.declaringClass.name}.${layout.reserve.name}；卡片输入=${views.paddingInputs.size}；图文=${photos.calculate.declaringClass.name}.${photos.calculate.name}")
    }

    /** @param context 宿主应用上下文。@return Unit。Callers: TokiModule.hookApplication。 */
    fun refreshConfig(context: Context) {
        ConfigClient.init(context)
    }

    /**
     * 允许首页内容绘制到系统栏区域，不隐藏系统栏、不修改其他 Activity。
     * @param activity 已确认的宿主首页 Activity。
     * @return Unit。
     * Callers: init 中注册的 onPostResume Hook。
     */
    private fun applyEdgeToEdge(activity: Activity) {
        val window = activity.window
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
