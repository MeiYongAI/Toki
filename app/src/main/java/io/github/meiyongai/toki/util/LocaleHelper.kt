package io.github.meiyongai.toki.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import io.github.meiyongai.toki.R
import io.github.meiyongai.toki.model.RegionPresets
import java.util.Locale

/**
 * TikTok 47.0.3 正式语言清单对应的界面语言；系统选项不计入 57 项。
 *
 * @property code 保存到模块界面偏好的稳定标识，不用于宿主配置。
 * @property displayName 语言的原生名称，保证切换后仍可识别。
 * @property languageTag Android 资源使用的 BCP-47 语言标签。
 * @property region 旗帜所代表的地区；跨国地区留空并显示地球符号。
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val languageTag: String,
    val region: String
) {
    SYSTEM("system", "System", "", ""),
    AZ("az", "Azərbaycan", "az", "AZ"),
    ID_ID("id-ID", "Bahasa Indonesia", "id-ID", "ID"),
    MS_MY("ms-MY", "Bahasa Melayu", "ms-MY", "MY"),
    JV_ID("jv-ID", "Basa Jawa", "jv-ID", "ID"),
    CA("ca", "Català", "ca", "ES"),
    CEB_PH("ceb-PH", "Cebuano", "ceb-PH", "PH"),
    CS_CZ("cs-CZ", "Čeština", "cs-CZ", "CZ"),
    DA("da", "Dansk", "da", "DK"),
    DE_DE("de-DE", "Deutsch", "de-DE", "DE"),
    ET("et", "Eesti", "et", "EE"),
    EN_GB("en-GB", "English (UK)", "en-GB", "GB"),
    ENGLISH("en", "English (US)", "en", "US"),
    ES("es", "Español", "es", "ES"),
    ES_419("es-419", "Español (Latinoamérica)", "es-419", ""),
    FIL_PH("fil-PH", "Filipino", "fil-PH", "PH"),
    FR("fr", "Français", "fr", "FR"),
    FR_CA("fr-CA", "Français (Canada)", "fr-CA", "CA"),
    GA("ga", "Gaeilge", "ga", "IE"),
    HR("hr", "Hrvatski", "hr", "HR"),
    IS("is", "Íslenska", "is", "IS"),
    IT_IT("it-IT", "Italiano", "it-IT", "IT"),
    SW("sw", "Kiswahili", "sw", "TZ"),
    LV("lv", "Latviešu", "lv", "LV"),
    LT("lt", "Lietuvių", "lt", "LT"),
    HU_HU("hu-HU", "Magyar", "hu-HU", "HU"),
    NL_NL("nl-NL", "Nederlands", "nl-NL", "NL"),
    NB("nb", "norsk (bokmål)", "nb", "NO"),
    UZ("uz", "Oʻzbek", "uz", "UZ"),
    PL_PL("pl-PL", "Polski", "pl-PL", "PL"),
    PT("pt", "Português", "pt", "PT"),
    PT_BR("pt-BR", "Português (Brasil)", "pt-BR", "BR"),
    RO_RO("ro-RO", "Română", "ro-RO", "RO"),
    SQ("sq", "Shqip", "sq", "AL"),
    SK("sk", "Slovenčina", "sk", "SK"),
    SL("sl", "Slovenščina", "sl", "SI"),
    FI_FI("fi-FI", "Suomi", "fi-FI", "FI"),
    SV_SE("sv-SE", "Svenska", "sv-SE", "SE"),
    VI_VN("vi-VN", "Tiếng Việt", "vi-VN", "VN"),
    TR_TR("tr-TR", "Türkçe", "tr-TR", "TR"),
    EL_GR("el-GR", "Ελληνικά", "el-GR", "GR"),
    BG("bg", "Български", "bg", "BG"),
    KK("kk", "Қазақша", "kk", "KZ"),
    RU_RU("ru-RU", "Русский", "ru-RU", "RU"),
    UK_UA("uk-UA", "Українська", "uk-UA", "UA"),
    HE_IL("he-IL", "עברית", "he-IL", "IL"),
    UR("ur", "اردو", "ur", "PK"),
    AR("ar", "العربية", "ar", "SA"),
    FA_IR("fa-IR", "فارسی", "fa-IR", "IR"),
    HI_IN("hi-IN", "हिंदी", "hi-IN", "IN"),
    BN_IN("bn-IN", "বাংলা", "bn-IN", "IN"),
    TH_TH("th-TH", "ภาษาไทย", "th-TH", "TH"),
    MY_MM("my-MM", "မြန်မာ", "my-MM", "MM"),
    KM_KH("km-KH", "ខ្មែរ", "km-KH", "KH"),
    JA_JP("ja-JP", "日本語", "ja-JP", "JP"),
    ZH_HANT_TW("zh-Hant-TW", "中文（繁體）", "zh-Hant-TW", "TW"),
    SIMPLIFIED_CHINESE("zh", "中文（简体）", "zh-Hans", "CN"),
    KO_KR("ko-KR", "한국어", "ko-KR", "KR");

    /**
     * 提供对应地区的 Unicode 旗帜，跨国区域使用地球符号。
     * @return 地区旗帜或跨国区域标记；无入参。
     * Callers: LanguageRow、LocaleHelperTest。
     */
    val flag: String
        get() = if (region.isEmpty()) "🌎" else RegionPresets.isoToFlagEmoji(region)

    companion object {
        /**
         * 读取已保存的语言标识，未设置时使用系统语言。
         * @param code 已保存的偏好值；null 表示尚未设置。
         * @return 与偏好对应的唯一语言项。
         * @throws IllegalArgumentException 非空偏好不是应用支持的语言标识。
         * Callers: LocaleHelper.getAppLanguage、LocaleHelperTest。
         */
        fun fromCode(code: String?): AppLanguage {
            if (code == null) return SYSTEM
            return requireNotNull(entries.singleOrNull { it.code.equals(code, ignoreCase = true) }) {
                "Unsupported interface language: $code"
            }
        }
    }
}

