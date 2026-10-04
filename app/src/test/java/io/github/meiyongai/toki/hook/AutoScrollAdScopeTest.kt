package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 菜单资格放行不能泄漏到广告投放、其他模型、其他线程或异常后的调用。 */
class AutoScrollAdScopeTest {
    /** 验证对象身份及线程隔离。无参数、无返回。Callers: JUnit。 */
    @Test fun onlyCurrentMenuModelOnCurrentThreadIsAllowed() {
        val scope = AutoScrollAdScope()
        val model = Any()
        assertFalse(scope.contains(model))
        val result = scope.checking(model) {
            assertTrue(scope.contains(model))
            assertFalse(scope.contains(Any()))
            assertFalse(scope.contains(null))
            val otherThread = java.util.concurrent.atomic.AtomicBoolean(true)
            Thread { otherThread.set(scope.contains(model)) }.apply { start(); join() }
            assertFalse(otherThread.get())
            "native menu"
        }
        assertEquals("native menu", result)
        assertFalse(scope.contains(model))
    }

    /** 嵌套异常恢复外层菜单，异常原样传播，最外层退出清除状态。无参数、无返回。Callers: JUnit。 */
    @Test fun nestedFailureRestoresOuterScopeAndClearsOnExit() {
        val scope = AutoScrollAdScope()
        val outer = Any()
        val inner = Any()
        val failure = IllegalStateException("native failure")
        assertSame(failure, assertThrows(IllegalStateException::class.java) {
            scope.checking(outer) {
                assertSame(failure, assertThrows(IllegalStateException::class.java) {
                    scope.checking(inner) {
                        assertTrue(scope.contains(inner))
                        assertFalse(scope.contains(outer))
                        throw failure
                    }
                })
                assertTrue(scope.contains(outer))
                scope.checking(null) { assertFalse(scope.contains(outer)) }
                assertTrue(scope.contains(outer))
                throw failure
            }
        })
        assertFalse(scope.contains(outer))
        assertFalse(scope.contains(inner))
    }
}
