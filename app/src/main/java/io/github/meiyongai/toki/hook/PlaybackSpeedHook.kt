package io.github.meiyongai.toki.hook

import android.content.Context
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.provider.ConfigSnapshot
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/** 视频倍速保持与菜单扩展；通过当前宿主的调用关系和状态字段契约注册。 */
object PlaybackSpeedHook {

    private const val TAG = "TokiSpeedHook"

    /** 固定倍速功能总开关 */
    @Volatile
    private var isEnabled: Boolean = false

    /** 当前记忆的固定倍速 */
    @Volatile
    private var activeSpeed: Float = 1.0f

    /** 倍速档位拓展功能开关 */
    @Volatile
    private var isExpandEnabled: Boolean = false

    /** 自定义倍速档位列表 */
    @Volatile
    private var customSpeedList: List<Float> = listOf(0.5f, 1.0f, 1.5f, 2.0f)

    /** 宿主私有倍速记忆，不触发管理端服务启动。 */
    @Volatile
    private var speedMemory: PlaybackSpeedMemory? = null

    /** TikTok 原生倍速控制中枢类引用 */
    @Volatile
    private var speedManagerClass: Class<*>? = null

    /** 已完成 Hook 的实际声明方法，父类共享实现只注册一次。 */
    private val hookedPlayerManagerMethods = mutableSetOf<Method>()

    /** 由完整 DEX 成员契约确定的控制器调速方法。 */
    private var controllerSpeedMethod: Method? = null

    /** 由完整 DEX 成员契约确定的播放管理器访问方法。 */
    private var playerManagerGetter: Method? = null

    /** 播放管理器接口，动态实现必须满足该类型。 */
    private var playerManagerType: Class<*>? = null

    /** 当前构建已验证的播放管理器调速入口名。 */
    private var managerSpeedName: String? = null

    /** 最近一次活跃的视频播放控制器弱引用，用于选速时即时应用新倍速 */
    @Volatile
    private var activeControllerRef: java.lang.ref.WeakReference<Any>? = null

    /** 固定倍速的必需状态字段；全部在注册前验证。 */
    private var managerFields: Map<String, Field> = emptyMap()
    /** 首次真正覆盖中枢时才记录原值；待初始化期间的宿主原生更新必须保留。 */
    private var managerBaseline: Map<String, Any?>? = null

    private data class ControllerContract(
        val type: Class<*>, val managerType: Class<*>, val managerSpeed: String,
        val speed: Method, val getManager: Method, val setManager: Method, val renderReady: Method
    )

    private data class MenuContract(val gate: Method, val sources: List<Method>)

    private data class ManagerContract(val type: Class<*>, val fields: Map<String, Field>,
        val methods: Map<String, Method>)

    /** 允许设定的最小合法播放倍速（移动端音频 Sonic 重采样算法下限） */
    const val MIN_SPEED: Float = 0.1f

    /** 允许设定的最大合法播放倍速（移动端硬件解码器与 TTVideoEngine 安全上限 3.0x） */
    const val MAX_SPEED: Float = 3.0f

    /**
     * 将浮点数倍速格式化为用户友好的字符串显示形式。
     *
     * 整数倍速去除多余小数位（如 2.0f -> "2"），小数倍速完整保留（如 1.5f -> "1.5"）。
     *
     * Args:
     *     speed (Float): 待格式化的浮点倍速数值。
     *
     * Returns:
     *     String: 格式化后的倍速字符串。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.onUserSelectedSpeed`: 日志记录与数据处理。
     */
    fun formatSpeedText(speed: Float): String {
        return if (speed % 1.0f == 0f) {
            speed.toInt().toString()
        } else {
            speed.toString()
        }
    }

