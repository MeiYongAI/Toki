package io.github.meiyongai.toki.hook

import android.app.Application
import android.content.Context
import android.os.Handler
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.meiyongai.toki.provider.ConfigSnapshot
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.provider.ConfigStore
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Properties
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** 驱动真实注册路径，验证两个倍速请求互不引入未开启的契约，并验证完整撤销。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class PlaybackSpeedRegistrationTest {
    class Aweme

    interface PlayerManager { fun changeSpeed(speed: Float) }
    class ConcreteManager : PlayerManager { override fun changeSpeed(speed: Float) {} }

    class Controller {
        private var current: PlayerManager? = null
        fun getManager(): PlayerManager? = current
        fun setManager(manager: PlayerManager?) { current = manager }
        fun changeSpeed(speed: Float) {}
        fun renderReady(value: Any?) {}
    }

    class SpeedManager {
        companion object {
            @JvmField var LJI = false
            @JvmField var LJII = 1f
            @JvmField var LIZLLL = 1f
            @JvmField var LIZJ = 1f
            @JvmField var originalSelections = 0
            @JvmField var nativePersists = false
            @JvmStatic fun LIZ(value: Aweme): Boolean = false
            @JvmStatic fun LIZIZ(value: Aweme): Float = 1f
            @JvmStatic fun LIZJ(value: Aweme): Float = 1f
            @JvmStatic fun LJI(value: Aweme, source: String) {}
            @JvmStatic fun LJ(speed: Float, value: Aweme, source: String, scene: String) {
                originalSelections++
                if (nativePersists) LJII = speed
            }
            @JvmStatic fun LJFF(value: Aweme, source: String, first: Boolean, second: Boolean): Boolean = false
        }
    }

    class Gate { companion object { @JvmStatic fun LIZ(): Boolean = false } }
    class Menus {
        companion object {
            @JvmStatic fun options(): List<Float> = listOf(1f)
            @JvmStatic fun primary(value: Menus): List<Float> = listOf(1f)
            @JvmStatic fun secondary(value: Menus): List<Float> = listOf(1f)
            @JvmStatic fun middle(value: Menus): List<Float> = listOf(1f)
            @JvmStatic fun last(value: Menus): List<Float> = listOf(1f)
        }
    }

    private val resolved = HostSymbols::class.java.getDeclaredField("resolved").apply { isAccessible = true }
    private var previous: Any? = null

    @Before fun reset() {
        HookRuntime.start("test") { _, _ -> }
        rollback()
        resetScope()
        resetConfiguration()
        previous = resolved.get(null)
        SpeedManager.LJI = false
        SpeedManager.LJII = 1f
        SpeedManager.LIZLLL = 1f
        SpeedManager.LIZJ = 1f
        SpeedManager.originalSelections = 0
        SpeedManager.nativePersists = false
        RuntimeEnvironment.getApplication().getSharedPreferences("toki_playback", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After fun restore() { rollback(); resetScope(); resetConfiguration(); resolved.set(null, previous) }

    private fun resetConfiguration() {
        fun set(type: Class<*>, target: Any?, field: String, value: Any?) =
            type.getDeclaredField(field).apply { isAccessible = true }.set(target, value)
        (ConfigClient::class.java.getDeclaredField("main").apply { isAccessible = true }.get(null) as Handler)
            .removeCallbacksAndMessages(null)
        for (field in listOf("listeners", "stateListeners")) {
            (ConfigClient::class.java.getDeclaredField(field).apply { isAccessible = true }
                .get(null) as MutableCollection<*>).clear()
        }
        for (field in listOf("initialized", "host")) set(ConfigClient::class.java, null, field, false)
        for (field in listOf("connectionChanged", "hostSessionTransform", "effectiveCache")) {
            set(ConfigClient::class.java, null, field, null)
        }
        val store = ConfigClient::class.java.getDeclaredField("store").apply { isAccessible = true }.get(null)
        for (field in listOf("current", "syncError", "local", "remote")) set(ConfigStore::class.java, store, field, null)
        for (field in listOf("isReady", "host")) set(ConfigStore::class.java, store, field, false)
    }

    private fun bindFixedConfiguration(): ConfigSnapshot {
        val preferences = RuntimeEnvironment.getApplication().getSharedPreferences("speed_configuration", Context.MODE_PRIVATE)
        preferences.edit().clear().putInt(ConfigStore.PROTOCOL, 1).putLong(ConfigStore.REVISION, 1L)
            .putBoolean("fixed_speed_enabled", true).putString("fixed_speed_value", "2.0").commit()
        assertTrue(ConfigClient.initHost({ preferences }) {})
        return ConfigClient.snapshot()
    }

    private fun rollback() {
        if (HookRuntime.scope("PlaybackSpeedHook").active) {
            HookRuntime.install("PlaybackSpeedHook") { throw IllegalStateException("test process reset") }
        }
    }

    /** 每个测试代表新的宿主进程；真实生产会话不能重新激活已撤销的范围。 */
    private fun resetScope() {
        (HookRuntime::class.java.getDeclaredField("scopes").apply { isAccessible = true }
            .get(null) as MutableMap<*, *>).remove("PlaybackSpeedHook")
    }

    private fun fixedSymbols(): Properties = Properties().apply {
        setProperty("PLAYER_CONTROLLER", Controller::class.java.name)
        setProperty("PLAYER_MANAGER", PlayerManager::class.java.name)
        setProperty("SPEED_MANAGER", SpeedManager::class.java.name)
        setProperty("member.PLAYER_MANAGER.setSpeed", "changeSpeed")
        setProperty("member.PLAYER_CONTROLLER.setSpeed", "changeSpeed")
        setProperty("member.PLAYER_CONTROLLER.getManager", "getManager")
        setProperty("member.PLAYER_CONTROLLER.setManager", "setManager")
        setProperty("member.PLAYER_CONTROLLER.renderReady", "renderReady")
    }

    private fun menuSymbols(): Properties = Properties().apply {
        setProperty("THREE_TIMES_SPEED", Gate::class.java.name)
        for (symbol in listOf("SPEED_OPTIONS", "SPEED_LAMBDA11", "SPEED_LAMBDA21", "SPEED_LAMBDA31")) {
            setProperty(symbol, Menus::class.java.name)
        }
        setProperty("member.SPEED_OPTIONS.options", "options")
        setProperty("member.SPEED_LAMBDA11.optionsPrimary", "primary")
        setProperty("member.SPEED_LAMBDA11.optionsSecondary", "secondary")
        setProperty("member.SPEED_LAMBDA21.options", "middle")
        setProperty("member.SPEED_LAMBDA31.options", "last")
    }

    private class Recorder {
        val points = mutableListOf<Executable>()
        val callbacks = mutableMapOf<Executable, XposedInterface.Hooker>()
        var removed = 0
        val module = object : XposedModule() {}

        init {
            val framework = Proxy.newProxyInstance(XposedInterface::class.java.classLoader,
                arrayOf(XposedInterface::class.java)) { _, method, args ->
                when (method.name) {
                    "hook" -> {
                        val point = args!![0] as Executable
                        Proxy.newProxyInstance(method.returnType.classLoader, arrayOf(method.returnType)) { builder, step, stepArgs ->
                            when (step.name) {
                                "intercept" -> {
                                    points.add(point)
                                    callbacks[point] = stepArgs!![0] as XposedInterface.Hooker
                                    Proxy.newProxyInstance(step.returnType.classLoader, arrayOf(step.returnType)) { _, handle, _ ->
                                        when (handle.name) {
                                            "unhook" -> { removed++; callbacks.remove(point); null }
                                            else -> throw AssertionError("Unexpected handle method: ${handle.name}")
                                        }
                                    }
                                }
                                "setExceptionMode", "setPriority" -> builder
                                else -> throw AssertionError("Unexpected builder method: ${step.name}")
                            }
                        }
                    }
                    "log" -> null
                    else -> throw AssertionError("Unexpected framework method: ${method.name}")
                }
            } as XposedInterface
            module.attachFramework(framework) {}
        }

        fun install(snapshot: ConfigSnapshot) {
            var failure: Throwable? = null
            HookRuntime.install("PlaybackSpeedHook") {
                try {
                    PlaybackSpeedHook.init(module, javaClass.classLoader!!, snapshot)
                } catch (error: Throwable) {
                    failure = error
                    throw error
                }
            }
            failure?.let { throw it }
        }

        /** 调用实际注册的中枢拦截器，原调用链由真实静态方法提供。 */
        fun select(speed: Float) {
            val point = callbacks.keys.single { it.declaringClass == SpeedManager::class.java && it.name == "LJ" } as Method
            val values = arrayOf<Any?>(speed, Aweme(), "test", "menu")
            val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
                arrayOf(XposedInterface.Chain::class.java)) { _, method, args ->
                when (method.name) {
                    "getExecutable" -> point
                    "getThisObject" -> null
                    "getArgs" -> values.toList()
                    "getArg" -> values[args!![0] as Int]
                    "proceed" -> {
                        val actual = if (args.isNullOrEmpty()) values else args[0] as Array<*>
                        point.invoke(null, *actual)
                    }
                    else -> throw AssertionError("Unexpected chain method: ${method.name}")
                }
            } as XposedInterface.Chain
            callbacks.getValue(point).intercept(chain)
        }
    }

    @Test fun fixedSpeedDoesNotRequireAnyMenuSymbols() {
        resolved.set(null, fixedSymbols())
        val recorder = Recorder()
        recorder.install(ConfigSnapshot(mapOf("fixed_speed_enabled" to true)))
        assertEquals(9, recorder.points.size)
        assertTrue(recorder.points.all { it.declaringClass in setOf(Controller::class.java, SpeedManager::class.java) })
        assertFalse(PlaybackSpeedHook.isFeatureEnabled()) // 注册不等于存储和中枢配置已经就绪。
        assertFalse(PlaybackSpeedHook.isExpansionEnabled())
    }

    @Test fun menuExpansionDoesNotRequirePlayerOrFixedSpeedSymbols() {
        resolved.set(null, menuSymbols())
        val recorder = Recorder()
        recorder.install(ConfigSnapshot(mapOf("speed_expand_enabled" to true)))
        assertEquals(6, recorder.points.size)
        assertTrue(recorder.points.all { it.declaringClass in setOf(Gate::class.java, Menus::class.java) })
        assertFalse(PlaybackSpeedHook.isFeatureEnabled())
        assertTrue(PlaybackSpeedHook.isExpansionEnabled())
    }

    @Test fun disabledRequestsResolveNoHostSymbolsAndRegisterNothing() {
        resolved.set(null, Properties())
        val recorder = Recorder()
        recorder.install(ConfigSnapshot(emptyMap()))
        assertTrue(recorder.points.isEmpty())
    }

    @Test fun incompleteCombinedRequestFailsBeforeAnyRegistration() {
        resolved.set(null, fixedSymbols().apply {
            putAll(menuSymbols())
            remove("SPEED_LAMBDA31")
        })
        val recorder = Recorder()
        val failure = assertThrows(IllegalStateException::class.java) {
            recorder.install(ConfigSnapshot(mapOf("fixed_speed_enabled" to true, "speed_expand_enabled" to true)))
        }
        assertTrue(failure.message.orEmpty().contains("SPEED_LAMBDA31"))
        assertTrue(recorder.points.isEmpty())
        assertFalse(SpeedManager.LJI)
    }

    @Test fun missingControllerMemberFailsBeforeFixedSpeedRegistration() {
        resolved.set(null, fixedSymbols().apply { setProperty("member.PLAYER_CONTROLLER.renderReady", "absent") })
        val recorder = Recorder()
        assertThrows(NoSuchElementException::class.java) {
            recorder.install(ConfigSnapshot(mapOf("fixed_speed_enabled" to true)))
        }
        assertTrue(recorder.points.isEmpty())
    }

    @Test fun rollbackRemovesDynamicHooksClearsMethodSetAndRestoresManagerFields() {
        resolved.set(null, fixedSymbols())
        val config = bindFixedConfiguration()
        val recorder = Recorder()
        recorder.install(config)
        PlaybackSpeedHook.refreshConfig(RuntimeEnvironment.getApplication())
        val installDynamic = PlaybackSpeedHook::class.java.getDeclaredMethod("hookPlayerManagerSetSpeed",
            XposedModule::class.java, Class::class.java).apply { isAccessible = true }
        installDynamic.invoke(PlaybackSpeedHook, recorder.module, ConcreteManager::class.java)
        assertEquals(10, recorder.points.size)
        SpeedManager.LJI = true
        SpeedManager.LJII = 2f
        rollback()
        assertEquals(recorder.points.size, recorder.removed)
        val methods = PlaybackSpeedHook::class.java.getDeclaredField("hookedPlayerManagerMethods")
            .apply { isAccessible = true }.get(null) as Set<Method>
        assertTrue(methods.isEmpty())
        assertFalse(PlaybackSpeedHook.isFeatureEnabled())
        assertFalse(SpeedManager.LJI)
        assertEquals(1f, SpeedManager.LJII, 0f)
        assertNull(PlaybackSpeedHook::class.java.getDeclaredField("playerManagerType")
            .apply { isAccessible = true }.get(null))
    }

    @Test fun earlyManagerSelectionUsesOriginalChainAndApplicationInitializationEnablesMemory() {
        resolved.set(null, fixedSymbols())
        val config = bindFixedConfiguration()
        val recorder = Recorder()
        recorder.install(config)
        val application = RuntimeEnvironment.getApplication()
        val memory = application.getSharedPreferences("toki_playback", Context.MODE_PRIVATE)
        recorder.select(1.5f)
        assertEquals(1, SpeedManager.originalSelections)
        assertEquals(1f, SpeedManager.LJII, 0f)
        assertFalse(memory.contains("selected"))

        PlaybackSpeedHook.refreshConfig(application)
        assertTrue(PlaybackSpeedHook.isFeatureEnabled())
        assertEquals(2f, SpeedManager.LJII, 0f)
        recorder.select(1.5f)
        assertEquals(2, SpeedManager.originalSelections)
        assertEquals(1.5f, PlaybackSpeedMemory(memory).read(config), 0f)
        assertEquals(1.5f, SpeedManager.LJII, 0f)
    }

    @Test fun invalidMemoryCannotActivateFixedChainOrInterceptNativeSelection() {
        resolved.set(null, fixedSymbols())
        val config = bindFixedConfiguration()
        val recorder = Recorder()
        recorder.install(config)
        val application = RuntimeEnvironment.getApplication()
        val memory = application.getSharedPreferences("toki_playback", Context.MODE_PRIVATE)
        memory.edit().putString("configured", "2.0").putLong("importRevision", config.importRevision)
            .putFloat("selected", Float.NaN).commit()
        assertThrows(IllegalArgumentException::class.java) { PlaybackSpeedHook.refreshConfig(application) }
        assertFalse(PlaybackSpeedHook.isFeatureEnabled())
        recorder.select(1.5f)
        assertEquals(1, SpeedManager.originalSelections)
        assertEquals(1f, SpeedManager.LJII, 0f)
        assertTrue(memory.getFloat("selected", 1f).isNaN())
    }

    @Test fun rollbackBeforeActivationPreservesNativeManagerSelection() {
        resolved.set(null, fixedSymbols())
        val config = bindFixedConfiguration()
        val recorder = Recorder()
        recorder.install(config)
        SpeedManager.nativePersists = true
        recorder.select(1.5f)
        assertEquals(1.5f, SpeedManager.LJII, 0f)
        rollback()
        assertEquals(1.5f, SpeedManager.LJII, 0f)
        assertEquals(1, SpeedManager.originalSelections)
    }
}
