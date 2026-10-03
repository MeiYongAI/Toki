package io.github.meiyongai.toki.hook

import android.util.Log
import android.view.View
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method


/**
 * 视频播放进度条常显 Hook 处理器。
 *
 * 统一拦截宿主上游数据模型（Aweme/VideoControl）与播控组件（X.06jj），
 * 解除因视频时长不足 30 秒隐藏进度条的限制，强制激活播放进度条全场景常显。
 *
 * 清屏联动状态机（三个对称收口，规则统一为"保留开→细线(0)，保留关→隐藏(4)"）：
 * 1. 决策层 [LJIIJJI]：清屏期间计算型路径直接返回目标模式；
 * 2. 落盘层 [setSeekBarShowType]：清屏期间 0↔4 互换；暂停/拖拽态放行并恢复
 *    外层不透明（首帧渐隐残留治理）；[assertPlayingSeekBarMode] 在播放态
 *    断言时主动收敛模式（宿主恢复播放路径存在条件跳过重算的分支）；
 * 3. 透明层 [setAlpha]：清屏期间保留开启或暂停/拖拽态强制外层不透明，
 *    压制清屏管线的首帧渐隐动画。
 * 暂停时进度条可见与子开关无关；非清屏态一律放行原生行为。
 */
object ProgressBarHook {

    private const val TAG = "TokiProgressBar"

    /** 总是显示进度条功能配置项键名 */
    const val KEY_ALWAYS_SHOW_PROGRESS_BAR = "always_show_progress_bar"

    /** 自动清屏时保留进度条显示的子开关键名（依赖播放时自动清屏主开关） */
    const val KEY_CLEAN_SHOW_PROGRESS_BAR = "clean_mode_show_progress_bar"

    /** 活跃进度条实例弱引用（借决策层调用捕获） */
    private var activeSeekBar: WeakReference<Any>? = null

    /** 宿主进度条显隐落盘方法（init 期解析） */
    private var seekBarShowTypeMethod: Method? = null

    /**
     * 清屏期间进度条最近应用的显隐模式（由 setSeekBarShowType 收口同步）。
     *
     * 模式 0=细线、4=隐藏 均为播放态；非 0/4（暂停粗条 1、拖拽态等）时
     * 外层容器必须保持不透明，否则暂停进度条会因首帧渐隐残留而不可见。
     */
    @Volatile
    private var cleanSeekBarMode: Int = 4

