package io.github.meiyongai.toki.ui.screen

import android.os.Bundle

/**
 * 提取列表状态所需的宿主诊断数据，必要字段缺失时明确报告契约错误。
 * @param source 进程名到会话 Bundle 的映射。
 * @return 主进程优先、同级按进程名排序的报告。
 * Callers: HookStatusSection 的后台读取任务。
 */
internal fun readHookSessions(source: Bundle): List<HookProcessReport> = source.keySet().map { process ->
    val session = requireNotNull(source.diagnosticValue<Bundle>(process)) { "诊断会话不是 Bundle：$process" }
    val features = requireNotNull(session.diagnosticValue<Bundle>("features")) { "诊断会话缺少功能集合：$process" }
    HookProcessReport(
        process = process,
        updated = requireNotNull(session.diagnosticValue<Long>("updated")) { "诊断会话缺少报告时间：$process" },
        reports = features.keySet().associateWith { id ->
            val feature = requireNotNull(features.diagnosticValue<Bundle>(id)) { "功能诊断不是 Bundle：$id" }
            HookFeatureReport(
                enabled = feature.diagnosticValue<Boolean>("enabled"),
                state = feature.diagnosticValue<String>("state"), registered = feature.diagnosticValue<Int>("registered") ?: 0,
                error = feature.diagnosticValue<String>("error"), errorStatus = feature.diagnosticValue<String>("errorStatus"),
                appliedEnabled = feature.diagnosticValue<Boolean>("appliedEnabled"),
                appliedRevision = feature.diagnosticValue<Long>("appliedRevision"),
                restartRequired = feature.diagnosticValue<Boolean>("restartRequired") ?: false,
            )
        }
    )
}.sortedWith(compareBy<HookProcessReport> { !it.isMain }.thenBy { it.process })

/**
 * 读取诊断字段并校验类型，避免 Bundle 类型化访问器将错误类型解释成默认值。
 * @param T 发布协议规定的字段类型。
 * @param key 字段名称；未发布的可选字段返回 null。
 * @return 原始值；存在但类型错误时抛出契约异常。
 * Callers: readHookSessions。
 */
@Suppress("DEPRECATION")
private inline fun <reified T : Any> Bundle.diagnosticValue(key: String): T? {
    val value = get(key)
    require(value == null || value is T) { "诊断字段 $key 应为 ${T::class.java.simpleName}，实际为 ${value?.javaClass?.simpleName}" }
    return value as T?
}
