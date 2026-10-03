package io.github.meiyongai.toki.ui.screen

import io.github.meiyongai.toki.util.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 验证搜索使用输入时的语言，同时支持原生名称与语言标签。 */
class LanguageSearchTest {
    /**
     * 中文搜索能找到英语地区项，匹配规则不依赖当前已选的界面语言。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun searchesEnglishUsingTheInputLanguage() {
        val results = filterAppLanguages("英语", Locale.SIMPLIFIED_CHINESE, "跟随系统")
        assertEquals(listOf(AppLanguage.EN_GB, AppLanguage.ENGLISH), results)
    }

    /**
     * 原生语言名称与地区标签不受搜索语言限制，便于切换回熟悉的语言。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun searchesNativeNamesAndLanguageTags() {
        assertEquals(listOf(AppLanguage.JA_JP), filterAppLanguages("日本語", Locale.ENGLISH, "System"))
        assertEquals(listOf(AppLanguage.ZH_HANT_TW), filterAppLanguages("zh-Hant-TW", Locale.JAPANESE, "システム"))
        assertEquals(listOf(AppLanguage.SYSTEM), filterAppLanguages("跟随系统", Locale.SIMPLIFIED_CHINESE, "跟随系统"))
    }

    /**
     * 空查询显示完整目录，未知查询显示空列表而不改变语言选择。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun handlesEmptyAndUnmatchedQueries() {
        assertEquals(AppLanguage.entries.toList(), filterAppLanguages("  ", Locale.ENGLISH, "System"))
        assertTrue(filterAppLanguages("not-a-supported-language", Locale.ENGLISH, "System").isEmpty())
    }
}
