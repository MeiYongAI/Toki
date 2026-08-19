package com.seepd.toki

import java.math.BigDecimal

internal data class SettingsUiState(
    val regionSpoof: Boolean,
    val region: RegionPreset,
    val languageSpoof: Boolean,
    val timeZoneSpoof: Boolean,
    val skipStartupLogin: Boolean,
    val downloadRestrictions: Boolean,
    val hideFeedAds: Boolean,
    val hideLive: Boolean,
    val hideImages: Boolean,
    val hideAiGenerated: Boolean,
    val hideTrendingTopics: Boolean,
    val hideContentClassification: Boolean,
    val forceRegion: Boolean,
    val hideLongPosts: Boolean,
    val filterViewsLikes: Boolean,
    val disableOfflineColdCacheWithNetwork: Boolean,
    val disableLoop: Boolean,
    val alwaysShowVideoProgressBar: Boolean,
    val showAuthorLocation: Boolean,
    val defaultPlaybackSpeed: Float,
    val autoTranslateComments: Boolean,
    val videoLocation: String,
    val picLocation: String,
    val gifLocation: String,
    val allowDuet: Boolean,
    val allowStitch: Boolean,
    val longPostSeconds: Int,
    val viewsMin: Long,
    val viewsMax: Long?,
    val likesMin: Long,
    val likesMax: Long?,
    val viewsMinInput: String,
    val viewsMaxInput: String,
    val likesMinInput: String,
    val likesMaxInput: String,
    val hideAuthorAvatar: Boolean,
    val hideAuthorInfo: Boolean,
    val hideFollowButton: Boolean,
    val hideVideoDescription: Boolean,
    val hideVideoTags: Boolean,
    val hideMusicTitle: Boolean,
    val hideMusicCover: Boolean,
    val hideLikeButton: Boolean,
    val hideCommentButton: Boolean,
    val hideFavoriteButton: Boolean,
    val hideShareButton: Boolean,
    val hideDuetButton: Boolean,
    val hideStitchButton: Boolean,
    val hideQuickDm: Boolean,
    val hideStoryTags: Boolean,
    val hideCollabLabel: Boolean,
    val hideCommercialLabels: Boolean,
    val hideCreativeToolAnchors: Boolean,
    val hideMovieAnimeAnchors: Boolean,
    val hideIncentiveShare: Boolean,
    val hideTako: Boolean,
    val hideContentSearch: Boolean,
    val hideSafetyWarning: Boolean,
    val hideStatusBar: Boolean,
    val hideLiveEntry: Boolean,
    val hideTopNavigation: Boolean,
    val hideSearchEntry: Boolean,
    val hideBottomNavigation: Boolean,
    val hideVideoProgressBar: Boolean,
    val hideTranslationControls: Boolean,
    val gpsSpoof: Boolean,
    val gpsLatitude: String,
    val gpsLongitude: String,
)

internal object SettingsDefaults {
    fun create() = SettingsUiState(
        regionSpoof = false,
        region = RegionPreset.US,
        languageSpoof = false,
        timeZoneSpoof = false,
        skipStartupLogin = false,
        downloadRestrictions = false,
        hideFeedAds = false,
        hideLive = false,
        hideImages = false,
        hideAiGenerated = false,
        hideTrendingTopics = false,
        hideContentClassification = false,
        forceRegion = false,
        hideLongPosts = false,
        filterViewsLikes = false,
        disableOfflineColdCacheWithNetwork = false,
        disableLoop = false,
        alwaysShowVideoProgressBar = false,
        showAuthorLocation = false,
        defaultPlaybackSpeed = PlaybackSpeed.DEFAULT,
        autoTranslateComments = false,
        videoLocation = "Movies/TikTok",
        picLocation = "Pictures/TikTok",
        gifLocation = "Movies/TikTok",
        allowDuet = false,
        allowStitch = false,
        longPostSeconds = 60,
        viewsMin = 0,
        viewsMax = null,
        likesMin = 0,
        likesMax = null,
        viewsMinInput = "0",
        viewsMaxInput = "",
        likesMinInput = "0",
        likesMaxInput = "",
        hideAuthorAvatar = false,
        hideAuthorInfo = false,
        hideFollowButton = false,
        hideVideoDescription = false,
        hideVideoTags = false,
        hideMusicTitle = false,
        hideMusicCover = false,
        hideLikeButton = false,
        hideCommentButton = false,
        hideFavoriteButton = false,
        hideShareButton = false,
        hideDuetButton = false,
        hideStitchButton = false,
        hideQuickDm = false,
        hideStoryTags = false,
        hideCollabLabel = false,
        hideCommercialLabels = false,
        hideCreativeToolAnchors = false,
        hideMovieAnimeAnchors = false,
        hideIncentiveShare = false,
        hideTako = false,
        hideContentSearch = false,
        hideSafetyWarning = false,
        hideStatusBar = false,
        hideLiveEntry = false,
        hideTopNavigation = false,
        hideSearchEntry = false,
        hideBottomNavigation = false,
        hideVideoProgressBar = false,
        hideTranslationControls = false,
        gpsSpoof = false,
        gpsLatitude = "0.0",
        gpsLongitude = "0.0",
    )
}

internal data class NumericRange(
    val minimum: Long,
    val maximum: Long?,
    val minimumInput: String = minimum.toString(),
    val maximumInput: String = maximum?.toString().orEmpty(),
)

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
    private val compactCountPattern = Regex("""^(\d+(?:\.\d+)?)([KMB])?$""")
    private val groupedCompactCountPattern = Regex(
        """^\d{1,3}(?:,\d{3})+(?:\.\d+)?[KMB]?$""",
    )

    fun parseCoordinate(value: String, minimum: Double, maximum: Double): Double? =
        value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in minimum..maximum }

    fun validateRange(minimum: String, maximum: String): RangeValidation {
        val parsedMinimum = parseMetricCount(minimum)
        if (parsedMinimum == null || parsedMinimum < 0) {
            return RangeValidation(error = RangeInputError.INVALID_MINIMUM)
        }

        val maximumText = maximum.trim()
        val parsedMaximum = if (maximumText.isEmpty()) null else parseMetricCount(maximumText)
        if (maximumText.isNotEmpty() && (parsedMaximum == null || parsedMaximum <= 0)) {
            return RangeValidation(error = RangeInputError.INVALID_MAXIMUM)
        }
        if (parsedMaximum != null && parsedMaximum < parsedMinimum) {
            return RangeValidation(error = RangeInputError.INVALID_ORDER)
        }
        return RangeValidation(
            value = NumericRange(parsedMinimum, parsedMaximum, minimum, maximum),
        )
    }

    private fun parseMetricCount(value: String): Long? {
        val trimmed = value.trim().uppercase()
        if (trimmed.contains(',') && !groupedCompactCountPattern.matches(trimmed)) {
            return null
        }
        val match = compactCountPattern.matchEntire(trimmed.replace(",", "")) ?: return null
        val multiplier = when (match.groupValues[2]) {
            "K" -> 1_000L
            "M" -> 1_000_000L
            "B" -> 1_000_000_000L
            else -> 1L
        }
        return runCatching {
            BigDecimal(match.groupValues[1])
                .multiply(BigDecimal.valueOf(multiplier))
                .longValueExact()
        }.getOrNull()
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

}