    /**
     * 解析逗号分隔的倍速配置字符串为排序去重后的浮点数列表。
     *
     * 过滤非 [MIN_SPEED]..[MAX_SPEED] 范围的非法数值并升序排序，若解析结果为空则返回标准默认档位列表。
     *
     * Args:
     *     rawStr (String?): 原始逗号分隔字符串。
     *
     * Returns:
     *     List<Float>: 解析并清洗、排序去重后的 Float 列表，若空则返回默认四档倍速。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.MainActivity.MainScreenContent`: UI 层倍速标签列表状态生成。
     */
    fun parseSpeedList(rawStr: String?): List<Float> {
        if (rawStr.isNullOrBlank()) {
            return listOf(0.5f, 1.0f, 1.5f, 2.0f)
        }
        val parsed = rawStr.split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .filter { it in MIN_SPEED..MAX_SPEED }
            .distinct()
            .sorted()
        return if (parsed.isNotEmpty()) parsed else listOf(0.5f, 1.0f, 1.5f, 2.0f)
    }

    /**
     * 从框架配置快照读取开关，并从宿主私有存储读取上次选择的倍速。
     *
     * 首次使用以管理端的倍速配置作为基准，后续选择由宿主保存，导入配置后重新应用导入基准。
     *
     * Args:
     *     context (Context): 宿主目标应用的上下文对象。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.hookApplication`: 宿主进程初始化时同步。
     */
    fun refreshConfig(context: Context) {
        // 固定链只有在其全部运行依赖就绪后才可进入；初始化失败仍保留明确的未激活状态。
        isEnabled = false
        isExpandEnabled = false
        ConfigClient.init(context)
        val snapshot = ConfigClient.snapshot()
        val fixed = snapshot.boolean("fixed_speed_enabled")
        val expanded = snapshot.boolean("speed_expand_enabled")
        val options = parseSpeedList(snapshot.string("speed_expand_list"))

        if (!fixed) {
            Log.i(TAG, "固定播放倍速功能处于停用状态")
            syncSpeedManagerFields(1.0f, enablePersist = false)
            activeSpeed = 1.0f
            customSpeedList = options
            isExpandEnabled = expanded
            return
        }

        val memory = speedMemory ?: PlaybackSpeedMemory(
            context.getSharedPreferences("toki_playback", Context.MODE_PRIVATE))
        val speed = memory.read(snapshot)
        syncSpeedManagerFields(speed, enablePersist = speed != 1.0f)
        speedMemory = memory
        activeSpeed = speed
        customSpeedList = options
        isExpandEnabled = expanded
        isEnabled = true
        Log.i(TAG, "已同步倍速记忆配置 -> 状态: 启用, 记忆倍速: ${speed}x")
    }

    /**
     * 获取倍速档位拓展功能是否处于激活状态。
     *
     * 校验倍速档位拓展开关状态。
     *
     * Args:
     *     无。
     *
     * Returns:
     *     Boolean: 激活返回 true，停用返回 false。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.hookSpeedDialogDataSource`: 动态档位下发时判断。
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.hookLongPressSpeedDataSource`: 长按面板档位下发时判断。
     */
    fun isExpansionEnabled(): Boolean = isExpandEnabled

    /**
     * 获取当前倍速固定功能是否处于激活状态。
     *
     * 校验全局开关与当前配置状态。
     *
     * Args:
     *     无。
     *
     * Returns:
     *     Boolean: 激活返回 true，停用返回 false。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.hookPlayerManagerSetSpeed`: 底层拦截判断。
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.hookSpeedManagerMethods`: 中枢调度拦截判断。
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.hookPlayerControllerMethods`: 控制器拦截判断。
     */
    fun isFeatureEnabled(): Boolean = isEnabled

