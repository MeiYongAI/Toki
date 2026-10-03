package io.github.meiyongai.toki.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * 为管理界面与宿主扫描窗口提供一致的 Material 3 配色，只读取系统资源。
 * @param context 当前界面上下文，用于读取系统动态色。
 * @param dark 是否使用深色主题。
 * @return Android 12 及以上使用动态配色，其他系统使用 Material 3 标准配色。
 * Callers: TokiAppTheme、HostScanDialog。
 */
internal fun tokiColorScheme(context: Context, dark: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
