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
        downloadRestrictions = preferences.getBoolean(
            ModuleConfig.KEY_DOWNLOAD_RESTRICTIONS,
            false,
        ),
        hideFeedAds = preferences.getBoolean(ModuleConfig.KEY_HIDE_FEED_ADS, false),
        hideLive = preferences.getBoolean(ModuleConfig.KEY_HIDE_LIVE, false),
        hideImages = preferences.getBoolean(ModuleConfig.KEY_HIDE_IMAGES, false),
        forceRegion = preferences.getBoolean(ModuleConfig.KEY_FORCE_REGION, false),
        hideLongPosts = preferences.getBoolean(ModuleConfig.KEY_HIDE_LONG_POSTS, false),
        filterViewsLikes = preferences.getBoolean(
            ModuleConfig.KEY_FILTER_VIEWS_LIKES,
            false,
        ),
        disableLoop = preferences.getBoolean(ModuleConfig.KEY_DISABLE_LOOP, false),
        defaultPlaybackSpeed = PlaybackSpeed.sanitize(
            preferences.getFloat(
                ModuleConfig.KEY_DEFAULT_PLAYBACK_SPEED,
                ModuleConfig.DEFAULT_PLAYBACK_SPEED,
            ),
        ),
        antiBurnIn = preferences.getBoolean(ModuleConfig.KEY_ANTI_BURN_IN, false),
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
        .putBoolean(ModuleConfig.KEY_DOWNLOAD_RESTRICTIONS, state.downloadRestrictions)
        .putBoolean(ModuleConfig.KEY_HIDE_FEED_ADS, state.hideFeedAds)
        .putBoolean(ModuleConfig.KEY_HIDE_LIVE, state.hideLive)
        .putBoolean(ModuleConfig.KEY_HIDE_IMAGES, state.hideImages)
        .putBoolean(ModuleConfig.KEY_FORCE_REGION, state.forceRegion)
        .putBoolean(ModuleConfig.KEY_HIDE_LONG_POSTS, state.hideLongPosts)
        .putBoolean(ModuleConfig.KEY_FILTER_VIEWS_LIKES, state.filterViewsLikes)
        .putBoolean(ModuleConfig.KEY_DISABLE_LOOP, state.disableLoop)
        .putFloat(
            ModuleConfig.KEY_DEFAULT_PLAYBACK_SPEED,
            PlaybackSpeed.sanitize(state.defaultPlaybackSpeed),
        )
        .putBoolean(ModuleConfig.KEY_ANTI_BURN_IN, state.antiBurnIn)
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

    private companion object {
        const val TAG = "TokiSettings"
    }
}
