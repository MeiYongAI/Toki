package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 验证复制正文、表情位置和线程上下文不影响其他评论或剪贴板操作。 */
class CommentCopyTextTest {
    /** 正在显示的译文保持 Unicode 和对应表情索引。@return Unit。Callers: JUnit。 */
    @Test fun translationUsesMatchingTextAndExtra() {
        val source = mutableListOf("translated-emoji-offset")
        val text = CommentCopyText.displayed("one", "one", true, "译文🙂\n第二行", source)!!
        source.clear()
        assertEquals("译文🙂\n第二行", text.text)
        assertEquals(listOf("translated-emoji-offset"), text.extra)
    }

    /** 切回原文及视图复用都不使用译文。@return Unit。Callers: JUnit。 */
    @Test fun originalAndRecycledViewsUseHostBody() {
        assertNull(CommentCopyText.displayed("one", "one", false, "译文", emptyList<Any>()))
        assertNull(CommentCopyText.displayed("one", "two", true, "译文", emptyList<Any>()))
        assertNull(CommentCopyText.displayed(null, null, true, "译文", null))
    }

    /** 不完整的译文数据明确报错。@return Unit。Callers: JUnit。 */
    @Test fun missingDisplayedTextIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            CommentCopyText.displayed("one", "one", true, null, null)
        }
    }

    /** 复制结束立即释放译文上下文。@return Unit。Callers: JUnit。 */
    @Test fun copyScopeDoesNotLeak() {
        val scope = CommentCopyScope()
        val text = CommentCopyText("译文", null)
        assertEquals(42, scope.within(text) { assertSame(text, scope.current); 42 })
        assertNull(scope.current)
    }

    /** 嵌套复制恢复外层目标，避免跨评论替换。@return Unit。Callers: JUnit。 */
    @Test fun nestedCopyRestoresOuterContext() {
        val scope = CommentCopyScope()
        val outer = CommentCopyText("outer", null)
        val inner = CommentCopyText("inner", null)
        scope.within(outer) {
            scope.within(inner) { assertSame(inner, scope.current) }
            assertSame(outer, scope.current)
            scope.within(null) { assertNull(scope.current) }
            assertSame(outer, scope.current)
        }
        assertNull(scope.current)
    }

    /** 异常原样传播，且不残留下一次复制的正文。@return Unit。Callers: JUnit。 */
    @Test fun failureClearsScopeAndPropagates() {
        val scope = CommentCopyScope()
        val failure = IllegalArgumentException("test")
        val actual = assertThrows(IllegalArgumentException::class.java) {
            scope.within(CommentCopyText("text", null)) { throw failure }
        }
        assertSame(failure, actual)
        assertNull(scope.current)
    }

    /** 其他线程的复制调用不继承当前正文。@return Unit。Callers: JUnit。 */
    @Test fun scopeIsThreadLocal() {
        val scope = CommentCopyScope()
        val observed = java.util.concurrent.atomic.AtomicReference<CommentCopyText?>()
        scope.within(CommentCopyText("text", null)) {
            val worker = Thread { observed.set(scope.current) }
            worker.start()
            worker.join()
            assertNull(observed.get())
        }
    }
}
