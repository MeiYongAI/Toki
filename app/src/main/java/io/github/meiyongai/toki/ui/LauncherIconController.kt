package io.github.meiyongai.toki.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标可见性控制器。
 *
 * 桌面启动项由 Manifest 中的 [ALIAS_COMPONENT]（activity-alias）承载：
 * 禁用该别名组件即可让桌面隐藏模块图标，主活动 [io.github.meiyongai.toki.ui.MainActivity]
 * 通过独立的 MAIN + MODULE_SETTINGS 过滤器保持可发现、可启动，
 * 隐藏后可从 LSPosed 管理器中的“模块设置”重新进入本界面。
 *
 * 组件启用状态（PackageManager）即唯一事实源，读取实时返回，不经过
 * 配置中心缓存，避免隐藏后状态与真实组件状态脱节。
 */
object LauncherIconController {

    private const val TAG = "TokiLauncherIcon"

    /** 桌面启动别名组件名（与 Manifest 中 activity-alias 一致） */
    private const val ALIAS_CLASS = "io.github.meiyongai.toki.LauncherAlias"

    /**
     * 查询桌面图标当前是否处于隐藏状态。
     *
     * Args:
     *     context (Context): 模块自身上下文。
     *
     * Returns:
     *     Boolean: 别名组件被显式禁用时为 true。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 卡片初始状态读取。
     */
    fun isIconHidden(context: Context): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(aliasComponent())
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    /**
     * 设置桌面图标隐藏状态。
     *
     * 以 DONT_KILL_APP 方式切换别名组件启用状态，避免设置过程中断当前
     * 界面；桌面在收到组件变更广播后自行刷新（部分桌面会有短暂重载）。
     *
     * Args:
     *     context (Context): 模块自身上下文。
     *     hidden (Boolean): true 隐藏图标，false 恢复显示。
     *
     * Returns:
     *     Unit: 无返回值。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 「隐藏桌面图标」卡片开关切换。
     */
    fun setIconHidden(context: Context, hidden: Boolean) {
        val state =
            if (hidden) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        context.packageManager.setComponentEnabledSetting(
            aliasComponent(),
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * 构造桌面启动别名的组件标识。
     *
     * Returns:
     *     ComponentName: 别名组件。
     *
     * Callers:
     *     - `io.github.meiyongai.toki.ui.LauncherIconController.isIconHidden`: 状态查询。
     *     - `io.github.meiyongai.toki.ui.LauncherIconController.setIconHidden`: 状态切换。
     */
    private fun aliasComponent(): ComponentName =
        ComponentName("io.github.meiyongai.toki", ALIAS_CLASS)
}
