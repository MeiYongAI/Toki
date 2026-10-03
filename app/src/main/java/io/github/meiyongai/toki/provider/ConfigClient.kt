package io.github.meiyongai.toki.provider

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 管理端私有配置与宿主框架只读快照的统一接口，不通过 ContentProvider 同步设置。 */
object ConfigClient {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val stateListeners = java.util.concurrent.CopyOnWriteArrayList<StateSubscription>()
    private val stateSequence = java.util.concurrent.atomic.AtomicLong()
    private data class Publication(val sequence: Long, val snapshot: ConfigSnapshot?)
    private class StateSubscription(val listener: (ConfigSnapshot?) -> Unit) {
        @Volatile var active = true
        var delivered = Long.MIN_VALUE // 仅在主线程分发时读写。
        fun deliver(publication: Publication) {
            if (!active || publication.sequence <= delivered) return
            delivered = publication.sequence
            listener(publication.snapshot)
        }
    }
    private val importedRevisionState = MutableStateFlow(0L)
    val importedRevision = importedRevisionState.asStateFlow()
    private var initialized = false
    private var host = false
    @Volatile private var hostSessionTransform: ((ConfigSnapshot) -> ConfigSnapshot)? = null
    private var effectiveCache: Pair<ConfigSnapshot, ConfigSnapshot>? = null
    private var connectionChanged: (() -> Unit)? = null
    private val store = ConfigStore(::notifyChanged) { Log.e("TokiConfig", "配置连接或同步失败", it) }
    private val reload = Runnable { store.reloadHost() }
    // SharedPreferences 对监听器持有弱引用，此处必须持有进程级强引用。
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.removeCallbacks(reload)
        main.post(reload)
    }
    val isReady: Boolean get() = store.isReady
    val syncError: Exception? get() = store.syncError
    val revision: Long get() = store.snapshot().revision
    const val FRAMEWORK_GROUP = ConfigStore.GROUP

    /**
     * 仅在管理端连接私有设置；已初始化的宿主不会访问该 Context 的存储或 Provider。
     * @param context 管理端上下文或已绑定框架的宿主上下文。
     * @return Unit。
     * Callers: TokiApplication、MainActivity、管理界面、各功能 refreshConfig。
     */
    @Synchronized fun init(context: Context) {
        if (initialized) return
        check(!host && context.packageName == "io.github.meiyongai.toki") { "宿主必须通过框架初始化配置" }
        store.attachLocal(context.getSharedPreferences(ConfigStore.LOCAL_NAME, Context.MODE_PRIVATE))
        initialized = true
    }

    /**
     * 在模块加载阶段连接框架只读配置，不需要启动 Toki。
     * @param acquire 获取框架只读 SharedPreferences 的操作。
     * @param onConnectionChanged 连接状态诊断回调，主线程执行。
     * @return 首次读取是否成功；失败时调用方不得启用依赖配置的功能。
     * Callers: TokiModule.onModuleLoaded。
     */
    @Synchronized fun initHost(acquire: () -> SharedPreferences, onConnectionChanged: () -> Unit): Boolean {
        check(!initialized) { "配置已经初始化" }
        host = true
        initialized = true
        connectionChanged = onConnectionChanged
        return store.attachHost(acquire) { it.registerOnSharedPreferenceChangeListener(preferenceListener) }
    }

    /** 宿主安装计划一次确定当前会话可应用的配置；管理端及原始请求保持完整。 */
    fun configureHostSession(transform: (ConfigSnapshot) -> ConfigSnapshot) {
        synchronized(this) {
            check(host && initialized) { "仅已连接框架配置的宿主可设置会话计划" }
            check(hostSessionTransform == null) { "宿主会话计划已经确定" }
            hostSessionTransform = transform
            effectiveCache = null
        }
        notifyChanged()
    }

    /** 安装计划明确改变其可应用范围时重新投影同一请求，通知在自身锁外执行。 */
    fun refreshHostSession() {
        synchronized(this) {
            check(host && hostSessionTransform != null) { "宿主会话计划尚未确定" }
            effectiveCache = null
        }
        notifyChanged()
    }

    /**
     * 将管理端设置发布到新绑定的框架服务。
     * @param acquire 框架管理端可写配置的获取操作。
     * @return Unit。
     * Callers: LSPosedStatusHelper.onServiceBind。
     */
    fun connectFramework(acquire: () -> SharedPreferences) = store.connect(acquire)

    /** 标记框架服务断开，不启动轮询。@return Unit。Callers: LSPosedStatusHelper.onServiceDied。 */
    fun disconnectFramework() = store.disconnect()

    /**
     * 在主线程通知连接状态和已验证的配置更新，保证界面及宿主视图的线程约束。
     * @return Unit。
     * Callers: ConfigStore。
     */
    private fun notifyChanged() {
        // 新订阅不接收安装前已排队的历史事件；取消订阅会阻止未执行的通知。
        val subscriptions = stateListeners.toList()
        val validListeners = listeners.toList()
        val publication = captureState()
        dispatch {
            connectionChanged?.invoke()
            subscriptions.forEach { it.deliver(publication) }
            if (publication.snapshot != null && isReady) validListeners.forEach { if (it in listeners) it() }
        }
    }

    /** 订阅有效配置变更；关闭返回的订阅后不再通知。 */
    fun addListener(listener: () -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }

    /**
     * 订阅配置状态并立即安排当前状态；null 是明确失效通知，不代表所有开关关闭。
     * 主线程状态变化同步分发，保证随后布局/绘制前释放依赖配置的监听。
     */
    fun addStateListener(listener: (ConfigSnapshot?) -> Unit): AutoCloseable {
        val subscription = StateSubscription(listener)
        stateListeners.add(subscription)
        var created = false
        try {
            val publication = captureState()
            dispatch { subscription.deliver(publication) }
            created = true
            return AutoCloseable {
                subscription.active = false
                stateListeners.remove(subscription)
            }
        } finally {
            // 主线程首次应用失败时，调用方还没拿到可拥有的句柄，创建必须原子撤销。
            if (!created) {
                subscription.active = false
                stateListeners.remove(subscription)
            }
        }
    }

    private fun dispatch(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    /** 状态与序号在存储同一个锁下取样，后台初始通知不能覆盖后来已应用的主线程事件。 */
    private fun captureState(): Publication = synchronized(store) {
        Publication(stateSequence.incrementAndGet(), store.snapshotOrNull()?.let(::effectiveSnapshot))
    }

    /** 取得已验证配置。@return 不可变快照。Callers: 业务 Hook、ConfigTransferSection、HookRuntime。 */
    fun snapshot(): ConfigSnapshot = effectiveSnapshot(store.snapshot())

    /** 管理端最新完整请求，诊断不将当前会话安装限制误报为用户开关。 */
    fun requestedSnapshot(): ConfigSnapshot = store.snapshot()

    /** null 明确表示原始请求目前未就绪。 */
    fun requestedSnapshotOrNull(): ConfigSnapshot? = store.snapshotOrNull()

    @Synchronized private fun effectiveSnapshot(snapshot: ConfigSnapshot): ConfigSnapshot {
        val transform = hostSessionTransform ?: return snapshot
        effectiveCache?.let { if (it.first === snapshot) return it.second }
        return transform(snapshot).also { effectiveCache = snapshot to it }
    }

    /** 读取开关。@param key 配置键。@return 已验证布尔值。Callers: 业务 Hook。 */
    fun getBoolean(key: String): Boolean = snapshot().boolean(key, ConfigSchema.booleanDefault(key))

    /** 初始化后读取开关。@param context 上下文。@param key 配置键。@return 布尔值。Callers: 管理界面、refreshConfig。 */
    fun getBoolean(context: Context, key: String): Boolean { init(context); return getBoolean(key) }

    /** 读取字符串。@param key 配置键。@param defaultValue 未设置时的默认值。@return 配置值。Callers: 业务 Hook。 */
    fun getString(key: String, defaultValue: String? = null): String? = snapshot().string(key, defaultValue)

    /**
     * 初始化后读取字符串。
     * @param context 上下文。
     * @param key 配置键。
     * @param defaultValue 未设置时的默认值。
     * @return 配置值。
     * Callers: 管理界面、refreshConfig。
     */
    fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        init(context)
        return getString(key, defaultValue)
    }

    /** 保存开关。@param context 管理端上下文。@param key 配置键。@param value 值。@return Unit。Callers: DashboardScreen。 */
    fun putBoolean(context: Context, key: String, value: Boolean) { init(context); store.update(mapOf(key to value)) }

    /** 保存字符串。@param context 管理端上下文。@param key 配置键。@param value 值，null 删除。@return Unit。Callers: DashboardScreen。 */
    fun putString(context: Context, key: String, value: String?) { init(context); store.update(mapOf(key to value)) }

    /** 保存关联字段。@param context 管理端上下文。@param values 同批字段。@return Unit。Callers: DashboardScreen。 */
    fun putStrings(context: Context, values: Map<String, String?>) { init(context); store.update(values) }

    /**
     * 验证并全量导入，保存成功后通知功能配置页重新读取。
     * @param context 管理端上下文。
     * @param values 用户确认的配置集合。
     * @return Unit。
     * Callers: ConfigTransferSection。
     */
    fun replace(context: Context, values: Map<String, Any>) {
        init(context)
        importedRevisionState.value = store.update(values, replace = true)
    }
}
