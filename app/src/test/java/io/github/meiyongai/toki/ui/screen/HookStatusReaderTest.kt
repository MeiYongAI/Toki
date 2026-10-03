package io.github.meiyongai.toki.ui.screen

import android.os.Bundle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 使用真实 Android Bundle 行为验证展示层读取协议；不启动应用或宿主。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class HookStatusReaderTest {
    /**
     * 验证无会话为空列表，不能生成虚构正常记录。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun emptySnapshotContainsNoSessions() {
        assertTrue(readHookSessions(Bundle()).isEmpty())
    }

    /**
     * 验证两个主包均优先显示，辅助进程按名称稳定排序。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun mainProcessesPrecedeAuxiliaryProcesses() {
        val names = listOf("com.zhiliaoapp.musically:push", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically:analytics")
        val source = Bundle().apply { names.forEach { putBundle(it, session()) } }
        val result = readHookSessions(source)
        assertEquals(listOf(names[2], names[1], names[3], names[0]), result.map { it.process })
        assertTrue(result.take(2).all { it.isMain })
        assertFalse(result[2].isMain)
    }

    /**
     * 验证配置缺失与显式关闭互不混淆，状态判断需要的异常完整保留。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun readsCompleteFeatureWithoutInferringMissingSwitches() {
        val counts = Bundle().apply { putLong("调用:B#bind()", 8); putLong("拒绝:时长", 3) }
        val feature = Bundle().apply {
            putBoolean("enabled", false)
            putString("state", "已注册")
            putInt("registered", 4)
            putString("detail", "测试诊断")
            putString("error", "java.lang.IllegalStateException")
            putString("errorStatus", "模块回调异常")
            putLong("errorAt", 5000)
            putBundle("counts", counts)
        }
        val features = Bundle().apply {
            putBundle("FeedFilterHook", feature)
            putBundle("HostSymbols", Bundle().apply { putString("state", "缓存已验证") })
        }
        val process = readHookSessions(snapshot(features)).single()
        assertEquals(1000L, process.updated)
        assertEquals(HookFeatureReport(false, "已注册", 4, "java.lang.IllegalStateException", "模块回调异常"),
            process.reports["FeedFilterHook"])
        assertNull(process.reports.getValue("HostSymbols").enabled)
    }

    /**
     * 验证后续 Bundle 修改不影响已生成的展示快照。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun parsedReportDoesNotRetainMutableBundles() {
        val feature = Bundle().apply { putInt("registered", 2) }
        val parsed = readHookSessions(snapshot(Bundle().apply { putBundle("FeedFilterHook", feature) })).single()
        feature.putInt("registered", 90)
        feature.putBoolean("enabled", true)
        assertEquals(2, parsed.reports.getValue("FeedFilterHook").registered)
        assertNull(parsed.reports.getValue("FeedFilterHook").enabled)
    }

    /**
     * 验证缺少必要会话字段时明确拒绝，不制造默认会话。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun missingSessionFieldsAreRejected() {
        for (field in listOf("updated", "features")) {
            val session = session().apply { remove(field) }
            assertThrows(IllegalArgumentException::class.java) {
                readHookSessions(Bundle().apply { putBundle("com.zhiliaoapp.musically", session) })
            }
        }
    }

    /**
     * 验证错误开关类型不能被 Android 默认值解释成未开启。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun malformedSwitchIsRejected() {
        val features = Bundle().apply { putBundle("FeedFilterHook", Bundle().apply { putString("enabled", "false") }) }
        assertThrows(IllegalArgumentException::class.java) { readHookSessions(snapshot(features)) }
    }

    @Test fun readsRequestedAndAppliedConfigurationIndependently() {
        val feature = Bundle().apply {
            putBoolean("enabled", false)
            putBoolean("appliedEnabled", true)
            putLong("appliedRevision", 3L)
            putBoolean("restartRequired", true)
        }
        val report = readHookSessions(snapshot(Bundle().apply { putBundle("LocaleHook", feature) }))
            .single().reports.getValue("LocaleHook")
        assertEquals(false, report.enabled)
        assertEquals(true, report.appliedEnabled)
        assertEquals(3L, report.appliedRevision)
        assertTrue(report.restartRequired)
        feature.putString("appliedEnabled", "true")
        assertThrows(IllegalArgumentException::class.java) {
            readHookSessions(snapshot(Bundle().apply { putBundle("LocaleHook", feature) }))
        }
    }

    /**
     * 验证错误的注册数量不能被解释成零。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun malformedRegistrationCountIsRejected() {
        val features = Bundle().apply { putBundle("FeedFilterHook", Bundle().apply { putString("registered", "3") }) }
        assertThrows(IllegalArgumentException::class.java) { readHookSessions(snapshot(features)) }
    }

    /**
     * 验证错误的会话和功能集合类型不被跳过。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun malformedContainersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { readHookSessions(Bundle().apply { putString("process", "invalid") }) }
        assertThrows(IllegalArgumentException::class.java) { readHookSessions(snapshot(Bundle().apply { putInt("FeedFilterHook", 1) })) }
    }

    /**
     * 构建包含必要会话字段的发布协议样本。
     * @param features 按功能标识索引的 Bundle。
     * @return 一份有效的会话 Bundle。
     * Callers: snapshot、mainProcessesPrecedeAuxiliaryProcesses、missingSessionFieldsAreRejected。
     */
    private fun session(features: Bundle = Bundle()) = Bundle().apply {
        putString("session", "session")
        putInt("pid", 123)
        putLong("updated", 1000)
        putLong("configRevision", 7)
        putString("adaptation", "适配结果")
        putBundle("features", features)
    }

    /**
     * 构建单个主进程的 Provider 快照。
     * @param features 测试使用的功能报告。
     * @return 进程名称到会话 Bundle 的映射。
     * Callers: 本测试类的解析与错误类型测试。
     */
    private fun snapshot(features: Bundle) = Bundle().apply { putBundle("com.zhiliaoapp.musically", session(features)) }
}
