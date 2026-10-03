package io.github.meiyongai.toki.util

import android.content.res.Configuration
import android.os.LocaleList
import io.github.meiyongai.toki.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** 使用编译后的Android资源验证上下文切换，不启动模块入口或连接实机。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalizedResourceContextTest {
    /**
     * 切换语言后使用对应资源，系统中文无需显式脚本子标签也能显示简体中文。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun changingLanguageSelectsCompiledResourcesAndReturnsToSystem() {
        val context = RuntimeEnvironment.getApplication()
        val system = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.SIMPLIFIED_CHINESE))
        }
        val english = LocaleHelper.wrapContext(context, AppLanguage.ENGLISH, system)
        val chinese = LocaleHelper.wrapContext(context, AppLanguage.SIMPLIFIED_CHINESE, system)
        val following = LocaleHelper.wrapContext(context, AppLanguage.SYSTEM, system)
        assertEquals("App language", english.getString(R.string.language_title))
        assertEquals("界面语言", chinese.getString(R.string.language_title))
        assertEquals("界面语言", following.getString(R.string.language_title))
    }

    /**
     * 每个可选语言均从其编译资源读取全部文案，不能由基础英语资源替代。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun everyLanguageLoadsItsOwnCompiledStrings() {
        val context = RuntimeEnvironment.getApplication()
        for (language in AppLanguage.entries.filter { it != AppLanguage.SYSTEM }) {
            val localized = LocaleHelper.wrapContext(context, language, context.resources.configuration)
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                isXIncludeAware = false
                isExpandEntityReferences = false
            }
            val file = languageResourceFile(language)
            assertTrue("Missing ${language.languageTag}", file.isFile)
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                val name = element.getAttribute("name")
                val resourceId = localized.resources.getIdentifier(name, "string", localized.packageName)
                assertTrue("Unknown resource: $name", resourceId != 0)
                val expected = decode(element.textContent)
                assertEquals("${language.languageTag}/$name", expected, localized.getString(resourceId))
                val arguments = Regex("%([1-9][0-9]*)\\$([sd])").findAll(expected)
                    .associate { match -> match.groupValues[1].toInt() to match.groupValues[2] }
                if (arguments.isNotEmpty()) {
                    val values = (1..arguments.keys.max()).map { position ->
                        when (requireNotNull(arguments[position])) {
                            "d" -> 2
                            "s" -> "sample"
                            else -> error("Unsupported formatting argument")
                        }
                    }.toTypedArray<Any>()
                    assertEquals("${language.languageTag}/$name: formatted",
                        String.format(localized.resources.configuration.locales[0], expected, *values),
                        localized.getString(resourceId, *values))
                }
            }
        }
    }

    /**
     * 解码资源生成器采用的Android引号与转义，以比较原始文案。
     * @param value XML解析后仍保留Android转义的文本。
     * @return Android字符串解码结果。
     * Callers: everyLanguageLoadsItsOwnCompiledStrings。
     */
    private fun decode(value: String): String {
        require(value.startsWith('"') && value.endsWith('"'))
        return Regex("\\\\(.)").replace(value.substring(1, value.length - 1)) { match ->
            when (val character = match.groupValues[1]) {
                "n" -> "\n"
                "\\", "'", "\"" -> character
                else -> error("Unsupported resource escape: $character")
            }
        }
    }
}
