package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

class DownloadSourceScopeTest {
    private val scope = DownloadSourceScope()
    private val video = Any()
    private val usable: (Any) -> Boolean = { it == "play" || it == "dedicated" }
    private fun resolve(target: Any = video, original: Any? = null) =
        scope.resolve(target, original, usable) { "play" }

    @Test fun missingOrEmptyDownloadSourceUsesPlaybackDuringSelection() {
        scope.selecting(video) {
            assertEquals("play", resolve())
            assertEquals("play", resolve(original = "empty"))
        }
    }

    @Test fun validDedicatedAddressRemainsUnchanged() {
        scope.selecting(video) {
            assertEquals("dedicated", scope.resolve(video, "dedicated", usable) { error("No fallback expected") })
        }
    }

    @Test fun playbackAndOtherVideoReadsAreUntouched() {
        assertNull(resolve())
        scope.selecting(video) { assertNull(resolve(Any())) }
        assertNull(resolve())
    }

    @Test fun scopeIsRestoredAfterNestedSelectionAndHostException() {
        val other = Any()
        scope.selecting(video) {
            try { scope.selecting(other) { assertEquals("play", resolve(other)); error("host") } }
            catch (_: IllegalStateException) { }
            assertEquals("play", resolve())
            assertNull(resolve(other))
        }
        assertNull(resolve())
    }

    @Test fun simultaneousPlaybackThreadDoesNotReceiveDownloadOverrides() {
        scope.selecting(video) {
            var result: Any? = "not run"
            val thread = Thread { result = resolve() }.apply { start() }
            thread.join()
            assertNull(result)
            assertEquals("play", resolve())
        }
    }

    @Test fun unavailablePlaybackSourceIsNotFabricated() {
        scope.selecting(video) {
            assertNull(scope.resolve(video, null, usable) { null })
            assertNull(scope.resolve(video, null, usable) { "dash-only" })
        }
    }
}
