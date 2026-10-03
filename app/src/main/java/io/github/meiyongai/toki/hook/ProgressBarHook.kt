package io.github.meiyongai.toki.hook

import android.graphics.Canvas
import android.util.Log
import android.view.View
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 进度条常显与净屏联动；播放模式按配置切换，暂停和拖拽保留宿主模式。 */
object ProgressBarHook {
    private const val TAG = "TokiProgressBar"
    const val KEY_ALWAYS_SHOW_PROGRESS_BAR = "always_show_progress_bar"
    const val KEY_CLEAN_SHOW_PROGRESS_BAR = "clean_mode_show_progress_bar"
    private var activeSeekBar: WeakReference<Any>? = null
    private var seekBarShowTypeMethod: Method? = null
    @Volatile private var cleanSeekBarMode = 4

    /**
     * 从有效配置读取播放态显示策略。
     * @return Unit；尚无活跃进度条时无需提交。无入参。
     * Callers: AutoCleanModeHook 的播放状态提交。
     */
    fun assertPlayingSeekBarMode() {
        if (!AutoCleanModeHook.isCleanActive || activeSeekBar?.get() == null || seekBarShowTypeMethod == null) return
        assertPlayingSeekBarMode(ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR))
    }

    /**
     * 在播放事件上应用同一配置快照的细线或隐藏模式。
     * @param keep 是否保留播放进度条。
     * @return Unit。
     * Callers: AutoCleanModeHook.commit、assertPlayingSeekBarMode。
     */
    fun assertPlayingSeekBarMode(keep: Boolean) {
        if (!AutoCleanModeHook.isCleanActive) return
        val seekBar = activeSeekBar?.get() ?: return
        val method = seekBarShowTypeMethod ?: return
        method.invoke(seekBar, if (keep) 0 else 4)
    }

    /** 注册所需的控制器、视图关联及绘制入口，缺失任意成员均不能部分安装。 */
    internal class ViewContract(val seekBar: Class<*>, controller: Class<*>, val mask: Class<*>,
        showTypeName: String, setShowTypeName: String) {
        val field: Field
        val decide: Method
        val apply: Method
        val draw: Method

        init {
            check(View::class.java.isAssignableFrom(seekBar) && View::class.java.isAssignableFrom(mask)) {
                "进度条或遮罩不是 View 类型"
            }
            field = controller.declaredFields.single { it.type == seekBar && !Modifier.isStatic(it.modifiers) }
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
            HostSymbols.member(HostSymbol.SEEK_CONTROLLER, "showType"), HostSymbols.member(HostSymbol.SEEK_BAR, "setShowType"))
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
            activeSeekBar?.clear()
            activeSeekBar = null
            seekBarShowTypeMethod = null
            cleanSeekBarMode = 4
        }
        seekBarShowTypeMethod = contract.apply
        module.trackHook("ProgressBarHook", getter).intercept { chain ->
            val original = chain.proceed()
            if (!ConfigClient.getBoolean(KEY_ALWAYS_SHOW_PROGRESS_BAR)) original
            else (original ?: constructor.newInstance()).also {
                show.setInt(it, 1)
                drag.setInt(it, 1)
            }
        }
        module.trackHook("ProgressBarHook", contract.decide).intercept { chain ->
            contract.field.get(chain.thisObject)?.let { activeSeekBar = WeakReference(it) }
            when {
                AutoCleanModeHook.isCleanActive -> if (ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR)) 0 else 4
                ConfigClient.getBoolean(KEY_ALWAYS_SHOW_PROGRESS_BAR) -> 0
                else -> chain.proceed()
            }
        }
        module.trackHook("ProgressBarHook", contract.apply).intercept { chain ->
            val view = chain.thisObject as View
            activeSeekBar = WeakReference(view)
            if (!AutoCleanModeHook.isCleanActive) return@intercept chain.proceed()
            val requested = chain.args[0] as Int
            val mode = cleanMode(requested, ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR))
            cleanSeekBarMode = mode
            val result = if (mode == requested) chain.proceed() else chain.proceed(arrayOf(mode))
            if (mode != 0 && mode != 4 && view.alpha != 1f) view.alpha = 1f
            result
        }
        module.trackHook("ProgressBarHook", contract.draw).intercept { chain ->
            if (ConfigClient.getBoolean(AutoCleanModeHook.KEY_CLEAN_MODE_ON_PLAY)) null else chain.proceed()
        }
        module.trackHook("ProgressBarHook", alpha).intercept { chain ->
            val viewType = chain.thisObject?.javaClass
            when {
                viewType == seekBar && AutoCleanModeHook.isCleanActive &&
                    (ConfigClient.getBoolean(KEY_CLEAN_SHOW_PROGRESS_BAR) || cleanSeekBarMode != 0 && cleanSeekBarMode != 4) ->
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
