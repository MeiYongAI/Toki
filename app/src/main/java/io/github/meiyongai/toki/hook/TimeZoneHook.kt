package io.github.meiyongai.toki.hook

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.model.RegionPresets
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier
import java.time.ZoneId
import java.util.TimeZone

/**
 * 系统时区（TimeZone）伪装 Hook 核心处理器。
 *
 * 拦截目标应用进程内对 [java.util.TimeZone]、[java.time.ZoneId] 以及 [android.icu.util.TimeZone] 的时区获取逻辑，
 * 支持跟随目标地区自动计算标准时区标识（如 Asia/Tokyo），亦支持用户全自由度自定义指定。
 */
object TimeZoneHook {

    private const val TAG = "TokiTimeZoneHook"

    /** 功能开关状态 */
    @Volatile
    private var isEnabled: Boolean = false

    /** 当前运行时的时区 IANA 标识 */
    @Volatile
    private var activeZoneIdString: String = "Asia/Tokyo"

    /** 当前运行时的 Java 经典 TimeZone 实例 */
    @Volatile
    private var activeTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Tokyo")

    /** 当前运行时的 Java 8+ ZoneId 实例 */
    @Volatile
    private var activeZoneId: ZoneId = ZoneId.of("Asia/Tokyo")

    /** 当前运行时的 Android ICU TimeZone 实例 */
    @Volatile
    private var activeIcuTimeZone: android.icu.util.TimeZone = android.icu.util.TimeZone.getTimeZone("Asia/Tokyo")

    /**
     * 从跨进程配置服务同步最新设定的时区伪装配置。
     *
     * 自动研判是否开启“跟随地区设置”，若开启则根据目标地区 ISO 代号反查推荐时区；
     * 否则读取用户自定义指定的 IANA 时区标识。
     *
     * @param context 宿主目标应用的上下文对象。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主启动时触发配置加载。
     */
    fun refreshConfig(context: Context) {
        isEnabled = ConfigClient.getBoolean(context, "timezone_spoof_enabled")
        if (!isEnabled) {
            Log.i(TAG, "系统时区伪装功能处于关闭状态")
            return
        }

        val followRegion = ConfigClient.getBoolean(context, "timezone_follow_region")
        activeZoneIdString = if (followRegion) {
            val targetRegion = ConfigClient.getString(context, "target_region", defaultValue = "JP") ?: "JP"
            RegionPresets.resolveTimeZoneForIso(targetRegion)
        } else {
            ConfigClient.getString(context, "custom_timezone", defaultValue = "Asia/Tokyo") ?: "Asia/Tokyo"
        }

        activeTimeZone = TimeZone.getTimeZone(activeZoneIdString)
        activeZoneId = ZoneId.of(activeZoneIdString)
        activeIcuTimeZone = android.icu.util.TimeZone.getTimeZone(activeZoneIdString)
        Log.i(TAG, "已同步伪装时区配置 -> $activeZoneIdString (followRegion=$followRegion)")
    }

    /**
     * 向宿主全局 JVM 运行时设置伪装的默认时区。
     *
     * @param app 宿主 Application 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主 Application 创建时注入生效。
     */
    fun applyToApplication(app: Application) {
        if (!isEnabled) {
            return
        }
        TimeZone.setDefault(activeTimeZone)
        Log.i(TAG, "宿主全局默认时区设置完成 -> $activeZoneIdString")
    }

    /**
     * 获取当前时区伪装是否处于激活状态。
     *
     * @return 激活返回 true，停用返回 false。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TimeZoneHook.hookTimeZoneMethods`: 方法拦截分发时校验。
     */
    fun isFeatureEnabled(): Boolean = isEnabled

    /**
     * 注册 Java 与 ICU 时区相关的 Hook 拦截器。
     *
     * @param module 当前注入的 [XposedModule] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TimeZoneHook.init`: 模块初始化流程。
     */
    private fun hookTimeZoneMethods(module: XposedModule) {
        // 1. Hook java.util.TimeZone.getDefault()
        val tzClass = TimeZone::class.java
        for (method in tzClass.declaredMethods) {
            if (Modifier.isStatic(method.modifiers) && method.name == "getDefault" && method.parameterCount == 0) {
                module.trackHook("TimeZoneHook", method).intercept { chain ->
                    if (isFeatureEnabled()) {
                        activeTimeZone
                    } else {
                        chain.proceed()
                    }
                }
            }
        }

        // 2. Hook java.time.ZoneId.systemDefault()
        val zoneIdClass = ZoneId::class.java
        for (method in zoneIdClass.declaredMethods) {
            if (Modifier.isStatic(method.modifiers) && method.name == "systemDefault" && method.parameterCount == 0) {
                module.trackHook("TimeZoneHook", method).intercept { chain ->
                    if (isFeatureEnabled()) {
                        activeZoneId
                    } else {
                        chain.proceed()
                    }
                }
            }
        }

        // 3. Hook android.icu.util.TimeZone.getDefault()
        val icuTzClass = android.icu.util.TimeZone::class.java
        for (method in icuTzClass.declaredMethods) {
            if (Modifier.isStatic(method.modifiers) && method.name == "getDefault" && method.parameterCount == 0) {
                module.trackHook("TimeZoneHook", method).intercept { chain ->
                    if (isFeatureEnabled()) {
                        activeIcuTimeZone
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
        Log.i(TAG, "TimeZone 与 ZoneId 系统方法拦截器挂载成功")
    }

    /**
     * 模块初始化入口，安装系统时区伪装拦截机制。
     *
     * @param module 当前注入的 [XposedModule] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标包加载时触发。
     */
    fun init(module: XposedModule) {
        hookTimeZoneMethods(module)
        Log.i(TAG, "系统时区伪装子系统就绪")
    }
}
