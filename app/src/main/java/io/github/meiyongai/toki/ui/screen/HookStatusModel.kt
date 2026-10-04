package io.github.meiyongai.toki.ui.screen

import androidx.annotation.StringRes
import io.github.meiyongai.toki.R

/**
 * 用户可见功能的固定标识和名称。
 * @param id 宿主报告使用的功能标识。
 * @param title 展示名称的字符串资源标识。
 * Callers: HookStatusCatalog、HookFeatureStatus。
 */
internal data class HookFeatureDefinition(val id: String, @param:StringRes val title: Int)

/** 仅列出用户功能；适配诊断用于判断状态，不作为单独功能展示。 */
internal object HookStatusCatalog {
    val features = listOf(
        HookFeatureDefinition("FeedFilterHook", R.string.status_feature_feed),
        HookFeatureDefinition("PlaybackSpeedHook", R.string.status_feature_speed),
        HookFeatureDefinition("ProgressBarHook", R.string.status_feature_progress),
        HookFeatureDefinition("AutoCleanModeHook", R.string.status_feature_clean),
        HookFeatureDefinition("LayoutCleanupHook", R.string.layout_title),
        HookFeatureDefinition("ImmersiveFullScreenHook", R.string.status_feature_immersive),
        HookFeatureDefinition("AutoScrollHook", R.string.status_feature_scroll),
        HookFeatureDefinition("VideoDurationAlertHook", R.string.status_feature_duration),
        HookFeatureDefinition("CommentTranslateHook", R.string.status_feature_comment_translate),
        HookFeatureDefinition("VideoTranslateHook", R.string.status_feature_video_translate),
        HookFeatureDefinition("CommentCopyHook", R.string.status_feature_comment_copy),
        HookFeatureDefinition("AuthorLocationHook", R.string.status_feature_author),
        HookFeatureDefinition("DownloadHook", R.string.status_feature_download),
        HookFeatureDefinition("MusicUnlockHook", R.string.status_feature_music),
        HookFeatureDefinition("SimHook", R.string.status_feature_sim),
        HookFeatureDefinition("LocaleHook", R.string.status_feature_language),
        HookFeatureDefinition("TimeZoneHook", R.string.status_feature_timezone),
        HookFeatureDefinition("GpsHook", R.string.status_feature_gps),
        HookFeatureDefinition("StatusBarHook", R.string.status_feature_bar)
    )
}

/**
 * 状态判断需要的不可变宿主报告，不复制页面不展示的方法明细。
 * @param enabled 宿主读取的开关；null 表示未报告。
 * @param state 原始注册或适配状态。
 * @param registered 已注册拦截点数量。
 * @param error 模块错误记录，保留用于判断而不直接显示堆栈。
 * @param errorStatus 异常发生阶段。
 * Callers: readHookSessions、HookFeatureStatus、HookStatusModelTest。
 */
internal data class HookFeatureReport(
    val enabled: Boolean? = null,
    val state: String? = null,
    val registered: Int = 0,
    val error: String? = null,
    val errorStatus: String? = null,
    val appliedEnabled: Boolean? = null,
    val appliedRevision: Long? = null,
    val restartRequired: Boolean = false,
) {
    val hasError = !error.isNullOrBlank() || !errorStatus.isNullOrBlank() || state == "注册失败，已撤销"
}

/** 正常仅表示已就绪且未报告异常，不据此承诺每次业务效果。 */
internal enum class HookVisualState(@param:StringRes val label: Int) {
    NORMAL(R.string.status_normal), ISSUE(R.string.status_issue), DISABLED(R.string.status_disabled), PENDING(R.string.status_pending)
}

/**
 * 将功能报告转换为普通用户可理解的单行状态。
 * @param definition 功能标识与名称。
 * @param report 最近功能报告；null 保持待确认。
 * @param speedMenu 倍速菜单子功能报告，仅播放倍速使用。
 * Callers: HookProcessReport.features、HookStatusModelTest。
 */
internal data class HookFeatureStatus(
    val definition: HookFeatureDefinition,
    val report: HookFeatureReport?,
    private val speedMenu: HookFeatureReport? = null
) {
    private val menuEnabled = definition.id == "PlaybackSpeedHook" && speedMenu?.enabled == true
    private val menuIssue = definition.id == "PlaybackSpeedHook" && (speedMenu?.hasError == true ||
        (menuEnabled && speedMenu?.state == "倍速菜单数据源未唯一解析；固定倍速独立注册"))
    val state = when {
        report?.hasError == true || menuIssue -> HookVisualState.ISSUE
        report?.restartRequired == true || speedMenu?.restartRequired == true -> HookVisualState.PENDING
        report?.enabled == null -> HookVisualState.PENDING
        report?.appliedEnabled == false -> HookVisualState.DISABLED
        menuEnabled && speedMenu?.state != "菜单数据源已注册；调用次数计入播放倍速" -> HookVisualState.PENDING
        report?.appliedEnabled == true && report.state == "已注册" && report.registered > 0 -> HookVisualState.NORMAL
        else -> HookVisualState.PENDING
    }
    @get:StringRes
    val problem: Int? = when {
        state == HookVisualState.PENDING && (report?.restartRequired == true || speedMenu?.restartRequired == true) -> R.string.restart_tiktok
        state != HookVisualState.ISSUE -> null
        report?.enabled == false -> R.string.status_problem_disabled
        report?.hasError == true -> if (report.state == "注册失败，已撤销") R.string.status_problem_unavailable else R.string.status_problem_runtime
        menuIssue -> R.string.status_problem_speed_menu
        else -> null
    }
}

/**
 * 一个进程最近提交的报告，只提取列表与提示所需信息。
 * @param process 完整进程名，用于区分 TikTok 主进程。
 * @param updated 报告时间，毫秒，不代表当前进程仍存活。
 * @param reports 按功能标识索引的报告。
 * Callers: readHookSessions、HookStatusContent、HookStatusModelTest。
 */
internal data class HookProcessReport(
    val process: String,
    val updated: Long,
    val reports: Map<String, HookFeatureReport>
) {
    val isMain = ':' !in process
    val features = HookStatusCatalog.features.map {
        HookFeatureStatus(it, reports[it.id], if (it.id == "PlaybackSpeedHook") reports["SpeedOptions"] else null)
    }
    val preparationFailed = reports["HostSymbols"]?.hasError == true || reports["ConfigClient"]?.hasError == true
    @get:StringRes
    val notice: Int? = when {
        preparationFailed -> R.string.status_prepare_failed
        reports["ConfigClient"]?.state == "配置就绪，等待重新打开" -> R.string.status_prepared
        reports["HostSymbols"]?.state in listOf("正在扫描", "等待主进程准备") -> R.string.status_preparing
        reports["HostSymbols"]?.state == "扫描完成，等待重新打开" -> R.string.status_prepared
        else -> null
    }
}

/**
 * 自动选择最近报告的 TikTok 主进程，不用辅助进程推断页面功能状态。
 * @param sessions 宿主查询返回的独立进程报告。
 * @return 最近的主进程报告；没有主进程时返回 null。
 * Callers: HookStatusContent、HookStatusModelTest。
 */
internal fun currentHookSession(sessions: List<HookProcessReport>): HookProcessReport? =
    sessions.filter { it.isMain }.maxWithOrNull(compareBy<HookProcessReport> { it.updated }.thenBy { it.process })
