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
    static final String KEY_LANGUAGE_SPOOF = "language_spoof";
    static final String KEY_TIMEZONE_SPOOF = "timezone_spoof";
    static final String KEY_SKIP_STARTUP_LOGIN = "skip_startup_login";
    static final String KEY_DOWNLOAD_RESTRICTIONS = "download_restrictions";
    static final String KEY_HIDE_FEED_ADS = "hide_feed_ads";
    static final String KEY_HIDE_LIVE = "hide_live";
    static final String KEY_HIDE_IMAGES = "hide_images";
    static final String KEY_HIDE_AI_GENERATED = "hide_ai_generated";
    static final String KEY_HIDE_TRENDING_TOPICS = "hide_trending_topics";
    static final String KEY_HIDE_CONTENT_CLASSIFICATION = "hide_content_classification";
    static final String KEY_FORCE_REGION = "force_region";
    static final String KEY_HIDE_LONG_POSTS = "hide_long_posts";
    static final String KEY_FILTER_VIEWS_LIKES = "filter_views_likes";
    static final String KEY_DISABLE_OFFLINE_COLD_CACHE_WITH_NETWORK =
            "disable_offline_cold_cache_with_network";
    static final String KEY_DISABLE_LOOP = "disable_loop";
    static final String KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed";
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
    static final String KEY_VIEWS_MIN_INPUT = "views_min_input";
    static final String KEY_VIEWS_MAX_INPUT = "views_max_input";
    static final String KEY_LIKES_MIN_INPUT = "likes_min_input";
    static final String KEY_LIKES_MAX_INPUT = "likes_max_input";
    static final String KEY_HIDE_AUTHOR_INFO = "hide_author_info";
    static final String KEY_HIDE_FOLLOW_BUTTON = "hide_follow_button";
    static final String KEY_HIDE_VIDEO_DESCRIPTION = "hide_video_description";
    static final String KEY_HIDE_VIDEO_TAGS = "hide_video_tags";
    static final String KEY_HIDE_MUSIC_TITLE = "hide_music_title";
    static final String KEY_HIDE_MUSIC_COVER = "hide_music_cover";
    static final String KEY_HIDE_LIKE_BUTTON = "hide_like_button";
    static final String KEY_HIDE_COMMENT_BUTTON = "hide_comment_button";
    static final String KEY_HIDE_FAVORITE_BUTTON = "hide_favorite_button";
    static final String KEY_HIDE_SHARE_BUTTON = "hide_share_button";
    static final String KEY_HIDE_DUET_BUTTON = "hide_duet_button";
    static final String KEY_HIDE_STITCH_BUTTON = "hide_stitch_button";
    static final String KEY_HIDE_QUICK_DM = "hide_quick_dm";
    static final String KEY_HIDE_STORY_TAGS = "hide_story_tags";
    static final String KEY_HIDE_COLLAB_LABEL = "hide_collab_label";
    static final String KEY_HIDE_TAKO = "hide_tako";
    static final String KEY_HIDE_CONTENT_SEARCH = "hide_content_search";
    static final String KEY_HIDE_TOP_NAVIGATION = "hide_top_navigation";
    static final String KEY_HIDE_SEARCH_ENTRY = "hide_search_entry";
    static final String KEY_HIDE_BOTTOM_NAVIGATION = "hide_bottom_navigation";
    static final String KEY_LEGACY_HIDE_BOTTOM_HOME = "hide_bottom_home";
    static final String KEY_LEGACY_HIDE_BOTTOM_FRIENDS = "hide_bottom_friends";
    static final String KEY_LEGACY_HIDE_BOTTOM_CREATE = "hide_bottom_create";
    static final String KEY_LEGACY_HIDE_BOTTOM_INBOX = "hide_bottom_inbox";
    static final String KEY_LEGACY_HIDE_BOTTOM_PROFILE = "hide_bottom_profile";
    static final String KEY_HIDE_VIDEO_PROGRESS_BAR = "hide_video_progress_bar";
    static final String KEY_HIDE_TRANSLATION_CONTROLS = "hide_translation_controls";
    static final String KEY_GPS_SPOOF = "gps_spoof";
    static final String KEY_GPS_LATITUDE = "gps_latitude";
    static final String KEY_GPS_LONGITUDE = "gps_longitude";
    static final float DEFAULT_PLAYBACK_SPEED = PlaybackSpeed.DEFAULT;

    private static final Uri SETTINGS_URI = Uri.parse("content://com.seepd.toki.settings");
    private static final String METHOD_GET_COMMENT_TRANSLATION_STATE =
            "getCommentTranslationState";
    private static final String METHOD_SET_COMMENT_TRANSLATION_STATE =
            "setCommentTranslationState";

    final boolean regionSpoof;
    final RegionPreset region;
    final boolean languageSpoof;
    final boolean timeZoneSpoof;
    final boolean skipStartupLogin;
    final boolean removeDownloadRestrictions;
    final boolean hideFeedAds;
    final boolean hideLive;
    final boolean hideImages;
    final boolean hideAiGenerated;
    final boolean hideTrendingTopics;
    final boolean hideContentClassification;
    final boolean forceRegion;
    final boolean hideLongPosts;
    final boolean filterViewsLikes;
    final boolean disableOfflineColdCacheWithNetwork;
    final boolean disableLoop;
    final float defaultPlaybackSpeed;
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
    final boolean hideAuthorInfo;
    final boolean hideFollowButton;
    final boolean hideVideoDescription;
    final boolean hideVideoTags;
    final boolean hideMusicTitle;
    final boolean hideMusicCover;
    final boolean hideLikeButton;
    final boolean hideCommentButton;
    final boolean hideFavoriteButton;
    final boolean hideShareButton;
    final boolean hideDuetButton;
    final boolean hideStitchButton;
    final boolean hideQuickDm;
    final boolean hideStoryTags;
    final boolean hideCollabLabel;
    final boolean hideTako;
    final boolean hideContentSearch;
    final boolean hideTopNavigation;
    final boolean hideSearchEntry;
    final boolean hideBottomNavigation;
    final boolean hideVideoProgressBar;
    final boolean hideTranslationControls;
    final boolean gpsSpoof;
    final double gpsLatitude;
    final double gpsLongitude;

    ModuleConfig(boolean regionSpoof, RegionPreset region, boolean languageSpoof,
                 boolean timeZoneSpoof, boolean skipStartupLogin,
                 boolean removeDownloadRestrictions,
                 boolean hideFeedAds, boolean hideLive, boolean hideImages,
                 boolean hideAiGenerated, boolean hideTrendingTopics,
                 boolean hideContentClassification, boolean forceRegion, boolean hideLongPosts,
                 boolean filterViewsLikes, boolean disableOfflineColdCacheWithNetwork,
                 boolean disableLoop, float defaultPlaybackSpeed, boolean autoTranslateComments,
                 String videoLocation, String picLocation, String gifLocation,
                 boolean allowDuet, boolean allowStitch,
                 int longPostSeconds, long viewsMin, long viewsMax, long likesMin, long likesMax,
                 boolean hideAuthorInfo, boolean hideFollowButton, boolean hideVideoDescription,
                 boolean hideVideoTags, boolean hideMusicTitle, boolean hideMusicCover,
                 boolean hideLikeButton, boolean hideCommentButton, boolean hideFavoriteButton,
                 boolean hideShareButton, boolean hideDuetButton, boolean hideStitchButton,
                 boolean hideQuickDm, boolean hideStoryTags, boolean hideCollabLabel,
                 boolean hideTako, boolean hideContentSearch, boolean hideTopNavigation, boolean hideSearchEntry,
                 boolean hideBottomNavigation, boolean hideVideoProgressBar,
                 boolean hideTranslationControls, boolean gpsSpoof,
                 double gpsLatitude, double gpsLongitude) {
        this.regionSpoof = regionSpoof;
        this.region = region;
        this.languageSpoof = languageSpoof;
        this.timeZoneSpoof = timeZoneSpoof;
        this.skipStartupLogin = skipStartupLogin;
        this.removeDownloadRestrictions = removeDownloadRestrictions;
        this.hideFeedAds = hideFeedAds;
        this.hideLive = hideLive;
        this.hideImages = hideImages;
        this.hideAiGenerated = hideAiGenerated;
        this.hideTrendingTopics = hideTrendingTopics;
        this.hideContentClassification = hideContentClassification;
        this.forceRegion = forceRegion;
        this.hideLongPosts = hideLongPosts;
        this.filterViewsLikes = filterViewsLikes;
        this.disableOfflineColdCacheWithNetwork = disableOfflineColdCacheWithNetwork;
        this.disableLoop = disableLoop;
        this.defaultPlaybackSpeed = PlaybackSpeed.sanitize(defaultPlaybackSpeed);
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
        this.hideAuthorInfo = hideAuthorInfo;
        this.hideFollowButton = hideFollowButton;
        this.hideVideoDescription = hideVideoDescription;
        this.hideVideoTags = hideVideoTags;
        this.hideMusicTitle = hideMusicTitle;
        this.hideMusicCover = hideMusicCover;
        this.hideLikeButton = hideLikeButton;
        this.hideCommentButton = hideCommentButton;
        this.hideFavoriteButton = hideFavoriteButton;
        this.hideShareButton = hideShareButton;
        this.hideDuetButton = hideDuetButton;
        this.hideStitchButton = hideStitchButton;
        this.hideQuickDm = hideQuickDm;
        this.hideStoryTags = hideStoryTags;
        this.hideCollabLabel = hideCollabLabel;
        this.hideTako = hideTako;
        this.hideContentSearch = hideContentSearch;
        this.hideTopNavigation = hideTopNavigation;
        this.hideSearchEntry = hideSearchEntry;
        this.hideBottomNavigation = hideBottomNavigation;
        this.hideVideoProgressBar = hideVideoProgressBar;
        this.hideTranslationControls = hideTranslationControls;
        this.gpsSpoof = gpsSpoof;
        this.gpsLatitude = clampCoordinate(gpsLatitude, -90.0, 90.0, 0.0);
        this.gpsLongitude = clampCoordinate(gpsLongitude, -180.0, 180.0, 0.0);
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
                preferences.getBoolean(KEY_LANGUAGE_SPOOF, false),
                preferences.getBoolean(KEY_TIMEZONE_SPOOF, false),
                preferences.getBoolean(KEY_SKIP_STARTUP_LOGIN, false),
                preferences.getBoolean(KEY_DOWNLOAD_RESTRICTIONS, false),
                preferences.getBoolean(KEY_HIDE_FEED_ADS, false),
                preferences.getBoolean(KEY_HIDE_LIVE, false),
                preferences.getBoolean(KEY_HIDE_IMAGES, false),
                preferences.getBoolean(KEY_HIDE_AI_GENERATED, false),
                preferences.getBoolean(KEY_HIDE_TRENDING_TOPICS, false),
                preferences.getBoolean(KEY_HIDE_CONTENT_CLASSIFICATION, false),
                preferences.getBoolean(KEY_FORCE_REGION, false),
                preferences.getBoolean(KEY_HIDE_LONG_POSTS, false),
                preferences.getBoolean(KEY_FILTER_VIEWS_LIKES, false),
                preferences.getBoolean(KEY_DISABLE_OFFLINE_COLD_CACHE_WITH_NETWORK, false),
                preferences.getBoolean(KEY_DISABLE_LOOP, false),
                preferences.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, DEFAULT_PLAYBACK_SPEED),
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
                positiveLong(preferences.getString(KEY_LIKES_MAX, Long.toString(Long.MAX_VALUE)), Long.MAX_VALUE),
                preferences.getBoolean(KEY_HIDE_AUTHOR_INFO, false),
                preferences.getBoolean(KEY_HIDE_FOLLOW_BUTTON, false),
                preferences.getBoolean(KEY_HIDE_VIDEO_DESCRIPTION, false),
                preferences.getBoolean(KEY_HIDE_VIDEO_TAGS, false),
                preferences.getBoolean(KEY_HIDE_MUSIC_TITLE, false),
                preferences.getBoolean(KEY_HIDE_MUSIC_COVER, false),
                preferences.getBoolean(KEY_HIDE_LIKE_BUTTON, false),
                preferences.getBoolean(KEY_HIDE_COMMENT_BUTTON, false),
                preferences.getBoolean(KEY_HIDE_FAVORITE_BUTTON, false),
                preferences.getBoolean(KEY_HIDE_SHARE_BUTTON, false),
                preferences.getBoolean(KEY_HIDE_DUET_BUTTON, false),
                preferences.getBoolean(KEY_HIDE_STITCH_BUTTON, false),
                preferences.getBoolean(KEY_HIDE_QUICK_DM, false),
                preferences.getBoolean(KEY_HIDE_STORY_TAGS, false),
                preferences.getBoolean(KEY_HIDE_COLLAB_LABEL, false),
                preferences.getBoolean(KEY_HIDE_TAKO, false),
                preferences.getBoolean(KEY_HIDE_CONTENT_SEARCH, false),
                preferences.getBoolean(KEY_HIDE_TOP_NAVIGATION, false),
                preferences.getBoolean(KEY_HIDE_SEARCH_ENTRY, false),
                loadBottomNavigationHidden(preferences),
                preferences.getBoolean(KEY_HIDE_VIDEO_PROGRESS_BAR, false),
                preferences.getBoolean(KEY_HIDE_TRANSLATION_CONTROLS, false),
                preferences.getBoolean(KEY_GPS_SPOOF, false),
                parseCoordinate(preferences.getString(KEY_GPS_LATITUDE, "0"), -90.0, 90.0),
                parseCoordinate(preferences.getString(KEY_GPS_LONGITUDE, "0"), -180.0, 180.0)
        );
    }

    static ModuleConfig defaults() {
        return new ModuleConfig(false, RegionPreset.US, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false,
                PlaybackSpeed.DEFAULT, false,
                "Movies/TikTok", "Pictures/TikTok", "Movies/TikTok",
                false, false,
                60, 0, Long.MAX_VALUE, 0, Long.MAX_VALUE,
                 false, false, false, false, false, false, false, false,
                false, false, false, false, false, false, false, false,
                 false, false, false, false, false, false,
                 false, 0.0, 0.0);
    }

    boolean hasPagePurificationEnabled() {
        return hideAuthorInfo || hideFollowButton || hideVideoDescription || hideVideoTags
                || hideMusicTitle || hideMusicCover || hideLikeButton || hideCommentButton
                || hideFavoriteButton || hideShareButton || hideDuetButton || hideStitchButton
                || hideQuickDm || hideStoryTags || hideCollabLabel || hideTako || hideContentSearch
                || hideTranslationControls;
    }

    boolean hasGlobalNavigationPurificationEnabled() {
        return hideTopNavigation || hideSearchEntry || hideBottomNavigation || hideVideoProgressBar;
    }

    static boolean loadBottomNavigationHidden(SharedPreferences preferences) {
        if (preferences.contains(KEY_HIDE_BOTTOM_NAVIGATION)) {
            return preferences.getBoolean(KEY_HIDE_BOTTOM_NAVIGATION, false);
        }
        return preferences.getBoolean(KEY_LEGACY_HIDE_BOTTOM_HOME, false)
                || preferences.getBoolean(KEY_LEGACY_HIDE_BOTTOM_FRIENDS, false)
                || preferences.getBoolean(KEY_LEGACY_HIDE_BOTTOM_CREATE, false)
                || preferences.getBoolean(KEY_LEGACY_HIDE_BOTTOM_INBOX, false)
                || preferences.getBoolean(KEY_LEGACY_HIDE_BOTTOM_PROFILE, false);
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

    private static double parseCoordinate(String value, double minimum, double maximum) {
        try {
            return clampCoordinate(Double.parseDouble(value), minimum, maximum, 0.0);
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }

    private static double clampCoordinate(
            double value, double minimum, double maximum, double fallback) {
        return Double.isNaN(value) || Double.isInfinite(value)
                ? fallback : Math.max(minimum, Math.min(maximum, value));
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
