package io.github.meiyongai.toki.hook

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule

/**
 * 全屏沉浸播放 Hook 处理器。
 *
 * ═══════════════ 设计总纲：屏幕是画布，视口自适应铺满 ═══════════════
 *
 * 沉浸的目标形态：屏幕即画布，视频自适应铺满——比例与画布一致的自然铺满，
 * 接近的按 cover 外扩盒铺满（轻微裁边），悬殊的（1:1、16:9 横屏等）按
 * 最大内接盒（contain）居中完整呈现。各级只有一个输入修正点，
 * 全部由宿主自身代码原生完成布局，不做任何输出劫持：
 *
 * 1. [hookScreenAdaptionResult]（屏幕级）：告知宿主屏幕顶部与底部均无预留
 *    （top=0, bottom=0），ScreenAdaptionResult.containerHeight 即物理全屏，
 *    信息流容器与 ViewPager 包裹层由宿主自行纵贯，底部占位条随之归零。
 *    [hookCanvasReserveZero] 进一步把多容器配置按服务端掩码折算的
 *    状态栏/顶栏/底栏预留面积归零，使所有容器变体的 adjustContainer
 *    即物理全屏——画布与屏幕重合，这是视口铺满的前提。
 * 2. [hookCellFullBleed]（卡片级）：FeedCell 卡片内边距统一归零，
 *    视频/照片卡片不蚕食画布，与 1 共同构成"画布铺满"的两级输入修正。
 * 3. [hookVideoContainViewport]（视频级）：所有引擎产出最终收敛于
 *    CellAdaptionComponentV2.Kr（视图落盘器 X.09H5 的全部调用都在其内）。
 *    在该收敛点把引擎裁定统一规范化为视口：自适应＝面积差 ≤ 1/4 取 cover
 *    外扩盒铺满（抖音同款观感）、悬殊取 contain 内接盒完整，加零位移
 *    （落盘视图 layout_gravity=CENTER，gravity 原生居中，零位移同时抵消
 *    引擎按 TOP/黑边率配置写入的偏移）。原生 MATCH_PARENT
 *    （动态模糊模式：容器直通 + 分发层重推契约）原样放行。
 * 4. [hookPhotoSlideFill]：照片类页卡由宿主照片管线自适应决策缩放
 *    （服务端整图标志 + 比例阈值 ⇒ 近比例 CENTER_CROP 铺满、悬殊 FIT_CENTER
 *    完整呈现于原生 blurhash 虚化背景），与视频语义天然同构，模块不做干预；
 *    唯一修正是卡片高度百分比统一为 1.0，照片卡片贯通画布全高。
 *
 * 其余 Hook（底栏透明化 / 横屏门控 / Edge-to-Edge）均为沉浸意图下
 * 宿主原生行为的放行或最小修正。
 */
object ImmersiveFullScreenHook {

    private const val TAG = "TokiImmersiveFullScreen"

    /** 全屏沉浸播放功能配置项键名 */
    const val KEY_IMMERSIVE_FULL_SCREEN = "immersive_full_screen"

    /** 自适应铺满阈值：|视频比例 − 画布比例| / 画布比例 ≤ 1/4 时 cover 铺满，悬殊时 contain 完整 */
    private const val COVER_AREA_DIFF_NUMERATOR = 4L

    private fun immersiveEnabled(): Boolean = ConfigClient.getBoolean(KEY_IMMERSIVE_FULL_SCREEN)