    /**
     * 播放态清屏进度条模式收敛。
     *
     * 宿主恢复播放路径存在条件跳过模式重算的分支，暂停态模式（粗条）会滞留
     * 至视频切换。由 [AutoCleanModeHook] 的播放态生命周期断言触发，将活跃
     * 进度条主动归位为细线(0)或隐藏(4)；仅在播放态事件上触发，暂停/拖拽态
     * 不受影响。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.AutoCleanModeHook.hookPlayStateAssertion`:
     *       首页 Feed 播放态生命周期断言时调用。
     */
    fun assertPlayingSeekBarMode() {
        if (!AutoCleanModeHook.isCleanActive || activeSeekBar?.get() == null || seekBarShowTypeMethod == null) return
        assertPlayingSeekBarMode(ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR))
    }

    /** 清屏状态机传递同一次已验证配置的显示决策，避免异步状态回调再次查询配置。 */
    fun assertPlayingSeekBarMode(keep: Boolean) {
        if (!AutoCleanModeHook.isCleanActive) return
        val seekBar = activeSeekBar?.get() ?: return
        val method = seekBarShowTypeMethod ?: return
        val target = if (keep) 0 else 4
        method.invoke(seekBar, target)
    }

    /**
     * 初始化挂载视频进度条常显与清屏显隐统筹驱动。
     *
     * 1. 拦截宿主播控组件 [X.06jj] 中的进度条显隐模式判定方法 [LJIIJJI]：
     *    - 处于自动清屏纯净状态（[AutoCleanModeHook.isCleanActive]）时，依子开关返回 4（隐藏）
     *      或 0（保留细线显示）；
     *    - 处于常显模式（[KEY_ALWAYS_SHOW_PROGRESS_BAR]）时强制返回 0（细线显示模式），解除不足 30 秒隐藏限制；
     * 2. 拦截底层 [setSeekBarShowType] 显隐落盘点，清屏纯净态下双向统一（保留开启时 4→0，
     *    保留关闭时非 4→4），覆盖宿主绕过决策层的全部直调路径（长按菜单暂停直设、清屏管线直设等）；
     * 3. 拦截 [Aweme.getVideoControl] 上游数据模型，确保进度条显示标识与拖拽标识全局激活；
     * 4. 挂载 [X.06nS.LJI] 边距自适应拦截，确保清屏贴底与非清屏原生排版保真。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 宿主目标包加载完成时注册。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 全局渲染回调的必需符号先完整验证；缺失时由注册事务报告，不能留到每帧才查询。
        val seekBarClassName = HostSymbols.resolve(classLoader, HostSymbol.SEEK_BAR).name
        val darkLayerClass = HostSymbols.resolve(classLoader, HostSymbol.DARK_LAYER)
        val darkLayerClassName = darkLayerClass.name
        val awemeClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val videoControlClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.VideoControl")
        val getVideoControlMethod = awemeClass.getMethod("getVideoControl")
        val showProgressBarField = videoControlClass.getField("showProgressBar")
        val draftProgressBarField = videoControlClass.getField("draftProgressBar")
        val videoControlConstructor = videoControlClass.getConstructor()

        getVideoControlMethod.isAccessible = true
        showProgressBarField.isAccessible = true
        draftProgressBarField.isAccessible = true

        module.trackHook("ProgressBarHook", getVideoControlMethod).intercept { chain ->
            var control = chain.proceed()
            if (ConfigClient.getBoolean(KEY_ALWAYS_SHOW_PROGRESS_BAR)) {
                if (control == null) {
                    control = videoControlConstructor.newInstance()
                }
                showProgressBarField.setInt(control, 1)
                draftProgressBarField.setInt(control, 1)
            }
            control
        }

        val controllerClass = HostSymbols.resolve(classLoader, HostSymbol.SEEK_CONTROLLER)
        val fieldLL = controllerClass.declaredFields.firstOrNull { it.name == "LL" }
        fieldLL?.isAccessible = true

        val ljiijjiMethod = controllerClass.declaredMethods.firstOrNull {
            it.name == HostSymbols.member(HostSymbol.SEEK_CONTROLLER, "showType") &&
                it.parameterCount == 1 && it.parameterTypes[0] == java.lang.Boolean.TYPE &&
                it.returnType == java.lang.Integer.TYPE
        }
        if (ljiijjiMethod != null) {
            ljiijjiMethod.isAccessible = true
            module.trackHook("ProgressBarHook", ljiijjiMethod).intercept { chain ->
                // 借决策层调用捕获活跃进度条实例，供播放态模式收敛使用
                fieldLL?.get(chain.thisObject)?.let { activeSeekBar = WeakReference(it) }
                if (AutoCleanModeHook.isCleanActive) {
                    // 清屏纯净态：子开关决定保留细线进度条(0)或完全隐藏(4)
                    return@intercept if (ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR)) 0 else 4
                }
                if (ConfigClient.getBoolean(KEY_ALWAYS_SHOW_PROGRESS_BAR)) {
                    return@intercept 0
                }
                chain.proceed()
            }
            Log.i(TAG, "已成功挂载 X.06jj.LJIIJJI 进度条显隐模式判定拦截（清屏依子开关，常显模式强制激活）")
        }

        // 底层 SeekBar 显隐收口：清屏纯净态下的唯一权威落盘点，仅做 0↔4 互换。
        // 宿主存在多条绕过决策层 LJIIJJI 的直调路径（长按菜单暂停直设 1、清屏管线直设等），
        // 互换规则：保留开启时 4→0（隐藏请求转为细线），保留关闭时 0→4（细线请求转为隐藏）；
        // 暂停粗条（1）、拖拽态（2/100+）永远放行——暂停时进度条可见与子开关无关。
        val setSeekBarShowTypeMethod = fieldLL?.type?.declaredMethods?.firstOrNull {
            it.name == "setSeekBarShowType" && it.parameterCount == 1 &&
                it.parameterTypes[0] == java.lang.Integer.TYPE
        }
        if (setSeekBarShowTypeMethod == null) {
            Log.w(TAG, "setSeekBarShowType 未匹配，清屏进度条收口未挂载")
        } else {
            setSeekBarShowTypeMethod.isAccessible = true
            seekBarShowTypeMethod = setSeekBarShowTypeMethod
            module.trackHook("ProgressBarHook", setSeekBarShowTypeMethod).intercept { chain ->
                val mode = chain.args[0] as? Int
                if (AutoCleanModeHook.isCleanActive && mode != null) {
                    val keep = ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR)
                    val converted = when (mode) {
                        0 -> if (keep) 0 else 4
                        4 -> if (keep) 0 else 4
                        else -> mode
                    }
                    cleanSeekBarMode = converted
                    val result = if (converted != mode) {
                        val newArgs = chain.args.toTypedArray()
                        newArgs[0] = converted
                        chain.proceed(newArgs)
                    } else {
                        chain.proceed()
                    }
                    // 暂停/拖拽态：直接恢复外层 alpha 至不透明。首帧渲染的渐隐
                    // 动画结束后无人再调 setAlpha，收口拦截无法被动生效，须主动
                    // 归位，否则翻页后的暂停进度条会因渐隐残留而不可见。
                    if (converted != 0 && converted != 4) {
                        (chain.thisObject as? android.view.View)?.let { v ->
                            if (v.alpha != 1.0f) v.alpha = 1.0f
                        }
                    }
                    result
                } else {
                    chain.proceed()
                }
            }
            Log.i(TAG, "已成功挂载 setSeekBarShowType 底层显隐收口拦截（清屏期间按保留开关互换 0↔4，暂停态放行并恢复不透明）")
        }

        hookSeekDurationMask(module, darkLayerClass)

        // 外层 SeekBar 容器与拖拽遮罩 alpha 收口：
        // 1. SeekBar 容器：面板清屏管线 FeedCleanComponent.im 经由 06jk.LJ -> 00lv.N4/Od 动画
        //    在首帧渲染时将外层 X.06hm 渐隐至 0。强制不透明时机为：保留开启（播放中细线可见）或
        //    处于暂停/拖拽态（模式非 0/4，确保暂停粗条可见）；
        // 2. 拖拽暗色遮罩：宿主在拖拽时调度属性动画将 X.05vo 渐显至 1.0f。在清屏模式生效期间，
        //    强制将其 alpha 设为 0.0f，消除拖拽黑雾。
        run {
            val setAlphaMethod = android.view.View::class.java
                .getMethod("setAlpha", java.lang.Float.TYPE)
            setAlphaMethod.isAccessible = true
            module.trackHook("ProgressBarHook", setAlphaMethod).intercept { chain ->
                val v = chain.thisObject as? android.view.View
                val viewName = v?.javaClass?.name
                if (viewName == seekBarClassName && AutoCleanModeHook.isCleanActive) {
                    val keep = ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR)
                    val pauseLike = cleanSeekBarMode != 0 && cleanSeekBarMode != 4
                    if (keep || pauseLike) {
                        val newArgs = chain.args.toTypedArray()
                        newArgs[0] = 1.0f
                        return@intercept chain.proceed(newArgs)
                    }
                } else if (viewName == darkLayerClassName && ConfigClient.getBoolean(AutoCleanModeHook.KEY_CLEAN_MODE_ON_PLAY)) {
                    val newArgs = chain.args.toTypedArray()
                    newArgs[0] = 0.0f
                    return@intercept chain.proceed(newArgs)
                }
                chain.proceed()
            }
            Log.i(TAG, "已成功挂载外层 SeekBar 与拖拽遮罩 alpha 收口拦截")
        }

        Log.i(TAG, "总是显示进度条数据模型与核心判定 Hook 初始化就绪")
    }

    /**
     * 通过已验证的暗色遮罩绘制入口保留清屏行为，不注册无语义特征的 Lambda。
     * @param module LSPosed 模块。
     * @param maskClass 注册前已验证的暗色遮罩类型。
     * @return Unit。
     * Callers: init。
     */
    private fun hookSeekDurationMask(module: XposedModule, maskClass: Class<*>) {
        val onDrawMethod = maskClass.getDeclaredMethod("onDraw", android.graphics.Canvas::class.java)
        onDrawMethod.isAccessible = true
        module.trackHook("ProgressBarHook", onDrawMethod).intercept { chain ->
            if (ConfigClient.getBoolean(AutoCleanModeHook.KEY_CLEAN_MODE_ON_PLAY)) null
            else chain.proceed()
        }
        Log.i(TAG, "拖动遮罩绘制控制已注册: ${maskClass.name}.onDraw")
    }
}
