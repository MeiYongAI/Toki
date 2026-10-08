package io.github.meiyongai.toki.hook

import android.view.View
import io.github.meiyongai.toki.model.LayoutGroup
import java.util.WeakHashMap

/** 使用 Android 的独立过渡透明度乘数，保留宿主 alpha 动画与可见性。 */
internal class LayoutOpacityGate {
    private class Entry(val group: LayoutGroup, var nativeAlpha: Float)
    private val entries = WeakHashMap<View, Entry>()
    private val factors = LayoutGroup.entries.associateWith { 1f }.toMutableMap()
    private val getter = View::class.java.getMethod("getTransitionAlpha")
    val setter = View::class.java.getMethod("setTransitionAlpha", Float::class.javaPrimitiveType)
    private var applying = false

    /** 登记或重新分配控件，嵌套控件不重复衰减。@param view 控件。@param group 分区或解除登记。@return Unit。Callers: LayoutCleanupHook.bind。 */
    @Synchronized fun bind(view: View, group: LayoutGroup?) {
        val previous = entries[view]
        if (previous?.group == group) { refresh(); return }
        if (previous != null) {
            entries.remove(view)
            write(view, previous.nativeAlpha)
        }
        if (group != null) entries[view] = Entry(group, getter.invoke(view) as Float)
        refresh()
    }

    /** 提交完整分区配置。@param values 分区乘数。@return Unit。Callers: LayoutCleanupHook 配置订阅。 */
    @Synchronized fun configure(values: Map<LayoutGroup, Float>) {
        require(values.keys == factors.keys && values.values.all { it.isFinite() && it in 0f..1f })
        factors.putAll(values)
        refresh()
    }

    /** 记录宿主过渡动画值并施加乘数。@param view 控件。@param requested 原生值。@return 生效值。Callers: LayoutCleanupHook 平台拦截、测试。 */
    @Synchronized fun alpha(view: View, requested: Float): Float {
        if (applying) return requested
        val entry = entries[view] ?: return requested
        entry.nativeAlpha = requested
        return requested * factor(view, entry.group)
    }

    /** 恢复宿主属性并释放引用。无参数。@return Unit。Callers: LayoutCleanupHook 撤销回调。 */
    @Synchronized fun close() {
        entries.forEach { (view, entry) -> write(view, entry.nativeAlpha) }
        entries.clear()
    }

    /** 判断是否已有同分区祖先承担乘数。@param view 控件。@param group 分区。@return 本层乘数。Callers: alpha、refresh。 */
    private fun factor(view: View, group: LayoutGroup): Float {
        var parent = view.parent
        while (parent is View) {
            if (entries[parent]?.group == group) return 1f
            parent = parent.parent
        }
        return factors.getValue(group)
    }

    /** 更新配置和控件层级的乘数，不改宿主 alpha。无参数。@return Unit。Callers: bind、configure。 */
    private fun refresh() {
        entries.forEach { (view, entry) -> write(view, entry.nativeAlpha * factor(view, entry.group)) }
    }

    /** 提交属性且避免自身写入成为宿主意图。@param view 控件。@param alpha 生效值。@return Unit。Callers: bind、close、refresh。 */
    private fun write(view: View, alpha: Float) {
        if (getter.invoke(view) as Float == alpha) return
        val previous = applying
        applying = true
        try { setter.invoke(view, alpha) } finally { applying = previous }
    }
}