    /**
     * 按已验证的入口名称解析公开调速方法，支持继承的具体实现并拒绝错误返回类型。
     * @param type 控制器或播放管理器的运行时类型。
     * @param name 扫描契约给出的方法名。
     * @return 可 Hook 的公开实例方法；缺失、抽象、静态或返回类型错误时抛出异常。
     * Callers: hookPlayerControllerMethods、hookPlayerManagerSetSpeed、PlaybackSpeedMethodTest。
     */
    internal fun resolveSpeedMethod(type: Class<*>, name: String): Method {
        val method = type.getMethod(name, java.lang.Float.TYPE)
        check(method.returnType == java.lang.Void.TYPE &&
            !java.lang.reflect.Modifier.isStatic(method.modifiers) &&
            !java.lang.reflect.Modifier.isAbstract(method.modifiers)) {
            "调速入口不是具体实例 void 方法：${type.name}.$name"
        }
        return method.apply { isAccessible = true }
    }

    /**
     * 使用已核实的控制器访问方法获取当前播放管理器。
     * @param controller 视频播放控制器实例。
     * @return 宿主提供的播放管理器；宿主尚未建立播放器时可为空。
     * Callers: hookPlayerControllerMethods。
     */
    private fun resolvePlayerManager(controller: Any): Any? = checkNotNull(playerManagerGetter).invoke(controller)

    /**
     * 经控制器向关联播放器应用一次调速指令，避免对同一播放器重复调速。
     * @param controller 已捕获的视频播放控制器。
     * @param speed 目标播放倍速。
     * @return Unit；反射或宿主调用异常交由统一 Hook 诊断报告。
     * Callers: hookPlayerControllerMethods、onUserSelectedSpeed。
     */
    private fun applySpeedToPlayerController(controller: Any, speed: Float) {
        checkNotNull(controllerSpeedMethod).invoke(controller, speed)
    }

    /**
     * 将记忆的倍速值与保持开关同步写入 TikTok 原生倍速控制中枢的状态字段。
     *
     * 对齐中枢内部的跨视频保持开关、目标保持倍速与两个当前播放倍速字段。
     *
     * Args:
     *     speed (Float): 待同步的倍速浮点数值。
     *     enablePersist (Boolean): 是否激活原生跨视频保持开关。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.refreshConfig`: 启动配置同步。
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.onUserSelectedSpeed`: 用户在菜单中选速时更新。
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.hookSpeedManagerMethods`: 状态重置守护。
     */
    private fun syncSpeedManagerFields(speed: Float, enablePersist: Boolean) {
        val clazz = speedManagerClass ?: return
        if (managerBaseline == null) managerBaseline = managerFields.mapValues { (_, field) -> field.get(null) }

        managerFields.getValue("enabled").setBoolean(null, enablePersist)
        managerFields.getValue("persist").setFloat(null, speed)
        managerFields.getValue("current0").setFloat(null, speed)
        managerFields.getValue("current1").setFloat(null, speed)

        Log.i(TAG, "已同步至原生倍速中枢 ${clazz.name} -> speed: ${speed}x, persist: $enablePersist")
    }

    /**
     * 响应用户在 TikTok 原生菜单中的倍速选择事件，完成倍速锁定、持久化存储以及当前视频即时调速。
     *
     * Args:
     *     speed (Float): 用户选择的目标倍速。
     *     scene (String?): 选速触发场景标识符。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.PlaybackSpeedHook.hookSpeedManagerMethods`: 拦截用户选速。
     */
    private fun onUserSelectedSpeed(speed: Float, scene: String?) {
        if (!isFeatureEnabled() || speed !in MIN_SPEED..MAX_SPEED) return

        activeSpeed = speed
        Log.i(TAG, "捕获到用户在 TikTok 原生菜单选择倍速 -> 锁定为: ${formatSpeedText(speed)}x, 场景: $scene")

        syncSpeedManagerFields(speed, enablePersist = true)

        activeControllerRef?.get()?.let { controller ->
            applySpeedToPlayerController(controller, speed)
            Log.i(TAG, "已向当前视频播放控制器即时应用新倍速 -> ${speed}x")
        }


        if (!checkNotNull(speedMemory).save(speed, ConfigClient.snapshot())) {
            HookRuntime.failure("PlaybackSpeedHook", IllegalStateException("宿主未确认倍速记忆保存"), "倍速记忆保存失败")
        }
    }

