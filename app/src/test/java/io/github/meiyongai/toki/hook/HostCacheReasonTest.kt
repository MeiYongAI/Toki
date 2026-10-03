package io.github.meiyongai.toki.hook

import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

/** 验证重查诊断区分代码、规则、缺失文件和格式，不把配置修改归因为代码变化。 */
class HostCacheReasonTest {
    /** 构造完整缓存元数据。@return 属性集合。Callers: 本类测试。 */
    private fun stored() = Properties().apply {
        setProperty("cache.identity", "code")
        setProperty("cache.rules", "rules")
    }

    /** 文件缺失和分项元数据缺失具有不同诊断。@return Unit。Callers: JUnit。 */
    @Test fun missingMetadataIsExplicit() {
        assertEquals("未找到已保存的结果", HostCacheReason.describe(Properties(), "code", "rules"))
        val value = Properties().apply { setProperty("cache.key", "key") }
        assertEquals("缓存标识不匹配，缺少分项记录", HostCacheReason.describe(value, "code", "rules"))
    }

    /** 动态代码分包变化明确归因为代码集合。@return Unit。Callers: JUnit。 */
    @Test fun codeChangeIsNotRuleChange() {
        val reason = HostCacheReason.describe(stored(), "newCode", "rules")
        assertTrue(reason.contains("宿主代码集合变化"))
        assertFalse(reason.contains("查找规则变化"))
    }

    /** 规则升级与配置变化互相独立。@return Unit。Callers: JUnit。 */
    @Test fun rulesAndConfigurationAreIndependent() {
        val values = stored().apply { setProperty("speed", "3") }
        assertEquals("缓存格式标识不匹配", HostCacheReason.describe(values, "code", "rules"))
        assertTrue(HostCacheReason.describe(values, "code", "newRules").contains("查找规则变化"))
    }

    /** 两种变化同时发生时均记录。@return Unit。Callers: JUnit。 */
    @Test fun simultaneousChangesAreReported() {
        val reason = HostCacheReason.describe(stored(), "newCode", "newRules")
        assertTrue(reason.contains("宿主代码集合变化") && reason.contains("查找规则变化"))
    }
}
