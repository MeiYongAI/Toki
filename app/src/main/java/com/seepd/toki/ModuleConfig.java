package com.seepd.toki;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

final class ModuleConfig {
    static final String TARGET_PACKAGE = "com.zhiliaoapp.musically";
    static final String PREFS = "module_settings";
    static final String KEY_REGION_SPOOF = "region_spoof";
    static final String KEY_REGION = "region";
    static final String KEY_DOWNLOAD_RESTRICTIONS = "download_restrictions";
    static final String KEY_HIDE_FEED_ADS = "hide_feed_ads";
    static final String KEY_HIDE_LIVE = "hide_live";
    static final String KEY_HIDE_IMAGES = "hide_images";
    static final String KEY_FORCE_REGION = "force_region";
    static final String KEY_HIDE_LONG_POSTS = "hide_long_posts";
    static final String KEY_FILTER_VIEWS_LIKES = "filter_views_likes";
    static final String KEY_DISABLE_LOOP = "disable_loop";
    static final String KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed";
    static final String KEY_ANTI_BURN_IN = "anti_burn_in";
    static final String KEY_AUTO_TRANSLATE_COMMENTS = "auto_translate_comments";
    static final String KEY_COMMENT_TRANSLATION_ACTIVE = "comment_translation_active";
    static final String KEY_VIDEO_LOCATION = "video_location";
    static final String KEY_PIC_LOCATION = "pic_location";
    static final String KEY_GIF_LOCATION = "gif_location";
    static final String KEY_ALLOW_DUET = "allow_duet";
    static final String KEY_ALLOW_STITCH = "allow_stitch";
    static final String KEY_LONG_POST_SECONDS = "long_post_seconds";
    static final String KEY_VIEWS_MIN = "views_min";
    static final String KEY_VIEWS_MAX = "views_max";
    static final String KEY_LIKES_MIN = "likes_min";
    static final String KEY_LIKES_MAX = "likes_max";
    static final float DEFAULT_PLAYBACK_SPEED = PlaybackSpeed.DEFAULT;

    private static final Uri SETTINGS_URI = Uri.parse("content://com.seepd.toki.settings");
    private static final String METHOD_GET_COMMENT_TRANSLATION_STATE =
            "getCommentTranslationState";
    private static final String METHOD_SET_COMMENT_TRANSLATION_STATE =
            "setCommentTranslationState";

    final boolean regionSpoof;
    final RegionPreset region;
    final boolean removeDownloadRestrictions;
    final boolean hideFeedAds;
    final boolean hideLive;
    final boolean hideImages;
    final boolean forceRegion;
    final boolean hideLongPosts;
    final boolean filterViewsLikes;
    final boolean disableLoop;
    final float defaultPlaybackSpeed;
    final boolean antiBurnIn;
    final boolean autoTranslateComments;
    final String videoLocation;
    final String picLocation;
    final String gifLocation;
    final boolean allowDuet;
    final boolean allowStitch;
    final int longPostSeconds;
    final long viewsMin;
    final long viewsMax;
    final long likesMin;
    final long likesMax;

    ModuleConfig(boolean regionSpoof, RegionPreset region, boolean removeDownloadRestrictions,
                 boolean hideFeedAds, boolean hideLive, boolean hideImages,
                 boolean forceRegion, boolean hideLongPosts, boolean filterViewsLikes,
                 boolean disableLoop, float defaultPlaybackSpeed, boolean antiBurnIn,
                 boolean autoTranslateComments,
                 String videoLocation, String picLocation, String gifLocation,
                 boolean allowDuet, boolean allowStitch,
                 int longPostSeconds, long viewsMin, long viewsMax, long likesMin, long likesMax) {
        this.regionSpoof = regionSpoof;
        this.region = region;
        this.removeDownloadRestrictions = removeDownloadRestrictions;
        this.hideFeedAds = hideFeedAds;
        this.hideLive = hideLive;
        this.hideImages = hideImages;
        this.forceRegion = forceRegion;
        this.hideLongPosts = hideLongPosts;
        this.filterViewsLikes = filterViewsLikes;
        this.disableLoop = disableLoop;
        this.defaultPlaybackSpeed = PlaybackSpeed.sanitize(defaultPlaybackSpeed);
        this.antiBurnIn = antiBurnIn;
        this.autoTranslateComments = autoTranslateComments;
        this.videoLocation = nonEmpty(videoLocation, "Movies/TikTok");
        this.picLocation = nonEmpty(picLocation, "Pictures/TikTok");
        this.gifLocation = nonEmpty(gifLocation, "Movies/TikTok");
        this.allowDuet = allowDuet;
        this.allowStitch = allowStitch;
        this.longPostSeconds = longPostSeconds;
        this.viewsMin = viewsMin;
        this.viewsMax = viewsMax;
        this.likesMin = likesMin;
        this.likesMax = likesMax;
    }

