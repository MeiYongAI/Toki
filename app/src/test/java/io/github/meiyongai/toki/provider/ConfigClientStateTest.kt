package io.github.meiyongai.toki.provider

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** 验证配置失效事件、取消订阅及当前会话配置与用户请求的分离。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class ConfigClientStateTest {
    private fun field(target: Class<*>, name: String) = target.getDeclaredField(name).apply { isAccessible = true }
    private fun store() = field(ConfigClient::class.java, "store").get(null) as ConfigStore

    @Before @After fun reset() {
        (field(ConfigClient::class.java, "main").get(null) as Handler).removeCallbacksAndMessages(null)
        for (name in listOf("listeners", "stateListeners")) {
            (field(ConfigClient::class.java, name).get(null) as MutableCollection<*>).clear()
        }
        for (name in listOf("initialized", "host")) field(ConfigClient::class.java, name).set(null, false)
        for (name in listOf("connectionChanged", "hostSessionTransform", "effectiveCache")) {
            field(ConfigClient::class.java, name).set(null, null)
        }
        for (name in listOf("current", "syncError", "local", "remote")) field(ConfigStore::class.java, name).set(store(), null)
        for (name in listOf("isReady", "host")) field(ConfigStore::class.java, name).set(store(), false)
    }

    private fun preferences() = RuntimeEnvironment.getApplication().getSharedPreferences("state", Context.MODE_PRIVATE).also {
        it.edit().clear().putInt(ConfigStore.PROTOCOL, 1).putLong(ConfigStore.REVISION, 1L)
            .putBoolean("clean_mode_on_play", true).commit()
    }

    @Test fun rejectedConfigurationEmitsNullImmediatelyAndCancellationStopsRecoveryDelivery() {
        val preferences = preferences()
        assertTrue(ConfigClient.initHost({ preferences }) {})
        val states = mutableListOf<ConfigSnapshot?>()
        val subscription = ConfigClient.addStateListener { states.add(it) }
        assertEquals(1, states.size)
        assertNotNull(states.single())
        preferences.edit().putInt(ConfigStore.PROTOCOL, 0).commit()
        store().reloadHost()
        assertFalse(ConfigClient.isReady)
        assertEquals(2, states.size)
        assertNull(states.last())
        subscription.close()
        preferences.edit().putInt(ConfigStore.PROTOCOL, 1).putLong(ConfigStore.REVISION, 2L).commit()
        store().reloadHost()
        assertTrue(ConfigClient.isReady)
        assertEquals(2, states.size)
    }

    @Test fun hostSessionProjectsEffectiveConfigurationWithoutChangingUserRequest() {
        val preferences = preferences()
        assertTrue(ConfigClient.initHost({ preferences }) {})
        ConfigClient.configureHostSession { requested ->
            ConfigSnapshot(requested.configuration() + ("clean_mode_on_play" to false), requested.revision, requested.importRevision)
        }
        val effectiveStates = mutableListOf<ConfigSnapshot?>()
        val subscription = ConfigClient.addStateListener { effectiveStates.add(it) }
        assertTrue(ConfigClient.requestedSnapshot().boolean("clean_mode_on_play"))
        assertFalse(ConfigClient.snapshot().boolean("clean_mode_on_play"))
        assertSame(ConfigClient.snapshot(), ConfigClient.snapshot())
        assertSame(ConfigClient.snapshot(), effectiveStates.single())
        preferences.edit().putLong(ConfigStore.REVISION, 2L).commit()
        store().reloadHost()
        assertEquals(2L, ConfigClient.requestedSnapshot().revision)
        assertFalse(effectiveStates.last()!!.boolean("clean_mode_on_play"))
        preferences.edit().putInt(ConfigStore.PROTOCOL, 0).commit()
        store().reloadHost()
        assertNull(ConfigClient.requestedSnapshotOrNull())
        assertNull(effectiveStates.last())
        assertThrows(IllegalStateException::class.java) { ConfigClient.snapshot() }
        subscription.close()
    }

    @Test fun lateQueuedInitialDeliveryCannotReplaceNewerConfiguration() {
        val preferences = preferences()
        assertTrue(ConfigClient.initHost({ preferences }) {})
        val states = mutableListOf<ConfigSnapshot?>()
        val subscription = java.util.concurrent.atomic.AtomicReference<AutoCloseable>()
        Thread { subscription.set(ConfigClient.addStateListener { states.add(it) }) }.apply { start(); join() }
        assertTrue(states.isEmpty())
        preferences.edit().putLong(ConfigStore.REVISION, 2L).putBoolean("clean_mode_on_play", false).commit()
        store().reloadHost()
        assertEquals(2L, states.single()!!.revision)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, states.size)
        assertFalse(states.single()!!.boolean("clean_mode_on_play"))
        subscription.get().close()
    }

    @Test fun queuedEventsPublishedBeforeSubscriptionCannotLeakUnprojectedStartupConfiguration() {
        val preferences = preferences()
        Thread { assertTrue(ConfigClient.initHost({ preferences }) {}) }.apply { start(); join() }
        ConfigClient.configureHostSession { requested ->
            ConfigSnapshot(requested.configuration() + ("clean_mode_on_play" to false), requested.revision)
        }
        val states = mutableListOf<ConfigSnapshot?>()
        val subscription = ConfigClient.addStateListener { states.add(it) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, states.size)
        assertFalse(states.single()!!.boolean("clean_mode_on_play"))
        assertTrue(ConfigClient.requestedSnapshot().boolean("clean_mode_on_play"))
        subscription.close()
    }

    @Test fun explicitSessionPolicyChangePublishesNewEffectiveStateForTheSameRequest() {
        val preferences = preferences()
        assertTrue(ConfigClient.initHost({ preferences }) {})
        var allowClean = true
        ConfigClient.configureHostSession { requested ->
            if (allowClean) requested else ConfigSnapshot(requested.configuration() + ("clean_mode_on_play" to false),
                requested.revision, requested.importRevision)
        }
        val states = mutableListOf<ConfigSnapshot?>()
        val subscription = ConfigClient.addStateListener { states.add(it) }
        val originalRequested = ConfigClient.requestedSnapshot()
        assertTrue(states.single()!!.boolean("clean_mode_on_play"))
        allowClean = false
        ConfigClient.refreshHostSession()
        assertSame(originalRequested, ConfigClient.requestedSnapshot())
        assertEquals(2, states.size)
        assertFalse(states.last()!!.boolean("clean_mode_on_play"))
        assertSame(states.last(), ConfigClient.snapshot())
        assertEquals(originalRequested.revision, states.last()!!.revision)
        assertTrue(originalRequested.boolean("clean_mode_on_play"))
        subscription.close()
    }

    @Test fun failedInitialSubscriberApplicationLeavesNoSubscriptionBehind() {
        val preferences = preferences()
        assertTrue(ConfigClient.initHost({ preferences }) {})
        val original = IllegalStateException("initial application")
        assertSame(original, assertThrows(IllegalStateException::class.java) {
            ConfigClient.addStateListener { throw original }
        })
        assertTrue((field(ConfigClient::class.java, "stateListeners").get(null) as Collection<*>).isEmpty())
        assertTrue(ConfigClient.isReady)
        assertNull(ConfigClient.syncError)
    }
}
