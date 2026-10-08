package io.github.meiyongai.toki.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.model.LayoutGroup
import io.github.meiyongai.toki.provider.ConfigClient
import kotlin.math.roundToInt

/**
 * 分区滑块与百分比；松手保存，避免拖动时连续写入配置。
 * @param group 当前分区。
 * @return Unit。
 * Callers: LayoutCleanupSection。
 */
@Composable
internal fun LayoutOpacityControl(group: LayoutGroup) {
    val context = LocalContext.current
    var percent by remember(group) {
        mutableFloatStateOf(checkNotNull(ConfigClient.getString(context, group.opacityKey, "100")).toFloat())
    }
    Column {
        Text("${stringResource(R.string.layout_opacity)} · ${percent.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = percent, onValueChange = { percent = it }, valueRange = 0f..100f,
            onValueChangeFinished = { ConfigClient.putLayoutOpacity(context, group, percent.roundToInt()) })
    }
}
