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
        phase = when (isPlaying) { true -> Phase.PLAYING; false -> Phase.PAUSED; null -> Phase.LOADING }
    }

    /** 接收当前视频准备通知。@return Unit；无入参。Callers: AutoCleanModeHook.startPlayback、测试。 */
    fun prepare() { if (selected && phase != Phase.PLAYING && phase != Phase.PAUSED) phase = Phase.LOADING }

    /** 接收当前视频播放通知。@return Unit；无入参。Callers: AutoCleanModeHook.startPlayback、测试。 */
    fun play() { if (selected) phase = Phase.PLAYING }

    /** 明确暂停或播放失败立即显示控件。@return Unit；无入参。Callers: playbackFailed、暂停图标回调、测试。 */
    fun stop() { phase = Phase.PAUSED }

    /** 释放卡片身份，显示阶段持续到下一内容被确认。@return Unit；无入参。Callers: AutoCleanModeHook.releaseCell、测试。 */
    fun release() { selected = false }

    /** 接收空列表的重新加载请求。@return Unit；无入参。Callers: AutoCleanModeHook 的页面状态回调、测试。 */
    fun loading() { selected = false; phase = Phase.LOADING }

    /** 确认非视频内容或空列表，恢复控件。@return Unit；无入参。Callers: AutoCleanModeHook.selectCurrent、测试。 */
    fun clear() { selected = false; phase = Phase.NON_VIDEO }
}
