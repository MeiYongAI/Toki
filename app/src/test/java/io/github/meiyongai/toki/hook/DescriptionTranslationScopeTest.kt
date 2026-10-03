package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

class DescriptionTranslationScopeTest {
    private val scope = DescriptionTranslationScope()
    private val video = Any()

    /** 同一正文放行不改变语言等原生拒绝条件。无参数，无返回。Callers: JUnit。 */
    @Test fun nativeEligibilityStillControlsResult() {
        for (nativeFlag in listOf(false, true)) {
            for (languageAllowed in listOf(false, true)) {
                val eligible = scope.checking(video) { (nativeFlag || scope.allows(video)) && languageAllowed }
                assertEquals(languageAllowed, eligible)
            }
        }
    }

    /** 正文检查外、其他对象和其他线程不能获得放行。无参数，无返回。Callers: JUnit。 */
    @Test fun unrelatedTranslationReadsAreUntouched() {
        assertFalse(scope.allows(video))
        scope.checking(video) {
            assertTrue(scope.allows(video))
            assertFalse(scope.allows(Any()))
            assertFalse(scope.allows(null))
            var otherThreadAllowed = true
            Thread { otherThreadAllowed = scope.allows(video) }.apply { start(); join() }
            assertFalse(otherThreadAllowed)
        }
        assertFalse(scope.allows(video))
    }

    /** 嵌套检查和宿主异常必须恢复原上下文。无参数，无返回。Callers: JUnit。 */
    @Test fun nestedFailureRestoresAndClearsContext() {
        val nested = Any()
        assertThrows(IllegalStateException::class.java) {
            scope.checking(video) {
                assertThrows(IllegalArgumentException::class.java) {
                    scope.checking(nested) {
                        assertTrue(scope.allows(nested))
                        assertFalse(scope.allows(video))
                        throw IllegalArgumentException("native nested failure")
                    }
                }
                assertTrue(scope.allows(video))
                assertFalse(scope.allows(nested))
                error("native failure")
            }
        }
        assertFalse(scope.allows(video))
        assertFalse(scope.allows(nested))
    }
}
