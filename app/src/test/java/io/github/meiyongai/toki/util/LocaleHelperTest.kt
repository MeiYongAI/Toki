package io.github.meiyongai.toki.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/** 验证语言目录、偏好隔离与资源上下文，不启动Toki或TikTok的业务入口。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LocaleHelperTest {
    /**
     * 核对57个正式语言标签与一个系统选项，排除宿主内部测试语言。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun languageCatalogMatchesTikTok4703() {
        val expected = setOf(
            "az", "id-ID", "ms-MY", "jv-ID", "ca", "ceb-PH", "cs-CZ", "da", "de-DE", "et",
            "en-GB", "en", "es", "es-419", "fil-PH", "fr", "fr-CA", "ga", "hr", "is", "it-IT",
            "sw", "lv", "lt", "hu-HU", "nl-NL", "nb", "uz", "pl-PL", "pt", "pt-BR", "ro-RO",
            "sq", "sk", "sl", "fi-FI", "sv-SE", "vi-VN", "tr-TR", "el-GR", "bg", "kk", "ru-RU",
            "uk-UA", "he-IL", "ur", "ar", "fa-IR", "hi-IN", "bn-IN", "th-TH", "my-MM", "km-KH",
            "ja-JP", "zh-Hant-TW", "zh-Hans", "ko-KR"
        )
        assertEquals(58, AppLanguage.entries.size)
        assertEquals(expected, AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.map { it.languageTag }.toSet())
        assertEquals(58, AppLanguage.entries.map { it.code }.toSet().size)
    }

    /**
     * 每个选项保存后可精确读回，且不覆盖同一文件中的其他偏好。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun savesEveryLanguageWithoutChangingOtherPreferences() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("launcher_hidden", true).apply()
        AppLanguage.entries.forEach { language ->
            LocaleHelper.setAppLanguage(context, language)
            assertEquals(language, LocaleHelper.getAppLanguage(context))
            assertTrue(preferences.getBoolean("launcher_hidden", false))
        }
    }

    /**
     * 未设置偏好时采用系统语言，中文标识对应简体中文。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun unsetAndChinesePreferencesAreUnambiguous() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromCode("zh"))
        assertEquals("zh-Hans", AppLanguage.fromCode("zh").languageTag)
    }

    /**
     * 未定义的持久化语言必须显式拒绝，不能静默更改用户选择。
     * @return Unit；无入参。
     * @throws IllegalArgumentException 用于验证非法配置报告。
     * Callers: JUnit。
     */
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownPreference() {
        AppLanguage.fromCode("unsupported-locale")
    }

    /**
     * 阿拉伯语资源配置采用从右到左排版，同时保留窗口与字体信息。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun arabicConfigurationPreservesDisplaySettings() {
        val context = RuntimeEnvironment.getApplication()
        val original = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.US))
            fontScale = 1.8f
            screenWidthDp = 411
            uiMode = Configuration.UI_MODE_NIGHT_YES
        }
        val defaultLocale = Locale.getDefault()
        val localized = LocaleHelper.wrapContext(context, AppLanguage.AR, original).resources.configuration
        assertEquals("ar", localized.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, localized.layoutDirection)
        assertEquals(1.8f, localized.fontScale)
        assertEquals(411, localized.screenWidthDp)
        assertEquals(Configuration.UI_MODE_NIGHT_YES, localized.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(Locale.US, original.locales[0])
        assertEquals(defaultLocale, Locale.getDefault())
    }

    /**
     * 从指定语言改回跟随系统时，使用实时系统语言而非上次的派生语言。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun followingSystemUsesCurrentSystemConfiguration() {
        val context = RuntimeEnvironment.getApplication()
        val system = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.JAPAN, Locale.US))
        }
        val english = LocaleHelper.wrapContext(context, AppLanguage.ENGLISH, system)
        val following = LocaleHelper.wrapContext(context, AppLanguage.SYSTEM, system)
        assertEquals("en", english.resources.configuration.locales[0].language)
        assertEquals(Locale.JAPAN, following.resources.configuration.locales[0])
        assertEquals(Locale.US, following.resources.configuration.locales[1])
        system.setLocales(LocaleList(Locale.FRANCE))
        val changed = LocaleHelper.wrapContext(context, AppLanguage.SYSTEM, system)
        assertEquals(Locale.FRANCE, changed.resources.configuration.locales[0])
    }

    /**
     * 主题上下文保留原Activity，供系统文件选择器与对话框查找所有者。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun contextWrapperRetainsActivityOwner() {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        val activity = controller.get()
        val localized = LocaleHelper.wrapContext(activity, AppLanguage.ENGLISH, activity.resources.configuration)
        assertTrue(localized is ContextWrapper)
        assertSame(activity, (localized as ContextWrapper).baseContext)
        controller.destroy()
    }

    /**
     * 旗帜字符只使用有效的两位地区，跨国地区使用地球符号。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun flagsRepresentRegionsWithoutInventingLatinAmericaCountry() {
        assertEquals("🌎", AppLanguage.ES_419.flag)
        for (language in AppLanguage.entries.filter { it.region.isNotEmpty() }) {
            assertTrue(language.region.matches(Regex("[A-Z]{2}")))
            val points = language.flag.codePoints().toArray()
            assertEquals(2, points.size)
            assertTrue(points.all { it in 0x1F1E6..0x1F1FF })
        }
    }
}
