package io.github.meiyongai.toki.hook

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TabHost
import android.widget.TabWidget
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 绘制真实 Android 视图，覆盖先首绘后播放、动态子控件、离页和原生清屏。 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class CleanSceneBindingTest {
    private val gate = CleanViewGate()
    /** 创建容器。@return FrameLayout；无入参。Callers: 本类测试。 */
    private fun frame() = FrameLayout(RuntimeEnvironment.getApplication()).apply { id = View.generateViewId() }
    /** 创建与普通容器类型不同的进度条测试视图。@return ViewGroup；无入参。Callers: 本类测试。 */
    private fun seekBar() = object : FrameLayout(RuntimeEnvironment.getApplication()) { }
    /** 绘制完成布局的视图。@param root 根视图。@return 中心像素颜色。Callers: 本类测试。 */
    private fun draw(root: View): Int {
        root.measure(View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(20, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 20, 20)
        val pixels = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(pixels))
        return pixels.getPixel(10, 10)
    }

    /**
     * 重建 FragmentTabHost.onFinishInflate/LIZLLL 的同 ID 双容器结构，验证内容仍参与绘制。
     * XML 中的视频容器先于辅助 LinearLayout；FragmentManager 按 ID 找到前者，字段保存后者。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun tabHostAuxiliaryFieldMustNotSelectTheHiddenVideoBranch() {
        val context = RuntimeEnvironment.getApplication()
        val root = TabHost(context).apply { setBackgroundColor(Color.BLACK) }
        val contentId = View.generateViewId()
        val videoMask = frame().also(root::addView)
        val actualContent = frame().apply {
            id = contentId
            setBackgroundColor(Color.BLUE)
        }.also(videoMask::addView)
        val controls = frame().apply { setBackgroundColor(Color.RED) }.also(videoMask::addView)
        val auxiliary = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }.also(root::addView)
        val tabs = TabWidget(context).apply { id = android.R.id.tabs }.also(auxiliary::addView)
        auxiliary.addView(frame().apply { id = android.R.id.tabcontent }, LinearLayout.LayoutParams(0, 0))
        val declaredContent = frame().also { auxiliary.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
        declaredContent.id = contentId
        tabs.visibility = View.GONE
        assertNotSame(declaredContent, root.findViewById<View>(contentId))
        assertSame(actualContent, root.findViewById<View>(contentId))

        val binding = CleanSceneBinding(root, declaredContent.id, gate, CleanViewGate.Owner(true)) { gate.refresh() }
        binding.onPreDraw()
        assertEquals(Color.BLUE, draw(root))
        assertEquals(View.VISIBLE, videoMask.visibility)
        assertEquals(View.VISIBLE, actualContent.visibility)
        assertEquals(View.INVISIBLE, controls.visibility)
        binding.close()
    }

    /** 页面装饰收口不得接管进度条，进度条模式由专用 Hook 决定。@return Unit。Callers: JUnit。 */
    @Test fun preservedSeekBarRemainsOutsideSceneOwnership() {
        val root = frame()
        val content = frame().apply { setBackgroundColor(Color.BLUE) }.also(root::addView)
        val seekBar = seekBar().apply { setBackgroundColor(Color.RED) }
        val seekBarContainer = frame().also(root::addView)
        seekBarContainer.addView(seekBar)
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true),
            preserve = { CleanSceneBinding.containsInstance(it, seekBar.javaClass) }) { gate.refresh() }
        binding.onPreDraw()
        assertEquals(View.VISIBLE, seekBarContainer.visibility)
        assertEquals(1f, seekBarContainer.alpha)
        assertEquals(Color.RED, draw(root))
        binding.close()
    }

    /** 数字提示与进度条分开挂载时仍交由原生拖动流程管理，不强制显示数字。@return Unit。Callers: JUnit。 */
    @Test fun separateDurationContainerKeepsNativeDragVisibility() {
        val root = frame()
        val content = frame().also(root::addView)
        val controls = frame().also(root::addView)
        val duration = frame().apply { visibility = View.GONE }.also(root::addView)
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true),
            preserve = { CleanSceneBinding.containsView(it) { candidate -> candidate === duration } }) { gate.refresh() }
        binding.onPreDraw()
        assertEquals(View.GONE, duration.visibility)
        duration.visibility = View.VISIBLE
        binding.onPreDraw()
        assertEquals(View.VISIBLE, duration.visibility)
        assertEquals(View.INVISIBLE, controls.visibility)
        duration.visibility = View.GONE
        binding.onPreDraw()
        assertEquals(View.GONE, duration.visibility)
        binding.close()
    }

    /** 延迟加载容器填充进度条后在下一次布局提交前恢复容器。@return Unit。Callers: JUnit。 */
    @Test fun lazySeekBarContainerIsRestoredAfterInflation() {
        val root = frame()
        val content = frame().apply { setBackgroundColor(Color.BLUE) }.also(root::addView)
        val seekBarContainer = frame().also(root::addView)
        val seekBar = seekBar().apply { setBackgroundColor(Color.RED) }
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true),
            preserve = { CleanSceneBinding.containsInstance(it, seekBar.javaClass) }) { gate.refresh() }

        binding.onPreDraw()
        assertEquals(View.INVISIBLE, seekBarContainer.visibility)
        seekBarContainer.addView(seekBar)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        binding.onPreDraw()
        assertEquals(View.VISIBLE, seekBarContainer.visibility)
        assertEquals(1f, seekBarContainer.alpha)
        binding.close()
    }

    /** 复现实机：页面先绘制，视频页可见、准备和首帧随后才到达。@return Unit；无入参。Callers: JUnit。 */
    @Test fun firstDrawBeforeFeedCreationAlreadyExcludesAllChrome() {
        val root = frame()
        val content = frame().apply { setBackgroundColor(Color.BLUE) }.also(root::addView)
        val top = frame().apply { setBackgroundColor(Color.RED) }.also(root::addView)
        val bottom = frame().apply { setBackgroundColor(Color.GREEN) }.also(root::addView)
        val owner = CleanViewGate.Owner(true)
        val binding = CleanSceneBinding(root, content.id, gate, owner) { gate.refresh() }
        gate.refresh()
        assertTrue(binding.onPreDraw())
        assertEquals(Color.BLUE, draw(root))
        assertEquals(View.INVISIBLE, top.visibility)
        assertEquals(View.INVISIBLE, bottom.visibility)
        val state = CleanPlaybackState().apply { enabled = true; visible = true }
        state.select()
        state.prepare()
        state.release()
        state.select()
        state.play()
        owner.clean = state.shouldClean
        binding.onPreDraw()
        assertEquals(Color.BLUE, draw(root))
        state.pause()
        owner.clean = state.shouldClean
        binding.onPreDraw()
        assertEquals(View.VISIBLE, top.visibility)
        assertEquals(View.VISIBLE, bottom.visibility)
        binding.close()
    }

    /** 多层内容路径保留视频区域，只隐藏路径旁支。@return Unit；无入参。Callers: JUnit。 */
    @Test fun nestedContentKeepsVideoAndHidesWholeDecorationBranches() {
        val root = frame()
        val wrapper = frame().also(root::addView)
        val content = frame().apply { setBackgroundColor(Color.BLUE) }.also(wrapper::addView)
        val inner = frame().apply { setBackgroundColor(Color.RED) }.also(wrapper::addView)
        val outer = frame().apply { setBackgroundColor(Color.GREEN) }.also(root::addView)
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true)) { gate.refresh() }
        binding.onPreDraw()
        assertEquals(Color.BLUE, draw(root))
        assertEquals(View.INVISIBLE, inner.visibility)
        assertEquals(View.INVISIBLE, outer.visibility)
        assertEquals(View.VISIBLE, content.visibility)
        assertEquals(View.VISIBLE, wrapper.visibility)
        binding.close()
    }

    /** 尚未加载视频也不阻止绘制和事件循环。@return Unit；无入参。Callers: JUnit。 */
    @Test fun slowLoadingNeverBlocksDrawing() {
        val root = frame()
        val content = frame().also(root::addView)
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true)) { gate.refresh() }
        repeat(100) { assertTrue(binding.onPreDraw()) }
        binding.close()
    }

    /** 延迟创建的整个控件区域在下一轮布局提交时受同一规则管理。@return Unit；无入参。Callers: JUnit。 */
    @Test fun deferredChromeAndNestedIconsAreHiddenBeforeDraw() {
        val root = frame()
        val content = frame().apply { setBackgroundColor(Color.BLUE) }.also(root::addView)
        val owner = CleanViewGate.Owner(true)
        val binding = CleanSceneBinding(root, content.id, gate, owner) { gate.refresh() }
        val toolbar = frame().also(root::addView)
        toolbar.addView(frame().apply { setBackgroundColor(Color.RED) })
        root.viewTreeObserver.dispatchOnGlobalLayout()
        assertFalse(root.viewTreeObserver.dispatchOnPreDraw())
        assertEquals(Color.BLUE, draw(root))
        assertEquals(View.INVISIBLE, toolbar.visibility)
        binding.close()
    }

    /** 非播放页面恢复容器，另一窗口的弹窗不受影响。@return Unit；无入参。Callers: JUnit。 */
    @Test fun leavingVideoRestoresChromeWithoutTouchingOtherWindows() {
        val root = frame()
        val content = frame().also(root::addView)
        val toolbar = frame().also(root::addView)
        val dialog = frame()
        val owner = CleanViewGate.Owner(true)
        val binding = CleanSceneBinding(root, content.id, gate, owner) { gate.refresh() }
        binding.onPreDraw()
        owner.clean = false
        binding.onPreDraw()
        assertEquals(View.VISIBLE, toolbar.visibility)
        assertEquals(View.VISIBLE, dialog.visibility)
        binding.close()
    }

    /** 关闭功能时登记不改变首次画面，后续开启无需重建页面。@return Unit；无入参。Callers: JUnit。 */
    @Test fun disabledInitializationRemainsUnchangedAndSupportsHotUpdate() {
        val root = frame()
        val content = frame().also(root::addView)
        val toolbar = frame().also(root::addView)
        val owner = CleanViewGate.Owner(false)
        val binding = CleanSceneBinding(root, content.id, gate, owner) { gate.refresh() }
        binding.onPreDraw()
        assertEquals(View.VISIBLE, toolbar.visibility)
        owner.clean = true
        binding.onPreDraw()
        assertEquals(View.INVISIBLE, toolbar.visibility)
        owner.clean = false
        binding.onPreDraw()
        assertEquals(View.VISIBLE, toolbar.visibility)
        binding.close()
    }

    /** 销毁移除监听并恢复宿主属性，不污染复用容器。@return Unit；无入参。Callers: JUnit。 */
    @Test fun closingRestoresTargetsAndRemovesListeners() {
        val root = frame()
        val content = frame().also(root::addView)
        val controls = frame().also(root::addView)
        var commits = 0
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true)) { commits++; gate.refresh() }
        binding.onPreDraw()
        assertEquals(View.INVISIBLE, controls.visibility)
        binding.close()
        assertEquals(View.VISIBLE, controls.visibility)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, commits)
    }

    /** 原生清屏退出与模块释放不修改视频或原生剩余状态。@return Unit；无入参。Callers: JUnit。 */
    @Test fun nativeCleanExitCannotExposeManagedRegion() {
        val root = frame()
        val content = frame().also(root::addView)
        val controls = frame().also(root::addView)
        val owner = CleanViewGate.Owner(true)
        val binding = CleanSceneBinding(root, content.id, gate, owner) { gate.refresh() }
        binding.onPreDraw()
        controls.visibility = gate.visibility(controls, View.VISIBLE)
        controls.alpha = 1f
        assertEquals(View.INVISIBLE, controls.visibility)
        assertEquals(View.VISIBLE, content.visibility)
        controls.visibility = gate.visibility(controls, View.GONE)
        owner.clean = false
        binding.onPreDraw()
        assertEquals(View.GONE, controls.visibility)
        binding.close()
    }

    @Test fun replacingContentInstanceRebuildsTheCurrentAncestorPath() {
        val root = frame()
        val oldBranch = frame().also(root::addView)
        val oldContent = frame().also(oldBranch::addView)
        val nextBranch = frame().also(root::addView)
        val controls = frame().also(nextBranch::addView)
        val binding = CleanSceneBinding(root, oldContent.id, gate, CleanViewGate.Owner(true)) { gate.refresh() }
        binding.onPreDraw()
        assertEquals(View.INVISIBLE, nextBranch.visibility)
        oldBranch.removeView(oldContent)
        val currentContent = frame().apply { id = oldContent.id }.also(nextBranch::addView)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        binding.onPreDraw()
        assertEquals(View.VISIBLE, nextBranch.visibility)
        assertEquals(View.VISIBLE, currentContent.visibility)
        assertEquals(View.INVISIBLE, oldBranch.visibility)
        assertEquals(View.INVISIBLE, controls.visibility)
        binding.close()
    }

    @Test fun missingContentReleasesChromeUntilContentReturns() {
        val root = frame()
        val content = frame().also(root::addView)
        val controls = frame().also(root::addView)
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true)) { gate.refresh() }
        binding.onPreDraw()
        root.removeView(content)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        binding.onPreDraw()
        assertEquals(View.VISIBLE, controls.visibility)
        root.addView(content)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        binding.onPreDraw()
        assertEquals(View.INVISIBLE, controls.visibility)
        binding.close()
    }

    @Test fun movingContentToAnotherRootReleasesOnlyItsFormerScene() {
        val root = frame()
        val content = frame().also(root::addView)
        val controls = frame().also(root::addView)
        val otherRoot = frame()
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true)) { gate.refresh() }
        binding.onPreDraw()
        root.removeView(content)
        otherRoot.addView(content)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        assertEquals(View.VISIBLE, controls.visibility)
        assertEquals(View.VISIBLE, content.visibility)
        binding.close()
    }

    @Test fun inactiveBindingRemovesCallbacksAndRebuildsOnRecovery() {
        val root = frame()
        val content = frame().also(root::addView)
        val controls = frame().also(root::addView)
        var commits = 0
        val binding = CleanSceneBinding(root, content.id, gate, CleanViewGate.Owner(true)) { commits++; gate.refresh() }
        binding.onPreDraw()
        binding.setActive(false)
        assertEquals(View.VISIBLE, controls.visibility)
        root.removeView(content)
        val replacement = frame().apply { id = content.id }.also(root::addView)
        root.viewTreeObserver.dispatchOnGlobalLayout()
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(1, commits)
        binding.setActive(true)
        root.viewTreeObserver.dispatchOnPreDraw()
        assertEquals(2, commits)
        assertEquals(View.VISIBLE, replacement.visibility)
        assertEquals(View.INVISIBLE, controls.visibility)
        binding.close()
    }
}
