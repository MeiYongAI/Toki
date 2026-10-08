package io.github.meiyongai.toki.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.ui.component.CompactPreferenceSwitch

/**
 * 提供视频居中和全屏沉浸模式，关闭总开关使用原生布局。
 * @param shape 设置分组圆角。
 * @param enabled 是否扩展视频区域。
 * @param mode center 或 smart。
 * @param onEnabledChange 保存总开关。
 * @param onModeChange 保存显示方式。
 * @return Unit。
 * Callers: DashboardScreen。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoDisplayCard(shape: Shape, enabled: Boolean, mode: String,
    onEnabledChange: (Boolean) -> Unit, onModeChange: (String) -> Unit) {
    val strings = LocalContext.current.resources
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(title = strings.getString(R.string.feature_immersive),
                checked = enabled,
                onCheckedChange = onEnabledChange)
            AnimatedVisibility(enabled) {
                FlowRow(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((value, label) in listOf("center" to R.string.video_fit_center, "smart" to R.string.video_fit_smart)) {
                        FilterChip(selected = mode == value, onClick = { onModeChange(value) },
                            label = { Text(strings.getString(label)) })
                    }
                }
            }
        }
    }
}