    /**
     * 为播放管理器的真实调速实现注册防重置拦截，父类共享实现只注册一次。
     * @param module 当前 LSPosed 模块。
     * @param managerClass 宿主播放器的具体运行时类型。
     * @return Unit；类型或方法契约不满足时明确报告注册错误。
     * Callers: hookPlayerControllerMethods。
     */
    private fun hookPlayerManagerSetSpeed(module: XposedModule, managerClass: Class<*>) {
        check(checkNotNull(playerManagerType).isAssignableFrom(managerClass)) { "播放器实例不满足已验证接口" }
        val method = resolveSpeedMethod(managerClass, checkNotNull(managerSpeedName))
        if (!synchronized(hookedPlayerManagerMethods) { hookedPlayerManagerMethods.add(method) }) return
        var registered = false
        try {
            module.trackHook("PlaybackSpeedHook", method).intercept { chain ->
                val requestedSpeed = chain.args[0] as Float
                if (isFeatureEnabled() && activeSpeed != 1.0f && requestedSpeed == 1.0f) {
                    chain.proceed(arrayOf(activeSpeed))
                } else {
                    chain.proceed()
                }
            }
            registered = true
        } finally {
            if (!registered) synchronized(hookedPlayerManagerMethods) { hookedPlayerManagerMethods.remove(method) }
        }
        Log.i(TAG, "已注册播放器调速入口：${method.declaringClass.name}.${method.name}")
    }

    /**
     * 依照已验证的角色注册中枢查询、恢复、选速与重置入口。
     * @param module 当前模块。
     * @param contract 完整的中枢方法与状态契约。
     * @return Unit；注册失败交由统一事务撤销。
     * Callers: init。
     */
    private fun hookSpeedManagerMethods(module: XposedModule, contract: ManagerContract) {
        speedManagerClass = contract.type
        module.trackHook("PlaybackSpeedHook", contract.methods.getValue("gate")).intercept { chain ->
            if (isFeatureEnabled() && activeSpeed != 1f) true else chain.proceed()
        }
        for (role in listOf("query0", "query1")) {
            module.trackHook("PlaybackSpeedHook", contract.methods.getValue(role)).intercept { chain ->
                if (isFeatureEnabled() && activeSpeed != 1f) activeSpeed else chain.proceed()
            }
        }
        module.trackHook("PlaybackSpeedHook", contract.methods.getValue("restore")).intercept { chain ->
            val aweme = chain.args[0]
            if (isFeatureEnabled() && activeSpeed != 1f && aweme != null) {
                contract.methods.getValue("select").invoke(null, activeSpeed, aweme, chain.args[1], "long_press")
                null
            } else chain.proceed()
        }
        module.trackHook("PlaybackSpeedHook", contract.methods.getValue("select")).intercept { chain ->
            if (!isFeatureEnabled()) return@intercept chain.proceed()
            val speed = chain.args[0] as Float
            val scene = chain.args[3] as? String
            for (role in listOf("current0", "current1", "persist")) managerFields.getValue(role).setFloat(null, speed)
            onUserSelectedSpeed(speed, scene.takeUnless { it.isNullOrEmpty() } ?: "long_press")
            if (scene.isNullOrEmpty()) {
                chain.proceed(arrayOf(speed, chain.args[1], chain.args[2], "long_press"))
            } else chain.proceed()
        }
        module.trackHook("PlaybackSpeedHook", contract.methods.getValue("reset")).intercept { chain ->
            val result = chain.proceed()
            if (isFeatureEnabled() && activeSpeed != 1f) syncSpeedManagerFields(activeSpeed, enablePersist = true)
            result
        }
        Log.i(TAG, "已注册倍速中枢：${contract.type.name}；入口=${contract.methods.size}")
    }
    /**
     * 解析控制器调速入口和播放器生命周期，所有成员由当前代码集合的契约确定。
     * @param classLoader 宿主最终类加载器。
     * @return 完整控制器契约；缺失或类型错误均在安装前抛出。
     * Callers: init。
     */
    private fun controllerContract(classLoader: ClassLoader): ControllerContract {
        val clazz = HostSymbols.resolve(classLoader, HostSymbol.PLAYER_CONTROLLER)
        val managerType = HostSymbols.resolve(classLoader, HostSymbol.PLAYER_MANAGER)
        val managerSpeed = HostSymbols.member(HostSymbol.PLAYER_MANAGER, "setSpeed")
        val managerContract = managerType.getDeclaredMethod(managerSpeed, java.lang.Float.TYPE)
        check(managerType.isInterface && managerContract.returnType == java.lang.Void.TYPE)
        val speed = resolveSpeedMethod(clazz, HostSymbols.member(HostSymbol.PLAYER_CONTROLLER, "setSpeed"))
        val getManager = clazz.getDeclaredMethod(HostSymbols.member(HostSymbol.PLAYER_CONTROLLER, "getManager"))
            .apply { isAccessible = true }
        check(getManager.returnType == managerType)
        val setManager = clazz.getDeclaredMethod(
            HostSymbols.member(HostSymbol.PLAYER_CONTROLLER, "setManager"), managerType
        ).apply { isAccessible = true }
        check(setManager.returnType == java.lang.Void.TYPE)
        val renderReady = clazz.declaredMethods.single {
            it.name == HostSymbols.member(HostSymbol.PLAYER_CONTROLLER, "renderReady")
        }.apply { isAccessible = true }
        check(renderReady.returnType == java.lang.Void.TYPE && renderReady.parameterCount == 1)
        return ControllerContract(clazz, managerType, managerSpeed, speed, getManager, setManager, renderReady)
    }

