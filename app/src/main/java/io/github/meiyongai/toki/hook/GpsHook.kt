package io.github.meiyongai.toki.hook

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.util.Log
import io.github.meiyongai.toki.model.RegionPresets
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/**
 * 系统 GPS 与地理位置（Location）伪装 Hook 核心处理器。
 *
 * 拦截目标应用进程内对 [Location] 以及 [LocationManager] 的定位获取逻辑，
 * 将经纬度坐标重定向至用户设定的目标位置，重置 [Location.isFromMockProvider] 规避风控检测，
 * 支持跟随目标地区自动计算代表经纬度，亦支持用户全自由度自定义指定。
 */
object GpsHook {

    private const val TAG = "TokiGpsHook"

    /** 功能开关状态 */
    @Volatile
    private var isEnabled: Boolean = false

    /** 当前运行时的生效纬度 */
    @Volatile
    private var activeLatitude: Double = 35.6762

    /** 当前运行时的生效经度 */
    @Volatile
    private var activeLongitude: Double = 139.6503

    /**
     * 从跨进程配置服务同步最新设定的定位伪装配置。
     *
     * 自动研判是否开启“跟随地区设置”，若开启则根据目标地区 ISO 代号反查推荐经纬度；
     * 否则读取用户自定义指定的坐标值。
     *
     * @param context 宿主目标应用的上下文对象。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主启动时触发配置加载。
     */
    fun refreshConfig(context: Context) {
        isEnabled = ConfigClient.getBoolean(context, "gps_spoof_enabled")
        if (!isEnabled) {
            Log.i(TAG, "系统 GPS 伪装功能处于关闭状态")
            return
        }

        val followRegion = ConfigClient.getBoolean(context, "gps_follow_region")
        if (followRegion) {
            val targetRegion = ConfigClient.getString(context, "target_region", defaultValue = "JP") ?: "JP"
            activeLatitude = RegionPresets.resolveLatitudeForIso(targetRegion)
            activeLongitude = RegionPresets.resolveLongitudeForIso(targetRegion)
        } else {
            val customLatStr = ConfigClient.getString(context, "custom_latitude", defaultValue = "35.6762") ?: "35.6762"
            val customLngStr = ConfigClient.getString(context, "custom_longitude", defaultValue = "139.6503") ?: "139.6503"
            activeLatitude = customLatStr.toDoubleOrNull() ?: 35.6762
            activeLongitude = customLngStr.toDoubleOrNull() ?: 139.6503
        }

        Log.i(TAG, "已同步伪装 GPS 坐标 -> ($activeLatitude, $activeLongitude) (followRegion=$followRegion)")
    }

    /**
     * 获取当前定位伪装是否处于激活状态。
     *
     * @return 激活返回 true，停用返回 false。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.GpsHook.hookLocationMethods`: 方法拦截分发时校验。
     */
    fun isFeatureEnabled(): Boolean = isEnabled

    /**
     * 构建包含当前伪装坐标与合理精度的合法 [Location] 实例。
     *
     * @param provider 定位提供者名称（如 "gps" 或 "network"）。
     * @return 注入伪装数据的 [Location] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.GpsHook.hookLocationManagerMethods`: 兜底返回合法定位数据。
     */
    fun createSpoofedLocation(provider: String = LocationManager.GPS_PROVIDER): Location {
        return Location(provider).apply {
            latitude = activeLatitude
            longitude = activeLongitude
            altitude = 35.0
            accuracy = 5.0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
    }

    /**
     * 注册 Location 实体类属性获取相关的 Hook 拦截器。
     *
     * @param module 当前注入的 [XposedModule] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.GpsHook.init`: 模块初始化流程。
     */
    private fun hookLocationMethods(module: XposedModule) {
        val locationClass = Location::class.java

        // 1. Hook getLatitude()
        val getLatMethod = locationClass.getMethod("getLatitude")
        module.trackHook("GpsHook", getLatMethod).intercept { chain ->
            if (isFeatureEnabled()) {
                activeLatitude
            } else {
                chain.proceed()
            }
        }

        // 2. Hook getLongitude()
        val getLngMethod = locationClass.getMethod("getLongitude")
        module.trackHook("GpsHook", getLngMethod).intercept { chain ->
            if (isFeatureEnabled()) {
                activeLongitude
            } else {
                chain.proceed()
            }
        }

        // 3. Hook isFromMockProvider()，防止 TikTok 检测虚拟定位
        for (method in locationClass.declaredMethods) {
            if (method.name == "isFromMockProvider" && method.parameterCount == 0) {
                module.trackHook("GpsHook", method).intercept { chain ->
                    if (isFeatureEnabled()) {
                        false
                    } else {
                        chain.proceed()
                    }
                }
            }
        }

        Log.i(TAG, "Location 经纬度与 Mock 防检测拦截器挂载完成")
    }

    /**
     * 注册 LocationManager 系统服务相关的 Hook 拦截器。
     *
     * @param module 当前注入的 [XposedModule] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.GpsHook.init`: 模块初始化流程。
     */
    private fun hookLocationManagerMethods(module: XposedModule) {
        val lmClass = LocationManager::class.java
        for (method in lmClass.declaredMethods) {
            if (Modifier.isAbstract(method.modifiers)) {
                continue
            }
            if (method.name == "getLastKnownLocation" && method.parameterCount == 1) {
                module.trackHook("GpsHook", method).intercept { chain ->
                    if (isFeatureEnabled()) {
                        val realResult = chain.proceed() as? Location
                        if (realResult != null) {
                            realResult.latitude = activeLatitude
                            realResult.longitude = activeLongitude
                            realResult
                        } else {
                            val provider = chain.args[0] as? String ?: LocationManager.GPS_PROVIDER
                            createSpoofedLocation(provider)
                        }
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
        Log.i(TAG, "LocationManager 定位服务拦截器挂载完成")
    }

    /**
     * 模块初始化入口，安装系统 GPS 伪装拦截机制。
     *
     * @param module 当前注入的 [XposedModule] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标包加载时触发。
     */
    fun init(module: XposedModule) {
        hookLocationMethods(module)
        hookLocationManagerMethods(module)
        Log.i(TAG, "系统 GPS 伪装子系统就绪")
    }
}