    /**
     * 初始化挂载全屏沉浸播放核心 Hook 链。
     *
     * 按已识别构建注册对应入口；解析错误向框架报告，不将异常解释为注册成功。
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
        hookScreenAdaptionResult(module, classLoader)
        hookCanvasReserveZero(module, classLoader)
        hookCellFullBleed(module, classLoader)
        hookVideoContainViewport(module, classLoader)
        hookBottomTabTransparency(module, classLoader)
        hookLandscapeService(module, classLoader)
        hookPhotoSlideFill(module, classLoader)
        Log.i(TAG, "全屏沉浸播放 Hook 初始化挂载就绪")
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


    // ═══════════════ Hook 一：屏幕上下预留归零 ═══════════════

    /**
     * 挂载屏幕自适应结果中枢决策 Hook。
     *
     * 拦截 [ScreenAdaptionResult] 构造函数，将顶部预留与底部预留全部归零：
     * containerHeight = screenHeight，信息流容器（含照片类页卡）天然物理全屏。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.init`: 模块初始化阶段挂载。
     */
    private fun hookScreenAdaptionResult(module: XposedModule, classLoader: ClassLoader) {
        val resultClass = classLoader.loadClass("com.ss.android.ugc.aweme.screenadaption.adaptionparams.ScreenAdaptionResult")
        val constructor = resultClass.declaredConstructors.firstOrNull { it.parameterTypes.size == 7 } ?: return
        constructor.isAccessible = true

        module.trackHook("ImmersiveFullScreenHook", constructor).intercept { chain ->
            if (immersiveEnabled()) {
                // libxposed 现代化 API 的 getArgs() 为不可变列表，
                // 必须通过 proceed(Object[]) 以替换后的参数数组继续调用
                val newArgs = chain.args.toTypedArray()
                newArgs[0] = 0
                newArgs[1] = 0
                return@intercept chain.proceed(newArgs)
            }
            chain.proceed()
        }
        Log.i(TAG, "已成功挂载 ScreenAdaptionResult 上下预留归零拦截")
    }

    // ═══════════════ Hook 一·B：容器配置预留面积归零 ═══════════════

    /**
     * 挂载多容器配置预留面积解析归零 Hook。
     *
     * 多容器引擎（X.0BaH）按服务端类型掩码把状态栏/顶栏/底栏面积折算为各容器变体的
     * 上下预留（X.0BaM.LIZ），使 adjustContainer 小于物理屏、视频画布四周留出黑边。
     * 本拦截把解析结果统一归零：所有容器变体的 adjustContainer 即物理全屏，
     * 画布与屏幕重合，这是视频视口铺满的前提。该解析器仅被多容器引擎消费。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.init`: 模块初始化阶段挂载。
     */
    private fun hookCanvasReserveZero(module: XposedModule, classLoader: ClassLoader) {
        val resolverClass = HostSymbols.resolve(classLoader, HostSymbol.RESERVED_AREA)
        val areaMethod = resolverClass.declaredMethods.firstOrNull {
            it.name == "LIZ" && it.parameterCount == 3 && it.returnType == java.lang.Float.TYPE
        } ?: return
        areaMethod.isAccessible = true

        module.trackHook("ImmersiveFullScreenHook", areaMethod).intercept { chain ->
            if (immersiveEnabled()) java.lang.Float.valueOf(0.0f) else chain.proceed()
        }
        Log.i(TAG, "已成功挂载多容器配置预留面积归零拦截")
    }

    // ═══════════════ Hook 二：卡片内边距归零 ═══════════════

    /**
     * 挂载 FeedCell 卡片内边距归零 Hook。
     *
     * 拦截 [FeedCellAdaptionComponentV2] 的 [Mx0] 与 [yf1] 两个内边距决策方法，
     * 统一返回四向均为 0 的内边距，使视频/照片卡片铺满全屏容器、chrome 悬浮其上。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.init`: 模块初始化阶段挂载。
     */
    private fun hookCellFullBleed(module: XposedModule, classLoader: ClassLoader) {
        val componentClass = classLoader.loadClass("com.ss.android.ugc.feed.platform.cell.component.adaption.FeedCellAdaptionComponentV2")
        val paddingValuesClass = classLoader.loadClass("com.ss.android.ugc.aweme.videoadaption.adaptionparams.AdaptionPaddingValues")
        val paddingConstructor = paddingValuesClass.getConstructor(
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE,
            java.lang.Integer.TYPE
        )

        for (methodName in listOf(HostSymbols.member(HostSymbol.FEED_ADAPTION, "paddingContent"),
            HostSymbols.member(HostSymbol.FEED_ADAPTION, "paddingViewport"))) {
            val method = componentClass.declaredMethods.firstOrNull {
                it.name == methodName && it.parameterCount == 0 && it.returnType == paddingValuesClass
            } ?: continue

            method.isAccessible = true
            module.trackHook("ImmersiveFullScreenHook", method).intercept { chain ->
                if (immersiveEnabled()) paddingConstructor.newInstance(0, 0, 0, 0) else chain.proceed()
            }
        }
        Log.i(TAG, "已成功挂载 FeedCell 内边距归零拦截")
    }

