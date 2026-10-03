package io.github.meiyongai.toki.provider

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/** 配置通信的启动隔离、原子发布、失效状态、导入和断线恢复测试。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ConfigStoreTest {
    /** 创建独立配置存储。@return 空的私有 SharedPreferences。Callers: 本类测试。 */
    private fun preferences(): SharedPreferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences(UUID.randomUUID().toString(), Context.MODE_PRIVATE)

    /**
     * 创建已发布的真实 Android 配置对象。
     * @param enabled 测试开关。
     * @param revision 测试版本。
     * @return 带协议和版本的配置。
     * Callers: 本类测试。
     */
    private fun published(enabled: Boolean = true, revision: Long = 4): SharedPreferences = preferences().also {
        assertTrue(it.edit().putInt(ConfigStore.PROTOCOL, 1).putLong(ConfigStore.REVISION, revision)
            .putBoolean("feed_remove_ads", enabled).commit())
    }

    /** 未连接不能被解释为全部开关关闭。@return Unit；无入参。Callers: JUnit。 */
    @Test fun unconnectedStoreHasNoUsableSnapshot() {
        val store = ConfigStore({}, {})
        assertFalse(store.isReady)
        assertThrows(IllegalStateException::class.java) { store.snapshot() }
    }

    /** 启动通信失败只产生明确错误状态，不向宿主生命周期抛出异常。@return Unit。Callers: JUnit。 */
    @Test fun hostConnectionFailureIsReportedWithoutEscapingStartup() {
        val failure = IllegalArgumentException("Unknown authority io.github.meiyongai.toki.provider")
        val errors = mutableListOf<Exception>()
        val store = ConfigStore({}, errors::add)
        assertFalse(store.attachHost({ throw failure }, { fail("未取得配置时不应监听") }))
        assertSame(failure, store.syncError)
        assertEquals(listOf(failure), errors)
        assertFalse(store.isReady)
        assertThrows(IllegalStateException::class.java) { store.snapshot() }
    }

    /** 缺少发布标识与空的有效配置必须区分。@return Unit。Callers: JUnit。 */
    @Test fun unpublishedConfigurationIsNotAnEmptySuccess() {
        val errors = mutableListOf<Exception>()
        val store = ConfigStore({}, errors::add)
        assertFalse(store.attachHost({ preferences() }, {}))
        assertEquals(1, errors.size)
        val empty = published().apply { edit().remove("feed_remove_ads").commit() }
        val valid = ConfigStore({}, { throw AssertionError(it) })
        assertTrue(valid.attachHost({ empty }, {}))
        assertFalse(valid.snapshot().boolean("feed_remove_ads"))
    }

    /** 初次读取前注册监听，注册过程中发生的修改不会丢失。@return Unit。Callers: JUnit。 */
    @Test fun observationPrecedesInitialRead() {
        val remote = published()
        val store = ConfigStore({}, { throw AssertionError(it) })
        assertTrue(store.attachHost({ remote }) {
            it.edit().putLong(ConfigStore.REVISION, 5L).putBoolean("feed_remove_ads", false).commit()
        })
        assertEquals(5L, store.snapshot().revision)
        assertFalse(store.snapshot().boolean("feed_remove_ads"))
    }

    /** 宿主读取不要求可写权限，也不访问管理端组件。@return Unit。Callers: JUnit。 */
    @Test fun hostReadsStrictlyReadOnlyPreferences() {
        val source = published()
        val readOnly = object : SharedPreferences by source {
            /** 禁止写入。@return 永不正常返回。Callers: 测试中的写入检测。 */
            override fun edit(): SharedPreferences.Editor = throw AssertionError("宿主不得编辑框架配置")
        }
        val store = ConfigStore({}, { throw AssertionError(it) })
        assertTrue(store.attachHost({ readOnly }, {}))
        assertTrue(store.snapshot().boolean("feed_remove_ads"))
        assertThrows(IllegalStateException::class.java) { store.update(mapOf("feed_remove_ads" to false)) }
    }

    /** 保留已有私有设置及其共享版本，不重置开关。@return Unit。Callers: JUnit。 */
    @Test fun managerPreservesExistingPreferencesAndPublishesThem() {
        val local = preferences()
        local.edit().putBoolean("fixed_speed_enabled", true).putString("fixed_speed_value", "2.0")
            .putLong(ConfigStore.REVISION, 75).commit()
        val store = ConfigStore({}, { throw AssertionError(it) })
        store.attachLocal(local)
        val remote = preferences()
        store.connect { remote }
        val host = ConfigStore({}, { throw AssertionError(it) })
        assertTrue(host.attachHost({ remote }, {}))
        assertEquals(75L, host.snapshot().revision)
        assertTrue(host.snapshot().boolean("fixed_speed_enabled"))
        assertEquals("2.0", host.snapshot().string("fixed_speed_value"))
    }

    /** 空白安装显式发布默认配置，不生成任何开启的功能。@return Unit。Callers: JUnit。 */
    @Test fun freshInstallPublishesAnExplicitDefaultSnapshot() {
        val manager = ConfigStore({}, { throw AssertionError(it) })
        manager.attachLocal(preferences())
        val remote = preferences()
        manager.connect { remote }
        val host = ConfigStore({}, { throw AssertionError(it) })
        assertTrue(host.attachHost({ remote }, {}))
        ConfigSchema.featureSwitches.values.flatten().forEach { assertFalse(host.snapshot().boolean(it)) }
    }

    /** 范围两端及版本在同一事务发布，单次重读只发布一个完整快照。@return Unit。Callers: JUnit。 */
    @Test fun batchRangeHotUpdateIsAtomicAndDeduplicated() {
        val manager = ConfigStore({}, { throw AssertionError(it) })
        manager.attachLocal(preferences())
        val remote = preferences()
        manager.connect { remote }
        var notifications = 0
        val host = ConfigStore({ notifications++ }, { throw AssertionError(it) })
        assertTrue(host.attachHost({ remote }, {}))
        manager.update(mapOf("feed_filter_duration_enabled" to true,
            "feed_filter_duration_min" to "40", "feed_filter_duration_max" to "50"))
        host.reloadHost()
        host.reloadHost()
        assertEquals(2, notifications)
        assertEquals(1L, host.snapshot().revision)
        assertEquals("40", host.snapshot().string("feed_filter_duration_min"))
        assertEquals("50", host.snapshot().string("feed_filter_duration_max"))
    }

    /** 删除键会从宿主快照中消失，不能残留已移除的关键词。@return Unit。Callers: JUnit。 */
    @Test fun removingKeysReplacesEntireHostSnapshot() {
        val manager = ConfigStore({}, { throw AssertionError(it) })
        manager.attachLocal(preferences())
        manager.update(mapOf("feed_filter_keywords_json" to "{\"tag\":[\"猫\"]}"))
        val remote = preferences()
        manager.connect { remote }
        val host = ConfigStore({}, { throw AssertionError(it) })
        host.attachHost({ remote }, {})
        manager.update(mapOf("feed_filter_keywords_json" to null))
        host.reloadHost()
        assertNull(host.snapshot().string("feed_filter_keywords_json"))
    }

    /** 导入只进行一次版本提交，并清除未导入的项。@return Unit。Callers: JUnit。 */
    @Test fun importIsAValidatedSingleReplacement() {
        val manager = ConfigStore({}, { throw AssertionError(it) })
        manager.attachLocal(preferences())
        manager.update(mapOf("feed_remove_ads" to true))
        assertEquals(2L, manager.update(mapOf("fixed_speed_value" to "1.5"), replace = true))
        assertEquals(2L, manager.snapshot().importRevision)
        assertFalse(manager.snapshot().boolean("feed_remove_ads"))
        assertEquals(mapOf("fixed_speed_value" to "1.5"), manager.snapshot().configuration())
    }

    /** 无效整批修改不改变本地配置、版本或框架副本。@return Unit。Callers: JUnit。 */
    @Test fun invalidBatchCannotPartiallyCommit() {
        val manager = ConfigStore({}, { throw AssertionError(it) })
        val local = preferences()
        val remote = preferences()
        manager.attachLocal(local)
        manager.connect { remote }
        val previous = remote.all
        assertThrows(IllegalArgumentException::class.java) {
            manager.update(mapOf("feed_filter_duration_min" to "60", "feed_filter_duration_max" to "50"))
        }
        assertEquals(0L, manager.snapshot().revision)
        assertEquals(previous, remote.all)
        assertTrue(local.all.isEmpty())
    }

    /** 损坏的宿主数据暂停功能，恢复有效数据后可以重新就绪。@return Unit。Callers: JUnit。 */
    @Test fun malformedHotUpdateIsAnExplicitFailureThenRecovers() {
        val remote = published()
        val errors = mutableListOf<Exception>()
        val host = ConfigStore({}, errors::add)
        host.attachHost({ remote }, {})
        remote.edit().putString("feed_remove_ads", "true").commit()
        host.reloadHost()
        assertFalse(host.isReady)
        assertThrows(IllegalStateException::class.java) { host.snapshot() }
        assertEquals(1, errors.size)
        remote.edit().putBoolean("feed_remove_ads", false).commit()
        host.reloadHost()
        assertTrue(host.isReady)
        assertNull(host.syncError)
        assertFalse(host.snapshot().boolean("feed_remove_ads"))
    }

    /** 配置发布失败不丢失已保存设置，也不能报告同步成功。@return Unit。Callers: JUnit。 */
    @Test fun publishingFailurePreservesLocalSettingsAndIsVisible() {
        val errors = mutableListOf<Exception>()
        val manager = ConfigStore({}, errors::add)
        val local = preferences()
        manager.attachLocal(local)
        manager.connect { throw IllegalStateException("framework disconnected") }
        manager.update(mapOf("feed_remove_ads" to true))
        assertTrue(local.getBoolean("feed_remove_ads", false))
        assertTrue(manager.isReady)
        assertNotNull(manager.syncError)
        val remote = preferences()
        manager.connect { remote }
        assertNull(manager.syncError)
        assertTrue(remote.getBoolean("feed_remove_ads", false))
    }

    /** 服务重连只发布当前完整版本，不重放中间修改。@return Unit。Callers: JUnit。 */
    @Test fun reconnectPublishesLatestLocalRevision() {
        val manager = ConfigStore({}, {})
        manager.attachLocal(preferences())
        manager.connect { preferences() }
        manager.disconnect()
        manager.update(mapOf("feed_remove_ads" to true))
        manager.update(mapOf("feed_remove_ads" to false, "feed_remove_live" to true))
        val remote = preferences()
        manager.connect { remote }
        assertEquals(2L, remote.getLong(ConfigStore.REVISION, -1))
        assertFalse(remote.getBoolean("feed_remove_ads", true))
        assertTrue(remote.getBoolean("feed_remove_live", false))
    }

    /** commit 返回失败时不可宣布框架已确认。@return Unit。Callers: JUnit。 */
    @Test fun falseRemoteCommitIsNotReportedAsSuccess() {
        val remote = preferences()
        val failing = object : SharedPreferences by remote {
            /** 包装提交失败。@return 提交返回 false 的事务。Callers: ConfigStore.publish。 */
            override fun edit(): SharedPreferences.Editor {
                val delegate = remote.edit()
                return object : SharedPreferences.Editor by delegate {
                    /** 保留包装对象。@return 当前事务。Callers: ConfigStore.publish。 */
                    override fun clear(): SharedPreferences.Editor { delegate.clear(); return this }
                    /** 写协议并保留包装。@param key 键。@param value 值。@return 当前事务。Callers: ConfigStore.publish。 */
                    override fun putInt(key: String, value: Int): SharedPreferences.Editor { delegate.putInt(key, value); return this }
                    /** 模拟未确认提交。@return false。Callers: ConfigStore.publish。 */
                    override fun commit(): Boolean = false
                }
            }
        }
        val manager = ConfigStore({}, {})
        manager.attachLocal(preferences())
        manager.connect { failing }
        assertNotNull(manager.syncError)
        assertTrue(remote.all.isEmpty())
    }

    /** 发布版本和导入版本类型严格校验。@return Unit。Callers: JUnit。 */
    @Test fun invalidVersionMetadataCannotEnableHooks() {
        for (change in listOf<(SharedPreferences.Editor) -> Unit>(
            { it.remove(ConfigStore.REVISION) }, { it.putInt(ConfigStore.REVISION, 4) },
            { it.putLong(ConfigStore.REVISION, -1) }, { it.putLong(ConfigStore.IMPORT_REVISION, 5) },
            { it.putInt(ConfigStore.PROTOCOL, 2) })) {
            val remote = published()
            remote.edit().also(change).commit()
            val host = ConfigStore({}, {})
            assertFalse(host.attachHost({ remote }, {}))
        }
    }

    @Test fun subscriberFailureDoesNotBecomeAConfigurationConnectionFailure() {
        val original = IllegalStateException("business subscriber")
        val errors = mutableListOf<Exception>()
        val initial = ConfigStore({ throw original }, errors::add)
        assertSame(original, assertThrows(IllegalStateException::class.java) {
            initial.attachHost({ published() }, {})
        })
        assertTrue(initial.isReady)
        assertNull(initial.syncError)
        assertTrue(errors.isEmpty())

        var failSubscriber = false
        val preferences = published()
        val current = ConfigStore({ if (failSubscriber) throw original }, errors::add)
        assertTrue(current.attachHost({ preferences }, {}))
        failSubscriber = true
        preferences.edit().putLong(ConfigStore.REVISION, 5L).commit()
        assertSame(original, assertThrows(IllegalStateException::class.java) { current.reloadHost() })
        assertTrue(current.isReady)
        assertNull(current.syncError)
        assertEquals(5L, current.snapshot().revision)
        assertTrue(errors.isEmpty())
    }
}
