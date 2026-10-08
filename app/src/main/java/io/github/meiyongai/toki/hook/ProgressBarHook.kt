package io.github.meiyongai.toki.hook

import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** 进度条常显与净屏联动；播放模式按配置切换，暂停和拖拽保留宿主模式。 */
object ProgressBarHook {
    private const val TAG = "TokiProgressBar"
    const val KEY_ALWAYS_SHOW_PROGRESS_BAR = "always_show_progress_bar"
    const val KEY_CLEAN_SHOW_PROGRESS_BAR = "clean_mode_show_progress_bar"
    private var seekBarShowTypeMethod: Method? = null
    private var panelField: Field? = null
    private val seekBars = WeakHashMap<View, SeekBinding>()
    private val interactionViews = WeakHashMap<View, Unit>()
    /** @param controller 此进度条所属原生控制器。 */
    private class SeekBinding(controller: Any) {
        val controller = WeakReference(controller)
        var mode = 4
    }

    /** 查询单个控件所属列表的清屏状态。@param view 进度条。@return 是否清屏。Callers: init、assertPlayingSeekBarMode。 */
    private fun isClean(view: View): Boolean = seekBars[view]?.controller?.get()?.let {
        AutoCleanModeHook.isCleanActive(panelField?.get(it))
    } == true

    /**
     * 识别由原生进度控制器管理的时间提示容器，保留其显示与收起流程。
     * @param view 页面布局中的候选视图。
     * @return 是否为已登记的进度交互容器。
     * Callers: AutoCleanModeHook.init 的页面保留规则。
     */
    internal fun ownsInteractionView(view: View): Boolean = interactionViews.containsKey(view)

    /**
     * 在播放事件上应用同一配置快照的细线或隐藏模式。
     * @param keep 是否保留播放进度条。
     * @return Unit。
     * Callers: AutoCleanModeHook.commit。
     */
    fun assertPlayingSeekBarMode(keep: Boolean) {
        val method = seekBarShowTypeMethod ?: return
        for ((view, binding) in seekBars.entries.toList()) {
            if (isClean(view)) method.invoke(view, cleanMode(binding.mode, keep))
        }
    }