    // ═══════════════ Hook 三：视频 contain 视口 ═══════════════

    /**
     * 挂载视频视口规范化 Hook。
     *
     * 宿主多容器引擎的裁定（多套容器变体、黑边率与对齐类型服务端配置、OCR 智能裁切）
     * 面向"视频填满其匹配容器"设计，产出可能是裁切盒（CROP）或无居中位移的信箱盒，
     * 且阈值路径还会以配置覆写位移。全部产出最终唯一收敛于
     * [CellAdaptionComponentV2.Kr]——视图落盘器（X.09H5.LIZIZ）的全部调用都在其内。
     * 在该收敛点把视口统一规范化：
     *
     * - 自适应视口盒：视频比例与画布比例面积差 ≤ 1/4 时取 cover 外扩盒（铺满画布、
     *   溢出裁边，抖音同款观感），悬殊时取 contain 内接盒（完整无裁切）。两种盒共用
     *   宿主 X.08LN.LIZIZ 同式的整数推导，比例与画布一致时二者重合、恰好铺满；
     * - 位移归零：被落盘的视图（video_adapted_wrapper_container / lgi 等）在宿主布局中
     *   均为 layout_gravity=CENTER，gravity 已将视口在画布内居中；宿主对齐配置枚举
     *   (CENTER/LEFT/TOP/RIGHT/BOTTOM) 亦以零位移表达 CENTER。故规范化位移恒为 (0,0)，
     *   抵消引擎按 TOP/黑边率配置写入的偏移，任何比例的视频都居中呈现；
     * - 原生 MATCH_PARENT（动态模糊契约）与非多容器算子（intext 嵌入等）原样放行；
     * - 尺寸缺失（无法决策）时放行原生裁定。
     *
     * 规范化幂等：引擎产出只在此一处被改写，下游（落盘、进度、播放器）读到的即规范视口。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.init`: 模块初始化阶段挂载。
     */
    private fun hookVideoContainViewport(module: XposedModule, classLoader: ClassLoader) {
        val componentClass = classLoader.loadClass("com.ss.android.ugc.feed.platform.cell.component.adaption.CellAdaptionComponentV2")
        val resultClass = classLoader.loadClass("com.ss.android.ugc.aweme.videoadaption.adaptionparams.VideoAdaptionResult")
        val resultConstructor = resultClass.constructors.firstOrNull { it.parameterTypes.size == 5 }

        val operatorClass = classLoader.loadClass(
            "com.ss.android.ugc.aweme.videoadaption.adaptionparams.resultoperator.MultiContainerThresholdResultOperator"
        )
        val getAlignTypeMethod = operatorClass.getDeclaredMethod("getAlignType")
        val matchParentConstant = getAlignTypeMethod.returnType.enumConstants
            ?.firstOrNull { (it as? Enum<*>)?.name == "MATCH_PARENT" }
        val getParamsMethod = operatorClass.getMethod("getVideoAdaptionParams")
        val getContainerWidthMethod = operatorClass.getMethod("getAdjustContainerWidth")
        val getContainerHeightMethod = operatorClass.getMethod("getAdjustContainerHeight")

        val paramsClass = classLoader.loadClass("com.ss.android.ugc.aweme.videoadaption.adaptionparams.VideoAdaptionParams")
        val getVideoWidthMethod = paramsClass.getMethod("getVideoWidth")
        val getVideoHeightMethod = paramsClass.getMethod("getVideoHeight")

        val getOperatorMethod = resultClass.getMethod("getResultOperator")

        val krMethod = componentClass.declaredMethods.firstOrNull {
            it.name == HostSymbols.member(HostSymbol.ADAPTION, "apply") && it.parameterCount == 2 && it.parameterTypes[0] == resultClass
        }
        if (krMethod == null || resultConstructor == null || matchParentConstant == null) {
            Log.w(TAG, "Kr 收敛点或结果构造器未匹配，视频 contain 视口拦截未挂载")
            return
        }
        krMethod.isAccessible = true

        module.trackHook("ImmersiveFullScreenHook", krMethod).intercept { chain ->
            if (!immersiveEnabled()) {
                return@intercept chain.proceed()
            }
            val original = chain.args[0]
            if (original == null) {
                return@intercept chain.proceed()
            }

            val canonical = run {
                val operator = getOperatorMethod.invoke(original)
                if (!operatorClass.isInstance(operator)) {
                    return@run null
                }
                val params = getParamsMethod.invoke(operator)
                val videoWidth = getVideoWidthMethod.invoke(params) as Int
                val videoHeight = getVideoHeightMethod.invoke(params) as Int
                val containerWidth = (getContainerWidthMethod.invoke(operator) as Float).toInt()
                val containerHeight = (getContainerHeightMethod.invoke(operator) as Float).toInt()
                if (videoWidth <= 0 || videoHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
                    return@run null
                }
                if (getAlignTypeMethod.invoke(operator) == matchParentConstant) {
                    return@run null
                }
                // 视口盒二选一：宽适配盒（宽=画布宽，高按视频比推导）与高适配盒（高=画布高，宽按视频比推导）。
                // 自适应语义：视频比例与画布比例面积差 ≤ 1/4 时取 cover（铺满、溢出裁边），
                // 悬殊时取 contain（完整居中）。画布已由预留归零修正为物理全屏。
                val widthFitCross = videoHeight.toLong() * containerWidth
                val heightFitCross = videoWidth.toLong() * containerHeight
                val preferWidthFit = widthFitCross <= heightFitCross
                val useCover = COVER_AREA_DIFF_NUMERATOR * kotlin.math.abs(widthFitCross - heightFitCross) <= heightFitCross
                val boxWidth: Int
                val boxHeight: Int
                if (preferWidthFit != useCover) {
                    boxWidth = containerWidth
                    boxHeight = (widthFitCross / videoWidth).toInt()
                } else {
                    boxHeight = containerHeight
                    boxWidth = (heightFitCross / videoHeight).toInt()
                }
                // 位移归零：gravity(CENTER) 已居中视口，抵消引擎 TOP/黑边率配置写入的偏移
                resultConstructor.newInstance(
                    boxWidth,
                    boxHeight,
                    java.lang.Float.valueOf(0.0f),
                    java.lang.Float.valueOf(0.0f),
                    operator
                )
            } ?: original

            val newArgs = chain.args.toTypedArray()
            newArgs[0] = canonical
            chain.proceed(newArgs)
        }
        Log.i(TAG, "已成功挂载视频 contain 视口收敛拦截")
    }

