package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

/** 验证规则独立失效、补查合并及宿主变化的全量失效边界。 */
class HostScanPlanTest {
    private val a = HostSymbol.SETTINGS
    private val b = HostSymbol.SEARCH_AUTO_SCROLL
    private val rules = "SETTINGS\tfirst\nSEARCH_AUTO_SCROLL\tsecond\n"

    /** @return 包含成功和未匹配结果的有效缓存。无参数。Callers: 测试。 */
    private fun saved(): Properties = HostScanPlan(Properties(), rules, "host", "format", setOf(a, b)).merge(Properties().apply {
        setProperty(a.name, "X.Settings")
        setProperty("member.SETTINGS.reader", "read()I")
        setProperty("members.SETTINGS.group", "one\ntwo")
        setProperty("error.${b.name}", "候选数量=0")
    })

    /** 模块更新不改变规则时无需扫描；调整行顺序也无需扫描。无参数、无返回。Callers: JUnit。 */
    @Test fun unchangedTargetsAndNegativeResultsAreReused() {
        val saved = saved()
        assertTrue(HostScanPlan(saved, rules, "host", "format", setOf(a, b)).pending.isEmpty())
        val reordered = rules.lineSequence().filter { it.isNotBlank() }.toList().reversed().joinToString("\r\n")
        assertTrue(HostScanPlan(saved, reordered, "host", "format", setOf(a, b)).pending.isEmpty())
    }

    /** 单目标变更只重查该目标，并清除旧错误保留其他角色。无参数、无返回。Callers: JUnit。 */
    @Test fun changedRuleOnlyRescansItsOwnTarget() {
        val plan = HostScanPlan(saved(), rules.replace("second", "changed"), "host", "format", setOf(a, b))
        assertEquals(setOf(b), plan.pending)
        val result = plan.merge(Properties().apply { setProperty(b.name, "X.Search") })
        assertNull(result.getProperty("error.${b.name}"))
        assertEquals("read()I", result.getProperty("member.SETTINGS.reader"))
        assertEquals("one\ntwo", result.getProperty("members.SETTINGS.group"))
    }

    /** 未启用目标变化不触发查找，新增依赖只查新增项。无参数、无返回。Callers: JUnit。 */
    @Test fun disabledRuleChangesDoNotBlockStartupAndNewDependencyOnlyAddsItself() {
        assertTrue(HostScanPlan(saved(), rules.replace("second", "changed"), "host", "format", setOf(a)).pending.isEmpty())
        val onlyA = HostScanPlan(Properties(), rules, "host", "format", setOf(a)).merge(Properties().apply { setProperty(a.name, "X.A") })
        assertEquals(setOf(b), HostScanPlan(onlyA, rules, "host", "format", setOf(a, b)).pending)
    }

    /** 删除目标及枚举后保留有效缓存，合并时清除失效记录。无参数、无返回。Callers: JUnit。 */
    @Test fun removedSymbolsArePrunedBeforeEnumResolution() {
        val stored = saved().apply {
            setProperty("cache.symbols", "$a,$b,REMOVED_TARGET")
            setProperty("REMOVED_TARGET", "X.Removed")
            setProperty("member.REMOVED_TARGET.reader", "read()Z")
        }
        val plan = HostScanPlan(stored, "SETTINGS\tfirst\n", "host", "format", setOf(a))
        assertEquals(setOf(a), plan.retained)
        assertTrue(plan.pending.isEmpty())
        val result = plan.merge(Properties())
        assertEquals(a.name, result.getProperty("cache.symbols"))
        assertNull(result.getProperty("REMOVED_TARGET"))
        assertNull(result.getProperty("member.REMOVED_TARGET.reader"))
        assertNull(result.getProperty("error.${b.name}"))
        assertEquals("X.Settings", result.getProperty(a.name))
    }

    /** TikTok 代码或扫描语义改变必须失效，缺失结果不能发布。无参数、无返回。Callers: JUnit。 */
    @Test fun hostAndScannerChangesInvalidateAllRequiredTargets() {
        assertEquals(setOf(a, b), HostScanPlan(saved(), rules, "other", "format", setOf(a, b)).pending)
        assertEquals(setOf(a, b), HostScanPlan(saved(), rules, "host", "other", setOf(a, b)).pending)
        val plan = HostScanPlan(saved(), rules.replace("first", "changed"), "host", "format", setOf(a, b))
        assertThrows(IllegalStateException::class.java) { plan.merge(Properties()) }
        val failed = plan.merge(Properties().apply { setProperty("error.${a.name}", "候选数量=2") })
        assertNull(failed.getProperty("member.SETTINGS.reader"))
        assertNull(failed.getProperty("members.SETTINGS.group"))
    }
}
