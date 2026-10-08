package io.github.meiyongai.toki.hook

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.meiyongai.toki.provider.HookDiagnostics
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.provider.ConfigSchema
import io.github.meiyongai.toki.provider.ConfigSnapshot
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.util.UUID

/** 所有功能共用的注册事务与会话诊断；不保存视频、作者或用户关键词。 */
internal object HookRuntime {
    private val session = UUID.randomUUID().toString()
    private val started = SystemClock.elapsedRealtime()
    private val features = Bundle()
    private val handles = mutableMapOf<String, MutableList<XposedInterface.HookHandle>>()
    private val resources = mutableMapOf<String, MutableList<AutoCloseable>>()
    private val scopes = mutableMapOf<String, RegistrationScope>()
    /** 一次功能安装范围的资格；撤销后本会话内不再重新获得资格。 */
    internal class RegistrationScope {
        @Volatile var active = true
            private set
        fun revoke() { active = false }
    }
    @Synchronized fun scope(feature: String): RegistrationScope =
        scopes.getOrPut(feature) { RegistrationScope() }
    private var receiver: android.content.BroadcastReceiver? = null
    private var process = ""
    private var adaptation = "等待宿主解析"
    private lateinit var reportEvent: (String, String) -> Unit

    /** 设置进程标识和持久诊断出口。@param name 进程名。@param reporter 框架日志出口。@return Unit。Callers: TokiModule。 */
    fun start(name: String, reporter: (String, String) -> Unit) { process = name; reportEvent = reporter }

    /** 记录不包含用户内容的关键生命周期事件。@param tag 组件名称。@param message 元数据摘要。@return Unit。Callers: HostSymbols、HostScanController、install。 */
    fun event(tag: String, message: String) { reportEvent(tag, message) }

    /**
     * 注册仅响应管理端查询的诊断接收器，不创建定时发布任务。
     * @param value 已附加的宿主上下文。
     * @return Unit。
     * Callers: TokiModule.hookApplication。
     */
    @Synchronized fun attach(value: Context) {
        if (receiver != null || process != value.packageName) return
        receiver = HookDiagnostics.register(value.applicationContext, ::snapshot)
    }

    /** 更新适配状态。@param value 无敏感数据的状态说明。@return Unit。Callers: HostSymbols。 */
    @Synchronized fun adaptation(value: String) { adaptation = value }

    /**
     * 独立注册一个功能，失败时撤销该功能的所有已注册方法并保留可见错误。
     * @param feature 功能标识，与 trackHook 一致。
     * @param install 注册操作。
     * @return Unit；不可恢复的虚拟机错误继续抛出。
     * Callers: TokiModule。
     */
    fun install(feature: String, install: () -> Unit) {
        val installStarted = SystemClock.elapsedRealtime()
        event("TokiHookRuntime", "开始注册 feature=$feature")
        val scope = synchronized(this) {
            scope(feature).also {
                check(it.active) { "本次会话已撤销功能 $feature，不能重新安装" }
                state(feature, "注册中")
            }
        }
        try {
            install()
            synchronized(this) { if (scope.active) state(feature, "已注册") }
        } catch (error: Throwable) {
            dispose(feature, error)
            if (error !is Exception && error !is LinkageError) throw error
            failure(feature, error, "注册失败，已撤销")
        } finally {
            event("TokiHookRuntime", "结束注册 feature=$feature elapsedMs=${SystemClock.elapsedRealtime() - installStarted} active=${scope.active}")
        }
    }

    /** 更新功能状态。@param feature 功能标识。@param value 状态。@return Unit。Callers: install、TokiModule。 */
    @Synchronized fun state(feature: String, value: String) {
        entry(feature).putString("state", value)
    }

    /**
     * 写入不含用户内容的配置或诊断摘要。
     * @param feature 功能标识。
     * @param value 仅包含开关、数量、范围等信息的说明。
     * @return Unit。
     * Callers: FeedFilterHook.policy。
     */
    @Synchronized fun detail(feature: String, value: String) {
        entry(feature).putString("detail", value)
    }

    /** 将订阅、页面监听等纳入功能安装事务；撤销时按创建顺序的逆序释放。 */
    fun own(feature: String, resource: AutoCloseable) {
        val retained = synchronized(this) {
            if (!scope(feature).active) false else {
                resources.getOrPut(feature) { mutableListOf() }.add(resource)
                true
            }
        }
        if (!retained) resource.close()
    }

    /** 登记功能级状态和视图清理，和 Hook 句柄拥有同一个生命周期。 */
    fun onDispose(feature: String, dispose: () -> Unit) = own(feature, AutoCloseable(dispose))

    /** 配置订阅与同一次安装范围绑定，首次通知及迟到通知都遵守范围资格。 */
    fun subscribe(feature: String, listener: (ConfigSnapshot?) -> Unit) {
        val scope = scope(feature)
        own(feature, ConfigClient.addStateListener { snapshot ->
            if (scope.active) listener(snapshot)
        })
    }

