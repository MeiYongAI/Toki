package io.github.meiyongai.toki.provider

/**
 * 不可变配置与共享版本，完整替换时自然清除已删除的键。
 * @param values 存储端返回的完整键值。
 * @param revision 与这些键值同一事务保存的配置版本。
 * @param importRevision 最近一次完整导入的版本，用于重置宿主运行偏好。
 * Callers: ConfigStore、单元测试。
 */
class ConfigSnapshot(values: Map<String, Any>, val revision: Long = 0, val importRevision: Long = 0) {
    private val values: Map<String, Any> = values.toMap()

    /**
     * 读取布尔配置。
     * @param key 配置键。
     * @param defaultValue 未配置时的产品默认值。
     * @return 配置值；类型错误立即报告。
     * Callers: ConfigClient.getBoolean、FeedFilterPolicy.fromConfig。
     */
    fun boolean(key: String, defaultValue: Boolean = ConfigSchema.booleanDefaults[key] ?: false): Boolean =
        values[key]?.let { it as Boolean } ?: defaultValue

    /**
     * 提取可导出的功能配置，不暴露内部版本或诊断字段。
     * @return 独立的功能配置副本。
     * Callers: ConfigTransferSection。
     */
    fun configuration(): Map<String, Any> = values.filterKeys { it in ConfigSchema.keys }

    /**
     * 是否需要在本次宿主启动中注册功能；从属选项不能单独启动父功能。
     * @return 至少一个主功能开启时为 true。
     * Callers: TokiModule.onModuleLoaded。
     */
    fun hasEnabledFeatures(): Boolean = ConfigSchema.featureSwitches.values.any { switches ->
        switches.any { it != "clean_mode_show_progress_bar" && boolean(it) }
    }

    /**
     * 读取字符串配置。
     * @param key 配置键。
     * @param defaultValue 未配置时的产品默认值。
     * @return 字符串或默认值；类型错误立即报告。
     * Callers: ConfigClient.getString、FeedFilterPolicy.fromConfig。
     */
    fun string(key: String, defaultValue: String? = null): String? =
        values[key]?.let { it as String } ?: defaultValue
}
