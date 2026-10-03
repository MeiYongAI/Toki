package io.github.meiyongai.toki.hook

import com.ss.android.ugc.aweme.feed.model.Aweme
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
            @JvmField var keepSpeed = false
            @JvmField var savedSpeed = 1f
            @JvmField var firstSpeed = 1f
            @JvmField var secondSpeed = 1f
            @JvmField var originalSelections = 0
            @JvmField var nativePersists = false
            @JvmStatic fun allowSpeed(value: Aweme): Boolean = false
            @JvmStatic fun readFirst(value: Aweme): Float = 1f
            @JvmStatic fun secondSpeed(value: Aweme): Float = 1f
            @JvmStatic fun keepSpeed(value: Aweme, source: String) {}
            @JvmStatic fun chooseSpeed(speed: Float, value: Aweme, source: String, scene: String) {
                originalSelections++
                if (nativePersists) savedSpeed = speed
            }
            @JvmStatic fun resetSpeed(value: Aweme, source: String, first: Boolean, second: Boolean): Boolean = false
        }
    }

    class Gate { companion object { @JvmStatic fun allowSpeed(): Boolean = false } }
    class Menus {
        companion object {
            @JvmStatic fun options(): List<Float> = listOf(1f)
            @JvmStatic fun primary(value: Menus): Any = listOf(1f)
            @JvmStatic fun secondary(value: Menus): Any = listOf(1f)
            @JvmStatic fun middle(value: Menus): Any = listOf(1f)
            @JvmStatic fun last(value: Menus): Any = listOf(1f)
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
        SpeedManager.keepSpeed = false
        SpeedManager.savedSpeed = 1f
        SpeedManager.firstSpeed = 1f
        SpeedManager.secondSpeed = 1f
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
        for ((role, name) in mapOf("gate" to "allowSpeed", "query0" to "readFirst", "query1" to "secondSpeed",
            "restore" to "keepSpeed", "select" to "chooseSpeed", "reset" to "resetSpeed", "enabled" to "keepSpeed",
            "persist" to "savedSpeed", "current0" to "firstSpeed", "current1" to "secondSpeed")) {
            setProperty("member.SPEED_MANAGER.$role", name)
        }
        setProperty("member.PLAYER_MANAGER.setSpeed", "changeSpeed")
        setProperty("member.PLAYER_CONTROLLER.setSpeed", "changeSpeed")
        setProperty("member.PLAYER_CONTROLLER.getManager", "getManager")
        setProperty("member.PLAYER_CONTROLLER.setManager", "setManager")
        setProperty("member.PLAYER_CONTROLLER.renderReady", "renderReady")
    }

    /** @return 同一实验开关关联的五个已验证数据源。Callers: 本类注册测试。 */
    private fun menuSymbols(): Properties = Properties().apply {
        setProperty("SPEED_OPTIONS", Gate::class.java.name)
        setProperty("member.SPEED_OPTIONS.gate", "allowSpeed()Z")
        val owner = "L${Menus::class.java.name.replace('.', '/')};"
        val sources = listOf("$owner->options()Ljava/util/List;") +
            listOf("primary", "secondary", "middle", "last").map { "$owner->$it($owner)Ljava/lang/Object;" }
        setProperty("members.SPEED_OPTIONS.sources", sources.joinToString("\n"))
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
            val point = callbacks.keys.single { it.declaringClass == SpeedManager::class.java && it.name == "chooseSpeed" } as Method
            invokeStatic(point, speed, Aweme(), "test", "menu")
        }

        /** @param point 已注册入口。@param values 调用参数。@return 实际拦截结果。Callers: select、菜单测试。 */
        fun invokeStatic(point: Method, vararg values: Any?): Any? {
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
            return callbacks.getValue(point).intercept(chain)
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

    /** 数据源数量由扫描结果决定，自定义档位覆盖全部匹配入口。无参数，无返回。Callers: JUnit。 */
    @Test fun menuSourceCountCanChangeAndAllSourcesReturnConfiguredSpeeds() {
        bindFixedConfiguration()
        resolved.set(null, menuSymbols().apply {
            setProperty("members.SPEED_OPTIONS.sources", getProperty("members.SPEED_OPTIONS.sources").split('\n').take(2).joinToString("\n"))
        })
        val recorder = Recorder()
        recorder.install(ConfigSnapshot(mapOf("speed_expand_enabled" to true, "speed_expand_list" to "0.75,1,2.5")))
        assertEquals(3, recorder.points.size)
        val source = Menus::class.java.getDeclaredMethod("primary", Menus::class.java)
        assertEquals(listOf(0.75f, 1f, 2.5f), recorder.invokeStatic(source, Menus()))
    }

    /** 空白、重复、错误签名和缺少普通列表入口均在注册前拒绝。无参数，无返回。Callers: JUnit。 */
    @Test fun invalidMenuCollectionsRegisterNothing() {
        val valid = menuSymbols().getProperty("members.SPEED_OPTIONS.sources").split('\n')
        for (encoded in listOf("", valid[0] + "\n" + valid[0], valid[0].replace("()", "(F)"), valid[1])) {
            resolved.set(null, menuSymbols().apply { setProperty("members.SPEED_OPTIONS.sources", encoded) })
            val recorder = Recorder()
            assertThrows(IllegalStateException::class.java) {
                recorder.install(ConfigSnapshot(mapOf("speed_expand_enabled" to true)))
            }
            assertTrue(recorder.points.isEmpty())
        }
    }

    @Test fun incompleteCombinedRequestFailsBeforeAnyRegistration() {
        resolved.set(null, fixedSymbols().apply {
            putAll(menuSymbols())
            remove("members.SPEED_OPTIONS.sources")
        })
        val recorder = Recorder()
        val failure = assertThrows(IllegalStateException::class.java) {
            recorder.install(ConfigSnapshot(mapOf("fixed_speed_enabled" to true, "speed_expand_enabled" to true)))
        }
        assertTrue(failure.message.orEmpty().contains("SPEED_OPTIONS.sources"))
        assertTrue(recorder.points.isEmpty())
        assertFalse(SpeedManager.keepSpeed)
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
        SpeedManager.keepSpeed = true
        SpeedManager.savedSpeed = 2f
        rollback()
        assertEquals(recorder.points.size, recorder.removed)
        val methods = PlaybackSpeedHook::class.java.getDeclaredField("hookedPlayerManagerMethods")
            .apply { isAccessible = true }.get(null) as Set<Method>
        assertTrue(methods.isEmpty())
        assertFalse(PlaybackSpeedHook.isFeatureEnabled())
        assertFalse(SpeedManager.keepSpeed)
        assertEquals(1f, SpeedManager.savedSpeed, 0f)
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
        assertEquals(1f, SpeedManager.savedSpeed, 0f)
        assertFalse(memory.contains("selected"))

        PlaybackSpeedHook.refreshConfig(application)
        assertTrue(PlaybackSpeedHook.isFeatureEnabled())
        assertEquals(2f, SpeedManager.savedSpeed, 0f)
        recorder.select(1.5f)
        assertEquals(2, SpeedManager.originalSelections)
        assertEquals(1.5f, PlaybackSpeedMemory(memory).read(config), 0f)
        assertEquals(1.5f, SpeedManager.savedSpeed, 0f)
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
        assertEquals(1f, SpeedManager.savedSpeed, 0f)
        assertTrue(memory.getFloat("selected", 1f).isNaN())
    }

    @Test fun rollbackBeforeActivationPreservesNativeManagerSelection() {
        resolved.set(null, fixedSymbols())
        val config = bindFixedConfiguration()
        val recorder = Recorder()
        recorder.install(config)
        SpeedManager.nativePersists = true
        recorder.select(1.5f)
        assertEquals(1.5f, SpeedManager.savedSpeed, 0f)
        rollback()
        assertEquals(1.5f, SpeedManager.savedSpeed, 0f)
        assertEquals(1, SpeedManager.originalSelections)
    }
}
