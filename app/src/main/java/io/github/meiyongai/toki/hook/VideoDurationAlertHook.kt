package io.github.meiyongai.toki.hook

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.roundToInt

/**
 * 视频播放时长提示 Hook 处理器。
 *
 * 当用户在信息流或详情页中切换到视频并进入起播渲染阶段时，
 * 拦截上游数据实体 [com.ss.android.ugc.aweme.feed.model.Aweme]，
 * 获取视频总时长；若时长达到用户自定义设置的阈值（单位：分钟），
 * 则通过主线程 Toast 提示用户：“请注意！此视频时长xx分钟！”。
 */
object VideoDurationAlertHook {

    private const val TAG = "TokiVideoDurationAlert"

    /** 视频时长提示功能总开关配置项键名 */
    const val KEY_VIDEO_DURATION_ALERT_ENABLED = "video_duration_alert_enabled"

    /** 视频时长提示阈值配置项键名（单位：分钟，默认 3） */
    const val KEY_VIDEO_DURATION_ALERT_THRESHOLD = "video_duration_alert_threshold"

    /** 默认提示阈值（分钟） */
    const val DEFAULT_THRESHOLD_MINUTES_STRING = "3"

    /** 主线程 Handler，用于在 UI 线程调度展示 Toast */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 全局 Application 上下文环境引用 */
    @Volatile
    private var appContext: Context? = null

    /** 最近一次已提示过的视频 ID，避免同一视频在播放或重绘生命周期内重复弹出 Toast */
    @Volatile
    private var lastAlertedAid: String? = null

    /** 缓存的 Aweme.getVideo 反射方法句柄 */
    private var getVideoMethod: Method? = null

    /** 缓存的 Video.getDuration 反射方法句柄 */
    private var getDurationMethod: Method? = null

    /** 缓存的 VideoViewCell.getAweme 反射方法句柄 */
    private var getAwemeMethod: Method? = null

    /** 缓存的 VideoBaseCell.LLJJJJLIIL 字段反射句柄（作为 getAweme 的备选读取） */
    private var awemeField: Field? = null

    /** 缓存的 VideoViewCell.getRootView 反射方法句柄 */
    private var getRootViewMethod: Method? = null

    /**
     * 读取视频时长提示功能总开关状态。
     *
     * Returns:
     *     Boolean: 功能是否启用。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.VideoDurationAlertHook.handleVideoSelectedOrReady`: 选卡与首帧渲染时判定。
     */
    private fun isAlertEnabled(): Boolean =
        ConfigClient.getBoolean(KEY_VIDEO_DURATION_ALERT_ENABLED)

    /**
     * 解析用户配置的提示时长阈值（单位：分钟）。
     *
     * Args:
     *     raw (String?): 用户输入的原始阈值字符串。
     *
     * Returns:
     *     Double: 解析出的分钟数值，若配置无效或非正数则默认返回 3.0。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.VideoDurationAlertHook.handleVideoSelectedOrReady`: 校验视频时长是否达标。
     *     - `io.github.meiyongai.toki.ui.MainActivity`: 设置界面输入校验与回显。
     */
    fun parseThresholdMinutes(raw: String?): Double {
        val parsed = raw?.trim()?.toDoubleOrNull()
        return if (parsed != null && parsed > 0.0) parsed else 3.0
    }

