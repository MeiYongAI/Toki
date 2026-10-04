package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 控制器路由应随真实页面生命周期变化，不把作者页操作转发给推荐页。 */
class AutoScrollPageStateTest {
    /** 前后台、分页切换和销毁都必须撤销失效控制器。无参数、无返回。Callers: JUnit。 */
    @Test fun followsPageVisibilityAndLifecycle() {
        val state = AutoScrollPageState()
        val feed = Any()
        val author = Any()
        state.attach(feed, true, true)
        assertSame(feed, state.current { true })
        state.update(feed, visible = false)
        state.attach(author, true, true)
        assertSame(author, state.current { true })
        state.update(author, resumed = false)
        assertNull(state.current { true })
        state.update(author, resumed = true)
        assertSame(author, state.current { true })
        state.remove(author)
        assertNull(state.current { true })
        state.update(feed, visible = true)
        assertSame(feed, state.current { true })
        state.clear()
        assertNull(state.current { true })
    }

    /** 菜单及窗口约束不能借用其他页面的控制器。无参数、无返回。Callers: JUnit。 */
    @Test fun selectsOnlyMatchingActiveController() {
        val state = AutoScrollPageState()
        val first = Any()
        val second = Any()
        state.attach(first, true, true)
        state.attach(second, true, true)
        assertSame(second, state.current { true })
        assertSame(first, state.current { it === first })
        state.update(first, resumed = false)
        assertNull(state.current { it === first })
        state.update(first, resumed = true)
        assertSame(first, state.current { true })
        state.update(first, visible = false)
        state.update(first, resumed = true)
        assertSame(second, state.current { true })
    }
}
