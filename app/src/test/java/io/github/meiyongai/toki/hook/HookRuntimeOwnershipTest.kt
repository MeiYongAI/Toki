package io.github.meiyongai.toki.hook

import io.github.libxposed.api.XposedInterface
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.provider.ConfigStore
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 安装回滚拥有 Hook 句柄及订阅，按逆序全部释放，错误保留原始来源。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class HookRuntimeOwnershipTest {
    private fun handle(unhook: () -> Unit) = Proxy.newProxyInstance(XposedInterface.HookHandle::class.java.classLoader,
        arrayOf(XposedInterface.HookHandle::class.java)) { _, method, _ ->
        if (method.name == "unhook") { unhook(); null } else throw AssertionError(method.name)
    } as XposedInterface.HookHandle

    @Test fun failedInstallReleasesHooksSubscriptionsAndStateInReverseOrder() {
        val released = mutableListOf<String>()
        HookRuntime.install("ownership-test") {
            HookRuntime.onDispose("ownership-test") { released.add("state") }
            HookRuntime.registered("ownership-test", handle { released.add("hook") })
            HookRuntime.own("ownership-test", AutoCloseable { released.add("subscription") })
            throw IllegalStateException("registration failed after subscription")
        }
        assertEquals(listOf("subscription", "hook", "state"), released)
    }

    @Test fun cleanupFailureIsAttachedAndDoesNotPreventOtherResourcesFromClosing() {
        val original = IllegalStateException("registration")
        val cleanup = IllegalStateException("cleanup")
        var released = false
        HookRuntime.install("ownership-cleanup-test") {
            HookRuntime.onDispose("ownership-cleanup-test") { released = true }
            HookRuntime.onDispose("ownership-cleanup-test") { throw cleanup }
            throw original
        }
        assertTrue(released)
        assertArrayEquals(arrayOf<Throwable>(cleanup), original.suppressed)
    }

    @Test fun fatalInstallErrorStillClosesOwnedResourcesAndPropagates() {
        val original = AssertionError("fatal installation")
        var released = false
        assertSame(original, assertThrows(AssertionError::class.java) {
            HookRuntime.install("ownership-fatal-test") {
                HookRuntime.onDispose("ownership-fatal-test") { released = true }
                throw original
            }
        })
        assertTrue(released)
    }

    @Test fun lifecycleTrackingRunsDuringConfigurationLossWithoutRunningConfigurationDependentHooks() {
        val store = ConfigClient::class.java.getDeclaredField("store").apply { isAccessible = true }.get(null)
        ConfigStore::class.java.getDeclaredField("isReady").apply { isAccessible = true }.set(store, false)
        val executable = String::class.java.getMethod("length")
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            if (method.name == "proceed") "host" else throw AssertionError(method.name)
        } as XposedInterface.Chain
        var calls = 0
        for (requiresConfiguration in listOf(true, false)) {
            var interceptor: XposedInterface.Hooker? = null
            val feature = "lifecycle-gating-$requiresConfiguration"
            val builder = Proxy.newProxyInstance(XposedInterface.HookBuilder::class.java.classLoader,
                arrayOf(XposedInterface.HookBuilder::class.java)) { _, method, args ->
                if (method.name == "intercept") {
                    interceptor = args!![0] as XposedInterface.Hooker
                    handle { }
                } else throw AssertionError(method.name)
            } as XposedInterface.HookBuilder
            TrackedHook(feature, executable, builder, requiresConfiguration).intercept {
                calls++
                it.proceed()
            }
            assertEquals("host", interceptor!!.intercept(chain))
            HookRuntime.install(feature) { throw IllegalStateException("end test session") }
        }
        assertEquals(1, calls)
    }

    @Test fun revokedScopeClosesLateResourcesAndHandlesInsteadOfReopeningOwnership() {
        val feature = "ownership-late-test"
        HookRuntime.install(feature) { throw IllegalStateException("reject installation") }
        var resourceClosed = 0
        var hookClosed = 0
        HookRuntime.own(feature, AutoCloseable { resourceClosed++ })
        HookRuntime.registered(feature, handle { hookClosed++ })
        HookRuntime.onDispose(feature) { resourceClosed++ }
        val listeners = ConfigClient::class.java.getDeclaredField("stateListeners").apply { isAccessible = true }
            .get(null) as Collection<*>
        val subscriptionsBefore = listeners.size
        HookRuntime.subscribe(feature) { fail("revoked scope cannot apply initial subscriber state") }
        assertEquals(subscriptionsBefore, listeners.size)
        assertEquals(2, resourceClosed)
        assertEquals(1, hookClosed)
        assertFalse(HookRuntime.scope(feature).active)
        assertThrows(IllegalStateException::class.java) { HookRuntime.install(feature) { fail("cannot reinstall") } }
        @Suppress("UNCHECKED_CAST")
        val resources = HookRuntime::class.java.getDeclaredField("resources").apply { isAccessible = true }
            .get(null) as Map<String, *>
        assertFalse(resources.containsKey(feature))
    }

    @Test fun registrationCompletingAfterRevocationUnhooksItsHandleAndCannotRunBusinessCallbacks() {
        val feature = "ownership-register-race-test"
        val registrationStarted = CountDownLatch(1)
        val releaseRegistration = CountDownLatch(1)
        val callback = AtomicReference<XposedInterface.Hooker>()
        val registrationError = AtomicReference<Throwable>()
        val lateHandleClosed = AtomicInteger()
        val initialHandleClosed = AtomicInteger()
        val businessCalls = AtomicInteger()
        HookRuntime.install(feature) {
            HookRuntime.registered(feature, handle { initialHandleClosed.incrementAndGet() })
        }
        val builder = Proxy.newProxyInstance(XposedInterface.HookBuilder::class.java.classLoader,
            arrayOf(XposedInterface.HookBuilder::class.java)) { _, method, args ->
            if (method.name != "intercept") throw AssertionError(method.name)
            callback.set(args!![0] as XposedInterface.Hooker)
            registrationStarted.countDown()
            check(releaseRegistration.await(5, TimeUnit.SECONDS)) { "test registration release timed out" }
            handle { lateHandleClosed.incrementAndGet() }
        } as XposedInterface.HookBuilder
        val registration = Thread {
            try {
                TrackedHook(feature, String::class.java.getMethod("length"), builder, false).intercept {
                    businessCalls.incrementAndGet()
                    it.proceed()
                }
            } catch (error: Throwable) { registrationError.set(error) }
        }.apply { start() }
        try {
            assertTrue(registrationStarted.await(5, TimeUnit.SECONDS))
            HookRuntime.configure(feature) { throw IllegalStateException("configuration rejects active scope") }
        } finally {
            releaseRegistration.countDown()
            registration.join(5000)
        }
        assertFalse(registration.isAlive)
        assertNull(registrationError.get())
        assertEquals(1, initialHandleClosed.get())
        assertEquals(1, lateHandleClosed.get())
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            if (method.name == "proceed") "host" else throw AssertionError(method.name)
        } as XposedInterface.Chain
        assertEquals("host", callback.get().intercept(chain))
        assertEquals(0, businessCalls.get())
        assertFalse(HookRuntime.scope(feature).active)
    }
}
