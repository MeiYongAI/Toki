package io.github.meiyongai.toki.ui.screen

import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.provider.ConfigSchema
import org.junit.Assert.*
import org.junit.Test

/** 验证简明功能列表的状态语义与自动会话选择，不依赖宿主和设备。 */
class HookStatusModelTest {
    /** 配置通信失败必须显示准备失败，不能伪装成正常关闭。@return Unit。Callers: JUnit。 */
    @Test fun configurationFailureIsVisibleIndependentlyOfScan() {
        val current = HookProcessReport("com.zhiliaoapp.musically", 1, mapOf(
            "HostSymbols" to HookFeatureReport(state = "缓存已验证"),
            "ConfigClient" to HookFeatureReport(error = "connection unavailable")))
        assertTrue(current.preparationFailed)
        assertEquals(R.string.status_prepare_failed, current.notice)
        assertTrue(current.features.all { it.state == HookVisualState.PENDING })
    }

    /** 启动后首次收到配置但尚未注册功能时提示重新打开。@return Unit。Callers: JUnit。 */
    @Test fun lateConfigurationRequiresExplicitRestartNotice() {
        val current = HookProcessReport("com.zhiliaoapp.musically", 1, mapOf(
            "ConfigClient" to HookFeatureReport(state = "配置就绪，等待重新打开")))
        assertFalse(current.preparationFailed)
        assertEquals(R.string.status_prepared, current.notice)
    }
    /**
     * 验证正常关闭的功能没有问题摘要。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun disabledFeatureIsNotFailure() {
        val feature = status(HookFeatureReport(false, "已注册", 12, appliedEnabled = false))
        assertEquals(HookVisualState.DISABLED, feature.state)
        assertNull(feature.problem)
    }

    /**
     * 验证真实异常不会在配置关闭后从列表消失。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun disabledFeatureRetainsRealError() {
        val feature = status(HookFeatureReport(enabled = false, error = "java.lang.IllegalStateException"))
        assertEquals(HookVisualState.ISSUE, feature.state)
        assertEquals(R.string.status_problem_disabled, feature.problem)
    }

    /**
     * 验证正常必须同时满足开启、注册完成和具有拦截点。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun normalRequiresConfirmedReadiness() {
        assertEquals(HookVisualState.PENDING, status(HookFeatureReport(true, "已注册", 0)).state)
        assertEquals(HookVisualState.PENDING, status(HookFeatureReport(true, "注册中", 2)).state)
        val feature = status(HookFeatureReport(true, "已注册", 2, appliedEnabled = true))
        assertEquals(HookVisualState.NORMAL, feature.state)
        assertNull(feature.problem)
    }

    /**
     * 验证未报告数据和未知状态不被推断为正常或关闭。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun missingDataStaysPending() {
        assertEquals(HookVisualState.PENDING, status(null).state)
        assertEquals(HookVisualState.PENDING, status(HookFeatureReport(state = "已注册", registered = 2)).state)
        assertEquals(HookVisualState.PENDING, status(HookFeatureReport(true, "未知状态", 2)).state)
    }

    /**
     * 验证注册失败使用简单说明，不要求用户理解拦截点。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun failedRegistrationShowsPlainProblem() {
        val feature = status(HookFeatureReport(true, "注册失败，已撤销"))
        assertEquals(HookVisualState.ISSUE, feature.state)
        assertEquals(R.string.status_problem_unavailable, feature.problem)
    }

    /**
     * 验证运行异常优先于注册成功，问题摘要不含堆栈。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun callbackErrorTakesPrecedenceOverReadiness() {
        val feature = status(HookFeatureReport(true, "已注册", 4, error = "internal.Class\nat Internal.method()", errorStatus = "模块回调异常"))
        assertEquals(HookVisualState.ISSUE, feature.state)
        assertEquals(R.string.status_problem_runtime, feature.problem)
    }

    /**
     * 验证开启的倍速菜单异常合并到播放倍速行。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun speedMenuProblemBelongsToPlaybackRow() {
        val process = process(mapOf(
            "PlaybackSpeedHook" to HookFeatureReport(true, "已注册", 4, appliedEnabled = true),
            "SpeedOptions" to HookFeatureReport(true, "倍速菜单数据源未唯一解析；固定倍速独立注册")
        ))
        val speed = process.features.single { it.definition.id == "PlaybackSpeedHook" }
        assertEquals(HookVisualState.ISSUE, speed.state)
        assertEquals(R.string.status_problem_speed_menu, speed.problem)
        assertFalse(process.features.any { it.definition.id == "SpeedOptions" })
        assertEquals(HookVisualState.PENDING, process.features.first().state)
    }

    /**
     * 验证没有启用菜单扩展时，菜单不可用不影响固定倍速状态。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun disabledSpeedMenuDoesNotReportFalseProblem() {
        val process = process(mapOf(
            "PlaybackSpeedHook" to HookFeatureReport(true, "已注册", 4, appliedEnabled = true),
            "SpeedOptions" to HookFeatureReport(false, "倍速菜单数据源未唯一解析；固定倍速独立注册")
        ))
        assertEquals(HookVisualState.NORMAL, process.features.single { it.definition.id == "PlaybackSpeedHook" }.state)
    }

    /**
     * 验证已开启的菜单必须上报就绪，不能仅凭父功能注册成功判定正常。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun enabledSpeedMenuMustReportReady() {
        val reports = mapOf(
            "PlaybackSpeedHook" to HookFeatureReport(true, "已注册", 4, appliedEnabled = true),
            "SpeedOptions" to HookFeatureReport(enabled = true)
        )
        assertEquals(HookVisualState.PENDING, process(reports).features.single { it.definition.id == "PlaybackSpeedHook" }.state)
        val ready = reports + ("SpeedOptions" to HookFeatureReport(true, "菜单数据源已注册；调用次数计入播放倍速"))
        assertEquals(HookVisualState.NORMAL, process(ready).features.single { it.definition.id == "PlaybackSpeedHook" }.state)
    }

    /**
     * 验证页面仅保留四种普通用户状态。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun visibleStatesAreMinimal() {
        assertEquals(listOf(R.string.status_normal, R.string.status_issue, R.string.status_disabled, R.string.status_pending), HookVisualState.entries.map { it.label })
    }

    @Test fun requestedDisableDoesNotPretendCachedRuntimeIsAlreadyDisabled() {
        val feature = status(HookFeatureReport(enabled = false, state = "已注册", registered = 4,
            appliedEnabled = true, appliedRevision = 1L, restartRequired = true))
        assertEquals(HookVisualState.PENDING, feature.state)
        assertEquals(R.string.restart_tiktok, feature.problem)
    }

    @Test fun requestedEnableWithoutInstalledHooksShowsRestartAction() {
        val feature = status(HookFeatureReport(enabled = true, state = "本次会话未安装",
            appliedEnabled = false, restartRequired = true))
        assertEquals(HookVisualState.PENDING, feature.state)
        assertEquals(R.string.restart_tiktok, feature.problem)
    }

    @Test fun missingAppliedStateCannotBeInferredFromTheLatestRequest() {
        assertEquals(HookVisualState.PENDING, status(HookFeatureReport(true, "已注册", 12)).state)
        assertEquals(HookVisualState.PENDING, status(HookFeatureReport(false, "本次会话未安装")).state)
    }

    /**
     * 验证所有业务功能各出现一次，适配诊断不占用功能列表。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun catalogContainsOnlyUserFeaturesExactlyOnce() {
        val expected = ConfigSchema.featureSwitches.keys - "SpeedOptions"
        assertEquals(expected, HookStatusCatalog.features.map { it.id }.toSet())
        assertEquals(expected.size, HookStatusCatalog.features.size)
        assertEquals(18, process(emptyMap()).features.size)
    }

    /**
     * 验证正常适配不增加页面信息，准备和待重开仅显示必要提示。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun adaptationOnlyAddsActionableNotice() {
        assertNull(process(mapOf("HostSymbols" to HookFeatureReport(state = "缓存已验证"))).notice)
        assertEquals(R.string.status_preparing, process(mapOf("HostSymbols" to HookFeatureReport(state = "正在扫描"))).notice)
        assertEquals(R.string.status_prepared, process(mapOf("HostSymbols" to HookFeatureReport(state = "扫描完成，等待重新打开"))).notice)
    }

    /**
     * 验证适配异常保留可见提示，不将未知业务状态渲染为正常。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun preparationFailureRemainsVisible() {
        val process = process(mapOf("HostSymbols" to HookFeatureReport(error = "error", errorStatus = "扫描失败，未发布缓存")))
        assertTrue(process.preparationFailed)
        assertEquals(R.string.status_prepare_failed, process.notice)
        assertTrue(process.features.all { it.state == HookVisualState.PENDING })
    }

    /**
     * 验证只选最近主进程，辅助进程的新报告不会替代用户功能列表。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun latestMainProcessIsSelectedAutomatically() {
        val first = process(emptyMap())
        val second = first.copy(process = "com.ss.android.ugc.trill", updated = 2000)
        val auxiliary = first.copy(process = "com.zhiliaoapp.musically:push", updated = 3000)
        assertSame(second, currentHookSession(listOf(auxiliary, first, second)))
        assertSame(second, currentHookSession(listOf(second, first, auxiliary)))
    }

    /**
     * 验证没有主进程时显示无记录，而不是拼凑辅助进程报告。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun auxiliaryOnlyAndEmptyReportsHaveNoCurrentSession() {
        assertNull(currentHookSession(emptyList()))
        assertNull(currentHookSession(listOf(process(emptyMap()).copy(process = "com.zhiliaoapp.musically:push"))))
    }

    /**
     * 验证同一时间的主进程选择具有稳定结果。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun equalReportTimesHaveStableSelection() {
        val first = process(emptyMap())
        val second = first.copy(process = "com.ss.android.ugc.trill")
        assertSame(currentHookSession(listOf(first, second)), currentHookSession(listOf(second, first)))
    }

    /**
     * 创建信息流过滤的列表状态。
     * @param report 模拟报告，可为 null。
     * @return 待断言的派生状态。
     * Callers: 本测试类的单功能状态测试。
     */
    private fun status(report: HookFeatureReport?) =
        HookFeatureStatus(HookStatusCatalog.features.single { it.id == "FeedFilterHook" }, report)

    /**
     * 创建单一主进程的功能报告。
     * @param reports 按功能标识索引的测试数据。
     * @return 待断言的主进程报告。
     * Callers: 本测试类的会话与子功能测试。
     */
    private fun process(reports: Map<String, HookFeatureReport>) =
        HookProcessReport("com.zhiliaoapp.musically", 1000, reports)
}
