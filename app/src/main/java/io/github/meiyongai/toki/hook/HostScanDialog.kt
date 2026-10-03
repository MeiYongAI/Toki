package io.github.meiyongai.toki.hook

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.ui.tokiColorScheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

/**
 * 使用 Toki 资源与独立生命周期的 Material 3 扫描窗口，尺寸不随扫描内容变化。
 * @param activity 当前前台宿主活动，只在可见期间持有。
 * @param moduleResources 框架提供的模块 APK 资源来源。
 * @param onResultDismiss 关闭完成或失败提示的回调。
 * Callers: HostScanController.render。
 */
internal class HostScanDialog(activity: Activity, moduleResources: ModuleResources, onResultDismiss: () -> Unit) :
    ComponentDialog(moduleResources.createActivityContext(activity), android.R.style.Theme_Material_Light_Dialog_NoActionBar) {
    private var status by mutableStateOf(HostScanStatus())
    private val composeView = ComposeView(context).apply {
        layoutDirection = context.resources.configuration.layoutDirection
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MaterialTheme(colorScheme = tokiColorScheme(LocalContext.current, isSystemInDarkTheme())) {
                ScanContent(status, onResultDismiss)
            }
        }
    }

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        // ComponentDialog 在 setContentView 中安装自身的 Lifecycle / SavedState 所有者。
        setContentView(composeView)
        checkNotNull(window).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.48f)
        }
    }

    /**
     * 普通字号使用 320×200dp，大字体适度增高；各扫描阶段保持同一尺寸，长说明内部滚动。
     * @return Unit。
     * Callers: HostScanController.render。
     */
    override fun show() {
        super.show()
        val configuration = context.resources.configuration
        val density = context.resources.displayMetrics.density
        val width = minOf(320, (configuration.screenWidthDp - 48).coerceAtLeast(1))
        val targetHeight = 200 + (80 * (configuration.fontScale - 1f).coerceIn(0f, 0.5f)).toInt()
        val height = minOf(targetHeight, (configuration.screenHeightDp - 48).coerceAtLeast(1))
        checkNotNull(window).setLayout((width * density).toInt(), (height * density).toInt())
    }

    /**
     * 发布真实扫描进度，不修改窗口尺寸或使用模拟进度。
     * @param state 扫描会话的不可变状态。
     * @return Unit。
     * Callers: HostScanController.render。
     */
    fun render(state: HostScanStatus) { status = state }
}

/**
 * 渲染简洁 Material 3 窗口；说明可滚动，真实进度或结果确认按钮固定在底部。
 * @param state 当前阶段和工作量。
 * @param onResultDismiss 完成或失败提示的关闭回调。
 * @return Unit。
 * Callers: HostScanDialog 的 ComposeView。
 */
@Composable
private fun ScanContent(state: HostScanStatus, onResultDismiss: () -> Unit) {
    val failed = state.phase == HostScanPhase.FAILED
    val complete = state.phase == HostScanPhase.READY
    val contentScroll = key(state.phase) { rememberScrollState() }
    val heading = when {
        failed -> R.string.host_scan_title_failed
        complete -> R.string.host_scan_title_complete
        state.phase == HostScanPhase.SAVING -> R.string.host_scan_title_saving
        else -> R.string.host_scan_title_running
    }
    val description = when {
        failed -> R.string.host_scan_failure_hint
        complete -> R.string.host_scan_restart_hint
        state.phase == HostScanPhase.SAVING -> R.string.host_scan_saving_hint
        else -> R.string.host_scan_keep_foreground
    }
    val locale = LocalContext.current.resources.configuration.locales[0]
    val percentFormat = remember(locale) {
        NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
            roundingMode = RoundingMode.DOWN
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
        Column(Modifier.padding(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(contentScroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(heading), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            if (complete || failed) {
                TextButton(onClick = onResultDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(if (complete) R.string.host_scan_done else R.string.common_close))
                }
            } else if (state.phase == HostScanPhase.SAVING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                // 准备工作量时保持空进度，避免循环动画看起来先填满、再退回零。
                val total = state.total.coerceAtLeast(0)
                val completed = state.completed.coerceIn(0, total)
                val progress = if (total > 0) completed.toFloat() / total else 0f
                val percent = if (total > 0) completed * 100L / total else 0L
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(progress = { progress },
                        modifier = Modifier.fillMaxWidth())
                    Text(percentFormat.format(BigDecimal.valueOf(percent, 2)),
                        modifier = Modifier.align(Alignment.End),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
