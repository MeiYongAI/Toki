package io.github.meiyongai.toki.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import io.github.meiyongai.toki.util.AppLanguage
import io.github.meiyongai.toki.util.LocaleHelper
import java.util.Locale

/**
 * 界面语言的单一状态入口。
 * @property language 当前选项；系统语言变化不改变此选择。
 * @property systemLocale 未经过界面语言覆盖的当前系统首选语言。
 * @property select 保存并更新语言的回调，不触发 Activity 重建。
 */
internal data class AppLanguageSettings(
    val language: AppLanguage,
    val systemLocale: Locale,
    val select: (AppLanguage) -> Unit
)

internal val LocalAppLanguage = staticCompositionLocalOf<AppLanguageSettings> {
    error("AppLanguageSettings must be provided by LocalizedTokiApp")
}

/**
 * 在同一 Compose 界面树内更新资源上下文、系统配置和文字方向。
 *
 * 语言变化不替换界面树的身份，因此导航、滚动位置和编辑状态保持不变。
 * 不修改 Locale 默认值，不影响 TikTok 的语言伪装设置。
 *
 * @param content 使用本地化资源的界面内容。
 * @return Unit。
 * Callers: MainActivity.onCreate。
 */
@Composable
internal fun LocalizedTokiApp(content: @Composable () -> Unit) {
    val activityContext = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    var language by remember { mutableStateOf(LocaleHelper.getAppLanguage(activityContext)) }
    val localizedContext = remember(activityContext, systemConfiguration, language) {
        LocaleHelper.wrapContext(activityContext, language, systemConfiguration)
    }
    val configuration = localizedContext.resources.configuration
    val direction = if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    val settings = remember(activityContext, systemConfiguration, language) {
        AppLanguageSettings(language, systemConfiguration.locales[0]) { selected ->
            if (selected != language) {
                LocaleHelper.setAppLanguage(activityContext, selected)
                language = selected
            }
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        LocalLayoutDirection provides direction,
        LocalAppLanguage provides settings,
        content = content
    )
}