/** 模块界面语言的偏好存储与配置上下文，不修改进程默认语言或宿主功能配置。 */
object LocaleHelper {
    private const val PREF_NAME = "ui_preferences"
    private const val KEY_LANGUAGE = "app_language"

    /**
     * 读取界面语言偏好。
     * @param context 模块上下文。
     * @return 已保存的语言或尚未设置时的系统选项。
     * Callers: LocalizedTokiApp、LocaleHelperTest。
     */
    fun getAppLanguage(context: Context): AppLanguage =
        AppLanguage.fromCode(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, null))

    /**
     * 保存界面语言选择，保留同一偏好文件中的其他设置。
     * @param context 模块上下文。
     * @param language 用户选定的语言。
     * @return Unit。
     * Callers: LocalizedTokiApp 的语言选择回调、LocaleHelperTest。
     */
    fun setAppLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE, language.code).apply()
    }

    /**
     * 生成独立语言上下文并保留 Activity 的 ContextWrapper 链。
     *
     * 只修改派生配置的语言和排版方向；字体大小、深色模式及窗口配置不变。
     * 系统选项使用传入的系统配置，不读取上次选择的派生语言。
     *
     * @param context 原始 Activity 上下文，不能传入已经本地化的上下文。
     * @param language 当前界面语言。
     * @param systemConfiguration Compose 从系统接收的实时配置。
     * @return 保持 Activity 所有者可追溯的主题上下文。
     * Callers: LocalizedTokiApp、LocaleHelperTest。
     */
    fun wrapContext(
        context: Context,
        language: AppLanguage,
        systemConfiguration: Configuration
    ): Context {
        val localizedConfiguration = Configuration(systemConfiguration)
        if (language != AppLanguage.SYSTEM) {
            localizedConfiguration.setLocales(LocaleList(Locale.forLanguageTag(language.languageTag)))
        }
        return ContextThemeWrapper(context, R.style.Theme_Toki).apply {
            applyOverrideConfiguration(localizedConfiguration)
        }
    }
}