    /** 记录当前会话已实际应用的配置，避免将最新请求误报为已经生效。 */
    @Synchronized fun appliedConfiguration(feature: String, snapshot: ConfigSnapshot,
        enabled: Boolean = featureEnabled(feature, snapshot)) {
        entry(feature).putLong("appliedRevision", snapshot.revision)
        entry(feature).putBoolean("appliedEnabled", enabled)
    }

    /** 是否需重开宿主由启动安装集合与功能自身的更新契约决定。 */
    @Synchronized fun restartRequired(feature: String, value: Boolean) {
        entry(feature).putBoolean("restartRequired", value)
    }

    private fun featureEnabled(feature: String, snapshot: ConfigSnapshot): Boolean =
        if (feature == "ProgressBarHook") snapshot.boolean("always_show_progress_bar") ||
            snapshot.boolean("clean_mode_on_play")
        else ConfigSchema.featureSwitches[feature]?.any { snapshot.boolean(it) } == true

    /**
     * 释放事务拥有的全部资源。清理错误附加到原错误并可见报告，不能阻止其它资源释放。
     * 此处仅处理撤销事务，运行时业务异常仍由调用方原样传播。
     */
    private fun dispose(feature: String, cause: Throwable) {
        val owned = synchronized(this) {
            scope(feature).revoke()
            handles.remove(feature)
            entry(feature).putInt("registered", 0)
            entry(feature).remove("appliedEnabled")
            entry(feature).remove("appliedRevision")
            resources.remove(feature).orEmpty().asReversed()
        }
        for (resource in owned) {
            try { resource.close() } catch (error: Exception) {
                cause.addSuppressed(error)
                Log.e("TokiHookRuntime", "$feature: 撤销资源失败", error)
            }
        }
    }

    /**
     * 获取唯一的功能状态容器，必须持有对象锁。
     * @param feature 功能标识。
     * @return 可变状态容器。
     * Callers: state、registered、count、failure。
     */
    private fun entry(feature: String): Bundle = features.getBundle(feature) ?: Bundle().also {
        features.putBundle(feature, it)
    }

    /**
     * 记录注册句柄以支持失败事务的撤销。
     * @param feature 功能标识。
     * @param handle 已完成注册的句柄。
     * @return Unit。
     * Callers: TrackedHook.intercept。
     */
    fun registered(feature: String, handle: XposedInterface.HookHandle) {
        val retained = synchronized(this) {
            if (!scope(feature).active) false else {
                handles.getOrPut(feature) { mutableListOf() }.add(handle)
                resources.getOrPut(feature) { mutableListOf() }.add(AutoCloseable { handle.unhook() })
                entry(feature).putInt("registered", handles.getValue(feature).size)
                true
            }
        }
        if (!retained) handle.unhook()
    }

    /**
     * 在进程内累加无内容诊断计数，不执行跨进程通信。
     * @param feature 功能标识。
     * @param key 固定计数名或方法签名，不得包含用户内容。
     * @param amount 增量。
     * @return Unit。
     * Callers: TrackedHook、FeedFilterHook。
     */
    @Synchronized fun count(feature: String, key: String, amount: Long = 1) {
        val row = entry(feature)
        val counts = row.getBundle("counts") ?: Bundle().also { row.putBundle("counts", it) }
        counts.putLong(key, counts.getLong(key) + amount)
    }

    /**
     * 记录错误类型及模块调用位置，诊断回复不携带可能包含宿主数据的异常消息。
     * @param feature 功能标识。
     * @param error 实际异常。
     * @param status 明确的异常状态。
     * @return Unit。
     * Callers: install、TrackedHook.intercept。
     */
    @Synchronized fun failure(feature: String, error: Throwable, status: String) {
        val row = entry(feature)
        row.putString("errorStatus", status)
        row.putLong("errorAt", System.currentTimeMillis())
        if (status.endsWith("已撤销")) row.putString("state", status)
        row.putString("error", error.javaClass.name + "\n" + error.stackTrace.take(5).joinToString("\n"))
        Log.e("TokiHookRuntime", "$feature: $status", error)
    }

    /**
     * 按需生成诊断快照；配置未就绪时不填入虚构的开关状态。
     * @return 不包含用户内容的独立报告。
     * Callers: HookDiagnostics 的受权限保护接收器。
     */
    private fun snapshot(): Bundle {
        // 配置通知在存储事务内运行；先读配置，避免和诊断锁形成反向锁顺序。
        val config = ConfigClient.requestedSnapshotOrNull()
        return synchronized(this) { Bundle().apply {
        putString("session", session)
        putString("process", process)
        putInt("pid", Process.myPid())
        putLong("started", started)
        putLong("updated", System.currentTimeMillis())
        putString("adaptation", adaptation)
        if (config != null) putLong("configRevision", config.revision)
        putBundle("features", features.deepCopy().apply {
            for (feature in ConfigSchema.featureSwitches.keys) {
                val row = getBundle(feature) ?: continue
                if (config != null) {
                    val requested = featureEnabled(feature, config)
                    row.putBoolean("enabled", requested)
                    row.putBoolean("requestedEnabled", requested)
                } else {
                    row.remove("enabled")
                    row.remove("requestedEnabled")
                }
            }
        })
        } }
    }

