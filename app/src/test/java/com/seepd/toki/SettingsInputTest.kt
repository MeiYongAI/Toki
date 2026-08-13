package com.seepd.toki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsInputTest {
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
    fun maximumMustBeGreaterThanMinimum() {
        val result = SettingsInput.validateRange("1000", "1000")

        assertNull(result.value)
        assertEquals(RangeInputError.INVALID_ORDER, result.error)
    }

    @Test
    fun absolutePrimaryStoragePathIsRejected() {
        val result = SettingsInput.normalizeMediaDirectory("/sdcard/Movies/TikTok/")

        assertNull(result.value)
        assertEquals(PathInputError.ABSOLUTE, result.error)
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
    fun primaryStorageDocumentIdBecomesMediaDirectory() {
        assertEquals(
            "Movies/TikTok",
            SettingsInput.mediaDirectoryFromDocumentId("primary:Movies/TikTok"),
        )
        assertEquals(
            "Pictures/My App",
            SettingsInput.mediaDirectoryFromDocumentId("PRIMARY:Pictures/My App"),
        )
    }

    @Test
    fun unsupportedDocumentIdsAreRejected() {
        assertNull(SettingsInput.mediaDirectoryFromDocumentId("primary:"))
        assertNull(SettingsInput.mediaDirectoryFromDocumentId("1234-5678:Movies/TikTok"))
        assertNull(SettingsInput.mediaDirectoryFromDocumentId("Movies/TikTok"))
        assertNull(SettingsInput.mediaDirectoryFromDocumentId(":Movies/TikTok"))
        assertNull(SettingsInput.mediaDirectoryFromDocumentId("primary:Movies/../TikTok"))
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
}
