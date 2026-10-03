package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

/** 自动滚动读取范围和反射契约回归，不依赖宿主混淆名称。 */
class AutoScrollContractTest {
    class Settings {
        /** @param group 实验组。@param default 默认值。@param key 键。@param exposure 曝光。@return 默认值。Callers: 反射契约测试。 */
        fun renamed(group: Int, default: Int, key: String, exposure: Boolean): Int = default
    }
    class Search {
        companion object { @JvmField val state: Any = error("宿主尚未初始化") }
        /** @return 原生实验结果。无参数。Callers: 反射定位，不执行。 */
        fun renamed(): Boolean = false
    }
    class InvalidSearch {
        /** @return 错误返回类型。无参数。Callers: 反射契约测试。 */
        fun renamed(): Int = 0
    }
    class StaticSearch {
        companion object {
            /** @return 错误调用方式。无参数。Callers: 反射契约测试。 */
            @JvmStatic fun renamed(): Boolean = false
        }
    }
    /** 精确键匹配及关闭解锁时原样放行。无参数、无返回。Callers: JUnit。 */
    @Test fun onlyUnlocksRequestedExperiment() {
        assertTrue(AutoScrollHook.unlocks(true, "fyp_auto_scroll"))
        assertFalse(AutoScrollHook.unlocks(false, "fyp_auto_scroll"))
        for (key in listOf(null, "tablet_fyp_auto_scroll", "search_auto_scroll", "fyp_auto_scroll_extra", "")) {
            assertFalse(AutoScrollHook.unlocks(true, key))
        }
    }
    /** 方法改名不影响类型契约，也不触发宿主初始化。无参数、无返回。Callers: JUnit。 */
    @Test fun resolvesRenamedMethodsWithoutInitialization() {
        val contract = AutoScrollHook.Contract(Settings::class.java, "renamed", Search::class.java, "renamed")
        assertEquals(4, contract.reader.parameterCount)
        assertEquals(Boolean::class.javaPrimitiveType, contract.gate.returnType)
    }
    /** 不允许错误返回类型注册一半功能。无参数、无返回。Callers: JUnit。 */
    @Test fun rejectsWrongSearchReturnType() {
        assertThrows(IllegalStateException::class.java) {
            AutoScrollHook.Contract(Settings::class.java, "renamed", InvalidSearch::class.java, "renamed")
        }
    }
    /** 不允许静态入口混入实例契约。无参数、无返回。Callers: JUnit。 */
    @Test fun rejectsWrongInvocationKind() {
        assertThrows(IllegalStateException::class.java) {
            AutoScrollHook.Contract(Settings::class.java, "renamed", StaticSearch::class.java, "renamed")
        }
    }
}