    private fun hookPlayerControllerMethods(module: XposedModule, contract: ControllerContract) {
        playerManagerType = contract.managerType
        managerSpeedName = contract.managerSpeed
        controllerSpeedMethod = contract.speed
        playerManagerGetter = contract.getManager

        module.trackHook("PlaybackSpeedHook", contract.setManager).intercept { chain ->
            val controller = checkNotNull(chain.thisObject)
            activeControllerRef = java.lang.ref.WeakReference(controller)
            val manager = chain.args[0]
            if (manager != null) hookPlayerManagerSetSpeed(module, manager.javaClass)
            val result = chain.proceed()
            if (manager != null && isFeatureEnabled() && activeSpeed != 1.0f) {
                applySpeedToPlayerController(controller, activeSpeed)
            }
            result
        }

        module.trackHook("PlaybackSpeedHook", contract.speed).intercept { chain ->
            activeControllerRef = java.lang.ref.WeakReference(checkNotNull(chain.thisObject))
            val requestedSpeed = chain.args[0] as Float
            if (isFeatureEnabled() && activeSpeed != 1.0f && requestedSpeed == 1.0f) {
                chain.proceed(arrayOf(activeSpeed))
            } else {
                chain.proceed()
            }
        }

        module.trackHook("PlaybackSpeedHook", contract.renderReady).intercept { chain ->
            val result = chain.proceed()
            val controller = checkNotNull(chain.thisObject)
            activeControllerRef = java.lang.ref.WeakReference(controller)
            val manager = resolvePlayerManager(controller)
            if (manager != null) {
                hookPlayerManagerSetSpeed(module, manager.javaClass)
                if (isFeatureEnabled() && activeSpeed != 1.0f) applySpeedToPlayerController(controller, activeSpeed)
            }
            result
        }
        Log.i(TAG, "已注册播放控制器调速入口：${contract.type.name}.${contract.speed.name}")
    }

