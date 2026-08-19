package com.seepd.toki;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Filters the stable public TikTok feed model without changing request or account code. */
final class FeedFilter {
    private final boolean hideAds;
    private final boolean hideLive;
    private final boolean hideImages;
    private final boolean hideAiGenerated;
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
        hideAiGenerated = config.hideAiGenerated;
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
            Object value = readField(feedItemList, "items", "awemeList");
            if (!(value instanceof List<?>)) {
                Method getItems = feedItemList.getClass().getMethod("getItems");
                value = getItems.invoke(feedItemList);
            }
            if (!(value instanceof List<?>)) {
                return;
            }

            Object filtered = filterListResult(value);
            if (filtered != value) {
                replaceItems(feedItemList, (List<?>) value, (List<?>) filtered);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // TikTok model signatures change across versions; leave that response untouched.
        }
    }

    /** Supports both List and concrete collection setters used across TikTok feed model versions. */
    static boolean replaceItems(Object owner, List<?> original, List<?> replacement) {
        for (Method method : owner.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (!"setItems".equals(method.getName())
                    || parameters.length != 1
                    || !parameters[0].isAssignableFrom(replacement.getClass())) {
                continue;
            }
            try {
                method.invoke(owner, replacement);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A different setter signature or the mutable-list fallback may still work.
            }
        }
        try {
            @SuppressWarnings("unchecked")
            List<Object> mutableItems = (List<Object>) original;
            mutableItems.clear();
            mutableItems.addAll(replacement);
            return true;
        } catch (UnsupportedOperationException | ClassCastException ignored) {
            return false;
        }
    }

    Object filterListResult(Object value) {
        if (!(value instanceof List<?>)) {
            return value;
        }
        List<?> original = (List<?>) value;
        int firstHiddenIndex = -1;
        for (int index = 0; index < original.size(); index++) {
            if (shouldHide(original.get(index))) {
                firstHiddenIndex = index;
                break;
            }
        }
        if (firstHiddenIndex < 0) {
            return value;
        }

        ArrayList<Object> kept = new ArrayList<>(original.size() - 1);
        for (int index = 0; index < firstHiddenIndex; index++) {
            kept.add(original.get(index));
        }
        for (int index = firstHiddenIndex + 1; index < original.size(); index++) {
            Object item = original.get(index);
            if (!shouldHide(item)) {
                kept.add(item);
            }
        }
        return kept;
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
        if (hideAiGenerated && isAiGeneratedContent(item)) {
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

    /** Uses TikTok's AIGC metadata rather than heuristics based on captions or video frames. */
    static boolean isAiGeneratedContent(Object item) {
        Object aigcInfo = callObject(item, "getAigcInfo");
        if (callBoolean(aigcInfo, "isCreateByAI")
                || callLong(aigcInfo, "getAIGCLabelType") > 0L) {
            return true;
        }
        Object moderationAigcInfo = callObject(item, "getModerationAigcInfo");
        return callLong(moderationAigcInfo, "getModerationAigcLabelType") > 0L;
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
        return isOutsideInclusiveRange(likes, likesMin, likesMax)
                || isOutsideInclusiveRange(views, viewsMin, viewsMax);
    }

    static boolean isOutsideInclusiveRange(long value, long minimum, long maximum) {
        return value < minimum || value > maximum;
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
            return readField(target, name, propertyName(name));
        }
    }

    /** TikTok feed models expose a mix of public getters and public/obfuscated fields. */
    private static Object readField(Object target, String... names) {
        Class<?> current = target.getClass();
        while (current != null) {
            for (String name : names) {
                if (name == null || name.isEmpty()) {
                    continue;
                }
                try {
                    java.lang.reflect.Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try the next field name or superclass.
                } catch (IllegalAccessException | RuntimeException ignored) {
                    // This field cannot be read in the current TikTok build.
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String propertyName(String accessorName) {
        if (accessorName.startsWith("get") && accessorName.length() > 3) {
            return Character.toLowerCase(accessorName.charAt(3)) + accessorName.substring(4);
        }
        if (accessorName.startsWith("is") && accessorName.length() > 2) {
            return Character.toLowerCase(accessorName.charAt(2)) + accessorName.substring(3);
        }
        return accessorName;
    }
}
