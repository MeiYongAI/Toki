package io.github.meiyongai.toki.hook

import java.util.Properties

/** 将缓存失效原因拆分为可核实的代码、规则或元数据变化，不读取功能配置。 */
internal object HostCacheReason {
    /**
     * 解释不匹配的缓存；不改变校验结论或复用未验证结果。
     * @param stored 实际读取的缓存属性，文件不存在时为空。
     * @param identity 当前完整代码身份。
     * @param rules 当前规则摘要。
     * @return 不包含用户配置的诊断说明。
     * Callers: HostSymbols.initialize、单元测试。
     */
    fun describe(stored: Properties, identity: String, rules: String): String {
        if (stored.isEmpty) return "未找到已保存的结果"
        val previousCode = stored.getProperty("cache.identity")
        val previousRules = stored.getProperty("cache.rules")
        if (previousCode == null || previousRules == null) return "缓存标识不匹配，缺少分项记录"
        val changes = buildList {
            if (previousCode != identity) add("宿主代码集合变化：$previousCode → $identity")
            if (previousRules != rules) add("查找规则变化：$previousRules → $rules")
        }
        return changes.joinToString("；").ifEmpty { "缓存格式标识不匹配" }
    }
}