    /**
     * 解析按构建核实的倍速档位数据源，不监听全局 ClassLoader 或猜测 Lambda 编号。
     * @param classLoader 宿主最终类加载器。
     * @return 完整菜单契约；任何缺失或类型错误均在安装前抛出。
     * Callers: init。
     */
    private fun menuContract(classLoader: ClassLoader): MenuContract {
        val gate = HostSymbols.resolve(classLoader, HostSymbol.SPEED_OPTIONS).getDeclaredMethod(
            HostSymbols.member(HostSymbol.SPEED_OPTIONS, "gate"))
        check(gate.returnType == Boolean::class.javaPrimitiveType && java.lang.reflect.Modifier.isStatic(gate.modifiers))
        val methods = HostSymbols.members(HostSymbol.SPEED_OPTIONS, "sources").map { reference ->
            val owner = reference.substringBefore("->")
            check(owner.startsWith('L') && owner.endsWith(';') && reference.contains("->")) { "无效倍速菜单引用：$reference" }
            val descriptor = reference.substringAfter("->")
            val type = Class.forName(owner.substring(1, owner.length - 1).replace('/', '.'), false, classLoader)
            val name = descriptor.substringBefore('(')
            val signature = descriptor.substring(name.length)
            val method = when (signature) {
                "()Ljava/util/List;" -> type.getDeclaredMethod(name).also { check(it.returnType == List::class.java) }
                "($owner)Ljava/lang/Object;" -> type.getDeclaredMethod(name, type).also { check(it.returnType == Any::class.java) }
                else -> error("无效倍速菜单签名：$reference")
            }
            check(java.lang.reflect.Modifier.isStatic(method.modifiers))
            method.apply { isAccessible = true }
        }
        check(methods.any { it.parameterCount == 0 }) { "倍速菜单缺少列表入口" }
        return MenuContract(gate.apply { isAccessible = true }, methods)
    }
    private fun hookSpeedDialogMethods(module: XposedModule, contract: MenuContract) {
        module.trackHook("PlaybackSpeedHook", contract.gate).intercept { chain ->
            if (isExpansionEnabled()) true else chain.proceed()
        }
        for (method in contract.sources) {
            module.trackHook("PlaybackSpeedHook", method).intercept { chain ->
                if (isExpansionEnabled()) customSpeedList else chain.proceed()
            }
        }
        HookRuntime.state("SpeedOptions", "菜单数据源已注册；调用次数计入播放倍速")
        Log.i(TAG, "已注册倍速档位数据源：${contract.sources.size} 个")
    }

