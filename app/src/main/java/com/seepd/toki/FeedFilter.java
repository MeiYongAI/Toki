package com.seepd.toki;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Filters the stable public TikTok feed model without changing request or account code. */
final class FeedFilter {
    private final boolean hideAds;
    private final boolean hideLive;
    private final boolean hideImages;
    private final boolean forceRegion;
    private final boolean hideLongPosts;
    private final boolean filterViewsLikes;
    private final String regionCode;
    private final int longPostSeconds;
    private final long viewsMin;
    private final long viewsMax;
    private final long likesMin;
    private final long likesMax;

    FeedFilter(ModuleConfig config) {
        hideAds = config.hideFeedAds;
        hideLive = config.hideLive;
        hideImages = config.hideImages;
        forceRegion = config.forceRegion;
        hideLongPosts = config.hideLongPosts;
        filterViewsLikes = config.filterViewsLikes;
        regionCode = config.region.code;
        longPostSeconds = config.longPostSeconds;
        viewsMin = config.viewsMin;
        viewsMax = config.viewsMax;
        likesMin = config.likesMin;
        likesMax = config.likesMax;
    }

    void apply(Object feedItemList) {
        if (feedItemList == null) {
            return;
        }
        try {
            Method getItems = feedItemList.getClass().getMethod("getItems");
            Object value = getItems.invoke(feedItemList);
            if (!(value instanceof List<?>)) {
                return;
            }

            Object filtered = filterListResult(value);
            if (filtered != value) {
                feedItemList.getClass().getMethod("setItems", List.class).invoke(feedItemList, filtered);
            }
        } catch (ReflectiveOperationException ignored) {
            // TikTok model signatures change across versions; leave that response untouched.
        }
    }

    Object filterListResult(Object value) {
        if (!(value instanceof List<?>)) {
            return value;
        }
        List<?> original = (List<?>) value;
        ArrayList<Object> kept = new ArrayList<>(original.size());
        for (Object item : original) {
            if (!shouldHide(item)) {
                kept.add(item);
            }
        }
        return kept.size() == original.size() ? value : kept;
    }

    void applyBanners(Object bannerList) {
        if (!hideAds || bannerList == null) {
            return;
        }
        try {
            Object value = callObject(bannerList, "getItems");
            if (!(value instanceof List<?>)) {
                value = bannerList.getClass().getField("items").get(bannerList);
            }
            if (!(value instanceof List<?>)) {
                return;
            }
            List<?> original = (List<?>) value;
            ArrayList<Object> kept = new ArrayList<>(original.size());
            for (Object banner : original) {
                if (!callBoolean(banner, "isAd")) {
                    kept.add(banner);
                }
            }
            if (kept.size() == original.size()) {
                return;
            }
            try {
                bannerList.getClass().getMethod("setItems", List.class).invoke(bannerList, kept);
            } catch (ReflectiveOperationException ignored) {
                @SuppressWarnings("unchecked")
                List<Object> mutableItems = (List<Object>) original;
                mutableItems.clear();
                mutableItems.addAll(kept);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Discover model signatures and mutability differ across versions.
        }
    }

    void clearProfileAds(Object result) {
        if (!hideAds || result == null
                || !"com.ss.android.ugc.aweme.commercialize.profile.impl.ad.CommerceProfileAdResponse"
                .equals(result.getClass().getName())) {
            return;
        }
        try {
            java.lang.reflect.Field field = result.getClass().getDeclaredField("awemeList");
            field.setAccessible(true);
            Object value = field.get(result);
            if (value instanceof List<?>) {
                ((List<?>) value).clear();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The profile-ad response is version-specific and may expose an immutable list.
        }
    }

    private boolean shouldHide(Object item) {
        if (item == null) {
            return false;
        }
        if (hideAds && (callBoolean(item, "isAd")
                || callBoolean(item, "withFakeUser")
                || callBoolean(item, "isWithPromotionalMusic"))) {
            return true;
        }
        if (hideLive && (callBoolean(item, "isLive")
                || callLong(item, "getLiveId") != 0L
                || callObject(item, "getLiveType") != null
                || callObject(item, "getRoomFeedCellStruct") != null)) {
            return true;
        }
        if (hideImages && (callBoolean(item, "isImage")
                || callBoolean(item, "isPhotoMode")
                || hasItems(item, "getImageInfos")
                || callObject(item, "getPhotoModeImageInfo") != null)) {
            return true;
        }
        if (forceRegion && shouldHideForRegion(item)) {
            return true;
        }
        if (hideLongPosts && shouldHideLongPost(item)) {
            return true;
        }
        return filterViewsLikes && shouldHideForCounts(item);
    }

    private boolean shouldHideForRegion(Object item) {
        Object value = callObject(item, "getRegion");
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            return false;
        }
        return !regionCode.equalsIgnoreCase((String) value);
    }

    private boolean shouldHideLongPost(Object item) {
        Object video = callObject(item, "getVideo");
        long milliseconds = callLong(video, "getDuration");
        if (milliseconds == 0L) {
            milliseconds = callLong(video, "getVideoLength");
        }
        return milliseconds / 1000L > longPostSeconds;
    }

    private boolean shouldHideForCounts(Object item) {
        Object statistics = callObject(item, "getStatistics");
        if (statistics == null) {
            return false;
        }
        long likes = callLong(statistics, "getDiggCount");
        long views = callLong(statistics, "getPlayCount");
        return likes <= likesMin || likes >= likesMax || views <= viewsMin || views >= viewsMax;
    }

    private static boolean callBoolean(Object target, String name) {
        Object value = callObject(target, name);
        return value instanceof Boolean && (Boolean) value;
    }

    private static long callLong(Object target, String name) {
        Object value = callObject(target, name);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static boolean hasItems(Object target, String name) {
        Object value = callObject(target, name);
        return value instanceof List<?> && !((List<?>) value).isEmpty();
    }

    private static Object callObject(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
