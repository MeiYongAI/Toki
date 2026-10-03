package io.github.meiyongai.toki.hook

import android.app.Application
import android.content.Context
import android.os.LocaleList
import android.util.Log
import io.github.meiyongai.toki.model.RegionPresets
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier
import java.util.Locale

/**
 * 系统语言与区域设置（Locale）伪装 Hook 核心处理器。
 *
 * 采用双层分离拦截架构：
 * - **业务层**：拦截 [Locale.getDefault] 返回伪装语言，满足网络公共参数
 *   （`sys_language`）、AppLog 埋点、SecSDK 风控上报等所有 Java 层业务需求。
 * - **排版保护层**：拦截 [LocaleList.getDefault] 与 [LocaleList.getAdjustedDefault]，
 *   始终返回模块初始化时捕获的真实系统 [LocaleList]，确保 [android.graphics.Paint]
 *   初始化时的 `setTextLocales` 使用真实中文环境，阻止 Minikin 排版引擎的 CJK
 *   字体 Fallback 链路被日文字体截胡。
 *
 * 核心设计约束：
 * - 严禁调用 [Locale.setDefault]：该方法通过 JNI `nativeSetDefault` 传播到 C++
 *   层 ICU 与 Minikin，不可逆地污染整个进程的原生文字排版引擎默认语言。
 * - 严禁调用 [android.content.res.Configuration.setLocales] 或
 *   [android.content.res.Resources.updateConfiguration]，避免侵入宿主资源树。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 宿主目标包加载就绪后初始化拦截器。
 *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主应用启动时刷新并应用配置。
 */
object LocaleHook {

    private const val TAG = "TokiLocaleHook"

    /** 功能开关状态缓存 */
    @Volatile
    private var isEnabled: Boolean = false

    /** 当前运行时的伪装语言标签（如 "ja-JP"、"en-US"） */
    @Volatile
    private var activeLanguageTag: String = "ja-JP"

    /** 当前运行时的 Java 标准 Locale 实例 */
    @Volatile
    private var activeLocale: Locale = Locale.forLanguageTag("ja-JP")

    /**
     * 在安装任何拦截器之前捕获的真实系统 [LocaleList]。
     *
     * 用于在 [hookLocaleListMethods] 中替换 [LocaleList.getDefault] 和
     * [LocaleList.getAdjustedDefault] 的返回值，确保 [android.graphics.Paint.init]
     * 使用真实中文 Locale 初始化文本排版参数，阻止日文字体 Fallback 截胡。
     */
    private lateinit var realSystemLocaleList: LocaleList

    /**
     * 将符合 BCP-47 或 POSIX 格式的语言代码解析为标准 [Locale] 实例。
     *
     * 规范化语言标签中的下划线分隔符为标准连字符，并构造对应的 Locale 对象。
     *
     * Args:
     *     tag (String): 语言标签字符串（如 "ja-JP" 或 "zh_CN"）。
     *
     * Returns:
     *     Locale: 规整化构造完成的 Java 标准 [Locale] 实例。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.LocaleHook.refreshConfig`: 刷新配置并生成语言实体。
     */
    fun parseLocale(tag: String): Locale {
        val normalized = tag.trim().replace('_', '-')
        return Locale.forLanguageTag(normalized)
    }

    /**
     * 从跨进程配置服务同步最新设定的系统语言伪装配置。
     *
     * 自动研判是否开启"跟随地区设置"，若开启则根据目标地区 ISO 代号反查推荐语言；
     * 否则读取用户自定义指定的语言标签，并同步更新内存中的激活状态。
     *
     * Args:
     *     context (Context): 宿主目标应用的上下文对象。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主启动时触发配置加载。
     */
    fun refreshConfig(context: Context) {
        isEnabled = ConfigClient.getBoolean(context, "language_spoof_enabled")
        if (!isEnabled) {
            Log.i(TAG, "系统语言伪装功能处于关闭状态")
            return
        }

        val followRegion = ConfigClient.getBoolean(context, "language_follow_region")
        activeLanguageTag = if (followRegion) {
            val targetRegion = ConfigClient.getString(context, "target_region", defaultValue = "JP") ?: "JP"
            RegionPresets.resolveLanguageForIso(targetRegion)
        } else {
            ConfigClient.getString(context, "custom_language", defaultValue = "ja-JP") ?: "ja-JP"
        }

        activeLocale = parseLocale(activeLanguageTag)
        Log.i(TAG, "已同步伪装语言配置 -> $activeLanguageTag (Locale: $activeLocale, followRegion=$followRegion)")
    }

