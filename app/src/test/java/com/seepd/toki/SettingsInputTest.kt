package com.seepd.toki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInputTest {
    @Test
    fun gpsCoordinatesStayWithinValidRanges() {
        assertEquals(25.0, SettingsInput.parseCoordinate("25.0", -90.0, 90.0))
        assertEquals(-180.0, SettingsInput.parseCoordinate("-180", -180.0, 180.0))
        assertEquals(null, SettingsInput.parseCoordinate("91", -90.0, 90.0))
        assertEquals(null, SettingsInput.parseCoordinate("east", -180.0, 180.0))
    }

    @Test
    fun regionCatalogCoversGlobalPresets() {
        val presets = RegionPreset.values()
        assertTrue(presets.size >= 90)
        assertEquals(presets.size, presets.map { it.code }.toSet().size)
        assertEquals(RegionPreset.JP, RegionPreset.fromCode("jp"))
        assertEquals(RegionPreset.CN, RegionPreset.fromCode("CN"))
    }

    @Test
    fun emptyMaximumMeansUnlimited() {
        val result = SettingsInput.validateRange("1000", "")

        assertEquals(NumericRange(1000, null), result.value)
        assertNull(result.error)
    }

    @Test
    fun equalMinimumAndMaximumAreAllowed() {
        val result = SettingsInput.validateRange("1000", "1000")

        assertEquals(NumericRange(1000, 1000), result.value)
        assertNull(result.error)
    }

    @Test
    fun compactMetricInputIsConvertedToExactCounts() {
        val result = SettingsInput.validateRange("10k", "1.5M")

        assertEquals(NumericRange(10_000, 1_500_000, "10k", "1.5M"), result.value)
        assertNull(result.error)
    }

    @Test
    fun compactMetricInputRejectsFractionsWithoutAUnit() {
        val result = SettingsInput.validateRange("1.5", "2K")

        assertNull(result.value)
        assertEquals(RangeInputError.INVALID_MINIMUM, result.error)
    }

    @Test
    fun metricInputKeepsTheUsersExactText() {
        val result = SettingsInput.validateRange("10k", "")

        assertEquals("10k", result.value?.minimumInput)
        assertEquals("", result.value?.maximumInput)
    }

    @Test
    fun absolutePrimaryStoragePathIsRejected() {
        val result = SettingsInput.normalizeMediaDirectory("/sdcard/Movies/TikTok/")

        assertNull(result.value)
        assertEquals(PathInputError.ABSOLUTE, result.error)
    }

    @Test
    fun relativeMediaDirectoryIsNormalizedForSharedStorage() {
        val result = SettingsInput.normalizeMediaDirectory(" Movies\\TikTok//Saved/ ")

        assertEquals("Movies/TikTok/Saved", result.value)
        assertNull(result.error)
    }

    @Test
    fun uriAndParentSegmentsAreRejected() {
        val uri = SettingsInput.normalizeMediaDirectory("content://downloads/TikTok")
        val parent = SettingsInput.normalizeMediaDirectory("Movies/../TikTok")
        val windows = SettingsInput.normalizeMediaDirectory("C:\\Users\\Example\\Movies")

        assertEquals(PathInputError.ABSOLUTE, uri.error)
        assertEquals(PathInputError.INVALID_SEGMENT, parent.error)
        assertEquals(PathInputError.ABSOLUTE, windows.error)
    }

    @Test
    fun durationMustBePositive() {
        assertNull(SettingsInput.validateDuration("0"))
        assertNull(SettingsInput.validateDuration("not-a-number"))
        assertTrue(SettingsInput.validateDuration("60") == 60)
    }

    @Test
    fun defaultPlaybackSpeedOnlyAcceptsSupportedValues() {
        assertEquals(1.5f, PlaybackSpeed.sanitize(1.5f))
        assertEquals(1.0f, PlaybackSpeed.sanitize(1.33f))
        assertEquals(1.0f, PlaybackSpeed.sanitize(Float.NaN))
    }

    @Test
    fun pagePurificationDefaultsToShowingEveryControl() {
        val state = SettingsDefaults.create()

        assertTrue(
            listOf(
                state.hideAuthorInfo,
                state.hideFollowButton,
                state.hideVideoDescription,
                state.hideVideoTags,
                state.hideMusicTitle,
                state.hideMusicCover,
                state.hideLikeButton,
                state.hideCommentButton,
                state.hideFavoriteButton,
                state.hideShareButton,
                state.hideDuetButton,
                state.hideStitchButton,
                state.hideQuickDm,
                state.hideStoryTags,
                state.hideCollabLabel,
                state.hideTako,
                state.hideContentSearch,
                state.hideTranslationControls,
                state.hideStatusBar,
            ).none { it },
        )
    }

    @Test
    fun startupLoginSkipDefaultsToDisabled() {
        assertTrue(!SettingsDefaults.create().skipStartupLogin)
    }

    @Test
    fun globalNavigationPurificationDefaultsToShowingEveryControl() {
        val state = SettingsDefaults.create()

        assertTrue(
            listOf(
                state.hideTopNavigation,
                state.hideSearchEntry,
                state.hideBottomNavigation,
                state.hideVideoProgressBar,
                state.hideStatusBar,
            ).none { it },
        )
    }
}