    /**
     * 独立应用功能配置；失败撤销该功能并报告，不中断宿主 Application 启动。
     * @param feature 已注册的功能标识。
     * @param configure 初始化或热更新操作。
     * @return Unit。
     * Callers: TokiModule.refreshConfiguration。
     */
    fun configure(feature: String, configure: () -> Unit) {
        if (synchronized(this) { !scope(feature).active || handles[feature].isNullOrEmpty() }) return
        try {
            configure()
        } catch (error: Exception) {
            dispose(feature, error)
            failure(feature, error, "配置应用失败，已撤销")
        }
    }

    /**
     * 清除已恢复配置连接的错误标记并设置明确状态。
     * @param value 配置就绪或等待重新打开状态。
     * @return Unit。
     * Callers: TokiModule 的配置连接回调。
     */
    @Synchronized fun configurationReady(value: String) {
        val row = entry("ConfigClient")
        row.remove("error")
        row.remove("errorAt")
        row.remove("errorStatus")
        row.putString("state", value)
    }

}

/** 保留 libxposed 链式注册接口，统一采集注册、触发与异常状态。 */
internal class TrackedHook(
    private val feature: String,
    private val executable: Executable,
    private val builder: XposedInterface.HookBuilder,
    private val requiresConfiguration: Boolean = true
) {
    private val scope = HookRuntime.scope(feature)
    /** 设置优先级。@param priority 框架优先级。@return 当前构建器。Callers: 业务 Hook。 */
    fun setPriority(priority: Int): TrackedHook = apply { builder.setPriority(priority) }

    /** 设置异常模式。@param mode 框架异常模式。@return 当前构建器。Callers: 业务 Hook。 */
    fun setExceptionMode(mode: XposedInterface.ExceptionMode): TrackedHook = apply { builder.setExceptionMode(mode) }

    /**
     * 注册带计数的拦截器；运行异常记录后原样抛出，不伪造返回值。
     * @param hooker 业务拦截逻辑。
     * @return 框架注册句柄。
     * Callers: 业务 Hook。
     */
    fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
        val point = executable.declaringClass.name + "#" + executable.name +
            executable.parameterTypes.joinToString(",", "(", ")") { it.name }
        HookRuntime.count(feature, "调用:$point", 0)
        val handle = builder.intercept { chain ->
            if (!scope.active || requiresConfiguration && !ConfigClient.isReady) return@intercept chain.proceed()
            HookRuntime.count(feature, "调用:$point")
            val invocation = HookInvocation()
            try {
                hooker.intercept(ObservedChain(chain, invocation))
            } catch (error: Throwable) {
                if (invocation.fromDownstream(error)) {
                    HookRuntime.count(feature, "宿主或后续拦截链异常")
                } else {
                    HookRuntime.count(feature, "模块异常")
                    HookRuntime.failure(feature, error, "模块回调异常")
                }
                throw error
            }
        }
        HookRuntime.registered(feature, handle)
        return handle
    }
}

/** 委托所有成员读取，完整覆盖 libxposed 102 的四个原调用入口。 */
private class ObservedChain(
    private val source: XposedInterface.Chain,
    private val invocation: HookInvocation
) : XposedInterface.Chain by source {
    /** 调用原参数链。@return 原始返回值。Callers: 业务 Hook。 */
    override fun proceed(): Any? = invocation.proceed { source.proceed() }
    /** 调用替换参数链。@param args 参数数组。@return 原始返回值。Callers: 业务 Hook。 */
    override fun proceed(args: Array<out Any?>): Any? = invocation.proceed { source.proceed(args) }
    /** 替换接收者调用。@param thisObject 接收者。@return 原始返回值。Callers: 业务 Hook。 */
    override fun proceedWith(thisObject: Any): Any? = invocation.proceed { source.proceedWith(thisObject) }
    /** 替换接收者和参数调用。@param thisObject 接收者。@param args 参数。@return 原始返回值。Callers: 业务 Hook。 */
    override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? =
        invocation.proceed { source.proceedWith(thisObject, args) }
}

/**
 * 创建统一的可观测 Hook 构建器。
 * @param feature 稳定功能标识。
 * @param executable 已验证的目标成员。
 * @return 可继续设置优先级和异常模式的构建器。
 * Callers: 所有业务 Hook。
 */
internal fun XposedModule.trackHook(feature: String, executable: Executable,
    requiresConfiguration: Boolean = true): TrackedHook =
    TrackedHook(feature, executable, hook(executable).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH),
        requiresConfiguration)
