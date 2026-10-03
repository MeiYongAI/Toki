package io.github.meiyongai.toki.hook

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.util.Locale
import java.lang.reflect.Modifier

/** 在信息流作者名展示结果追加地区标识，不修改 User 模型。 */
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

    /** 在挂载前验证作者展示及只读模型契约；缺失或类型不符直接报告。 */
    internal class Contract(builder: Class<*>, name: String, user: Class<*>, aweme: Class<*>) {
        val build = builder.getDeclaredMethod(name, String::class.java, user, aweme).apply {
            check(returnType == String::class.java && Modifier.isStatic(modifiers)) { "作者展示入口类型不符合契约" }
            isAccessible = true
        }
        val region = user.getMethod("getRegion").apply {
            check(returnType == String::class.java && !Modifier.isStatic(modifiers)) { "作者地区 getter 类型不符合契约" }
            isAccessible = true
        }
        val author = aweme.getMethod("getAuthor").apply {
            check(returnType == user && !Modifier.isStatic(modifiers)) { "视频作者 getter 类型不符合契约" }
            isAccessible = true
        }
    }

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
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageReady`: 目标包加载完成时注册。
     */
    fun init(module: XposedModule, classLoader: ClassLoader) {
        val userClass = classLoader.loadClass("com.ss.android.ugc.aweme.profile.model.User")
        val awemeClass = classLoader.loadClass("com.ss.android.ugc.aweme.feed.model.Aweme")
        val contract = Contract(HostSymbols.resolve(classLoader, HostSymbol.AUTHOR_LOCATION),
            HostSymbols.member(HostSymbol.AUTHOR_LOCATION, "build"), userClass, awemeClass)
        val builderMethod = contract.build
        val getRegionMethod = contract.region
        val getAuthorMethod = contract.author
        module.trackHook("AuthorLocationHook", builderMethod).intercept { chain ->
            val result = chain.proceed() as String?
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
            val region = if (user != null) getRegionMethod.invoke(user) as String? else null

            if (region.isNullOrBlank()) {
                return@intercept result
            }

            formatAuthorWithLocation(result, region)
        }

        Log.i(TAG, "作者位置展示入口已注册：${builderMethod.declaringClass.name}.${builderMethod.name}")
    }
}
