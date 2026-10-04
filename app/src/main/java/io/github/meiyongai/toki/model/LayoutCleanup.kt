package io.github.meiyongai.toki.model

import io.github.meiyongai.toki.R

/** 页面净化的三个独立配置区域；名称由资源系统本地化。 */
enum class LayoutGroup(val title: Int) {
    TOP(R.string.layout_top), BOTTOM(R.string.layout_bottom), SIDE(R.string.layout_side)
}

/**
 * 页面控件的配置与展示目录，不保存宿主混淆类名或资源编号。
 * @param group 所属区域。
 * @param title 本地化名称。
 * @param tag 原生导航标识；非导航控件为 null。
 * Callers: ConfigSchema、LayoutCleanupSection、LayoutCleanupHook。
 */
enum class LayoutElement(val group: LayoutGroup, val title: Int, val tag: String? = null) {
    TOP_SEARCH(LayoutGroup.TOP, R.string.layout_search, "search"),
    TOP_LIVE(LayoutGroup.TOP, R.string.layout_live, "live"),
    TOP_FRIENDS(LayoutGroup.TOP, R.string.layout_friends, "FRIENDS_FEED"),
    TOP_EXPLORE(LayoutGroup.TOP, R.string.layout_explore, "homepage_explore"),
    TOP_FOLLOWING(LayoutGroup.TOP, R.string.layout_following, "Following"),
    TOP_SHOP(LayoutGroup.TOP, R.string.layout_shop, "Shop"),
    TOP_FOR_YOU(LayoutGroup.TOP, R.string.layout_for_you, "For You"),
    TOP_NEARBY(LayoutGroup.TOP, R.string.layout_nearby, "Nearby"),
    TOP_STEM(LayoutGroup.TOP, R.string.layout_stem, "Stem"),
    TOP_SERIES(LayoutGroup.TOP, R.string.layout_series, "Drama"),
    BOTTOM_HOME(LayoutGroup.BOTTOM, R.string.layout_home, "HOME"),
    BOTTOM_FRIENDS(LayoutGroup.BOTTOM, R.string.layout_friends, "FRIENDS_TAB"),
    BOTTOM_SHOP(LayoutGroup.BOTTOM, R.string.layout_shop, "SHOP_MALL"),
    BOTTOM_CREATE(LayoutGroup.BOTTOM, R.string.layout_create, "PUBLISH"),
    BOTTOM_INBOX(LayoutGroup.BOTTOM, R.string.layout_inbox, "NOTIFICATION"),
    BOTTOM_PROFILE(LayoutGroup.BOTTOM, R.string.layout_profile, "USER"),
    BOTTOM_DISCOVER(LayoutGroup.BOTTOM, R.string.layout_discover, "DISCOVER"),
    BOTTOM_EXPLORE(LayoutGroup.BOTTOM, R.string.layout_explore, "homepage_explore"),
    AVATAR(LayoutGroup.SIDE, R.string.layout_avatar),
    FOLLOW(LayoutGroup.SIDE, R.string.layout_follow),
    LIKE(LayoutGroup.SIDE, R.string.layout_like),
    COMMENT(LayoutGroup.SIDE, R.string.layout_comment),
    FAVORITE(LayoutGroup.SIDE, R.string.layout_favorite),
    SHARE(LayoutGroup.SIDE, R.string.layout_share),
    MUSIC(LayoutGroup.SIDE, R.string.layout_music),
    AUTHOR(LayoutGroup.SIDE, R.string.layout_author),
    DESCRIPTION(LayoutGroup.SIDE, R.string.layout_description),
    MUSIC_TITLE(LayoutGroup.SIDE, R.string.layout_music_title),
    TAGS(LayoutGroup.SIDE, R.string.layout_tags),
    SEARCH_LABEL(LayoutGroup.SIDE, R.string.layout_search_label),
    AUTO_SCROLL_STOP(LayoutGroup.SIDE, R.string.layout_auto_scroll_stop);

    val key: String = "layout_hide_" + name.lowercase(java.util.Locale.ROOT)

    companion object {
        /** @param group 区域。@param tag 宿主语义标识。@return 对应控件或 null。Callers: LayoutCleanupHook。 */
        fun navigation(group: LayoutGroup, tag: String): LayoutElement? =
            entries.singleOrNull { it.group == group && it.tag == tag } ?:
                if (group == LayoutGroup.TOP && tag == "Live") TOP_LIVE else null
    }
}
