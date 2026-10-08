package io.github.meiyongai.toki.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.model.LayoutElement
import io.github.meiyongai.toki.model.LayoutGroup
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.ui.component.CompactPreferenceSwitch
import io.github.meiyongai.toki.ui.component.MorphingPreferenceContainer
import io.github.meiyongai.toki.ui.component.PreferenceSection
import io.github.meiyongai.toki.ui.component.getGroupedShape
import io.github.meiyongai.toki.ui.component.stableDialogHeight

/**
 * 提供三个独立分区入口和可滚动的控件开关，条目支持自然换行。
 * @return Unit；无参数。
 * Callers: DashboardScreen。
 */
@Composable
internal fun LayoutCleanupSection() {
    val context = LocalContext.current
    val strings = context.resources
    var group by remember { mutableStateOf<LayoutGroup?>(null) }
    var values by remember { mutableStateOf(LayoutElement.entries.associateWith { ConfigClient.getBoolean(context, it.key) }) }
    PreferenceSection(title = strings.getString(R.string.layout_title)) {
        LayoutGroup.entries.forEachIndexed { index, entry ->
            MorphingPreferenceContainer(shape = getGroupedShape(index, LayoutGroup.entries.size), onClick = { group = entry }) {
                Text(strings.getString(entry.title), Modifier.padding(16.dp))
            }
        }
    }
    group?.let { selected -> AlertDialog(
        onDismissRequest = { group = null },
        title = { Text(strings.getString(selected.title)) },
        text = {
            Column(Modifier.stableDialogHeight(420.dp)) {
                Text(strings.getString(R.string.layout_hint))
                Spacer(Modifier.height(12.dp))
                androidx.compose.runtime.key(group) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        LayoutOpacityControl(selected)
                        for (element in LayoutElement.entries.filter { it.group == selected }) {
                            CompactPreferenceSwitch(
                                title = strings.getString(element.title),
                                checked = values.getValue(element),
                                onCheckedChange = { enabled ->
                                    ConfigClient.putBoolean(context, element.key, enabled)
                                    values = values + (element to enabled)
                                },
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { group = null }) { Text(strings.getString(R.string.common_done)) } }
    ) }
}