    /**
     * 初始化挂载视频时长提示核心生命周期 Hook。
     *
     * 解析 [VideoViewCell] 及其子类 [FullFeedVideoViewHolder] 的切卡（onPageSelected）
     * 与首帧绘制就绪（onRenderFirstFrame）等核心播放入口，统一注入时长判定逻辑。
     *
     * Args:
     *     module (XposedModule): LSPosed 现代化模块注入上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标包加载完成时注册。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        val awemeClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val videoClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Video")
        val cellClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.adapter.VideoViewCell")
        val baseCellClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.adapter.VideoBaseCell")

        getVideoMethod = awemeClass.getMethod("getVideo").apply { isAccessible = true }
        getDurationMethod = videoClass.getMethod("getDuration").apply { isAccessible = true }

        getAwemeMethod = cellClass.methods.firstOrNull {
            it.name == "getAweme" && it.parameterCount == 0
        }?.apply { isAccessible = true }

        awemeField = (cellClass.declaredFields.firstOrNull { it.name == "LLJJJJLIIL" }
            ?: baseCellClass.declaredFields.firstOrNull { it.name == "LLJJJJLIIL" })
            ?.apply { isAccessible = true }

        getRootViewMethod = cellClass.methods.firstOrNull {
            it.name == "getRootView" && it.parameterCount == 0
        }?.apply { isAccessible = true }

        // 1. 拦截 VideoViewCell.onPageSelected：页面滑动选中新卡片时触发
        val onPageSelectedMethod = cellClass.declaredMethods.firstOrNull {
            it.name == "onPageSelected" && it.parameterCount == 1 &&
                it.parameterTypes[0] == java.lang.Integer.TYPE
        }
        if (onPageSelectedMethod != null) {
            onPageSelectedMethod.isAccessible = true
            module.trackHook("VideoDurationAlertHook", onPageSelectedMethod).intercept { chain ->
                val result = chain.proceed()
                handleVideoSelectedOrReady(chain.thisObject)
                result
            }
            Log.i(TAG, "已成功挂载 VideoViewCell.onPageSelected 时长检查拦截")
        } else {
            Log.w(TAG, "VideoViewCell.onPageSelected 方法未匹配")
        }

        // 2. 拦截 VideoViewCell 与 FullFeedVideoViewHolder 的 onRenderFirstFrame：首帧起播时触发
        val renderClasses = listOf(
            cellClass,
            classLoader.loadClass("com.ss.android.ugc.aweme.feed.adapter.FullFeedVideoViewHolder")
        )
        for (targetClass in renderClasses) {
            val renderMethods = targetClass.declaredMethods.filter {
                it.name == "onRenderFirstFrame" && !it.isSynthetic
            }
            for (renderMethod in renderMethods) {
                renderMethod.isAccessible = true
                module.trackHook("VideoDurationAlertHook", renderMethod).intercept { chain ->
                    val result = chain.proceed()
                    handleVideoSelectedOrReady(chain.thisObject)
                    result
                }
            }
        }
        Log.i(TAG, "已成功挂载 onRenderFirstFrame 起播首帧渲染时长检查拦截")
    }

    /**
     * 宿主 Application 上下文刷新同步入口。
     *
     * Args:
     *     context (Context): 目标应用宿主上下文环境。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主冷启动完成时分发。
     */
    fun refreshConfig(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 针对活跃视频卡片执行时长求值与 Toast 提示调度。
     *
     * 提取当前卡片承载的 [com.ss.android.ugc.aweme.feed.model.Aweme] 实例，
     * 判断其是否为未提示过的新视频；读取其时长并与用户设定的阈值进行比对，
     * 若达到阈值则在主线程弹出 Toast。
     *
     * Args:
     *     cellObject (Any?): 视频卡片实例对象（[VideoViewCell] 或其子类）。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.VideoDurationAlertHook.init`: 选卡或首帧渲染拦截触发。
     */
    private fun handleVideoSelectedOrReady(cellObject: Any?) {
        if (cellObject == null || !isAlertEnabled()) {
            return
        }

        val aweme = getAwemeFromCell(cellObject) ?: return
        val aid = resolveAid(aweme) ?: return

        if (aid == lastAlertedAid) {
            return
        }

        val video = getVideoMethod?.invoke(aweme) ?: return
        val durationMs = (getDurationMethod?.invoke(video) as? Number)?.toLong() ?: 0L
        if (durationMs <= 0L) {
            return
        }

        val thresholdMinutes = parseThresholdMinutes(
            ConfigClient.getString(KEY_VIDEO_DURATION_ALERT_THRESHOLD, DEFAULT_THRESHOLD_MINUTES_STRING)
        )
        val videoTotalMinutes = durationMs / 60000.0

        if (videoTotalMinutes >= thresholdMinutes) {
            lastAlertedAid = aid

            // 格式化提示分钟数：若十分位为 0（如 3.0 分钟）则显示整数 "3"，否则保留 1 位小数（如 "3.5"）
            val roundedOneDecimal = (videoTotalMinutes * 10).roundToInt() / 10.0
            val textMinutes = if (roundedOneDecimal % 1.0 == 0.0) {
                roundedOneDecimal.toInt().toString()
            } else {
                roundedOneDecimal.toString()
            }

            val alertText = "请注意！此视频时长${textMinutes}分钟！"
            val targetContext = resolveContextFromCell(cellObject) ?: appContext ?: return

            mainHandler.post {
                Toast.makeText(targetContext.applicationContext, alertText, Toast.LENGTH_SHORT).show()
            }
            Log.i(TAG, "视频时长达标触发提示: aid=$aid, durationMs=$durationMs, minutes=$textMinutes")
        }
    }

    /**
     * 从视频卡片实例中提取当前承载的 Aweme 实体对象。
     *
     * Args:
     *     cellObject (Any): 视频卡片实例。
     *
     * Returns:
     *     Any?: [com.ss.android.ugc.aweme.feed.model.Aweme] 实例，获取失败返回 null。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.VideoDurationAlertHook.handleVideoSelectedOrReady`: 提取 Aweme 数据。
     */
    private fun getAwemeFromCell(cellObject: Any): Any? {
        val fromMethod = getAwemeMethod?.invoke(cellObject)
        if (fromMethod != null) {
            return fromMethod
        }
        return awemeField?.get(cellObject)
    }

    /**
     * 从 Aweme 实体对象中提取全局唯一视频标识 Aid。
     *
     * Args:
     *     aweme (Any): [com.ss.android.ugc.aweme.feed.model.Aweme] 实例。
     *
     * Returns:
     *     String?: 视频唯一标识 ID，无法提取时返回 null。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.VideoDurationAlertHook.handleVideoSelectedOrReady`: 检查是否为新视频。
     */
    private fun resolveAid(aweme: Any): String? {
        val method = aweme.javaClass.getMethod("getAid")
        return method.invoke(aweme) as? String
    }

    /**
     * 从视频卡片实例中解析上下文 Context 实例。
     *
     * Args:
     *     cellObject (Any): 视频卡片实例。
     *
     * Returns:
     *     Context?: 关联的 Context 上下文，无法解析时返回 null。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.VideoDurationAlertHook.handleVideoSelectedOrReady`: Toast 弹出时使用。
     */
    private fun resolveContextFromCell(cellObject: Any): Context? {
        val view = getRootViewMethod?.invoke(cellObject) as? View
        return view?.context
    }
}
