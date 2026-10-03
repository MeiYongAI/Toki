package io.github.meiyongai.toki.provider

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

/** 配置文件的完整往返、默认值、类型边界和失败不修改输入测试。 */
class ConfigArchiveTest {
    /**
     * 两种推荐卡过滤默认关闭，独立参与状态监控、配置快照及文件导入导出。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun recommendationFiltersShareDefaultsStatusAndArchiveSchema() {
        val topic = "feed_remove_topic_recommendations"
        val creator = "feed_remove_creator_recommendations"
        for (key in listOf(topic, creator)) {
            assertFalse(ConfigSchema.booleanDefault(key))
            assertTrue(key in ConfigSchema.featureSwitches.getValue("FeedFilterHook"))
            assertThrows(IllegalArgumentException::class.java) { ConfigSchema.validate(mapOf(key to "true")) }
        }
        for (topicsEnabled in listOf(false, true)) {
            for (creatorsEnabled in listOf(false, true)) {
                val values = mapOf(topic to topicsEnabled, creator to creatorsEnabled)
                assertEquals(values, ConfigArchive.read(ByteArrayInputStream(ConfigArchive.encode(values))))
                assertEquals(values, ConfigSnapshot(values).configuration())
            }
        }
    }

    /** 构建版本一文件。@param values 配置 JSON 对象。@return 完整文件。Callers: 本类测试。 */
    private fun document(values: String): String = """{"format":"toki-config","version":1,"values":$values}"""

    /** 验证中文、转义字符及显式布尔值完整保留。@return Unit。Callers: JUnit。 */
    @Test fun roundTripPreservesConfiguration() {
        val values = mapOf("sim_spoof_enabled" to true, "target_region" to "JP",
            "feed_filter_keywords_json" to """{"desc":["中文\"词条","line\nbreak"],"tag":["猫"],"author":[]}""")
        assertEquals(values, ConfigArchive.read(ByteArrayInputStream(ConfigArchive.encode(values))))
    }

    /** 验证空配置合法，显式覆盖确认可恢复所有默认值。@return Unit。Callers: JUnit。 */
    @Test fun emptyConfigurationRoundTrips() {
        assertTrue(ConfigArchive.decode(document("{}")).isEmpty())
    }

    /** 验证全部功能总开关默认关闭，从属地区联动选项仍开启。@return Unit。Callers: JUnit。 */
    @Test fun featureDefaultsAreDisabledAndShared() {
        val empty = ConfigSnapshot(emptyMap())
        ConfigSchema.featureSwitches.values.flatten().forEach {
            assertFalse(it, ConfigSchema.booleanDefault(it))
            assertFalse(it, empty.boolean(it))
        }
        assertTrue(empty.boolean("language_follow_region"))
        assertTrue(empty.boolean("timezone_follow_region"))
        assertTrue(empty.boolean("gps_follow_region"))
    }

    /** 验证明确定义的用户配置优先于默认值。@return Unit。Callers: JUnit。 */
    @Test fun explicitValuesSurviveDefaultChanges() {
        assertTrue(ConfigSnapshot(mapOf("fixed_speed_enabled" to true)).boolean("fixed_speed_enabled"))
        assertFalse(ConfigSnapshot(mapOf("language_follow_region" to false)).boolean("language_follow_region"))
    }

    /** 验证内部版本及诊断不会被导出。@return Unit。Callers: JUnit。 */
    @Test fun snapshotExcludesInternalData() {
        assertEquals(mapOf("music_unlock" to false), ConfigSnapshot(mapOf(
            "music_unlock" to false, "__toki_revision" to 9L, "session" to "test")).configuration())
    }

