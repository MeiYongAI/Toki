package io.github.meiyongai.toki.hook

import org.junit.Assert.*
import org.junit.Test

class VideoFillGeometryTest {
    /** 无参数。验证常见竖屏铺满且裁切受限。返回 Unit。Callers: JUnit。 */
    @Test fun portraitCoversTallViewportWithoutStretching() {
        val value = checkNotNull(VideoFillGeometry.calculate(1080, 1920, 1080, 2356))
        assertTrue(value.cropped)
        assertTrue(value.width >= 1080 && value.height >= 2356)
        assertEquals(1080.0 / 1920, value.width.toDouble() / value.height, 0.001)
        assertTrue(1080.0 * 2356 / (value.width.toDouble() * value.height) >= 0.749)
    }
    /** 无参数。验证横屏、方形、过长视频和旋转后视口不被裁切。返回 Unit。Callers: JUnit。 */
    @Test fun unsuitableRatiosRemainComplete() {
        for ((vw, vh, w, h) in listOf(listOf(1920, 1080, 1080, 2356), listOf(1000, 1000, 1080, 2356),
            listOf(100, 1000, 1080, 2356), listOf(1080, 1920, 2356, 1080))) {
            val value = checkNotNull(VideoFillGeometry.calculate(vw, vh, w, h))
            assertFalse(value.cropped)
            assertTrue(value.width <= w && value.height <= h)
        }
    }
    /** 无参数。验证边界、未测量及大尺寸计算。返回 Unit。Callers: JUnit。 */
    @Test fun thresholdAndMeasurementBoundaries() {
        assertTrue(checkNotNull(VideoFillGeometry.calculate(9, 12, 9, 16)).cropped)
        assertFalse(checkNotNull(VideoFillGeometry.calculate(9, 12, 9, 17)).cropped)
        assertNull(VideoFillGeometry.calculate(0, 1920, 1080, 2356))
        assertNull(VideoFillGeometry.calculate(1080, 1920, -1, 2356))
        assertTrue(checkNotNull(VideoFillGeometry.calculate(Int.MAX_VALUE - 1, Int.MAX_VALUE, 1080, 2356)).width <= 1080)
    }
}
