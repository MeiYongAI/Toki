package io.github.meiyongai.toki.hook

import android.view.View
import java.util.WeakHashMap

/** 仅管理完整控件容器的可见性，透明度和动画始终由宿主管理。 */
internal class CleanViewGate {
    /** 一个页面区域的显示所有权；多个区域可以共用导航容器。 */
    class Owner(@Volatile var clean: Boolean = false, val hiddenVisibility: Int = View.INVISIBLE)
    private class Entry(var visibility: Int) {
        val owners = mutableSetOf<Owner>()
        val clean: Boolean get() = owners.any { it.clean }
        val effectiveVisibility: Int get() = when {
            owners.any { it.clean && it.hiddenVisibility == View.GONE } -> View.GONE
            clean && visibility == View.VISIBLE -> View.INVISIBLE
            else -> visibility
        }
    }
    private val entries = WeakHashMap<View, Entry>()
    private var applying = false

    /**
     * 将区域当前的完整容器集合提交到所有权表，移出区域的容器恢复宿主状态。
     * @param owner 区域所有者。
     * @param targets 已由宿主组件或内容路径确认的容器。
     * @return Unit。
     * Callers: CleanSceneBinding.updateTargets、AutoCleanModeHook 的原生容器注册回调、测试。
     */
    @Synchronized fun replace(owner: Owner, targets: Set<View>) {
        for (view in targets) bind(owner, view)
        for ((view, entry) in entries.entries.toList()) {
            if (view !in targets && entry.owners.remove(owner)) {
                apply(view, entry)
                if (entry.owners.isEmpty()) entries.remove(view)
            }
        }
    }

    /** 登记完整容器。@param owner 所有者。@param view 容器。@return Unit。Callers: replace、AutoCleanModeHook。 */
    @Synchronized fun bind(owner: Owner, view: View) {
        entries.getOrPut(view) { Entry(view.visibility) }.owners.add(owner)
    }

    /** 解除复用视图的单个所有者。@param owner 所有者。@param view 视图。@return Unit。Callers: LayoutCleanupHook.bind。 */
    @Synchronized fun unbind(owner: Owner, view: View) {
        val entry = entries[view] ?: return
        entry.owners.remove(owner)
        apply(view, entry)
        if (entry.owners.isEmpty()) entries.remove(view)
    }

    /**
     * 记录宿主显示意图并计算当前有效属性；模块自身提交不回写宿主意图。
     * @param view 目标视图。
     * @param requested 宿主请求的可见性。
     * @return 应传递给 Android 的值。
     * Callers: AutoCleanModeHook 的平台属性拦截、测试。
     */
    @Synchronized fun visibility(view: View, requested: Int): Int {
        val entry = entries[view] ?: return requested
        if (!applying) entry.visibility = requested
        return entry.effectiveVisibility
    }

    /** 所有区域决策完成后统一提交属性。@return Unit；无入参。Callers: AutoCleanModeHook.commit、测试。 */
    @Synchronized fun refresh() { for ((view, entry) in entries.entries.toList()) apply(view, entry) }

    /** 只提交刚创建的控件，不从后台预加载回调修改其它窗口视图。@param view 目标控件。@return Unit。Callers: LayoutCleanupHook.bind。 */
    @Synchronized fun refresh(view: View) { entries[view]?.let { apply(view, it) } }

    /** 释放一个区域。@param owner 被销毁的所有者。@return Unit。Callers: CleanSceneBinding.close、AutoCleanModeHook、测试。 */
    @Synchronized fun detach(owner: Owner) { replace(owner, emptySet()) }

    /**
     * 只写入改变的属性；finally 恢复调用标记，任何属性调用异常原样传播。
     * @param view 登记视图。
     * @param entry 宿主意图及所有权。
     * @return Unit。
     * Callers: replace、refresh。
     */
    private fun apply(view: View, entry: Entry) {
        val previous = applying
        applying = true
        try {
            if (view.visibility != entry.effectiveVisibility) view.visibility = entry.effectiveVisibility
        } finally {
            applying = previous
        }
    }
}
