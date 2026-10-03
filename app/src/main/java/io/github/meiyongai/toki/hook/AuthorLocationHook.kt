package io.github.meiyongai.toki.hook

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.util.Locale

/**
 * 视频作者地理位置解析与国旗标识渲染 Hook 处理器。
 *
 * 注入点位于信息流作者名展示构建器 [X.08vJ.LIZIZ]（作者行展示文本的唯一产出点，
 * 调用方均为作者信息展示组件）：仅对展示文本追加国旗与地区前缀，不触碰 User
 * 数据模型——模型层注入会使带前缀的昵称进入跨页面数据流（作者主页初次渲染
 * 复用信息流 User 即闪现前缀，接口刷新后消失）。
 *
 * 结果：信息流作者名稳定带位置标识；作者主页及一切数据管道保持原生文本。
 */
object AuthorLocationHook {

    private const val TAG = "TokiAuthorLocation"

    /** 作者位置显示功能配置项键名 */
    const val KEY_SHOW_AUTHOR_LOCATION = "show_author_location"

    /** Unicode 区域指示符基准码点（'A' 对应 0x1F1E6） */
    private const val REGIONAL_INDICATOR_A = 0x1F1E6

    /** 无法解析时的通用地球标识 */
    private const val GLOBE = "\uD83C\uDF10"

    /** 功能启用状态 */
    private val isEnabled: Boolean
        get() = ConfigClient.getBoolean(KEY_SHOW_AUTHOR_LOCATION)

    /**
     * 同步持久化存储中的作者位置显示功能开关。
     *
     * 初始化共享配置；展示时读取同一配置中心的最新快照。
     *
     * Args:
     *     context (Context): 宿主目标应用上下文对象。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主应用启动与前台初始化时同步。
     */
    fun refreshConfig(context: Context) {
        ConfigClient.init(context)
        Log.i(TAG, "作者位置显示功能开关同步: isEnabled=$isEnabled")
    }

    /**
     * 将两位国家/地区 ISO 字符代码转换为 Unicode 旗帜字符。
     *
     * Args:
     *     region (String): 两位 ISO 国家/地区代码字符串（如 US、JP、VN）。
     *
     * Returns:
     *     String: 转换得到的国旗字符或通用地球字符。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.AuthorLocationHook.formatAuthorWithLocation`: 拼接作者昵称标识。
     */
    fun flagFor(region: String): String {
        if (region.length != 2) {
            return GLOBE
        }
        val first = region[0]
        val second = region[1]
        if (first !in 'A'..'Z' || second !in 'A'..'Z') {
            return GLOBE
        }
        val firstCode = REGIONAL_INDICATOR_A + (first - 'A')
        val secondCode = REGIONAL_INDICATOR_A + (second - 'A')
        return String(Character.toChars(firstCode)) + String(Character.toChars(secondCode))
    }

    /**
     * 将作者原始昵称附加国家/地区地理位置标识。
     *
     * Args:
     *     originalText (String): 原始作者昵称文本。
     *     region (String): 原始国家/地区代码。
     *
     * Returns:
     *     String: 附加位置标识后的作者昵称。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.AuthorLocationHook.init`: 拦截昵称生成时调用。
     */
    fun formatAuthorWithLocation(originalText: String, region: String): String {
        val trimmedRegion = region.trim()
        if (trimmedRegion.isEmpty()) {
            return originalText
        }
        val upperRegion = if (trimmedRegion.length == 2) {
            trimmedRegion.uppercase(Locale.ROOT)
        } else {
            trimmedRegion
        }
        val flag = flagFor(upperRegion)
        val prefix = "[$flag$upperRegion] "
        if (originalText.startsWith(prefix)) {
            return originalText
        }
        return prefix + originalText
    }

    /**
     * 初始化挂载作者地理位置 Hook。
     *
     * 拦截上游作者昵称计算方法，注入国家/地区国旗与代码前缀。
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
        val userClass = classLoader.loadClass("com.ss.android.ugc.aweme.profile.model.User")
        val getRegionMethod = userClass.getMethod("getRegion")
        val awemeClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val getAuthorMethod = awemeClass.getMethod("getAuthor")

        getRegionMethod.isAccessible = true
        getAuthorMethod.isAccessible = true

        // 信息流作者名展示构建器 X.08vJ.LIZIZ——作者行展示文本的唯一产出点
        val builderClass = HostSymbols.resolve(classLoader, HostSymbol.AUTHOR_LOCATION)
        val builderMethod = builderClass.declaredMethods.firstOrNull {
            it.name == "LIZIZ" && it.parameterCount == 3 &&
                it.parameterTypes[1] == userClass &&
                it.parameterTypes[2] == awemeClass
        }
        if (builderMethod == null) {
            Log.w(TAG, "X.08vJ.LIZIZ 未匹配，作者位置注入未挂载")
            return
        }
        builderMethod.isAccessible = true
        module.trackHook("AuthorLocationHook", builderMethod).intercept { chain ->
            val result = chain.proceed() as? String
            if (!isEnabled || result.isNullOrEmpty()) {
                return@intercept result
            }

            // 作者对象：优先参数传入，缺省时为 aweme.author（与宿主内部分支一致）
            val userArg = chain.args[1]
            val user: Any? = if (userArg != null) {
                userArg
            } else {
                val awemeArg = chain.args[2]
                if (awemeArg != null) getAuthorMethod.invoke(awemeArg) else null
            }
            val region = if (user != null) getRegionMethod.invoke(user) as? String else null

            if (region.isNullOrBlank()) {
                return@intercept result
            }

            formatAuthorWithLocation(result, region)
        }

        Log.i(TAG, "作者位置展示层注入 Hook 初始化就绪")
    }
}
