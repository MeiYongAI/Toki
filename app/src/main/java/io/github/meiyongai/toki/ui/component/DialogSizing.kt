package io.github.meiyongai.toki.ui.component

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 固定对话框内容区域高度，仅随可用屏幕空间调整，不随条目数或说明文字改变。
 * @param preferred 正常屏幕下的内容高度；标题和按钮由 Material 3 单独布局。
 * @return 带稳定高度约束的修饰符，调用方负责提供内部滚动容器。
 * Callers: DashboardScreen 的列表弹窗、SponsorDialog。
 */
@Composable
internal fun Modifier.stableDialogHeight(preferred: Dp): Modifier {
    val available = (LocalConfiguration.current.screenHeightDp.dp - 200.dp).coerceAtLeast(80.dp)
    return height(minOf(preferred, available))
}
