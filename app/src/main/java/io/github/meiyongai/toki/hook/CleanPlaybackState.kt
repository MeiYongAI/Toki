package io.github.meiyongai.toki.hook

/** 视频页的显示阶段；未完成内容选择不等同于暂停。 */
internal class CleanPlaybackState {
    enum class Phase { LOADING, PLAYING, PAUSED, NON_VIDEO }
    var phase = Phase.LOADING
        private set
    var enabled = false
    var visible = false
    var selected = false
        private set
    private var scrolling = false
    private var pausedWhileScrolling = false

    /** 已选导航分支的显示策略。@return 是否隐藏控件；无入参。Callers: AutoCleanModeHook.resolve。 */
    val hidesControls: Boolean get() = phase == Phase.LOADING || phase == Phase.PLAYING
    /** 独立视频页面的显示策略。@return 是否清屏；无入参。Callers: AutoCleanModeHook.commit。 */
    val shouldClean: Boolean get() = enabled && visible && hidesControls

    /**
     * 在当前视频身份改变时进入加载阶段，加载到播放之间保持同一显示策略。
     * @param isPlaying 已知是否播放；null 表示尚未收到播放器结果。
     * @return Unit。
     * Callers: AutoCleanModeHook.selectCurrent、测试。
     */
    fun select(isPlaying: Boolean? = null) {
        selected = true
        pausedWhileScrolling = false
        phase = when (isPlaying) { true -> Phase.PLAYING; false -> Phase.PAUSED; null -> Phase.LOADING }
    }

    /** 接收当前视频准备通知。@return Unit；无入参。Callers: AutoCleanModeHook.startPlayback、测试。 */
    fun prepare() { if (selected && phase != Phase.PLAYING) phase = Phase.LOADING }

    /** 接收当前视频播放通知。@return Unit；无入参。Callers: AutoCleanModeHook.startPlayback、测试。 */
    fun play() { if (selected) { phase = Phase.PLAYING; pausedWhileScrolling = false } }

    /**
     * 处理实际停播；加载过程的播放器复位不等同于用户暂停，翻页停播在交接完成后判定。
     * @return Unit；无入参。
     * Callers: AutoCleanModeHook.pause、测试。
     */
    fun pause() {
        if (phase != Phase.PLAYING) return
        if (scrolling) pausedWhileScrolling = true else phase = Phase.PAUSED
    }

    /** 开始卡片交接，保持当前显示阶段。@return Unit；无入参。Callers: AutoCleanModeHook.beginTransition、测试。 */
    fun beginTransition() { scrolling = true }

    /**
     * 完成卡片交接；取消滑动且播放器确实停播时恢复控件。
     * @param changed 最终卡片是否已改变。
     * @return Unit。
     * Callers: AutoCleanModeHook.settle、测试。
     */
    fun settle(changed: Boolean) {
        scrolling = false
        if (!changed && pausedWhileScrolling) phase = Phase.PAUSED
        pausedWhileScrolling = false
    }

    /** 明确暂停或播放失败立即显示控件。@return Unit；无入参。Callers: AutoCleanModeHook.playbackStopped、pause、测试。 */
    fun stop() { phase = Phase.PAUSED; pausedWhileScrolling = false }

    /** 释放卡片身份，显示阶段持续到下一内容被确认。@return Unit；无入参。Callers: AutoCleanModeHook.releaseCell、测试。 */
    fun release() { selected = false }

    /** 接收空列表的重新加载请求。@return Unit；无入参。Callers: AutoCleanModeHook 的页面状态回调、测试。 */
    fun loading() { selected = false; phase = Phase.LOADING; scrolling = false; pausedWhileScrolling = false }

    /** 确认非视频内容或空列表，恢复控件。@return Unit；无入参。Callers: AutoCleanModeHook.selectCurrent、测试。 */
    fun clear() { selected = false; phase = Phase.NON_VIDEO; scrolling = false; pausedWhileScrolling = false }
}
