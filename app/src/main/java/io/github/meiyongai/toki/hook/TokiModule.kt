package io.github.meiyongai.toki.hook

import android.app.Application
import android.app.Instrumentation
import android.util.Log
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.provider.ConfigSchema
import io.github.meiyongai.toki.provider.ConfigSnapshot
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** 按同一会话的安装契约组织配置、平台 Hook、宿主符号和诊断。 */
class TokiModule : XposedModule() {
    private var processName = ""
    private lateinit var plan: HostFeaturePlan
    private val installed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var applicationHookInstalled = false

    companion object {
        private const val TAG = "TokiModule"
        const val PKG_TIKTOK_GLOBAL = "com.zhiliaoapp.musically"
        const val PKG_TIKTOK_TRILL = "com.ss.android.ugc.trill"
        private val SUPPORTED_PACKAGES = setOf(PKG_TIKTOK_GLOBAL, PKG_TIKTOK_TRILL)
    }

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        HookRuntime.start(processName) { tag, message -> log(Log.INFO, tag, message) }
        val ready = ConfigClient.initHost({ getRemotePreferences(ConfigClient.FRAMEWORK_GROUP) }) {
            ConfigClient.syncError?.let { HookRuntime.failure("ConfigClient", it, "配置未就绪，功能暂停") }
        }
        plan = HostFeaturePlan(ConfigClient.requestedSnapshotOrNull())
        ConfigClient.configureHostSession(plan::apply)
        ConfigSchema.featureSwitches.keys.forEach { feature ->
            HookRuntime.state(feature, if (feature in plan.features || feature == "SpeedOptions" && "PlaybackSpeedHook" in plan.features)
                "等待注册" else "本次会话未安装")
        }
        HookRuntime.own("HostSession", ConfigClient.addStateListener { updateConfigurationStatus(it) })
        updateConfigurationStatus(if (ready) ConfigClient.snapshot() else null)
        log(Log.INFO, TAG, "模块加载成功，process=$processName ready=$ready features=${plan.features} symbols=${plan.symbols.size}")
    }

    /** 诊断使用请求配置，业务只使用会话有效配置；失效是显式状态。 */
    private fun updateConfigurationStatus(effective: ConfigSnapshot?) {
        val requested = ConfigClient.requestedSnapshotOrNull()
        if (effective == null || requested == null) return
        var restart = false
        for (feature in ConfigSchema.featureSwitches.keys) {
            val pending = plan.requiresRestart(feature, requested)
            restart = restart || pending
            HookRuntime.restartRequired(feature, pending)
            val applied = if (feature == "SpeedOptions") "PlaybackSpeedHook" in installed else feature in installed
            if (applied || !HostFeaturePlan.enabled(feature, effective)) {
                val revision = plan.appliedRevision(if (feature == "SpeedOptions") "PlaybackSpeedHook" else feature, requested)
                HookRuntime.appliedConfiguration(feature,
                    ConfigSnapshot(effective.configuration(), revision, effective.importRevision),
                    applied && HostFeaturePlan.enabled(feature, effective))
            }
        }
        HookRuntime.configurationReady(if (restart) "配置就绪，等待重新打开" else "配置已就绪")
    }

    /** 全关也保留明确的会话诊断，平台业务、资源和扫描各自按需要初始化。 */
    private fun hookApplication() {
        if (applicationHookInstalled) return
        val method = Instrumentation::class.java.getMethod("callApplicationOnCreate", Application::class.java)
        hook(method).setExceptionMode(XposedInterface.ExceptionMode.PASSTHROUGH).intercept { chain ->
            val app = chain.args[0] as Application
            HookRuntime.attach(app)
            if (plan.requiresScan && processName == app.packageName) {
                HostScanController.attach(app, ModuleResources(moduleApplicationInfo,
                    checkNotNull(TokiModule::class.java.classLoader)))
            }
            if (ConfigClient.isReady) refreshConfiguration(app)
            chain.proceed()
        }
        applicationHookInstalled = true
    }

    private fun refreshConfiguration(app: Application) {
        val attempted = installed.toList()
        for (feature in attempted) HookRuntime.configure(feature) {
            when (feature) {
                "SimHook" -> SimHook.refreshConfig(app)
                "LocaleHook" -> { LocaleHook.refreshConfig(app); LocaleHook.applyToApplication(app) }
                "TimeZoneHook" -> { TimeZoneHook.refreshConfig(app); TimeZoneHook.applyToApplication(app) }
                "GpsHook" -> GpsHook.refreshConfig(app)
                "PlaybackSpeedHook" -> PlaybackSpeedHook.refreshConfig(app)
                "CommentTranslateHook" -> CommentTranslateHook.refreshConfig(app)
                "CommentCopyHook" -> CommentCopyHook.refreshConfig(app)
                "AuthorLocationHook" -> AuthorLocationHook.refreshConfig(app)
                "ImmersiveFullScreenHook" -> ImmersiveFullScreenHook.refreshConfig(app)
                "FeedFilterHook" -> FeedFilterHook.refreshConfig(app)
                "DownloadHook" -> DownloadHook.refreshConfig(app)
                "MusicUnlockHook" -> MusicUnlockHook.refreshConfig(app)
                "StatusBarHook" -> StatusBarHook.refreshConfig(app)
                "VideoDurationAlertHook" -> VideoDurationAlertHook.refreshConfig(app)
            }
        }
        for (feature in attempted - installed) plan.reject(feature)
        ConfigClient.refreshHostSession()
        updateConfigurationStatus(ConfigClient.snapshot())
    }

    private fun install(feature: String, loader: ClassLoader? = null) {
        HookRuntime.install(feature) {
            when (feature) {
                "SimHook" -> SimHook.init(this)
                "LocaleHook" -> LocaleHook.init(this)
                "TimeZoneHook" -> TimeZoneHook.init(this)
                "GpsHook" -> GpsHook.init(this)
                else -> installHost(feature, checkNotNull(loader))
            }
            installed.add(feature)
            HookRuntime.onDispose(feature) { installed.remove(feature) }
        }
        if (feature !in installed) {
            plan.reject(feature)
            ConfigClient.refreshHostSession()
        }
    }

    private fun installHost(feature: String, loader: ClassLoader) {
        when (feature) {
            "FeedFilterHook" -> FeedFilterHook.init(this, loader)
            "PlaybackSpeedHook" -> PlaybackSpeedHook.init(this, loader, ConfigClient.snapshot())
            "CommentTranslateHook" -> CommentTranslateHook.init(this, loader)
            "VideoTranslateHook" -> VideoTranslateHook.init(this, loader)
            "BackgroundAudioHook" -> BackgroundAudioHook.init(this, loader)
            "CommentCopyHook" -> CommentCopyHook.init(this, loader)
            "AuthorLocationHook" -> AuthorLocationHook.init(this, loader)
            "ProgressBarHook" -> ProgressBarHook.init(this, loader)
            "AutoCleanModeHook" -> AutoCleanModeHook.init(this, loader)
            "LayoutCleanupHook" -> LayoutCleanupHook.init(this, loader)
            "ImmersiveFullScreenHook" -> ImmersiveFullScreenHook.init(this, loader)
            "AutoScrollHook" -> AutoScrollHook.init(this, loader)
            "DownloadHook" -> DownloadHook.init(this, loader)
            "MusicUnlockHook" -> MusicUnlockHook.init(this, loader)
            "StatusBarHook" -> StatusBarHook.init(this, loader)
            "VideoDurationAlertHook" -> VideoDurationAlertHook.init(this, loader)
            else -> error("没有功能安装契约：$feature")
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName !in SUPPORTED_PACKAGES || !param.isFirstPackage) return
        hookApplication()
        plan.platformFeatures.forEach { install(it) }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName !in SUPPORTED_PACKAGES || !param.isFirstPackage) return
        // API 28 不分发 onPackageLoaded；ready 边界依然只安装一次。
        if (!applicationHookInstalled) {
            hookApplication()
            plan.platformFeatures.forEach { install(it) }
        }
        if (plan.directHostFeatures.isNotEmpty()) {
            val loader = param.classLoader
            plan.directHostFeatures.forEach { install(it, loader) }
        }
        if (!plan.requiresScan) {
            if (ConfigClient.isReady) updateConfigurationStatus(ConfigClient.snapshot())
            return
        }
        if (!HostSymbols.initialize(param.applicationInfo, processName == param.packageName, plan.symbols)) return
        // 清屏依赖进度条的安装成功，不能留下半套状态机。
        val ordered = plan.adaptedFeatures.sortedBy { if (it == "ProgressBarHook") 0 else 1 }
        for (feature in ordered) {
            if (feature == "AutoCleanModeHook" && "ProgressBarHook" !in installed) {
                HookRuntime.failure(feature, IllegalStateException("清屏所需的进度条功能未注册"), "注册失败，已撤销")
            } else install(feature, param.classLoader)
        }
        if (ConfigClient.isReady) updateConfigurationStatus(ConfigClient.snapshot())
    }
}