    static boolean loadCommentTranslationActive(Context context) {
        try {
            Bundle result = context.getContentResolver().call(
                    SETTINGS_URI, METHOD_GET_COMMENT_TRANSLATION_STATE, null, null);
            return result != null
                    && result.getBoolean(KEY_COMMENT_TRANSLATION_ACTIVE, false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static boolean saveCommentTranslationActive(Context context, boolean active) {
        try {
            Bundle extras = new Bundle();
            extras.putBoolean(KEY_COMMENT_TRANSLATION_ACTIVE, active);
            Bundle result = context.getContentResolver().call(
                    SETTINGS_URI, METHOD_SET_COMMENT_TRANSLATION_STATE, null, extras);
            return result != null && result.getBoolean("saved", false);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static ModuleConfig fromPreferences(SharedPreferences preferences) {
        return new ModuleConfig(
                preferences.getBoolean(KEY_REGION_SPOOF, false),
                RegionPreset.fromCode(preferences.getString(KEY_REGION, RegionPreset.US.code)),
                preferences.getBoolean(KEY_DOWNLOAD_RESTRICTIONS, false),
                preferences.getBoolean(KEY_HIDE_FEED_ADS, false),
                preferences.getBoolean(KEY_HIDE_LIVE, false),
                preferences.getBoolean(KEY_HIDE_IMAGES, false),
                preferences.getBoolean(KEY_FORCE_REGION, false),
                preferences.getBoolean(KEY_HIDE_LONG_POSTS, false),
                preferences.getBoolean(KEY_FILTER_VIEWS_LIKES, false),
                preferences.getBoolean(KEY_DISABLE_LOOP, false),
                preferences.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED),
                preferences.getBoolean(KEY_ANTI_BURN_IN, false),
                preferences.getBoolean(KEY_AUTO_TRANSLATE_COMMENTS, false),
                preferences.getString(KEY_VIDEO_LOCATION, "Movies/TikTok"),
                preferences.getString(KEY_PIC_LOCATION, "Pictures/TikTok"),
                preferences.getString(KEY_GIF_LOCATION, "Movies/TikTok"),
                preferences.getBoolean(KEY_ALLOW_DUET, false),
                preferences.getBoolean(KEY_ALLOW_STITCH, false),
                positiveInt(preferences.getString(KEY_LONG_POST_SECONDS, "60"), 60),
                nonNegativeLong(preferences.getString(KEY_VIEWS_MIN, "0"), 0),
                positiveLong(preferences.getString(KEY_VIEWS_MAX, Long.toString(Long.MAX_VALUE)), Long.MAX_VALUE),
                nonNegativeLong(preferences.getString(KEY_LIKES_MIN, "0"), 0),
                positiveLong(preferences.getString(KEY_LIKES_MAX, Long.toString(Long.MAX_VALUE)), Long.MAX_VALUE)
        );
    }

    static ModuleConfig defaults() {
        return new ModuleConfig(false, RegionPreset.US, false, false, false, false,
                false, false, false, false, PlaybackSpeed.DEFAULT, false, false,
                "Movies/TikTok", "Pictures/TikTok", "Movies/TikTok",
                false, false,
                60, 0, Long.MAX_VALUE, 0, Long.MAX_VALUE);
    }

    private static int positiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static long nonNegativeLong(String value, long fallback) {
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long positiveLong(String value, long fallback) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

}
