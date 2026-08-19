package com.seepd.toki

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

internal class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(ModuleConfig.PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var remotePreferences: SharedPreferences? = null

    fun load(): SettingsUiState = SettingsUiState(
        regionSpoof = preferences.getBoolean(ModuleConfig.KEY_REGION_SPOOF, false),
        region = RegionPreset.fromCode(
            preferences.getString(ModuleConfig.KEY_REGION, RegionPreset.US.code),
        ),
        languageSpoof = preferences.getBoolean(ModuleConfig.KEY_LANGUAGE_SPOOF, false),
        timeZoneSpoof = preferences.getBoolean(ModuleConfig.KEY_TIMEZONE_SPOOF, false),
        skipStartupLogin = preferences.getBoolean(ModuleConfig.KEY_SKIP_STARTUP_LOGIN, false),
        downloadRestrictions = preferences.getBoolean(
            ModuleConfig.KEY_DOWNLOAD_RESTRICTIONS,
            false,
        ),
        hideFeedAds = preferences.getBoolean(ModuleConfig.KEY_HIDE_FEED_ADS, false),
        hideLive = preferences.getBoolean(ModuleConfig.KEY_HIDE_LIVE, false),
        hideImages = preferences.getBoolean(ModuleConfig.KEY_HIDE_IMAGES, false),
        hideAiGenerated = preferences.getBoolean(ModuleConfig.KEY_HIDE_AI_GENERATED, false),
        hideTrendingTopics = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_TRENDING_TOPICS,
            false,
        ),
        hideContentClassification = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_CONTENT_CLASSIFICATION,
            false,
        ),
        forceRegion = preferences.getBoolean(ModuleConfig.KEY_FORCE_REGION, false),
        hideLongPosts = preferences.getBoolean(ModuleConfig.KEY_HIDE_LONG_POSTS, false),
        filterViewsLikes = preferences.getBoolean(
            ModuleConfig.KEY_FILTER_VIEWS_LIKES,
            false,
        ),
        disableOfflineColdCacheWithNetwork = preferences.getBoolean(
            ModuleConfig.KEY_DISABLE_OFFLINE_COLD_CACHE_WITH_NETWORK,
            false,
        ),
        disableLoop = preferences.getBoolean(ModuleConfig.KEY_DISABLE_LOOP, false),
        alwaysShowVideoProgressBar = preferences.getBoolean(
            ModuleConfig.KEY_ALWAYS_SHOW_VIDEO_PROGRESS_BAR,
            false,
        ),
        showAuthorLocation = preferences.getBoolean(
            ModuleConfig.KEY_SHOW_AUTHOR_LOCATION,
            false,
        ),
        defaultPlaybackSpeed = PlaybackSpeed.sanitize(
            preferences.getFloat(
                ModuleConfig.KEY_DEFAULT_PLAYBACK_SPEED,
                ModuleConfig.DEFAULT_PLAYBACK_SPEED,
            ),
        ),
        autoTranslateComments = preferences.getBoolean(
            ModuleConfig.KEY_AUTO_TRANSLATE_COMMENTS,
            false,
        ),
        videoLocation = stringValue(ModuleConfig.KEY_VIDEO_LOCATION, "Movies/TikTok"),
        picLocation = stringValue(ModuleConfig.KEY_PIC_LOCATION, "Pictures/TikTok"),
        gifLocation = stringValue(ModuleConfig.KEY_GIF_LOCATION, "Movies/TikTok"),
        allowDuet = preferences.getBoolean(ModuleConfig.KEY_ALLOW_DUET, false),
        allowStitch = preferences.getBoolean(ModuleConfig.KEY_ALLOW_STITCH, false),
        longPostSeconds = positiveInt(ModuleConfig.KEY_LONG_POST_SECONDS, 60),
        viewsMin = nonNegativeLong(ModuleConfig.KEY_VIEWS_MIN, 0),
        viewsMax = optionalPositiveLong(ModuleConfig.KEY_VIEWS_MAX),
        likesMin = nonNegativeLong(ModuleConfig.KEY_LIKES_MIN, 0),
        likesMax = optionalPositiveLong(ModuleConfig.KEY_LIKES_MAX),
        viewsMinInput = metricInput(
            ModuleConfig.KEY_VIEWS_MIN_INPUT,
            ModuleConfig.KEY_VIEWS_MIN,
            "0",
        ),
        viewsMaxInput = metricMaximumInput(
            ModuleConfig.KEY_VIEWS_MAX_INPUT,
            ModuleConfig.KEY_VIEWS_MAX,
        ),
        likesMinInput = metricInput(
            ModuleConfig.KEY_LIKES_MIN_INPUT,
            ModuleConfig.KEY_LIKES_MIN,
            "0",
        ),
        likesMaxInput = metricMaximumInput(
            ModuleConfig.KEY_LIKES_MAX_INPUT,
            ModuleConfig.KEY_LIKES_MAX,
        ),
        hideAuthorAvatar = ModuleConfig.loadAuthorAvatarHidden(preferences),
        hideAuthorInfo = preferences.getBoolean(ModuleConfig.KEY_HIDE_AUTHOR_INFO, false),
        hideFollowButton = preferences.getBoolean(ModuleConfig.KEY_HIDE_FOLLOW_BUTTON, false),
        hideVideoDescription = preferences.getBoolean(ModuleConfig.KEY_HIDE_VIDEO_DESCRIPTION, false),
        hideVideoTags = preferences.getBoolean(ModuleConfig.KEY_HIDE_VIDEO_TAGS, false),
        hideMusicTitle = preferences.getBoolean(ModuleConfig.KEY_HIDE_MUSIC_TITLE, false),
        hideMusicCover = preferences.getBoolean(ModuleConfig.KEY_HIDE_MUSIC_COVER, false),
        hideLikeButton = preferences.getBoolean(ModuleConfig.KEY_HIDE_LIKE_BUTTON, false),
        hideCommentButton = preferences.getBoolean(ModuleConfig.KEY_HIDE_COMMENT_BUTTON, false),
        hideFavoriteButton = preferences.getBoolean(ModuleConfig.KEY_HIDE_FAVORITE_BUTTON, false),
        hideShareButton = preferences.getBoolean(ModuleConfig.KEY_HIDE_SHARE_BUTTON, false),
        hideDuetButton = preferences.getBoolean(ModuleConfig.KEY_HIDE_DUET_BUTTON, false),
        hideStitchButton = preferences.getBoolean(ModuleConfig.KEY_HIDE_STITCH_BUTTON, false),
        hideQuickDm = preferences.getBoolean(ModuleConfig.KEY_HIDE_QUICK_DM, false),
        hideStoryTags = preferences.getBoolean(ModuleConfig.KEY_HIDE_STORY_TAGS, false),
        hideCollabLabel = preferences.getBoolean(ModuleConfig.KEY_HIDE_COLLAB_LABEL, false),
        hideCommercialLabels = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_COMMERCIAL_LABELS,
            false,
        ),
        hideCreativeToolAnchors = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_CREATIVE_TOOL_ANCHORS,
            false,
        ),
        hideMovieAnimeAnchors = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_MOVIE_ANIME_ANCHORS,
            false,
        ),
        hideIncentiveShare = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_INCENTIVE_SHARE,
            false,
        ),
        hideTako = preferences.getBoolean(ModuleConfig.KEY_HIDE_TAKO, false),
        hideContentSearch = preferences.getBoolean(ModuleConfig.KEY_HIDE_CONTENT_SEARCH, false),
        hideSafetyWarning = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_SAFETY_WARNING,
            false,
        ),
        hideStatusBar = preferences.getBoolean(ModuleConfig.KEY_HIDE_STATUS_BAR, false),
        hideLiveEntry = preferences.getBoolean(ModuleConfig.KEY_HIDE_LIVE_ENTRY, false),
        hideTopNavigation = preferences.getBoolean(ModuleConfig.KEY_HIDE_TOP_NAVIGATION, false),
        hideSearchEntry = preferences.getBoolean(ModuleConfig.KEY_HIDE_SEARCH_ENTRY, false),
        hideBottomNavigation = ModuleConfig.loadBottomNavigationHidden(preferences),
        hideVideoProgressBar = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_VIDEO_PROGRESS_BAR,
            false,
        ),
        hideTranslationControls = preferences.getBoolean(
            ModuleConfig.KEY_HIDE_TRANSLATION_CONTROLS,
            false,
        ),
        gpsSpoof = preferences.getBoolean(ModuleConfig.KEY_GPS_SPOOF, false),
        gpsLatitude = stringValue(ModuleConfig.KEY_GPS_LATITUDE, "0.0"),
        gpsLongitude = stringValue(ModuleConfig.KEY_GPS_LONGITUDE, "0.0"),
    )

    fun save(state: SettingsUiState) {
        write(preferences.edit(), state).apply()
    }

    fun connectRemote(onConnected: () -> Unit) {
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                try {
                    remotePreferences = service.getRemotePreferences(ModuleConfig.PREFS)
                    onConnected()
                } catch (error: RuntimeException) {
                    Log.e(TAG, "Unable to open LSPosed remote preferences", error)
                }
            }

            override fun onServiceDied(service: XposedService) {
                remotePreferences = null
            }
        })
    }

    fun syncRemote(state: SettingsUiState) {
        val target = remotePreferences ?: return
        try {
            if (!write(target.edit(), state).commit()) {
                Log.w(TAG, "LSPosed remote preferences rejected the settings update")
            }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to sync settings to LSPosed remote preferences", error)
        }
    }

    private fun write(editor: SharedPreferences.Editor, state: SettingsUiState) = editor
        .putBoolean(ModuleConfig.KEY_REGION_SPOOF, state.regionSpoof)
        .putString(ModuleConfig.KEY_REGION, state.region.code)
        .putBoolean(ModuleConfig.KEY_LANGUAGE_SPOOF, state.languageSpoof)
        .putBoolean(ModuleConfig.KEY_TIMEZONE_SPOOF, state.timeZoneSpoof)
        .putBoolean(ModuleConfig.KEY_SKIP_STARTUP_LOGIN, state.skipStartupLogin)
        .putBoolean(ModuleConfig.KEY_DOWNLOAD_RESTRICTIONS, state.downloadRestrictions)
        .putBoolean(ModuleConfig.KEY_HIDE_FEED_ADS, state.hideFeedAds)
        .putBoolean(ModuleConfig.KEY_HIDE_LIVE, state.hideLive)
        .putBoolean(ModuleConfig.KEY_HIDE_IMAGES, state.hideImages)
        .putBoolean(ModuleConfig.KEY_HIDE_AI_GENERATED, state.hideAiGenerated)
        .putBoolean(ModuleConfig.KEY_HIDE_TRENDING_TOPICS, state.hideTrendingTopics)
        .putBoolean(
            ModuleConfig.KEY_HIDE_CONTENT_CLASSIFICATION,
            state.hideContentClassification,
        )
        .putBoolean(ModuleConfig.KEY_FORCE_REGION, state.forceRegion)
        .putBoolean(ModuleConfig.KEY_HIDE_LONG_POSTS, state.hideLongPosts)
        .putBoolean(ModuleConfig.KEY_FILTER_VIEWS_LIKES, state.filterViewsLikes)
        .putBoolean(
            ModuleConfig.KEY_DISABLE_OFFLINE_COLD_CACHE_WITH_NETWORK,
            state.disableOfflineColdCacheWithNetwork,
        )
        .putBoolean(ModuleConfig.KEY_DISABLE_LOOP, state.disableLoop)
        .putBoolean(
            ModuleConfig.KEY_ALWAYS_SHOW_VIDEO_PROGRESS_BAR,
            state.alwaysShowVideoProgressBar,
        )
        .putBoolean(ModuleConfig.KEY_SHOW_AUTHOR_LOCATION, state.showAuthorLocation)
        .putFloat(
            ModuleConfig.KEY_DEFAULT_PLAYBACK_SPEED,
            PlaybackSpeed.sanitize(state.defaultPlaybackSpeed),
        )
        .putBoolean(ModuleConfig.KEY_AUTO_TRANSLATE_COMMENTS, state.autoTranslateComments)
        .putString(ModuleConfig.KEY_VIDEO_LOCATION, state.videoLocation)
        .putString(ModuleConfig.KEY_PIC_LOCATION, state.picLocation)
        .putString(ModuleConfig.KEY_GIF_LOCATION, state.gifLocation)
        .putBoolean(ModuleConfig.KEY_ALLOW_DUET, state.allowDuet)
        .putBoolean(ModuleConfig.KEY_ALLOW_STITCH, state.allowStitch)
        .putString(ModuleConfig.KEY_LONG_POST_SECONDS, state.longPostSeconds.toString())
        .putString(ModuleConfig.KEY_VIEWS_MIN, state.viewsMin.toString())
        .putString(ModuleConfig.KEY_VIEWS_MAX, (state.viewsMax ?: Long.MAX_VALUE).toString())
        .putString(ModuleConfig.KEY_LIKES_MIN, state.likesMin.toString())
        .putString(ModuleConfig.KEY_LIKES_MAX, (state.likesMax ?: Long.MAX_VALUE).toString())
        .putString(ModuleConfig.KEY_VIEWS_MIN_INPUT, state.viewsMinInput)
        .putString(ModuleConfig.KEY_VIEWS_MAX_INPUT, state.viewsMaxInput)
        .putString(ModuleConfig.KEY_LIKES_MIN_INPUT, state.likesMinInput)
        .putString(ModuleConfig.KEY_LIKES_MAX_INPUT, state.likesMaxInput)
        .putBoolean(ModuleConfig.KEY_HIDE_AUTHOR_AVATAR, state.hideAuthorAvatar)
        .putBoolean(ModuleConfig.KEY_HIDE_AUTHOR_INFO, state.hideAuthorInfo)
        .putBoolean(ModuleConfig.KEY_HIDE_FOLLOW_BUTTON, state.hideFollowButton)
        .putBoolean(ModuleConfig.KEY_HIDE_VIDEO_DESCRIPTION, state.hideVideoDescription)
        .putBoolean(ModuleConfig.KEY_HIDE_VIDEO_TAGS, state.hideVideoTags)
        .putBoolean(ModuleConfig.KEY_HIDE_MUSIC_TITLE, state.hideMusicTitle)
        .putBoolean(ModuleConfig.KEY_HIDE_MUSIC_COVER, state.hideMusicCover)
        .putBoolean(ModuleConfig.KEY_HIDE_LIKE_BUTTON, state.hideLikeButton)
        .putBoolean(ModuleConfig.KEY_HIDE_COMMENT_BUTTON, state.hideCommentButton)
        .putBoolean(ModuleConfig.KEY_HIDE_FAVORITE_BUTTON, state.hideFavoriteButton)
        .putBoolean(ModuleConfig.KEY_HIDE_SHARE_BUTTON, state.hideShareButton)
        .putBoolean(ModuleConfig.KEY_HIDE_DUET_BUTTON, state.hideDuetButton)
        .putBoolean(ModuleConfig.KEY_HIDE_STITCH_BUTTON, state.hideStitchButton)
        .putBoolean(ModuleConfig.KEY_HIDE_QUICK_DM, state.hideQuickDm)
        .putBoolean(ModuleConfig.KEY_HIDE_STORY_TAGS, state.hideStoryTags)
        .putBoolean(ModuleConfig.KEY_HIDE_COLLAB_LABEL, state.hideCollabLabel)
        .putBoolean(ModuleConfig.KEY_HIDE_COMMERCIAL_LABELS, state.hideCommercialLabels)
        .putBoolean(
            ModuleConfig.KEY_HIDE_CREATIVE_TOOL_ANCHORS,
            state.hideCreativeToolAnchors,
        )
        .putBoolean(
            ModuleConfig.KEY_HIDE_MOVIE_ANIME_ANCHORS,
            state.hideMovieAnimeAnchors,
        )
        .putBoolean(ModuleConfig.KEY_HIDE_INCENTIVE_SHARE, state.hideIncentiveShare)
        .putBoolean(ModuleConfig.KEY_HIDE_TAKO, state.hideTako)
        .putBoolean(ModuleConfig.KEY_HIDE_CONTENT_SEARCH, state.hideContentSearch)
        .putBoolean(ModuleConfig.KEY_HIDE_SAFETY_WARNING, state.hideSafetyWarning)
        .putBoolean(ModuleConfig.KEY_HIDE_STATUS_BAR, state.hideStatusBar)
        .putBoolean(ModuleConfig.KEY_HIDE_LIVE_ENTRY, state.hideLiveEntry)
        .putBoolean(ModuleConfig.KEY_HIDE_TOP_NAVIGATION, state.hideTopNavigation)
        .putBoolean(ModuleConfig.KEY_HIDE_SEARCH_ENTRY, state.hideSearchEntry)
        .putBoolean(ModuleConfig.KEY_HIDE_BOTTOM_NAVIGATION, state.hideBottomNavigation)
        .remove(ModuleConfig.KEY_LEGACY_HIDE_BOTTOM_HOME)
        .remove(ModuleConfig.KEY_LEGACY_HIDE_BOTTOM_FRIENDS)
        .remove(ModuleConfig.KEY_LEGACY_HIDE_BOTTOM_CREATE)
        .remove(ModuleConfig.KEY_LEGACY_HIDE_BOTTOM_INBOX)
        .remove(ModuleConfig.KEY_LEGACY_HIDE_BOTTOM_PROFILE)
        .putBoolean(ModuleConfig.KEY_HIDE_VIDEO_PROGRESS_BAR, state.hideVideoProgressBar)
        .putBoolean(ModuleConfig.KEY_HIDE_TRANSLATION_CONTROLS, state.hideTranslationControls)
        .putBoolean(ModuleConfig.KEY_GPS_SPOOF, state.gpsSpoof)
        .putString(ModuleConfig.KEY_GPS_LATITUDE, state.gpsLatitude)
        .putString(ModuleConfig.KEY_GPS_LONGITUDE, state.gpsLongitude)

    private fun stringValue(key: String, fallback: String): String =
        preferences.getString(key, fallback)?.trim().orEmpty().ifEmpty { fallback }

    private fun positiveInt(key: String, fallback: Int): Int =
        preferences.getString(key, null)?.toIntOrNull()?.takeIf { it > 0 } ?: fallback

    private fun nonNegativeLong(key: String, fallback: Long): Long =
        preferences.getString(key, null)?.toLongOrNull()?.takeIf { it >= 0 } ?: fallback

    private fun optionalPositiveLong(key: String): Long? =
        preferences.getString(key, null)
            ?.toLongOrNull()
            ?.takeIf { it > 0 && it != Long.MAX_VALUE }

    private fun metricInput(inputKey: String, valueKey: String, fallback: String): String =
        preferences.getString(inputKey, null)
            ?: preferences.getString(valueKey, fallback)
            ?: fallback

    private fun metricMaximumInput(inputKey: String, valueKey: String): String {
        preferences.getString(inputKey, null)?.let { return it }
        return preferences.getString(valueKey, null)
            ?.takeUnless { it == Long.MAX_VALUE.toString() }
            .orEmpty()
    }

    private companion object {
        const val TAG = "TokiSettings"
    }
}
