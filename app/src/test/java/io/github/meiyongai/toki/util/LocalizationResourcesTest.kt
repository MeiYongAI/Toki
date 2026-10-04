package io.github.meiyongai.toki.util

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 保证界面中列出的每一种语言都附带完整资源，不允许遗漏由默认语言掩盖。 */
class LocalizationResourcesTest {
    /**
     * 所有57个语言目录均存在，资源键和格式参数与基准文件完全一致。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun everySelectableLanguageHasCompleteResources() {
        val root = File("src/main/res")
        val baseline = readStrings(File(root, "values/strings.xml"))
        assertEquals("Expected the complete UI catalog", 296, baseline.size)
        val languages = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }
        assertEquals(57, languages.size)
        for (language in languages) {
            val tag = language.languageTag
            val localized = readStrings(languageResourceFile(language))
            assertEquals("$tag: resource keys", baseline.keys, localized.keys)
            baseline.forEach { (key, source) ->
                val value = requireNotNull(localized[key])
                assertTrue("$tag/$key: empty translation", value.trim('"', ' ', '\n').isNotEmpty())
                assertEquals("$tag/$key: formatting parameters", parameters(source), parameters(value))
                assertFalse("$tag/$key: invalid Unicode", '\uFFFD' in value)
            }
        }
    }

    /**
     * 主界面、功能设置、状态页与操作提示共同使用翻译资源，而非只翻译导航。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun catalogCoversEveryUserFacingArea() {
        val baseline = readStrings(File("src/main/res/values/strings.xml"))
        listOf("nav_home", "language_title", "page_hook_status", "config_import", "sponsor_title",
            "host_scan_title_running", "host_scan_restart_hint", "host_scan_done").forEach {
            assertTrue("Missing area: $it", baseline.containsKey(it))
        }
        assertTrue("Formatting placeholders must be indexed", baseline.values.none {
            Regex("%(?![1-9][0-9]*\\$)[sd]").containsMatchIn(it)
        })
    }

    /**
     * 解析指定Android字符串XML并拒绝重名资源。
     * @param file 待检查的本地资源文件。
     * @return 按名称索引的原始文案，不执行外部实体解析。
     * Callers: 本测试类中的两个资源完整性测试。
     */
    private fun readStrings(file: File): Map<String, String> {
        assertTrue("Missing language resource: ${file.path}", file.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
        val result = linkedMapOf<String, String>()
        for (index in 0 until nodes.length) {
            val item = nodes.item(index) as Element
            val name = item.getAttribute("name")
            assertNull("Duplicate resource: ${file.path}/$name", result.put(name, item.textContent))
        }
        return result
    }

    /**
     * 提取所有位置参数，允许译文调整语序，但不允许遗漏或改变类型。
     * @param value 含Android格式参数的文案。
     * @return 包括重复项的排序后参数列表。
     * Callers: everySelectableLanguageHasCompleteResources。
     */
    private fun parameters(value: String): List<String> =
        Regex("%[1-9][0-9]*\\$[sd]").findAll(value).map { it.value }.sorted().toList()
}

/**
 * 定位指定语言的资源文件，使用 Android ResourcesImpl 实际查询的语言代码。
 * @param language 非系统选项的界面语言。
 * @return 模块测试工作目录下的字符串资源文件。
 * Callers: LocalizationResourcesTest、LocalizedResourceContextTest。
 */
internal fun languageResourceFile(language: AppLanguage): File {
    require(language != AppLanguage.SYSTEM)
    val parts = language.languageTag.split('-').toMutableList()
    parts[0] = when (parts[0]) {
        "id" -> "in"
        "he" -> "iw"
        else -> parts[0]
    }
    return File("src/main/res/values-b+${parts.joinToString("+")}/strings.xml")
}
