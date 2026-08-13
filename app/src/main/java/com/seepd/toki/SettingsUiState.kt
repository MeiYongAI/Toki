package com.seepd.toki

internal data class SettingsUiState(
    val regionSpoof: Boolean,
    val region: RegionPreset,
    val downloadRestrictions: Boolean,
    val hideFeedAds: Boolean,
    val hideLive: Boolean,
    val hideImages: Boolean,
    val forceRegion: Boolean,
    val hideLongPosts: Boolean,
    val filterViewsLikes: Boolean,
    val disableLoop: Boolean,
    val defaultPlaybackSpeed: Float,
    val antiBurnIn: Boolean,
    val autoTranslateComments: Boolean,
    val videoLocation: String,
    val picLocation: String,
    val gifLocation: String,
    val allowDuet: Boolean,
    val allowStitch: Boolean,
    val forceUnmute: Boolean,
    val grayMode: Boolean,
    val longPostSeconds: Int,
    val viewsMin: Long,
    val viewsMax: Long?,
    val likesMin: Long,
    val likesMax: Long?,
)

internal object SettingsDefaults {
    fun create() = SettingsUiState(
        regionSpoof = false,
        region = RegionPreset.US,
        downloadRestrictions = false,
        hideFeedAds = false,
        hideLive = false,
        hideImages = false,
        forceRegion = false,
        hideLongPosts = false,
        filterViewsLikes = false,
        disableLoop = false,
        defaultPlaybackSpeed = PlaybackSpeed.DEFAULT,
        antiBurnIn = false,
        autoTranslateComments = false,
        videoLocation = "Movies/TikTok",
        picLocation = "Pictures/TikTok",
        gifLocation = "Movies/TikTok",
        allowDuet = false,
        allowStitch = false,
        forceUnmute = false,
        grayMode = false,
        longPostSeconds = 60,
        viewsMin = 0,
        viewsMax = null,
        likesMin = 0,
        likesMax = null,
    )
}

internal data class NumericRange(val minimum: Long, val maximum: Long?)

internal enum class RangeInputError {
    INVALID_MINIMUM,
    INVALID_MAXIMUM,
    INVALID_ORDER,
}

internal data class RangeValidation(
    val value: NumericRange? = null,
    val error: RangeInputError? = null,
)

internal enum class PathInputError {
    EMPTY,
    ABSOLUTE,
    INVALID_SEGMENT,
}

internal data class PathValidation(
    val value: String? = null,
    val error: PathInputError? = null,
)

internal object SettingsInput {
    fun validateRange(minimum: String, maximum: String): RangeValidation {
        val parsedMinimum = minimum.trim().toLongOrNull()
        if (parsedMinimum == null || parsedMinimum < 0) {
            return RangeValidation(error = RangeInputError.INVALID_MINIMUM)
        }

        val maximumText = maximum.trim()
        val parsedMaximum = if (maximumText.isEmpty()) null else maximumText.toLongOrNull()
        if (maximumText.isNotEmpty() && (parsedMaximum == null || parsedMaximum <= 0)) {
            return RangeValidation(error = RangeInputError.INVALID_MAXIMUM)
        }
        if (parsedMaximum != null && parsedMaximum <= parsedMinimum) {
            return RangeValidation(error = RangeInputError.INVALID_ORDER)
        }
        return RangeValidation(value = NumericRange(parsedMinimum, parsedMaximum))
    }

    fun validateDuration(value: String): Int? =
        value.trim().toIntOrNull()?.takeIf { it > 0 }

    fun normalizeMediaDirectory(value: String): PathValidation {
        var path = value.trim().replace('\\', '/')
        if (path.isEmpty()) {
            return PathValidation(error = PathInputError.EMPTY)
        }
        // MediaStore owns a directory relative to shared storage.
        if (path.contains(":") || path.startsWith("~") || path.startsWith('/')) {
            return PathValidation(error = PathInputError.ABSOLUTE)
        }

        val segments = path.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.isEmpty()) {
            return PathValidation(error = PathInputError.EMPTY)
        }
        if (segments.any { it == ".." || '\u0000' in it }) {
            return PathValidation(error = PathInputError.INVALID_SEGMENT)
        }
        return PathValidation(value = segments.joinToString("/"))
    }

    fun mediaDirectoryFromDocumentId(documentId: String): String? {
        val separatorIndex = documentId.indexOf(':')
        if (separatorIndex <= 0) {
            return null
        }

        val volumeName = documentId.substring(0, separatorIndex)
        if (!volumeName.equals("primary", ignoreCase = true)) {
            return null
        }

        return normalizeMediaDirectory(documentId.substring(separatorIndex + 1)).value
    }
}