    /**
     * 宿主应用启动时的语言伪装生效确认入口。
     *
     * 仅记录日志确认伪装状态就绪。此处严禁调用 [Locale.setDefault]——该方法会通过
     * JNI 层 `nativeSetDefault` 将 ICU 与 Minikin 的全局默认语言篡改为伪装语言
     * （如 ja-JP），导致 [android.graphics.Paint] 初始化时的 CJK 字体 Fallback
     * 排序被日文字体截胡，中国大陆独有简化汉字（如"复"U+590D）因日文字体缺字而
     * 回退到不同字体渲染，产生字宽收缩畸变。[Locale.getDefault] 的 Xposed 层拦截
     * 已完整覆盖所有 Java 层业务代码的读取需求。
     *
     * Args:
     *     app (Application): 宿主 Application 实例。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主 Application 创建时调用。
     */
    fun applyToApplication(app: Application) {
        if (!isEnabled) {
            return
        }
        Log.i(TAG, "宿主 Application 语言伪装就绪 -> $activeLanguageTag (仅 Java 拦截，Native 排版引擎保持原生)")
    }

    /**
     * 获取当前系统语言伪装是否处于激活状态。
     *
     * Returns:
     *     Boolean: 激活返回 true，停用返回 false。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.LocaleHook.hookLocaleMethods`: Locale 拦截器运行时状态校验。
     *     - `io.github.meiyongai.toki.hook.LocaleHook.hookLocaleListMethods`: LocaleList 拦截器运行时状态校验。
     */
    fun isFeatureEnabled(): Boolean = isEnabled

    /**
     * 注册 [Locale.getDefault] 拦截器（业务层伪装）。
     *
     * 拦截 [Locale.getDefault] 的全部静态重载（无参版本与含 [Locale.Category] 参数版本），
     * 使 TikTok 的网络公共参数构造（`sys_language`）、AppLog 设备注册、SecSDK
     * 风控数据上报等所有通过 `Locale.getDefault()` 获取系统语言的 Java 代码
     * 均读取到伪装后的语言设定。
     *
     * Args:
     *     module (XposedModule): 当前注入的 LSPosed 模块上下文实例。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.LocaleHook.init`: 模块初始化阶段安装拦截。
     */
    private fun hookLocaleMethods(module: XposedModule) {
        val localeClass = Locale::class.java
        for (method in localeClass.declaredMethods) {
            if (Modifier.isStatic(method.modifiers) && method.name == "getDefault") {
                module.trackHook("LocaleHook", method).intercept { chain ->
                    if (isFeatureEnabled()) {
                        activeLocale
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
        Log.i(TAG, "Locale.getDefault 业务层语言拦截器挂载成功")
    }

    /**
     * 注册 [LocaleList.getDefault] 与 [LocaleList.getAdjustedDefault] 拦截器（排版保护层）。
     *
     * [android.graphics.Paint] 构造函数中 `init()` 会调用
     * `setTextLocales(LocaleList.getAdjustedDefault())` 设置文本排版语言。若不加保护，
     * [LocaleList.getDefault] 内部读取被拦截的 [Locale.getDefault]（返回伪装日文），
     * 导致 Paint 的 `mLocales` 以日文优先，Minikin 排版引擎的 CJK 字体 Fallback
     * 链路优先选择 `Noto Sans CJK JP`。
     *
     * 日文字体缺少中国大陆独有的简化汉字（如"复"U+590D），Minikin 被迫向后回退
     * 到简体中文字体渲染该字符，造成相邻汉字分属不同字体、字面率与 Advance Width
     * 不一致，产生肉眼可见的字宽收缩畸变。
     *
     * 本拦截器在功能启用时，始终返回模块初始化时捕获的 [realSystemLocaleList]
     * （用户真实的系统语言列表），确保整个文本排版管线免受伪装语言的干扰。
     *
     * Args:
     *     module (XposedModule): 当前注入的 LSPosed 模块上下文实例。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.LocaleHook.init`: 模块初始化阶段安装拦截。
     */
    private fun hookLocaleListMethods(module: XposedModule) {
        val localeListClass = LocaleList::class.java
        for (method in localeListClass.declaredMethods) {
            if (Modifier.isStatic(method.modifiers) &&
                (method.name == "getDefault" || method.name == "getAdjustedDefault")
            ) {
                val targetName = method.name
                module.trackHook("LocaleHook", method).intercept { chain ->
                    if (isFeatureEnabled()) {
                        realSystemLocaleList
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "LocaleList.$targetName 排版保护拦截器挂载成功")
            }
        }
    }

    /**
     * 模块初始化入口，安装系统语言伪装双层拦截机制。
     *
     * 执行顺序严格如下：
     * 1. 捕获真实系统 [LocaleList]（必须在任何 Hook 安装之前，此时 [Locale.getDefault]
     *    尚未被拦截，[LocaleList.getDefault] 返回的是纯净的系统真实语言列表）
     * 2. 安装 [Locale.getDefault] 拦截器（业务层语言伪装）
     * 3. 安装 [LocaleList.getDefault]/[LocaleList.getAdjustedDefault] 拦截器（排版保护）
     *
     * Args:
     *     module (XposedModule): 当前注入的 LSPosed 模块上下文实例。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标包加载时触发。
     */
    fun init(module: XposedModule) {
        realSystemLocaleList = LocaleList.getDefault()
        Log.i(TAG, "已捕获真实系统 LocaleList: $realSystemLocaleList")

        hookLocaleMethods(module)
        hookLocaleListMethods(module)
        Log.i(TAG, "系统语言伪装子系统就绪（业务层伪装 + 排版管线保护）")
    }
}
