package io.github.meiyongai.toki.hook

import android.content.Context
import io.github.meiyongai.toki.provider.ConfigSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/** 验证倍速运行记忆不依赖管理端进程，且遵守配置导入语义。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PlaybackSpeedMemoryTest {
    /** 创建独立偏好。@return 宿主记忆存储。Callers: 本类测试。 */
    private fun preferences() = RuntimeEnvironment.getApplication()
        .getSharedPreferences(UUID.randomUUID().toString(), Context.MODE_PRIVATE)

    /** 初次使用保留管理端已记忆的倍速。@return Unit。Callers: JUnit。 */
    @Test fun firstReadUsesConfiguredSpeed() {
        val config = ConfigSnapshot(mapOf("fixed_speed_value" to "2.0"), 75)
        assertEquals(2f, PlaybackSpeedMemory(preferences()).read(config))
    }

    /** 宿主新实例仍读取同一倍速，无任何跨进程写入。@return Unit。Callers: JUnit。 */
    @Test fun selectionSurvivesNewHostInstance() {
        val preferences = preferences()
        val config = ConfigSnapshot(emptyMap(), 75)
        assertTrue(PlaybackSpeedMemory(preferences).save(3f, config))
        assertEquals(3f, PlaybackSpeedMemory(preferences).read(config))
    }

    /** 无关功能热更新不重置当前倍速。@return Unit。Callers: JUnit。 */
    @Test fun unrelatedConfigurationRevisionDoesNotResetSpeed() {
        val memory = PlaybackSpeedMemory(preferences())
        memory.save(2f, ConfigSnapshot(emptyMap(), 1))
        assertEquals(2f, memory.read(ConfigSnapshot(mapOf("feed_remove_ads" to true), 2)))
    }

    /** 全量导入即使倍速文本相同也重新使用导入基准。@return Unit。Callers: JUnit。 */
    @Test fun importResetsRuntimeChoice() {
        val memory = PlaybackSpeedMemory(preferences())
        memory.save(3f, ConfigSnapshot(mapOf("fixed_speed_value" to "1.5"), 1))
        assertEquals(1.5f, memory.read(ConfigSnapshot(mapOf("fixed_speed_value" to "1.5"), 2, 2)))
    }

    /** 配置中的倍速基准变化优先于宿主记忆。@return Unit。Callers: JUnit。 */
    @Test fun configuredSpeedChangeResetsRuntimeChoice() {
        val memory = PlaybackSpeedMemory(preferences())
        memory.save(3f, ConfigSnapshot(emptyMap(), 1))
        assertEquals(2f, memory.read(ConfigSnapshot(mapOf("fixed_speed_value" to "2.0"), 2)))
    }

    /** 非法输入不保存。@return Unit。Callers: JUnit。 */
    @Test fun invalidSelectionCannotBePersisted() {
        val preferences = preferences()
        val memory = PlaybackSpeedMemory(preferences)
        for (value in listOf(0f, 4f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { memory.save(value, ConfigSnapshot(emptyMap())) }
        }
        assertTrue(preferences.all.isEmpty())
    }
}
