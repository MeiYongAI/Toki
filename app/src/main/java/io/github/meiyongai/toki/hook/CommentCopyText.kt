package io.github.meiyongai.toki.hook

/** 显示数据的不可变复制快照；表情位置必须与同一份正文配套。 */
internal data class CommentCopyText(val text: String, val extra: List<*>?) {
    companion object {
        /**
         * 仅为当前评论确实显示的译文创建快照；原文由宿主原有复制参数提供。
         * @param commentId 菜单目标评论标识。
         * @param displayId 显示数据对应的评论标识。
         * @param translated 是否正在显示译文。
         * @param text 显示译文。
         * @param extra 对应译文的表情及文本位置。
         * @return 译文快照；非译文或视图已绑定其他评论时返回 null。
         * Callers: CommentCopyHook 菜单回调、单元测试。
         */
        fun displayed(commentId: String?, displayId: String?, translated: Boolean, text: String?, extra: List<*>?): CommentCopyText? {
            if (!translated || commentId == null || commentId != displayId) return null
            return CommentCopyText(checkNotNull(text) { "已显示的译文正文为空" }, extra?.toList())
        }
    }
}

/** 将正文替换权限限制在当前线程的一次评论复制调用内。 */
internal class CommentCopyScope {
    private val value = ThreadLocal<CommentCopyText?>()
    /** 当前复制事务中的译文。@return 译文快照或 null。Callers: CommentCopyHook 剪贴板回调。 */
    val current: CommentCopyText?
        get() = value.get()

    /**
     * 执行独立复制调用，并在正常返回或异常时恢复外层调用上下文。
     * @param text 本次菜单的译文快照；null 表示使用宿主正文。
     * @param action 宿主复制动作。
     * @return 宿主动作返回值；异常原样传播。
     * Callers: CommentCopyHook 复制动作回调、单元测试。
     */
    fun <T> within(text: CommentCopyText?, action: () -> T): T {
        val previous = value.get()
        value.set(text)
        try {
            return action()
        } finally {
            if (previous == null) value.remove() else value.set(previous)
        }
    }
}