    // ═══════════════ Hook 四：底栏 chrome 透明化与 Edge-to-Edge ═══════════════

    /**
     * 净化底栏悬浮层与顶栏背景为全透明。
     *
     * Args:
     *     view (View?): 视图树中的任意活跃节点实例。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.hookBottomTabTransparency`: 底栏装配与 Activity.onResume 触发时执行。
     *     - `io.github.meiyongai.toki.hook.AutoCleanModeHook.init`: FeedCleanComponent 与 CellCleanComponent 装配完成时执行。
     */
    fun eliminatePlaceholder(view: View?) {
        if (view == null) return
        eliminatePlaceholder(view, immersiveEnabled())
    }

    /** 页面状态机明确传入已验证的沉浸意图，布局处理不再选择配置来源。 */
    fun eliminatePlaceholder(view: View?, enabled: Boolean) {
        if (view == null || !enabled) {
            return
        }
        val rootView = view.rootView as? ViewGroup ?: (view as? ViewGroup) ?: return
        val context = rootView.context
        val pkg = context.packageName

        // 底栏悬浮层透明化（图标保留，仅去除实体背景）
        val ok6Id = context.resources.getIdentifier("ok6", "id", pkg)
        if (ok6Id != 0) {
            rootView.findViewById<View>(ok6Id)?.let { applyTransparentBackground(it) }
        }

        // 顶栏背景透明化（保留视图本体，顶部标题/搜索入口挂载于其中）
        val zgtId = context.resources.getIdentifier("zgt", "id", pkg)
        if (zgtId != 0) {
            rootView.findViewById<View>(zgtId)?.background = null
        }
        val z38Id = context.resources.getIdentifier("z38", "id", pkg)
        if (z38Id != 0) {
            rootView.findViewById<View>(z38Id)?.let { applyTransparentBackground(it) }
        }
    }

