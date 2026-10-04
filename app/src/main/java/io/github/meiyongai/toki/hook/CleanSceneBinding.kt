package io.github.meiyongai.toki.hook

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import java.lang.ref.WeakReference

/**
 * 按内容容器的祖先路径管理整页装饰区域，在布局提交后、绘制前统一应用显示决策。
 * 不取消绘制，不等待播放器，不改变视频表面的生命周期。
 * @param root 页面根视图。
 * @param contentId 宿主用于挂载页面的内容 ID；按根视图查找，与 FragmentManager 解析规则一致。
 * null 表示仅监听页面绘制，不划分装饰区域。
 * @param gate 共享的视图所有权管理器。
 * @param owner 区域所有者。
 * @param preserve 页面级清屏之外仍由专用状态机管理的视图判定。
 * @param commit 更新全部页面的显示决策并提交属性。
 */
internal class CleanSceneBinding(
    root: View, private val contentId: Int?, private val gate: CleanViewGate,
    val owner: CleanViewGate.Owner, private val preserve: (View) -> Boolean = { false },
    private val commit: () -> Unit
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener, ViewTreeObserver.OnPreDrawListener {
    companion object {
        /**
         * 判断视图自身或其子树是否包含指定类型的宿主控件。
         *
         * TikTok 会用通用的延迟加载容器包裹进度条，页面清屏必须保留这个完整容器，
         * 否则即使进度条实例自身收到显示指令，父容器仍会阻止其绘制。
         *
         * @param root 待检查的根视图。
         * @param type 需要保留的宿主控件类型。
         * @return 根视图或其后代是否为指定类型。
         * Callers: AutoCleanModeHook 的页面保留规则、CleanSceneBindingTest。
         */
        internal fun containsInstance(root: View, type: Class<*>): Boolean {
            return containsView(root, type::isInstance)
        }

        /**
         * 按控件身份识别完整交互子树，包含独立挂载和延迟加载的容器。
         * @param root 待检查的子树。
         * @param matches 由原生控件契约提供的身份判断。
         * @return 自身或后代是否命中。
         * Callers: containsInstance、AutoCleanModeHook.init、测试。
         */
        internal fun containsView(root: View, matches: (View) -> Boolean): Boolean {
            if (matches(root)) return true
            val group = root as? ViewGroup ?: return false
            for (index in 0 until group.childCount) {
                if (containsView(group.getChildAt(index), matches)) return true
            }
            return false
        }
    }

    private val root = WeakReference(root)
    private var observer = WeakReference<ViewTreeObserver>(null)
    private var active = true
    private var closed = false

    init {
        contentId?.let {
            require(it != View.NO_ID && it != 0) { "清屏内容容器必须具有有效 ID" }
            checkNotNull(root.findViewById<View>(it)) { "清屏页面未包含指定内容容器" }
        }
        root.addOnAttachStateChangeListener(this)
        observe(root)
        updateTargets()
    }

    /**
     * 仅沿内容到根的路径登记旁支；动态容器增加或换父容器后重新建立同一规则。
     * @return Unit；无入参。
     * Callers: 初始化、onGlobalLayout、测试。
     */
    fun updateTargets() {
        if (!active || closed) return
        val boundary = root.get() ?: return
        val id = contentId ?: return
        // 每次按宿主当前树解析。旧实例被移除、替换或移往其它页面属于内容生命周期。
        var branch = boundary.findViewById<View>(id)
        if (branch == null) {
            gate.detach(owner)
            return
        }
        val targets = mutableSetOf<View>()
        while (branch !== boundary) {
            val parent = checkNotNull(branch.parent as? ViewGroup) { "清屏内容容器已脱离所属页面" }
            for (index in 0 until parent.childCount) {
                val sibling = parent.getChildAt(index)
                if (sibling !== branch && !preserve(sibling)) targets.add(sibling)
            }
            branch = parent
        }
        gate.replace(owner, targets)
    }

    /** 布局变化后更新区域，属性在随后的绘制提交中统一计算。@return Unit；无入参。Callers: Android 视图树。 */
    override fun onGlobalLayout() { updateTargets() }

    /** 绘制前读取真实选中页面，始终允许本次绘制。@return true；无入参。Callers: Android 视图树、测试。 */
    override fun onPreDraw(): Boolean { if (active && !closed) commit(); return true }

    /** 附加到窗口时使用当前视图树。@param view 页面根视图。@return Unit。Callers: Android。 */
    override fun onViewAttachedToWindow(view: View) {
        if (active && !closed) { observe(view); updateTargets() }
    }

    /** 离开窗口时移除窗口监听。@param view 页面根视图。@return Unit。Callers: Android。 */
    override fun onViewDetachedFromWindow(view: View) {
        unobserve(view)
        if (contentId != null) gate.detach(owner)
    }

    /** 配置失效时解除监听与区域修改；恢复时从当前根视图重新解析内容。 */
    fun setActive(value: Boolean) {
        if (closed || active == value) return
        active = value
        root.get()?.let { view ->
            if (value) {
                view.addOnAttachStateChangeListener(this)
                observe(view)
                updateTargets()
            } else {
                unobserve(view)
                view.removeOnAttachStateChangeListener(this)
            }
        }
        if (!value && contentId != null) gate.detach(owner)
    }

    /**
     * 注册布局和绘制监听；移除浮动视图树合并到窗口后可能继承的同一监听。
     * @param view 页面根视图。
     * @return Unit。
     * Callers: 初始化、onViewAttachedToWindow。
     */
    private fun observe(view: View) {
        unobserve(view)
        view.viewTreeObserver.also {
            it.addOnGlobalLayoutListener(this)
            it.addOnPreDrawListener(this)
            observer = WeakReference(it)
        }
    }

    /** 移除浮动及当前窗口监听。@param view 页面根视图。@return Unit。Callers: observe、onViewDetachedFromWindow、close。 */
    private fun unobserve(view: View) {
        for (tree in setOfNotNull(observer.get(), view.viewTreeObserver)) {
            if (tree.isAlive) {
                tree.removeOnGlobalLayoutListener(this)
                tree.removeOnPreDrawListener(this)
            }
        }
        observer.clear()
    }

    /** 释放页面区域及监听。@return Unit；无入参。Callers: AutoCleanModeHook 的页面销毁与视图重建、测试。 */
    fun close() {
        if (closed) return
        closed = true
        root.get()?.let { unobserve(it); it.removeOnAttachStateChangeListener(this) }
        gate.detach(owner)
    }
}
