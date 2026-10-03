package io.github.meiyongai.toki.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.meiyongai.toki.ui.LauncherIconController
import io.github.meiyongai.toki.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.meiyongai.toki.ui.SponsorDialog
import io.github.meiyongai.toki.ui.component.PreferenceItem
import io.github.meiyongai.toki.ui.component.PreferenceSection
import io.github.meiyongai.toki.ui.component.SwitchPreferenceItem
import io.github.meiyongai.toki.ui.component.getGroupedShape
import io.github.meiyongai.toki.util.AppLanguage
import io.github.meiyongai.toki.util.LSPosedStatus
import io.github.meiyongai.toki.util.LSPosedStatusHelper
import io.github.meiyongai.toki.ui.LocalAppLanguage
import io.github.meiyongai.toki.util.TikTokInstallStatus
import io.github.meiyongai.toki.util.TikTokVersionHelper

/**
 * 模块管理端首页视图（HomeScreen）。
 *
 * 遵循 Material 3 分组自适应圆角设计体系，集中呈现：
 * 1. 模块激活状态头部容器；
 * 2. 目标应用 TikTok 当前安装状态与适配版本 Section；
 * 3. 功能配置文件导入、导出与官方社区快捷入口；
 * 4. 界面多语言切换、桌面图标隐藏开关及开发者赞助支持 Section。
 *
 * Args:
 *     scrollState (ScrollState): 外部传入的滚动状态实例，默认为 rememberScrollState()。
 *     onHookStatusClick (() -> Unit): 打开功能状态监控次级页面。
 *     onLanguageClick (() -> Unit): 打开界面语言次级页面。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.MainActivity`: 底部导航栏切换至首页时的内容渲染。
 */
@Composable
fun HomeScreen(
    scrollState: ScrollState = rememberScrollState(),
    onHookStatusClick: () -> Unit,
    onLanguageClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    val context = LocalContext.current
    val lsposedStatus by LSPosedStatusHelper.status.collectAsState()
    var tikTokStatus by remember { mutableStateOf(TikTokInstallStatus(isInstalled = false)) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val status = TikTokVersionHelper.getInstalledTikTokStatus(context)
            withContext(Dispatchers.Main) {
                tikTokStatus = status
            }
        }
    }
    val currentLanguage = LocalAppLanguage.current.language
    var isIconHidden by remember { mutableStateOf(LauncherIconController.isIconHidden(context)) }
    var showSponsorDialog by remember { mutableStateOf(false) }

    val githubUrl = "https://github.com/MeiYongAI/Toki"
    val telegramUrl = "https://t.me/toki_lsposed"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // === 1. LSPosed 激活状态卡片 ===
        LSPosedStatusCard(lsposedStatus = lsposedStatus)

        Spacer(modifier = Modifier.height(10.dp))

        // === 2. 宿主应用适配 Section ===
        HostStatusSection(tikTokStatus = tikTokStatus)
        PreferenceItem(
            title = strings.getString(R.string.page_hook_status),
            shape = getGroupedShape(0, 1),
            icon = Icons.Outlined.Code,
            onClick = onHookStatusClick
        )

        // === 3. 开源与社区 Section ===
        ConfigTransferSection()
        OpenSourceSection(
            githubUrl = githubUrl,
            telegramUrl = telegramUrl,
            onOpenUrl = { url -> openBrowserUrl(context, url) }
        )

        // === 4. 偏好设置 Section ===
        PreferencesSection(
            currentLanguage = currentLanguage,
            isIconHidden = isIconHidden,
            onLanguageClick = onLanguageClick,
            onIconHiddenChange = { state ->
                LauncherIconController.setIconHidden(context, state)
                isIconHidden = LauncherIconController.isIconHidden(context)
                val tip = if (state) strings.getString(R.string.home_icon_hidden) else strings.getString(R.string.home_icon_restored)
                Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
            },
            onSponsorClick = { showSponsorDialog = true }
        )

        Spacer(modifier = Modifier.height(20.dp))
    }

    // 赞助对话框
    if (showSponsorDialog) {
        SponsorDialog(onDismiss = { showSponsorDialog = false })
    }
}

/**
 * LSPosed 框架激活状态展示卡片。
 *
 * 采用精致紧凑的 Material 3 Surface 呈现：
 * - 激活状态：采用主题色 primaryContainer 与绿色系指示图标，展示「已激活」与详细运行环境文本；
 * - 未激活状态：采用 errorContainer 与警示图标，展示「未激活」与未绑定说明。
 *
 * Args:
 *     lsposedStatus (LSPosedStatus): 当前探测到的框架运行状态模型。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 首页顶部呈现模块运行状态。
 */