    /**
     * 为目标 Activity 窗口配置全屏 Edge-to-Edge 与异形屏贯通属性。
     *
     * Args:
     *     activity (Activity): 需要配置全屏沉浸属性的宿主 Activity 实例。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.hookBottomTabTransparency`: Activity.onResume 执行时调用。
     */
    private fun applyEdgeToEdge(activity: Activity) {
        val window = activity.window ?: return
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        val decorView = window.decorView
        @Suppress("DEPRECATION")
        decorView.systemUiVisibility = (
            decorView.systemUiVisibility
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    /**
     * 挂载底部导航栏容器透明化与窗口系统栏沉浸 Hook。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.init`: 模块初始化阶段挂载。
     */
    private fun hookBottomTabTransparency(module: XposedModule, classLoader: ClassLoader) {
        val bottomTabAssemClass = classLoader.loadClass("com.ss.android.ugc.aweme.main.assems.mainfragment.BottomTabAssemBase")

        val viewGroupMethod = bottomTabAssemClass.declaredMethods.firstOrNull {
            it.parameterCount == 0 && it.returnType == ViewGroup::class.java
        }
        if (viewGroupMethod != null) {
            viewGroupMethod.isAccessible = true
            module.trackHook("ImmersiveFullScreenHook", viewGroupMethod).intercept { chain ->
                val viewGroup = chain.proceed() as? ViewGroup
                if (viewGroup != null && immersiveEnabled()) {
                    applyTransparentBackground(viewGroup)
                    eliminatePlaceholder(viewGroup)
                }
                viewGroup
            }
        }

        val pairMethod = bottomTabAssemClass.declaredMethods.firstOrNull {
            it.parameterCount == 0 && it.returnType == Pair::class.java
        }
        if (pairMethod != null) {
            pairMethod.isAccessible = true
            module.trackHook("ImmersiveFullScreenHook", pairMethod).intercept { chain ->
                val result = chain.proceed()
                if (result is Pair<*, *> && immersiveEnabled()) {
                    (result.first as? View)?.let {
                        applyTransparentBackground(it)
                        eliminatePlaceholder(it)
                    }
                }
                result
            }
        }

        val activityClass = classLoader.loadClass("android.app.Activity")
        val onResumeMethod = activityClass.getDeclaredMethod("onResume")
        onResumeMethod.isAccessible = true

        module.trackHook("ImmersiveFullScreenHook", onResumeMethod).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            if (activity != null && immersiveEnabled()) {
                applyEdgeToEdge(activity)
                val decorView = activity.window?.decorView
                if (decorView != null) {
                    eliminatePlaceholder(decorView)
                    decorView.post {
                        eliminatePlaceholder(decorView)
                    }
                }
            }
            result
        }
    }

    // ═══════════════ Hook 五：横屏门控 ═══════════════

    /**
     * 挂载官方原生横屏全屏组件门控放行 Hook。
     *
     * 拦截 [LandscapeFeedServiceImpl] 门控方法，横屏视频直接放行，
     * 驱动官方 Assem 自动挂载原生旋转全屏胶囊按钮。
     *
     * Args:
     *     module (XposedModule): LSPosed 模块注入上下文。
     *     classLoader (ClassLoader): 宿主目标应用类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.init`: 模块初始化阶段挂载。
     */
    private fun hookLandscapeService(module: XposedModule, classLoader: ClassLoader) {
        val landscapeServiceImplClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.landscape.service.LandscapeFeedServiceImpl")
        val awemeClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val getVideoMethod = awemeClass.getMethod("getVideo")

        for (methodName in listOf("LJIIIZ", "LJIIJJI")) {
            val method = landscapeServiceImplClass.declaredMethods.firstOrNull {
                it.name == methodName && it.parameterCount == 2
            } ?: continue

            method.isAccessible = true

            module.trackHook("ImmersiveFullScreenHook", method).intercept { chain ->
                if (immersiveEnabled()) {
                    val aweme = chain.args[1]
                    if (aweme != null) {
                        val video = getVideoMethod.invoke(aweme)
                        if (video != null) {
                            val videoClass = video.javaClass
                            val width = videoClass.getMethod("getWidth").invoke(video) as Int
                            val height = videoClass.getMethod("getHeight").invoke(video) as Int
                            if (width > height) {
                                return@intercept true
                            }
                        }
                    }
                }
                chain.proceed()
            }
        }
    }

    // ═══════════════ Hook 四·B：照片卡片满高 ═══════════════

    /**
     * 挂载照片卡片满高 Hook。
     *
     * 照片类页卡由宿主照片管线（SmartImagePhotoViewHolderV2 等）自行决策缩放：
     * 服务端整图标志（showsWholeImage）与比例阈值判定（X.0MiS.LJIIIZ）共同决定
     * CENTER_CROP（近比例铺满、溢出裁边）或 FIT_CENTER（悬殊时完整呈现于原生
     * blurhash 虚化背景之上），与视频视口的自适应语义天然同构，模块不做干预。
     * 唯一修正点是卡片高度：拦截 [X.0MiS.LIZ] 百分比设置器统一置换为 1.0，
     * 照片卡片贯通画布全高，缩放判定随实际视图尺寸自适应。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.init`: 模块初始化阶段挂载。
     */
    private fun hookPhotoSlideFill(module: XposedModule, classLoader: ClassLoader) {
        val smartExpandHelperClass = HostSymbols.resolve(classLoader, HostSymbol.PHOTO_EXPANSION)
        val heightPercentMethod = smartExpandHelperClass.declaredMethods.firstOrNull {
            it.name == "LIZ" && it.parameterCount == 1 &&
                it.parameterTypes[0] == java.lang.Float.TYPE
        }
        if (heightPercentMethod == null) {
            Log.w(TAG, "X.0MiS.LIZ 未匹配，照片卡片满高拦截未挂载")
            return
        }
        heightPercentMethod.isAccessible = true
        module.trackHook("ImmersiveFullScreenHook", heightPercentMethod).intercept { chain ->
            if (immersiveEnabled()) {
                val newArgs = chain.args.toTypedArray()
                newArgs[0] = 1.0f
                chain.proceed(newArgs)
            } else {
                chain.proceed()
            }
        }
        Log.i(TAG, "已成功挂载 X.0MiS.LIZ 照片卡片满高拦截")
    }

    /**
     * 将目标视图容器及其直接父容器的背景净化为全透明。
     *
     * Args:
     *     view (View): 底部导航栏视图或其容器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.ImmersiveFullScreenHook.eliminatePlaceholder`: chrome 透明化时调用。
     */
    private fun applyTransparentBackground(view: View) {
        view.background = null
        var current: View? = view.parent as? View
        while (current != null && current !is Activity && current.id != android.R.id.content) {
            current.background = null
            current = current.parent as? View
        }
    }
}
