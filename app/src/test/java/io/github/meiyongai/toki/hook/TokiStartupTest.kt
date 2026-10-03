package io.github.meiyongai.toki.hook

import android.app.AppComponentFactory
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.provider.ConfigStore
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Executable
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import android.os.Looper

/** 驱动真实入口，验证按需安装边界；全关仅有明确的诊断入口。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class TokiStartupTest {
    private val hooks = mutableListOf<Pair<Executable, XposedInterface.Hooker>>()

    @Before @After fun resetHostProcess() {
        shadowOf(Looper.getMainLooper()).idle()
        fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val resources = field(HookRuntime::class.java, "resources").get(null) as MutableMap<String, MutableList<AutoCloseable>>
        resources.values.flatten().asReversed().forEach { it.close() }
        resources.clear()
        (field(HookRuntime::class.java, "handles").get(null) as MutableMap<*, *>).clear()
        (field(HookRuntime::class.java, "scopes").get(null) as MutableMap<*, *>).clear()
        (field(HookRuntime::class.java, "features").get(null) as android.os.Bundle).clear()
        for (name in listOf("listeners", "stateListeners"))
            (field(ConfigClient::class.java, name).get(null) as MutableCollection<*>).clear()
        for (name in listOf("initialized", "host")) field(ConfigClient::class.java, name).set(null, false)
        for (name in listOf("connectionChanged", "hostSessionTransform", "effectiveCache"))
            field(ConfigClient::class.java, name).set(null, null)
        val store = field(ConfigClient::class.java, "store").get(null)
        for (name in listOf("current", "syncError", "local", "remote")) field(ConfigStore::class.java, name).set(store, null)
        for (name in listOf("isReady", "host")) field(ConfigStore::class.java, name).set(store, false)
        hooks.clear()
        HostScanController.session.fail()
        HostScanController.session.dismissResult()
    }

    private fun preferences(): SharedPreferences = RuntimeEnvironment.getApplication()
        .getSharedPreferences("startup", Context.MODE_PRIVATE).also {
            it.edit().clear().putInt(ConfigStore.PROTOCOL, 1).putLong(ConfigStore.REVISION, 1L).commit()
        }

    private fun module(preferences: SharedPreferences): TokiModule {
        val framework = Proxy.newProxyInstance(XposedInterface::class.java.classLoader,
            arrayOf(XposedInterface::class.java)) { _, method, args ->
            when (method.name) {
                "getRemotePreferences" -> preferences
                "log" -> null
                "hook" -> {
                    val executable = args!![0] as Executable
                    Proxy.newProxyInstance(method.returnType.classLoader, arrayOf(method.returnType)) { builder, option, options ->
                        when {
                            option.name == "intercept" -> {
                                hooks.add(executable to (options!![0] as XposedInterface.Hooker))
                                Proxy.newProxyInstance(option.returnType.classLoader, arrayOf(option.returnType)) { _, handle, _ ->
                                    when (handle.name) {
                                        "unhook" -> null
                                        "getExecutable" -> executable
                                        else -> throw AssertionError("unexpected handle " + handle.name)
                                    }
                                }
                            }
                            option.returnType.isInstance(builder) -> builder
                            else -> throw AssertionError("unexpected builder " + option.name)
                        }
                    }
                }
                else -> throw AssertionError("会话不应调用框架 " + method.name)
            }
        } as XposedInterface
        return TokiModule().apply {
            attachFramework(framework) {}
            onModuleLoaded(object : XposedModuleInterface.ModuleLoadedParam {
                override fun isSystemServer() = false
                override fun getProcessName() = TokiModule.PKG_TIKTOK_GLOBAL
            })
        }
    }

    private fun host() = object : XposedModuleInterface.PackageReadyParam {
        override fun getPackageName() = TokiModule.PKG_TIKTOK_GLOBAL
        override fun isFirstPackage() = true
        override fun getApplicationInfo(): ApplicationInfo = throw AssertionError("unexpected APK/cache read")
        override fun getDefaultClassLoader(): ClassLoader = throw AssertionError("unexpected host type resolution")
        override fun getClassLoader(): ClassLoader = getDefaultClassLoader()
        override fun getAppComponentFactory(): AppComponentFactory = throw AssertionError("unexpected factory")
    }

    @Test fun allOffInstallsOnlySessionDiagnosticsWithoutDexOrBusinessHooks() {
        val module = module(preferences())
        module.onPackageLoaded(host())
        module.onPackageReady(host())
        assertEquals(listOf("callApplicationOnCreate"), hooks.map { it.first.name })
        assertEquals(HostScanPhase.IDLE, HostScanController.session.status.phase)
    }

    @Test fun diagnosticsDelegatesOriginalApplicationExactlyOnce() {
        val module = module(preferences())
        module.onPackageLoaded(host())
        var proceeded = 0
        val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "getArgs" -> listOf(RuntimeEnvironment.getApplication())
                "proceed" -> { proceeded++; "original" }
                else -> throw AssertionError(method.name)
            }
        } as XposedInterface.Chain
        assertEquals("original", hooks.single().second.intercept(chain))
        assertEquals(1, proceeded)
    }

    @Test fun storedOptionsDoNotEnableTheirParentFeatures() {
        val module = module(preferences().apply {
            edit().putBoolean("clean_mode_show_progress_bar", true).putBoolean("language_follow_region", true)
                .putString("custom_language", "en").putString("target_region", "US").commit()
        })
        module.onPackageLoaded(host())
        module.onPackageReady(host())
        assertEquals(1, hooks.size)
    }

    @Test fun unavailableConfigurationHasDiagnosticsAndNoScanning() {
        val module = module(preferences().apply { edit().remove(ConfigStore.PROTOCOL).commit() })
        assertFalse(ConfigClient.isReady)
        module.onPackageLoaded(host())
        module.onPackageReady(host())
        assertEquals(1, hooks.size)
    }

    @Test fun simOnlyInstallsTelephonyWithoutHostPreparation() {
        val module = module(preferences().apply { edit().putBoolean("sim_spoof_enabled", true).commit() })
        module.onPackageLoaded(host())
        module.onPackageReady(host())
        assertTrue(hooks.size > 1)
        assertTrue(hooks.filter { it.first.name != "callApplicationOnCreate" }
            .all { it.first.declaringClass.name == "android.telephony.TelephonyManager" })
        assertEquals(HostScanPhase.IDLE, HostScanController.session.status.phase)
    }

    @Test fun adaptedFeatureReachesOnlyTheRequiredPreparationBoundary() {
        val module = module(preferences().apply { edit().putBoolean("always_show_progress_bar", true).commit() })
        module.onPackageLoaded(host())
        assertEquals(1, hooks.size)
        val failure = assertThrows(AssertionError::class.java) { module.onPackageReady(host()) }
        assertTrue(failure.message.orEmpty().contains("APK/cache"))
    }

    @Test fun readyCanInitializeDiagnosticsOnPlatformsWithoutLoadedCallback() {
        val module = module(preferences())
        module.onPackageReady(host())
        assertEquals(listOf("callApplicationOnCreate"), hooks.map { it.first.name })
    }
}
