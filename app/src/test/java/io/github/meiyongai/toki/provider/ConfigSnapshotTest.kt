package io.github.meiyongai.toki.provider

import org.junit.Assert.*
import org.junit.Test

/** 配置快照复制、删除及类型契约测试。 */
class ConfigSnapshotTest {
    @Test fun eachMainFeatureCanActivateHostStartup() {
        val mainSwitches = ConfigSchema.featureSwitches.values.flatten().toSet() - "clean_mode_show_progress_bar"
        for (key in mainSwitches) {
            assertTrue(key, ConfigSnapshot(mapOf(key to true)).hasEnabledFeatures())
        }
        assertFalse(ConfigSnapshot(emptyMap()).hasEnabledFeatures())
        assertFalse(ConfigSnapshot(mapOf("clean_mode_show_progress_bar" to true,
            "gps_follow_region" to true, "timezone_follow_region" to true,
            "language_follow_region" to true)).hasEnabledFeatures())
    }

    /** 验证配置版本和同批上下限保存在不可变快照中，不被后续输入修改。@return Unit。Callers: JUnit。 */
    @Test fun revisionBelongsToTheCompleteSnapshot() {
        val values = mutableMapOf<String, Any>("min" to "40", "max" to "50")
        val snapshot = ConfigSnapshot(values, 7)
        values["max"] = "60"
        assertEquals(7L, snapshot.revision)
        assertEquals("40", snapshot.string("min"))
        assertEquals("50", snapshot.string("max"))
    }

    /** 验证输入 Map 的后续修改不会污染快照。@return Unit。Callers: JUnit。 */
    @Test fun snapshotOwnsItsValues() {
        val values = mutableMapOf<String, Any>("enabled" to true, "min" to "10")
        val snapshot = ConfigSnapshot(values)
        values["enabled"] = false
        values["min"] = "20"
        assertTrue(snapshot.boolean("enabled"))
        assertEquals("10", snapshot.string("min"))
    }

    /** 验证全量替换不会保留已删除的配置。@return Unit。Callers: JUnit。 */
    @Test fun replacementDoesNotRetainDeletedKeys() {
        val previous = ConfigSnapshot(mapOf("text" to "blocked"))
        val next = ConfigSnapshot(emptyMap())
        assertEquals("blocked", previous.string("text"))
        assertNull(next.string("text"))
    }

    /** 验证类型错误明确报告，不解释为功能关闭。@return Unit。Callers: JUnit。 */
    @Test(expected = ClassCastException::class)
    fun typeMismatchIsNotHidden() {
        ConfigSnapshot(mapOf("enabled" to "true")).boolean("enabled")
    }
}