@Composable
private fun LSPosedStatusCard(lsposedStatus: LSPosedStatus) {
    val strings = LocalContext.current.resources
    val isActivated = lsposedStatus.isActivated

    val badgeColor = if (isActivated) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    val iconColor = if (isActivated) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    val statusTitle = if (isActivated) strings.getString(R.string.home_activated) else strings.getString(R.string.home_inactive)
    val statusDetails = if (isActivated) lsposedStatus.details else strings.getString(R.string.home_activation_missing)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = badgeColor,
                modifier = Modifier.size(52.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActivated) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = statusTitle,
                        tint = iconColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 宿主应用 TikTok 当前版本与支持清单 Section。
 *
 * Args:
 *     tikTokStatus (TikTokInstallStatus): 目标宿主安装状态模型。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 渲染宿主适配信息。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostStatusSection(tikTokStatus: TikTokInstallStatus) {
    val strings = LocalContext.current.resources
    PreferenceSection(title = strings.getString(R.string.home_host_section)) {
        // 第一项：宿主应用安装与版本
        PreferenceItem(
            title = strings.getString(R.string.home_host_current),
            summary = if (tikTokStatus.isInstalled) {
                tikTokStatus.packageName.orEmpty()
            } else {
                strings.getString(R.string.home_host_missing)
            },
            shape = getGroupedShape(0, 2),
            icon = if (tikTokStatus.isInstalled) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            iconTint = if (tikTokStatus.isInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            trailingContent = {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (tikTokStatus.isInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = if (tikTokStatus.isInstalled) "v${tikTokStatus.versionName ?: strings.getString(R.string.common_unknown)}" else strings.getString(R.string.home_host_not_installed),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (tikTokStatus.isInstalled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        )

        // 第二项：已适配版本清单展示
        Surface(
            shape = getGroupedShape(1, 2),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = strings.getString(R.string.home_supported_versions),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TikTokVersionHelper.SUPPORTED_VERSIONS.forEach { ver ->
                        val isMatched = "v${tikTokStatus.versionName}" == ver
                        AssistChip(
                            onClick = {},
                            label = { Text(text = ver) },
                            leadingIcon = if (isMatched) {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isMatched) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = if (isMatched) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 开源与社区 Section。
 *
 * Args:
 *     githubUrl (String): 官方 GitHub 项目地址。
 *     telegramUrl (String): 官方 Telegram 交流群地址。
 *     onOpenUrl ((String) -> Unit): 外部链接跳转处理回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 渲染社区链接模块。
 */
@Composable
private fun OpenSourceSection(
    githubUrl: String,
    telegramUrl: String,
    onOpenUrl: (String) -> Unit
) {
    val strings = LocalContext.current.resources
    PreferenceSection(title = strings.getString(R.string.home_community)) {
        PreferenceItem(
            title = strings.getString(R.string.home_github),
            summary = githubUrl,
            shape = getGroupedShape(0, 2),
            icon = ImageVector.vectorResource(R.drawable.ic_brand_github),
            onClick = { onOpenUrl(githubUrl) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        PreferenceItem(
            title = strings.getString(R.string.home_telegram),
            summary = telegramUrl,
            shape = getGroupedShape(1, 2),
            icon = ImageVector.vectorResource(R.drawable.ic_brand_telegram),
            onClick = { onOpenUrl(telegramUrl) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

/**
 * 模块偏好设置 Section。
 *
 * Args:
 *     currentLanguage (AppLanguage): 当前界面语言枚举。
 *     isIconHidden (Boolean): 桌面图标当前隐藏状态。
 *     onLanguageClick (() -> Unit): 界面语言设置项点击回调。
 *     onIconHiddenChange ((Boolean) -> Unit): 隐藏桌面图标开关切换回调。
 *     onSponsorClick (() -> Unit): 支持开发点击回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.HomeScreen`: 渲染偏好设置模块。
 */
@Composable
private fun PreferencesSection(
    currentLanguage: AppLanguage,
    isIconHidden: Boolean,
    onLanguageClick: () -> Unit,
    onIconHiddenChange: (Boolean) -> Unit,
    onSponsorClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    PreferenceSection(title = strings.getString(R.string.home_preferences)) {
        // 界面语言
        PreferenceItem(
            title = strings.getString(R.string.language_title),
            summary = if (currentLanguage == AppLanguage.SYSTEM) strings.getString(R.string.language_system) else currentLanguage.displayName,
            shape = getGroupedShape(0, 3),
            icon = Icons.Outlined.Language,
            onClick = onLanguageClick
        )

        // 隐藏桌面图标开关
        SwitchPreferenceItem(
            title = strings.getString(R.string.home_hide_icon),
            summary = strings.getString(R.string.home_hide_icon_summary),
            checked = isIconHidden,
            onCheckedChange = onIconHiddenChange,
            shape = getGroupedShape(1, 3),
            icon = Icons.Outlined.VisibilityOff
        )

        // 支持开发
        PreferenceItem(
            title = strings.getString(R.string.home_support),
            summary = strings.getString(R.string.home_support_summary),
            shape = getGroupedShape(2, 3),
            icon = Icons.Outlined.Favorite,
            onClick = onSponsorClick
        )
    }
}

/**
 * 启动系统外部浏览器或应用加载指定 URL。
 *
 * Args:
 *     context (Context): 系统上下文环境。
 *     url (String): 待访问的目标超链接。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.OpenSourceSection`: 打开 GitHub 与 Telegram 社区链接。
 */
private fun openBrowserUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
