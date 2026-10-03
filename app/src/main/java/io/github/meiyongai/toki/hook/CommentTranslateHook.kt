package io.github.meiyongai.toki.hook

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * 评论区一键翻译功能 Hook 处理器。
 *
 * 通过挂载 TikTok 官方评论区原生批量自动翻译的总门控类（46.8.3 中为 [X.08Ut]），
 * 激活官方原生评论区一键翻译功能。
 *
 * 激活后，官方架构将自闭环完成以下完整生命周期：
 * 1. 在评论区操作栏（CommentPageActionBarAssem）中自动呈现官方原生的文A翻译切换图标，
 *    并由官方状态流（TranslationToggleVM 与 Keva）管理点击事件与选中状态；
 * 2. 触发列表组件（CommentPowerListAssem）向官方服务端批量并发拉取当前所有一级评论
 *    与二级折叠回复的高质量译文并存入中央缓存；
 * 3. 驱动全量评论单元格状态机（OverallCommentTranslationStateMachine）响应式渲染译文，
 *    并在用户再次点击按钮时全自动还原为原文；
 * 4. 用户在滑动过程中滑出的新评论以及展开的折叠楼中楼，自动由官方预加载链路（CommentPreload）
 *    执行预拉取并直接展示译文。
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
object CommentTranslateHook {

    private const val TAG = "TokiCommentTrans"

    /** 评论区一键翻译功能开关配置项键名 */
    const val KEY_COMMENT_TRANSLATE_ENABLED = "comment_translate_enabled"

    /** 官方评论区自动翻译门控混淆类名 */
    private const val GATING_CLASS_NAME = "X.08Ut"

    /** 功能总开关缓存（管理端配置，默认为开启） */
    @Volatile
    private var isEnabled: Boolean = false

    /**
     * 同步持久化存储中的评论区一键翻译功能配置开关。
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
        isEnabled = ConfigClient.getBoolean(context, KEY_COMMENT_TRANSLATE_ENABLED)
        Log.i(TAG, "评论区一键翻译功能配置同步: isEnabled=$isEnabled")
    }

    /**
     * 初始化挂载评论区官方翻译门控拦截。
     *
     * 解析官方门控类 [X.08Ut]，拦截其控制操作栏翻译按钮展示的 [LIZJ] 方法以及控制
     * 自动批量翻译协程启动的 [LIZ] 与 [LIZIZ] 方法，使其在功能开关启用时直接返回 true。
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
        val gatingClass = HostSymbols.resolve(classLoader, HostSymbol.COMMENT_TRANSLATION)

        // 拦截操作栏一键翻译按钮展示门控（LIZJ）
        val methodLizj: Method = gatingClass.getDeclaredMethod("LIZJ")
        module.trackHook("CommentTranslateHook", methodLizj).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_COMMENT_TRANSLATE_ENABLED)) {
                true
            } else {
                chain.proceed()
            }
        }

        // 拦截批量自动翻译协程拉取与监听门控（LIZ）
        val methodLiz: Method = gatingClass.getDeclaredMethod("LIZ", java.lang.Boolean.TYPE)
        module.trackHook("CommentTranslateHook", methodLiz).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_COMMENT_TRANSLATE_ENABLED)) {
                true
            } else {
                chain.proceed()
            }
        }

        // 拦截基础能力开启门控（LIZIZ）
        val methodLiziz: Method = gatingClass.getDeclaredMethod("LIZIZ")
        module.trackHook("CommentTranslateHook", methodLiziz).intercept { chain ->
            if (ConfigClient.getBoolean(KEY_COMMENT_TRANSLATE_ENABLED)) {
                true
            } else {
                chain.proceed()
            }
        }

        Log.i(TAG, "评论区官方原生一键翻译门控挂载完成")
    }
}
