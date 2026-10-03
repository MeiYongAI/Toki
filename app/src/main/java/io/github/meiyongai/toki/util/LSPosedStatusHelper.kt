package io.github.meiyongai.toki.util

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.meiyongai.toki.provider.ConfigClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LSPosed 框架激活状态与框架元数据载体。
 *
 * @property isActivated 模块是否已成功绑定 LSPosed 框架服务。
 * @property details 框架详细信息展示文本（框架名、版本号、构建号及支持的 API 等级）。
 */
data class LSPosedStatus(
    val isActivated: Boolean = false,
    val details: String = "None"
)

/**
 * LSPosed 框架服务监听与状态管理器。
 *
 * 负责通过标准现代化接口 [XposedServiceHelper] 监听并绑定系统下发的 [XposedService]，
 * 实时同步并维护模块当前运行时的激活状态与框架版本信息。
 */
object LSPosedStatusHelper {

    private val _status = MutableStateFlow(LSPosedStatus(isActivated = false, details = "None"))

    /**
     * 对外暴露的只读响应式框架激活状态流。
     */
    val status: StateFlow<LSPosedStatus> = _status.asStateFlow()

    private var isInitialized = false

    /**
     * 注册并启动 LSPosed 服务监听器。
     *
     * @return Unit；无入参。
     * Callers: TokiApplication.onCreate、MainActivity.onCreate。
     */
    fun init() {
        if (isInitialized) return
        isInitialized = true

        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            /** 发布当前完整配置并更新激活信息。@param service 框架连接。@return Unit。Callers: XposedServiceHelper。 */
            override fun onServiceBind(service: XposedService) {
                ConfigClient.connectFramework { service.getRemotePreferences(ConfigClient.FRAMEWORK_GROUP) }
                val frameworkName = service.frameworkName.ifBlank { "LSPosed" }
                val frameworkVersion = service.frameworkVersion.ifBlank { "2.2.0-it" }
                val frameworkVersionCode = service.frameworkVersionCode
                val apiVersion = service.apiVersion
                val details = "$frameworkName $frameworkVersion ($frameworkVersionCode), API $apiVersion"

                _status.value = LSPosedStatus(
                    isActivated = true,
                    details = details
                )
            }

            /** 标记服务断开，保留本地设置。@param service 已失效连接。@return Unit。Callers: XposedServiceHelper。 */
            override fun onServiceDied(service: XposedService) {
                ConfigClient.disconnectFramework()
                _status.value = LSPosedStatus(
                    isActivated = false,
                    details = "None"
                )
            }
        })
    }
}
