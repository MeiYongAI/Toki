package io.github.meiyongai.toki.hook

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 视频正文描述原生翻译（See translation / See original）Hook 处理器。
 *
 * 针对 TikTok 46.8.3 官方版中因风控下发、AB 实验阻断或语言熟练度判定，
 * 导致视频文案下方原生“查看翻译”（See translation）按钮消失的问题，
 * 在数据上游与业务决策层进行统一治理。
 *
 * 核心原理：
 * 1. 在数据模型层（Aweme）解除 [isDescTranslatable] 的下发风控标记限制，
 *    只要视频正文非空即标识为可翻译；
 * 2. 在实验门控层（C08W5）解除 [feed_translation_reverse] AB 实验，
 *    阻断官方 [TranslationServiceImpl.LJZ] 将视频拉入不可翻译黑名单；
 * 3. 在服务决策层（CLATranslatabilityServiceImpl）解除 [LIZLLL] 与语言同质化限制，
 *    使得官方 [TranslationStatusViewModel] 状态机自然流转至待翻译态（C2285608yL），
 *    从而驱动官方原生的 [TranslationControlsAssem] 与 [TranslationStatusAssem]
 *    正常展示文案切换控件。
 *
 * Args:
 *     无构造参数，该对象为全局单例。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 宿主目标包加载就绪后初始化挂载。
 *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 目标应用初始化时同步开关配置。
 */
object VideoTranslateHook {

    private const val TAG = "TokiVideoTrans"

    /** 视频文案原生翻译功能开关配置项键名 */
    const val KEY_VIDEO_TRANSLATE_ENABLED = "video_translate_enabled"

    /** 官方 Aweme 核心数据模型类名 */
    private const val AWEME_CLASS_NAME = "com.ss.android.ugc.aweme.feed.model.Aweme"

    /** 官方翻译黑名单 AB 实验门控类名 (TikTok 46.8.3) */
    private const val REVERSE_EXP_CLASS_NAME = "X.08W5"

    /** 官方翻译决策服务实现类名 */
    private const val TRANSLATABILITY_SERVICE_IMPL_CLASS_NAME =
        "com.ss.android.ugc.aweme.translation.service.CLATranslatabilityServiceImpl"

    /** 功能总开关缓存（管理端配置，默认为开启） */
    @Volatile
    private var isEnabled: Boolean = false

    /**
     * 同步持久化存储中的视频文案原生翻译功能配置开关。
     *
     * 从 ContentProvider 读取最新配置并更新内存中的 [isEnabled] 状态。
     *
     * Args:
     *     context (Context): 宿主目标应用上下文对象。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 应用启动阶段同步配置。
     */
    fun refreshConfig(context: Context) {
        isEnabled = ConfigClient.getBoolean(context, KEY_VIDEO_TRANSLATE_ENABLED)
        Log.i(TAG, "视频文案原生翻译功能配置同步: isEnabled=$isEnabled")
    }

    /**
     * 初始化挂载视频文案原生翻译各层门控拦截。
     *
     * 依序拦截 Aweme 数据模型、C08W5 实验门控及 CLATranslatabilityServiceImpl 决策门控。
     *
     * Args:
     *     module (XposedModule): LSPosed 模块上下文实例。
     *     classLoader (ClassLoader): 宿主目标应用的核心类加载器。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 宿主主包加载完成后调用。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook Aweme 数据模型层的 isDescTranslatable() 方法
        val awemeClass = classLoader.loadClass(AWEME_CLASS_NAME)
        val methodIsDescTranslatable: Method = awemeClass.getDeclaredMethod("isDescTranslatable")
        val methodGetDesc: Method = awemeClass.getDeclaredMethod("getDesc")

        module.trackHook("VideoTranslateHook", methodIsDescTranslatable).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_VIDEO_TRANSLATE_ENABLED)) {
                val desc = methodGetDesc.invoke(chain.thisObject) as? String
                if (!desc.isNullOrBlank()) {
                    true
                } else {
                    chain.proceed()
                }
            } else {
                chain.proceed()
            }
        }

        // 2. Hook C08W5 实验门控层：彻底废除 feed_translation_reverse 导致的黑名单封锁
        val reverseExpClass = HostSymbols.resolve(classLoader, HostSymbol.TRANSLATION_REVERSE)
        val methodLiz: Method = reverseExpClass.getDeclaredMethod("LIZ")
        module.trackHook("VideoTranslateHook", methodLiz).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_VIDEO_TRANSLATE_ENABLED)) {
                false
            } else {
                chain.proceed()
            }
        }

        // 3. Hook CLATranslatabilityServiceImpl 决策服务层：放行描述翻译能力判定
        val translatabilityServiceClass = classLoader.loadClass(TRANSLATABILITY_SERVICE_IMPL_CLASS_NAME)
        val methodLizlll: Method = translatabilityServiceClass.getDeclaredMethod("LIZLLL", awemeClass)
        module.trackHook("VideoTranslateHook", methodLizlll).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_VIDEO_TRANSLATE_ENABLED)) {
                val aweme = chain.args[0]
                if (aweme != null) {
                    val desc = methodGetDesc.invoke(aweme) as? String
                    if (!desc.isNullOrBlank()) {
                        true
                    } else {
                        chain.proceed()
                    }
                } else {
                    chain.proceed()
                }
            } else {
                chain.proceed()
            }
        }

        Log.i(TAG, "视频文案原生翻译（See translation）多层治理门控挂载完成")
    }
}
