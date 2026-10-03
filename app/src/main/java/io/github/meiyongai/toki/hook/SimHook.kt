package io.github.meiyongai.toki.hook

import android.app.Application
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import io.github.meiyongai.toki.model.RegionPresets
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Modifier

/**
 * 运营商与 SIM 卡地域伪装信息数据载荷。
 *
 * @property countryIso 两位 ISO 3166-1 国家或地区代号（全小写）。
 * @property operatorCode 移动国家代码（MCC）与移动网络代码（MNC）组合。
 * @property operatorName 运营商官方显示名称。
 */
data class CarrierInfo(
    val countryIso: String,
    val operatorCode: String,
    val operatorName: String
)

/**
 * SIM 卡与运营商地域伪装 Hook 核心处理器。
 *
 * 拦截目标应用进程内对 [TelephonyManager] 的各项反射及 JNI 调用，
 * 将 SIM 卡国家代码、运营商代号、运营商名称及网络状态重定向至用户配置的目标地区。
 */
object SimHook {

    private const val TAG = "TokiSimHook"

    /** 功能总开关状态，默认为开启 */
    @Volatile
    private var isEnabled: Boolean = false

    /** 当前运行时的运营商伪装数据载荷 */
    @Volatile
    private var activeCarrier: CarrierInfo = CarrierInfo(
        countryIso = "jp",
        operatorCode = "44010",
        operatorName = "NTT DOCOMO"
    )

    /**
     * 根据地区代号及自定义参数解析完整的运营商信息。
     *
     * 优先选用显式指定的运营商代码与名称；若未提供，则自动从预设数据库中检索补全。
     *
     * @param regionCode 国家或地区两位代号。
     * @param customOperatorCode 可选的自定义 MCC+MNC 组合。
     * @param customOperatorName 可选的自定义运营商名称。
     * @return 组装完成的 [CarrierInfo] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.SimHook.refreshConfig`: 读取并解析最新配置数据。
     */
    fun resolveCarrier(
        regionCode: String,
        customOperatorCode: String?,
        customOperatorName: String?
    ): CarrierInfo {
        val normalizedIso = regionCode.trim().lowercase()
        val preset = RegionPresets.findByIso(normalizedIso)

        val finalOperatorCode = if (!customOperatorCode.isNullOrBlank()) {
            customOperatorCode.trim()
        } else {
            preset?.operatorCode ?: "00000"
        }

        val finalOperatorName = if (!customOperatorName.isNullOrBlank()) {
            customOperatorName.trim()
        } else {
            preset?.operatorName ?: "${normalizedIso.uppercase()} Carrier"
        }

        return CarrierInfo(
            countryIso = normalizedIso,
            operatorCode = finalOperatorCode,
            operatorName = finalOperatorName
        )
    }

    /**
     * 从跨进程配置服务同步最新设定的功能开关与目标地区数据。
     *
     * @param context 宿主目标应用的上下文对象。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.SimHook.hookApplication`: 宿主 Application 创建时触发同步。
     */
    fun refreshConfig(context: Context) {
        isEnabled = ConfigClient.getBoolean(context, "sim_spoof_enabled")
        if (!isEnabled) {
            Log.i(TAG, "SIM 卡与地域伪装功能处于关闭状态")
            return
        }

        val targetRegion = ConfigClient.getString(context, "target_region", defaultValue = "JP") ?: "JP"
        val customOpCode = ConfigClient.getString(context, "sim_operator_code", defaultValue = null)
        val customOpName = ConfigClient.getString(context, "sim_operator_name", defaultValue = null)

        activeCarrier = resolveCarrier(targetRegion, customOpCode, customOpName)
        Log.i(
            TAG,
            "已同步目标国家/地区配置 -> ${activeCarrier.countryIso.uppercase()} | ${activeCarrier.operatorName} (${activeCarrier.operatorCode})"
        )
    }

    /**
     * 获取当前生效的运营商伪装载荷。
     *
     * @return 当前运行时的 [CarrierInfo] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.SimHook.hookTelephonyManager`: 各 TelephonyManager 拦截器调用。
     */
    fun getActiveCarrier(): CarrierInfo = activeCarrier

    /**
     * 获取当前伪装功能是否开启。
     *
     * @return 开启返回 true，停用返回 false。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.SimHook.hookTelephonyManager`: 执行前校验开关状态。
     */
    fun isFeatureEnabled(): Boolean = isEnabled

    /**
     * 注册 TelephonyManager 相关系统方法的 Hook 拦截器。
     *
     * 当功能停用时，自动放行原始方法调用；启用时按配置分发伪装返回值。
     *
     * @param module 当前注入的 [XposedModule] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.SimHook.init`: 模块初始化流程。
     */
    private fun hookTelephonyManager(module: XposedModule) {
        val telephonyClass = TelephonyManager::class.java
        for (method in telephonyClass.declaredMethods) {
            if (Modifier.isAbstract(method.modifiers)) {
                continue
            }

            when (method.name) {
                "getSimCountryIso", "getNetworkCountryIso" -> {
                    module.trackHook("SimHook", method).intercept { chain ->
                        if (isFeatureEnabled()) {
                            getActiveCarrier().countryIso
                        } else {
                            chain.proceed()
                        }
                    }
                }
                "getSimOperator", "getNetworkOperator" -> {
                    module.trackHook("SimHook", method).intercept { chain ->
                        if (isFeatureEnabled()) {
                            getActiveCarrier().operatorCode
                        } else {
                            chain.proceed()
                        }
                    }
                }
                "getSimOperatorName", "getNetworkOperatorName" -> {
                    module.trackHook("SimHook", method).intercept { chain ->
                        if (isFeatureEnabled()) {
                            getActiveCarrier().operatorName
                        } else {
                            chain.proceed()
                        }
                    }
                }
                "getSimState" -> {
                    module.trackHook("SimHook", method).intercept { chain ->
                        if (isFeatureEnabled()) {
                            TelephonyManager.SIM_STATE_READY
                        } else {
                            chain.proceed()
                        }
                    }
                }
                "hasIccCard" -> {
                    module.trackHook("SimHook", method).intercept { chain ->
                        if (isFeatureEnabled()) {
                            true
                        } else {
                            chain.proceed()
                        }
                    }
                }
                "getPhoneType" -> {
                    module.trackHook("SimHook", method).intercept { chain ->
                        if (isFeatureEnabled()) {
                            TelephonyManager.PHONE_TYPE_GSM
                        } else {
                            chain.proceed()
                        }
                    }
                }
            }
        }
        Log.i(TAG, "TelephonyManager 相关方法 Hook 拦截器注册完成")
    }

    /**
     * 模块初始化入口，安装运营商与地域伪装拦截机制。
     *
     * @param module 当前注入的 [XposedModule] 实例。
     *
     * Callers:
     * - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标包加载时触发。
     */
    fun init(module: XposedModule) {
        hookTelephonyManager(module)
        Log.i(TAG, "SIM 卡地域伪装子系统就绪")
    }
}