    /**
     * 依据启动请求分别核实并安装固定倍速链和菜单扩展链；全部所需契约先验证再注册。
     *
     * Args:
     *     module (XposedModule): 当前注入的 XposedModule 实例。
     *     classLoader (ClassLoader): 宿主目标应用类加载器。
     *     snapshot (ConfigSnapshot): 本次进程实际应用的启动配置。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.hook.TokiModule.onPackageLoaded`: 目标包加载时触发。
     */
    fun init(module: XposedModule, classLoader: ClassLoader, snapshot: ConfigSnapshot) {
        val fixed = snapshot.boolean("fixed_speed_enabled")
        val expanded = snapshot.boolean("speed_expand_enabled")
        if (!fixed && !expanded) return

        // 先核实本次请求的全部契约；不允许留下只有 gate 或部分锁速入口的安装结果。
        val controller = if (fixed) controllerContract(classLoader) else null
        val manager = if (fixed) managerContract(HostSymbols.resolve(classLoader, HostSymbol.SPEED_MANAGER)) else null
        val fields = manager?.fields.orEmpty()
        val menu = if (expanded) try {
            menuContract(classLoader)
        } catch (error: Exception) {
            HookRuntime.failure("SpeedOptions", error, "倍速菜单契约验证失败")
            throw error
        } else null
        HookRuntime.onDispose("PlaybackSpeedHook") {
            synchronized(hookedPlayerManagerMethods) { hookedPlayerManagerMethods.clear() }
            activeControllerRef?.clear()
            activeControllerRef = null
            speedManagerClass = null
            speedMemory = null
            controllerSpeedMethod = null
            playerManagerGetter = null
            playerManagerType = null
            managerSpeedName = null
            isEnabled = false
            isExpandEnabled = false
            activeSpeed = 1f
            customSpeedList = listOf(0.5f, 1f, 1.5f, 2f)
            managerFields = emptyMap()
            if (expanded) {
                HookRuntime.state("SpeedOptions", "注册失败，已撤销")
                HookRuntime.appliedConfiguration("SpeedOptions", snapshot, false)
            }
            val originalFields = managerBaseline
            managerBaseline = null
            if (originalFields != null) {
                for ((name, field) in fields) field.set(null, originalFields.getValue(name))
            }
        }
        managerFields = fields
        managerBaseline = null
        isEnabled = false // Application 中准备好倍速记忆和中枢状态后才能激活固定链。
        isExpandEnabled = expanded
        customSpeedList = parseSpeedList(snapshot.string("speed_expand_list"))
        if (controller != null) hookPlayerControllerMethods(module, controller)
        if (manager != null) hookSpeedManagerMethods(module, manager)
        if (menu != null) {
            hookSpeedDialogMethods(module, menu)
            HookRuntime.appliedConfiguration("SpeedOptions", snapshot)
        }
        Log.i(TAG, "倍速请求已注册：固定=$fixed，档位扩展=$expanded")
    }

    /**
     * 按角色解析唯一、静态且类型完整的中枢成员，在任何 Hook 注册前验证。
     * @param type 扫描确定的中枢类型。
     * @return 完整中枢契约；错误直接报告。
     * Callers: init。
     */
    private fun managerContract(type: Class<*>): ManagerContract {
        val symbol = HostSymbol.SPEED_MANAGER
        val fields = listOf("enabled", "persist", "current0", "current1").associateWith { role ->
            type.getDeclaredField(HostSymbols.member(symbol, role)).apply {
                check(java.lang.reflect.Modifier.isStatic(modifiers) &&
                    this.type == if (role == "enabled") java.lang.Boolean.TYPE else java.lang.Float.TYPE) {
                    "固定倍速状态字段契约错误：${type.name}.$name"
                }
                isAccessible = true
            }
        }
        check(fields.values.toSet().size == fields.size) { "倍速状态角色引用了重复字段" }
        val aweme = Class.forName("com.ss.android.ugc.aweme.feed.model.Aweme", false, type.classLoader)
        /** @param role 业务角色。@param returns 返回类型。@param parameters 参数类型。
         * @return 静态可访问方法。Callers: managerContract。 */
        fun method(role: String, returns: Class<*>, vararg parameters: Class<*>): Method =
            type.getDeclaredMethod(HostSymbols.member(symbol, role), *parameters).apply {
                check(java.lang.reflect.Modifier.isStatic(modifiers) && returnType == returns && !isSynthetic) {
                    "倍速中枢方法契约错误：$role"
                }
                isAccessible = true
            }
        val methods = mapOf(
            "gate" to method("gate", java.lang.Boolean.TYPE, aweme),
            "query0" to method("query0", java.lang.Float.TYPE, aweme),
            "query1" to method("query1", java.lang.Float.TYPE, aweme),
            "restore" to method("restore", java.lang.Void.TYPE, aweme, String::class.java),
            "select" to method("select", java.lang.Void.TYPE, java.lang.Float.TYPE, aweme, String::class.java, String::class.java),
            "reset" to method("reset", java.lang.Boolean.TYPE, aweme, String::class.java, java.lang.Boolean.TYPE, java.lang.Boolean.TYPE),
        )
        check(methods.values.toSet().size == methods.size) { "倍速方法角色引用了重复入口" }
        return ManagerContract(type, fields, methods)
    }
}