    /** 验证错误版本和结构整文件拒绝。@return Unit。Callers: JUnit。 */
    @Test fun unsupportedEnvelopesAreRejected() {
        val valid = document("{}")
        for (invalid in listOf(valid.replace("toki-config", "another-config"),
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"version\":1", "\"version\":1.5"),
            valid.replace("\"version\":1", "\"version\":\"1\""),
            document("[]"), "{}", valid.dropLast(1) + ",\"extra\":true}")) {
            assertThrows(IllegalArgumentException::class.java) { ConfigArchive.decode(invalid) }
        }
    }

    /** 验证未知配置和内部版本不能经导入写入。@return Unit。Callers: JUnit。 */
    @Test fun unknownAndInternalKeysAreRejected() {
        for (key in listOf("__toki_revision", "unknown", "host_cache")) {
            assertThrows(IllegalArgumentException::class.java) { ConfigArchive.decode(document("{\"$key\":\"x\"}")) }
        }
    }

    /** 验证不隐式转换布尔、字符串或 null。@return Unit。Callers: JUnit。 */
    @Test fun wrongTypesAreRejected() {
        for (values in listOf("""{"fixed_speed_enabled":"true"}""", """{"fixed_speed_value":2}""",
            """{"target_region":null}""", """{"music_unlock":1}""")) {
            assertThrows(IllegalArgumentException::class.java) { ConfigArchive.decode(document(values)) }
        }
    }

    /** 验证关键词分类和条目严格校验，不把错误内容当空列表。@return Unit。Callers: JUnit。 */
    @Test fun malformedKeywordStructuresAreRejected() {
        for (keywords in listOf("[]", "{\"desc\":\"word\"}", "{\"desc\":[12]}",
            "{\"author\":[null]}", "{\"tag\":[\" \" ]}", "{\"unexpected\":[]}")) {
            assertThrows(IllegalArgumentException::class.java) {
                ConfigArchive.validate(mapOf("feed_filter_keywords_json" to keywords))
            }
        }
    }

    /** 验证非负整数范围、上下限关系和毫秒换算溢出。@return Unit。Callers: JUnit。 */
    @Test fun invalidRangesAreRejected() {
        for (values in listOf(mapOf("feed_filter_duration_min" to "50", "feed_filter_duration_max" to "40"),
            mapOf("feed_filter_views_min" to "-1"), mapOf("feed_filter_likes_max" to "1.5"),
            mapOf("feed_filter_duration_max" to Long.MAX_VALUE.toString()))) {
            assertThrows(IllegalArgumentException::class.java) { ConfigArchive.validate(values) }
        }
        ConfigArchive.validate(mapOf("feed_filter_duration_min" to "40", "feed_filter_duration_max" to "0"))
    }

    /** 验证浮点无穷、NaN、越界坐标及倍速拒绝。@return Unit。Callers: JUnit。 */
    @Test fun invalidNumbersAreRejected() {
        for ((key, value) in listOf("custom_latitude" to "91", "custom_longitude" to "-181",
            "fixed_speed_value" to "NaN", "fixed_speed_value" to "3.1", "speed_expand_list" to "1,,2",
            "video_duration_alert_threshold" to "Infinity", "video_duration_alert_threshold" to "0")) {
            assertThrows(IllegalArgumentException::class.java) { ConfigArchive.validate(mapOf(key to value)) }
        }
    }

    /** 验证身份字段和下载目录拒绝不支持值。@return Unit。Callers: JUnit。 */
    @Test fun invalidIdentifiersAndPathsAreRejected() {
        for ((key, value) in listOf("target_region" to "JPN", "sim_operator_code" to "abc",
            "custom_language" to "_invalid", "custom_timezone" to "No/Such_Zone",
            "download_video_path" to "/Movies/TikTok", "download_image_path" to "Pictures/../private",
            "download_video_path" to "Invalid/TikTok", "download_video_path" to "Movies",
            "download_image_path" to "Pictures/ extra ")) {
            assertThrows(IllegalArgumentException::class.java) { ConfigArchive.validate(mapOf(key to value)) }
        }
        ConfigArchive.validate(mapOf("download_video_path" to "Movies/TikTok", "download_image_path" to "Pictures/TikTok"))
    }

    /** 验证文件截断和尾随内容不会导入。@return Unit。Callers: JUnit。 */
    @Test fun invalidJsonIsRejected() {
        assertThrows(JSONException::class.java) { ConfigArchive.decode(document("{}").dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) { ConfigArchive.decode(document("{}") + " extra") }
    }

    /** 验证读取上限在完整分配文件前生效。@return Unit。Callers: JUnit。 */
    @Test fun oversizedInputIsRejected() {
        val input = ByteArrayInputStream(ByteArray(ConfigArchive.MAX_BYTES + 1) { ' '.code.toByte() })
        assertThrows(IllegalArgumentException::class.java) { ConfigArchive.read(input) }
    }

    /** 验证无效 UTF-8 不被替换为其他字符后导入。@return Unit。Callers: JUnit。 */
    @Test fun invalidUtf8IsRejected() {
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            ConfigArchive.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }

    /** 验证读取失败原样传播，调用者能显示失败。@return Unit。Callers: JUnit。 */
    @Test fun inputFailureIsPropagated() {
        val failure = IOException("test read failure")
        val input = object : InputStream() {
            /** 模拟文件提供器读取失败。@return 不返回。Callers: ConfigArchive.read。 */
            override fun read(): Int = throw failure
        }
        assertSame(failure, assertThrows(IOException::class.java) { ConfigArchive.read(input) })
    }

    /** 验证失败不修改调用方集合，成功结果也不共享可变集合。@return Unit。Callers: JUnit。 */
    @Test fun validationIsIsolatedFromInput() {
        val values = mutableMapOf<String, Any>("music_unlock" to true, "feed_filter_duration_min" to "-1")
        val before = values.toMap()
        assertThrows(IllegalArgumentException::class.java) { ConfigArchive.validate(values) }
        assertEquals(before, values)
        values.remove("feed_filter_duration_min")
        val validated = ConfigArchive.validate(values)
        values["music_unlock"] = false
        assertEquals(true, validated["music_unlock"])
    }
}
