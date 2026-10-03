package io.github.meiyongai.toki.hook

/** 仅在当前视频的同步下载选源期间提供播放源，不影响播放器或其他线程。 */
internal class DownloadSourceScope {
    private val current = ThreadLocal<Any?>()

    fun <T> selecting(video: Any?, block: () -> T): T {
        val previous = current.get()
        current.set(video)
        return try { block() } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }

    fun resolve(video: Any?, original: Any?, usable: (Any) -> Boolean, fallback: () -> Any?): Any? {
        if (video == null || current.get() !== video || original != null && usable(original)) return original
        return fallback()?.takeIf(usable) ?: original
    }
}
