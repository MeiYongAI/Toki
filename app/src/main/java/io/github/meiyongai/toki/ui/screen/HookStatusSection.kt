package io.github.meiyongai.toki.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.annotation.StringRes
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.provider.HookDiagnostics
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.ui.component.getGroupedShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.DateFormat
import java.util.Date

/**
 * 页面可见时读取功能报告，进入后台暂停，不修改宿主和配置。
 * @return Unit；无入参。
 * Callers: MainAppScreen 的功能状态监控次级页面。
 */
@Composable
internal fun HookStatusSection() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var sessions by remember { mutableStateOf<List<HookProcessReport>?>(null) }
    var communicationError by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                try {
                    sessions = readHookSessions(HookDiagnostics.request(context))
                    communicationError = if (ConfigClient.syncError == null) null else R.string.status_update_failed
                } catch (error: SecurityException) {
                    Log.e("TokiHookStatus", "诊断快照读取失败", error)
                    communicationError = if (currentHookSession(sessions.orEmpty()) == null) {
                        R.string.status_read_failed
                    } else {
                        R.string.status_update_failed
                    }
                }
                delay(2000)
            }
        }
    }
    HookStatusContent(sessions, communicationError)
}

/**
 * 以单一列表展示功能与状态，只在准备、通信失败或无记录时补充提示。
 * @param sessions 最近报告；null 表示首次读取尚未结束。
 * @param communicationError 通信失败提示的资源标识；不将缓存结果解释为实时状态。
 * @return Unit。
 * Callers: HookStatusSection。
 */
@Composable
internal fun HookStatusContent(sessions: List<HookProcessReport>?, @StringRes communicationError: Int?) {
    val strings = LocalContext.current.resources
    val current = currentHookSession(sessions.orEmpty())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (communicationError != null) item(key = "communication-error") {
            StatusNotice(strings.getString(communicationError), error = true)
        }
        if (current == null) {
            item(key = "empty") {
                if (sessions == null && communicationError == null) {
                    Row(
                        Modifier.padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(strings.getString(R.string.status_reading), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (communicationError == null) {
                    StatusNotice(strings.getString(R.string.status_empty))
                }
            }
        } else {
            current.notice?.let { message ->
                item(key = "preparation") { StatusNotice(strings.getString(message), error = current.preparationFailed) }
            }
            itemsIndexed(current.features, key = { _, feature -> feature.definition.id }) { index, feature ->
                FeatureStatusRow(feature, index, current.features.size)
            }
            item(key = "report-note") {
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, strings.configuration.locales[0]).format(Date(current.updated))
                Text(
                    strings.getString(R.string.status_report_note, time),
                    Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 显示不可展开的功能状态行，有问题时附一条简短说明。
 * 按文字实际宽度安排状态标签，宽度不足时整项移到名称下方，避免挤占名称或拆开状态词。
 * @param feature 功能名称、状态及问题摘要。
 * @param index 列表内位置。
 * @param count 功能总数，用于首尾圆角。
 * @return Unit。
 * Callers: HookStatusContent。
 */
@Composable
private fun FeatureStatusRow(feature: HookFeatureStatus, index: Int, count: Int) {
    val strings = LocalContext.current.resources
    val color = when (feature.state) {
        HookVisualState.NORMAL -> MaterialTheme.colorScheme.primary
        HookVisualState.ISSUE -> MaterialTheme.colorScheme.error
        HookVisualState.DISABLED, HookVisualState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (feature.state) {
        HookVisualState.NORMAL -> Icons.Outlined.CheckCircle
        HookVisualState.ISSUE -> Icons.Outlined.WarningAmber
        HookVisualState.DISABLED -> Icons.Outlined.RadioButtonUnchecked
        HookVisualState.PENDING -> Icons.Outlined.HourglassEmpty
    }
    Surface(
        shape = getGroupedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { }
                .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            BoxWithConstraints(Modifier.weight(1f)) {
                val title = strings.getString(feature.definition.title)
                val status = strings.getString(feature.state.label)
                val titleStyle = MaterialTheme.typography.bodyLarge
                val statusStyle = MaterialTheme.typography.labelLarge
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val inline = measurer.measure(title, titleStyle, softWrap = false).size.width +
                    measurer.measure(status, statusStyle, softWrap = false).size.width +
                    with(density) { 12.dp.roundToPx() } <= constraints.maxWidth
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = titleStyle)
                        feature.problem?.let {
                            Text(strings.getString(it), style = MaterialTheme.typography.bodySmall,
                                color = if (feature.state == HookVisualState.ISSUE) MaterialTheme.colorScheme.error else color)
                        }
                        if (!inline) Text(status, style = statusStyle, color = color)
                    }
                    if (inline) Text(status, style = statusStyle, color = color)
                }
            }
        }
    }
}

/**
 * 显示必要的使用提示，不展示原始异常和技术字段。
 * @param message 普通用户可理解的说明。
 * @param error 是否需要错误强调。
 * @return Unit。
 * Callers: HookStatusContent。
 */
@Composable
private fun StatusNotice(message: String, error: Boolean = false) {
    Text(
        message,
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
