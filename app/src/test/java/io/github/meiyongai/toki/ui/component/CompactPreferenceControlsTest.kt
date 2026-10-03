package io.github.meiyongai.toki.ui.component

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

/** 验证紧凑控件使用实际文本尺寸分配空间，不依赖语言字符数。 */
class CompactPreferenceControlsTest {
    /**
     * 短文案保留并排排列，两个按钮正好使用可用空间。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun shortActionsKeepTheirInlineLayout() {
        val sizes = requireNotNull(calculateCompactActionWidths(48.dp, 48.dp, 280.dp, 48.dp, 58.dp, 8.dp))
        assertEquals(136.dp, sizes.first)
        assertEquals(136.dp, sizes.second)
        assertEquals(280.dp, sizes.first + sizes.second + 8.dp)
    }

    /**
     * 两个按钮长度不同时优先满足较长文案，不强制平分导致换行。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun longerActionReceivesEnoughWidth() {
        val sizes = requireNotNull(calculateCompactActionWidths(40.dp, 112.dp, 280.dp, 48.dp, 58.dp, 8.dp))
        assertTrue(sizes.first >= 88.dp)
        assertTrue(sizes.second >= 160.dp)
        assertEquals(280.dp, sizes.first + sizes.second + 8.dp)
    }

    /**
     * 窄屏或较大字体无法容纳两个完整按钮时使用纵向排列。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun insufficientWidthRequiresVerticalActions() {
        assertNull(calculateCompactActionWidths(120.dp, 130.dp, 260.dp, 48.dp, 58.dp, 8.dp))
    }

    /**
     * 分类名较短时保留等宽和32dp高度，较长或较高时扩展内容尺寸供横向滚动。
     * @return Unit；无入参。
     * Callers: JUnit。
     */
    @Test fun categorySizeAccommodatesTextAndFontScale() {
        val compact = calculateCompactCategorySize(40.dp, 16.dp, 270.dp, 3)
        assertEquals(90.dp, compact.width)
        assertEquals(32.dp, compact.height)
        val expanded = calculateCompactCategorySize(135.dp, 36.dp, 270.dp, 3)
        assertEquals(151.dp, expanded.width)
        assertEquals(44.dp, expanded.height)
    }
}
