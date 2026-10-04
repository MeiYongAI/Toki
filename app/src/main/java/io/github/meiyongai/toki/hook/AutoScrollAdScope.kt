package io.github.meiyongai.toki.hook

/** 将广告资格放行限定于当前视频的自动滚动菜单与翻页资格检查。 */
internal class AutoScrollAdScope {
    private val current = ThreadLocal<Any?>()

    /**
     * 执行资格判断，退出时恢复外层模型，不修改视频数据。
     * @param model 当前检查的视频模型。
     * @param block 原生资格检查或菜单构建。
     * @return 原生结果或原生异常。
     * Callers: AutoScrollHook.init 的菜单、翻页资格拦截；AutoScrollAdScopeTest。
     */
    fun <T> checking(model: Any?, block: () -> T): T {
        val previous = current.get()
        current.set(model)
        return try { block() } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    /**
     * 检查广告资格读取是否属于同线程、同一视频的自动滚动资格检查。
     * @param model 广告资格读取的模型。
     * @return 仅非空且身份一致时为 true。
     * Callers: AutoScrollHook.init 的广告资格拦截；AutoScrollAdScopeTest。
     */
    fun contains(model: Any?): Boolean = model != null && current.get() === model
}
