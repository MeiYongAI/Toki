package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test
import org.luckypray.dexkit.query.enums.StringMatchType

/** 使用依赖的真实查询对象核对 DEX 类型转换和结构条件，避免原生筛选漏候选。 */
class HostNativeQueryTest {
    /** 无参数、无返回。Callers: JUnit；数组、参数顺序和混淆占位符需保持语义。 */
    @Test fun signaturesPreserveParameterOrderArraysAndWildcardPositions() {
        val query = HostNativeIndex.signature("(I[[Ljava/lang/String;LX/*;)Z")
        val params = checkNotNull(query.paramsMatcher?.paramsMatcher)
        assertEquals(3, params.size)
        assertEquals("int", params[0]?.typeMatcher?.classNameMatcher?.value)
        assertEquals("java.lang.String[][]", params[1]?.typeMatcher?.classNameMatcher?.value)
        assertNull(params[2])
        assertEquals("boolean", query.returnTypeMatcher?.classNameMatcher?.value)
        assertThrows(IllegalArgumentException::class.java) { HostNativeIndex.signature("(junk)V") }
    }

    /** 无参数、无返回。Callers: JUnit；原生字符串使用完全匹配，角色关系仍由统一校验器判断。 */
    @Test fun exactStringsAreNotSubstringSearches() {
        val query = HostNativeIndex.methodQueries("SETTINGS\tmethod-v1\t()I\tstring:exact|name:read\tvalue").single()
        assertEquals(StringMatchType.Equals, query.usingStringsMatcher?.single()?.matchType)
        assertEquals("exact", query.usingStringsMatcher?.single()?.value)
        assertEquals("read", query.nameMatcher?.value)
    }

    /** 无参数、无返回。Callers: JUnit；添加方法名不能覆盖类方法数量约束。 */
    @Test fun allPublishedShapesHaveBothCountsAndNamedMethods() {
        val rules = checkNotNull(javaClass.getResourceAsStream("/toki-host-rules.tsv")).bufferedReader().use { it.readText() }
        val queries = HostNativeIndex.classQueries(rules)
        assertEquals(4, queries.size)
        queries.forEach { query ->
            val methods = checkNotNull(query.methodsMatcher)
            val count = checkNotNull(methods.rangeMatcher)
            assertEquals(count.min, count.max)
            assertTrue(count.min >= checkNotNull(methods.methodsMatcher).size)
            assertNotNull(query.fieldsMatcher?.rangeMatcher)
        }
    }
}