    /** 注册所需的控制器、视图关联及绘制入口，缺失任意成员均不能部分安装。 */
    internal class ViewContract(val seekBar: Class<*>, controller: Class<*>, val mask: Class<*>,
        showTypeName: String, setShowTypeName: String, listPanel: Class<*>) {
        val field: Field
        val duration: Field
        val panel: Field
        val constructors = controller.declaredConstructors
        val decide: Method
        val apply: Method
        val draw: Method

        init {
            check(View::class.java.isAssignableFrom(seekBar) && View::class.java.isAssignableFrom(mask)) {
                "进度条或遮罩不是 View 类型"
            }
            field = controller.declaredFields.single { it.type == seekBar && !Modifier.isStatic(it.modifiers) }
                .apply { isAccessible = true }
            duration = controller.declaredFields.single { it.type == ViewGroup::class.java && !Modifier.isStatic(it.modifiers) }
                .apply { isAccessible = true }
            panel = controller.declaredFields.single { it.type == listPanel && !Modifier.isStatic(it.modifiers) }
                .apply { isAccessible = true }
            decide = controller.getDeclaredMethod(showTypeName, Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
            apply = seekBar.getDeclaredMethod(setShowTypeName, Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
            draw = mask.getDeclaredMethod("onDraw", Canvas::class.java).apply { isAccessible = true }
            check(decide.returnType == Int::class.javaPrimitiveType && apply.returnType == Void.TYPE &&
                draw.returnType == Void.TYPE && listOf(decide, apply, draw).none { Modifier.isStatic(it.modifiers) }) {
                "进度条显隐或遮罩绘制入口类型不符合契约"
            }
        }
    }

    /**
     * 在注册前核实全部模型、视图及控制器契约，注册失败由统一事务撤销。
     * @param module 当前 LSPosed 模块。
     * @param classLoader 宿主最终类加载器。
     * @return Unit；缺失或歧义直接报告。
     * Callers: TokiModule.installHost。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        val seekBar = HostSymbols.resolve(classLoader, HostSymbol.SEEK_BAR)
        val controller = HostSymbols.resolve(classLoader, HostSymbol.SEEK_CONTROLLER)
        val mask = HostSymbols.resolve(classLoader, HostSymbol.DARK_LAYER)
        val contract = ViewContract(seekBar, controller, mask,
            HostSymbols.member(HostSymbol.SEEK_CONTROLLER, "showType"), HostSymbols.member(HostSymbol.SEEK_BAR, "setShowType"),
            classLoader.loadClass("com.ss.android.ugc.aweme.feed.panel.IBaseListFragmentPanel"))
        val aweme = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val control = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.VideoControl")
        val getter = aweme.getMethod("getVideoControl").apply { isAccessible = true }
        val show = control.getField("showProgressBar").apply { isAccessible = true }
        val drag = control.getField("draftProgressBar").apply { isAccessible = true }
        val constructor = control.getConstructor()
        val alpha = View::class.java.getMethod("setAlpha", Float::class.javaPrimitiveType)
        check(getter.returnType == control && !Modifier.isStatic(getter.modifiers))
        check(listOf(show, drag).all { it.type == Int::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers) })

        HookRuntime.onDispose("ProgressBarHook") {
            seekBars.clear()
            panelField = null
            seekBarShowTypeMethod = null
            interactionViews.clear()
        }
        seekBarShowTypeMethod = contract.apply
        panelField = contract.panel
        for (constructor in contract.constructors) {
            module.trackHook("ProgressBarHook", constructor, requiresConfiguration = false).intercept { chain ->
                val result = chain.proceed()
                interactionViews[checkNotNull(contract.duration.get(chain.thisObject) as? View)] = Unit
                seekBars[checkNotNull(contract.field.get(chain.thisObject) as? View)] = SeekBinding(checkNotNull(chain.thisObject))
                result
            }
        }
        module.trackHook("ProgressBarHook", getter).intercept { chain ->
            val original = chain.proceed()
            if (!ConfigClient.getBoolean(KEY_ALWAYS_SHOW_PROGRESS_BAR)) original
            else (original ?: constructor.newInstance()).also {
                show.setInt(it, 1)
                drag.setInt(it, 1)
            }
        }
        module.trackHook("ProgressBarHook", contract.decide).intercept { chain ->
            when {
                ConfigClient.getBoolean(KEY_ALWAYS_SHOW_PROGRESS_BAR) -> 0
                else -> chain.proceed()
            }
        }
        module.trackHook("ProgressBarHook", contract.apply).intercept { chain ->
            val view = chain.thisObject as View
            val requested = chain.args[0] as Int
            val binding = seekBars[view] ?: return@intercept chain.proceed()
            binding.mode = requested
            if (!isClean(view)) return@intercept chain.proceed()
            val mode = cleanMode(requested, ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR))
            val result = if (mode == requested) chain.proceed() else chain.proceed(arrayOf(mode))
            if (mode != 4 && mode != 3 && view.alpha != 1f) view.alpha = 1f
            result
        }
        module.trackHook("ProgressBarHook", contract.draw).intercept { chain ->
            if (ConfigClient.getBoolean(AutoCleanModeHook.KEY_CLEAN_MODE_ON_PLAY)) null else chain.proceed()
        }
        module.trackHook("ProgressBarHook", alpha).intercept { chain ->
            val viewType = chain.thisObject?.javaClass
            val view = chain.thisObject as View
            val mode = seekBars[view]?.mode
            when {
                viewType == seekBar && isClean(view) &&
                    (ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR) || mode != null && mode !in listOf(0, 3, 4)) ->
                    chain.proceed(arrayOf(1f))
                viewType == mask && ConfigClient.getBoolean(AutoCleanModeHook.KEY_CLEAN_MODE_ON_PLAY) ->
                    chain.proceed(arrayOf(0f))
                else -> chain.proceed()
            }
        }
        Log.i(TAG, "进度条已注册：${controller.name}.${contract.decide.name} → ${seekBar.name}.${contract.apply.name}；遮罩=${mask.name}")
    }

    /**
     * 净屏只转换播放模式；暂停粗条和拖拽模式由宿主决定。
     * @param requested 宿主请求的显示模式。
     * @param keep 是否保留播放中的细线。
     * @return 净屏状态下的显示模式。
     * Callers: init 中显隐拦截、ProgressBarRegistrationTest。
     */
    internal fun cleanMode(requested: Int, keep: Boolean): Int =
        if (requested == 0 || requested == 4) { if (keep) 0 else 4 } else requested
}
