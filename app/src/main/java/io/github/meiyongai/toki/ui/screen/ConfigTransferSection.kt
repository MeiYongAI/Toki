package io.github.meiyongai.toki.ui.screen

import android.content.res.Resources
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.provider.ConfigArchive
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.ui.component.PreferenceItem
import io.github.meiyongai.toki.ui.component.PreferenceSection
import io.github.meiyongai.toki.ui.component.getGroupedShape
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException

/**
 * 首页功能配置文件管理；系统文件选择器授权文件读写，导入前明确确认全量覆盖。
 * @return Unit。
 * Callers: HomeScreen。
 */
@Composable
internal fun ConfigTransferSection() {
    val context = LocalContext.current
    val strings = context.resources
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Map<String, Any>?>(null) }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            performConfigurationIo(strings, onError = { message = it }, onFinished = { busy = false }) {
                withContext(Dispatchers.IO) {
                    ConfigClient.init(context)
                    val bytes = ConfigArchive.encode(ConfigClient.snapshot().configuration())
                    val output = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("文件提供器未返回输出流")
                    output.use { it.write(bytes) }
                }
                message = strings.getString(R.string.config_exported)
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            performConfigurationIo(strings, onError = { message = it }, onFinished = { busy = false }) {
                pending = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("文件提供器未返回输入流")
                    input.use(ConfigArchive::read)
                }
            }
        }
    }
    PreferenceSection(title = strings.getString(R.string.config_section)) {
        PreferenceItem(title = strings.getString(R.string.config_import), summary = strings.getString(R.string.config_import_summary), icon = Icons.Outlined.FileDownload,
            shape = getGroupedShape(0, 2), onClick = { if (!busy) importFile.launch(arrayOf("application/json", "text/plain")) })
        PreferenceItem(title = strings.getString(R.string.config_export), summary = strings.getString(R.string.config_export_summary), icon = Icons.Outlined.FileUpload,
            shape = getGroupedShape(1, 2), onClick = { if (!busy) exportFile.launch("Toki-config.json") })
    }
    val selected = pending
    if (selected != null) AlertDialog(
        onDismissRequest = { if (!busy) pending = null },
        title = { Text(strings.getString(R.string.config_import)) },
        text = { Text(strings.getString(R.string.config_import_confirm, selected.size)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    performConfigurationIo(strings, onError = { message = it }, onFinished = { busy = false; pending = null }) {
                        withContext(Dispatchers.IO) { ConfigClient.replace(context, selected) }
                        message = strings.getString(R.string.config_imported)
                    }
                }
            }) { Text(strings.getString(R.string.config_overwrite)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text(strings.getString(R.string.common_cancel)) } }
    )
    if (busy && selected == null) AlertDialog(
        onDismissRequest = {}, title = { Text(strings.getString(R.string.config_processing)) },
        text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {}
    )
    message?.let { result ->
        AlertDialog(onDismissRequest = { message = null }, title = { Text(strings.getString(R.string.config_section)) },
            text = { Column(Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())) {
                Text(result, style = MaterialTheme.typography.bodyMedium)
            } },
            confirmButton = { TextButton(onClick = { message = null }) { Text(strings.getString(R.string.common_confirm)) } })
    }
}

/**
 * 将可预期的文件、校验和提交失败转为可见提示，未知编程异常及协程取消继续传播。
 * @param strings 当前界面语言的资源集合。
 * @param onError 提示错误原因的界面回调。
 * @param onFinished 无论成功失败都解除操作中的界面状态。
 * @param operation 文件读写或配置提交操作。
 * @return Unit。
 * Callers: ConfigTransferSection 的三个操作入口。
 */
private suspend fun performConfigurationIo(
    strings: Resources,
    onError: (String) -> Unit,
    onFinished: () -> Unit,
    operation: suspend () -> Unit
) {
    try {
        operation()
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        val reason = when (error) {
            is IOException -> strings.getString(R.string.config_error_io)
            is SecurityException -> strings.getString(R.string.config_error_permission)
            is JSONException -> strings.getString(R.string.config_error_invalid)
            is IllegalArgumentException -> strings.getString(R.string.config_error_validation, error.message)
            is IllegalStateException -> strings.getString(R.string.config_error_commit)
            else -> throw error
        }
        Log.e("TokiConfigTransfer", "配置操作失败：${error.javaClass.name}")
        onError(reason)
    } finally {
        onFinished()
    }
}
