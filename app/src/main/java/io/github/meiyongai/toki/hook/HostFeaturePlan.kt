package io.github.meiyongai.toki.hook

import io.github.meiyongai.toki.provider.ConfigSchema
import io.github.meiyongai.toki.provider.ConfigSnapshot

/** 一次宿主会话的安装与配置契约，未安装功能不能经交叉调用局部启用。 */
internal class HostFeaturePlan(private val startup: ConfigSnapshot?) {
    private val rejected = mutableSetOf<String>()
    val features: Set<String> = ConfigSchema.featureSwitches.keys.filterTo(linkedSetOf()) {
        it != "SpeedOptions" && startup != null && enabled(it, startup)
    }
    val platformFeatures: Set<String> = features.intersect(platform)
    private val downloadRepair = startup?.boolean("download_force_no_watermark") == true
    val directHostFeatures: Set<String> = features.intersect(directHost) +
        if ("DownloadHook" in features && !downloadRepair) setOf("DownloadHook") else emptySet()
    val adaptedFeatures: Set<String> = features - platform - directHostFeatures
    val symbols: Set<HostSymbol> = adaptedFeatures.flatMapTo(linkedSetOf()) { feature ->
        if (feature == "PlaybackSpeedHook") buildSet {
            if (checkNotNull(startup).boolean("fixed_speed_enabled")) addAll(fixedSpeedSymbols)
            if (startup.boolean("speed_expand_enabled")) addAll(speedMenuSymbols)
        } else symbolDependencies.getValue(feature)
    }
    val requiresScan: Boolean get() = symbols.isNotEmpty()

    /** 注册失败的安装单元不再提供有效开关；依赖单元同时失去运行资格。 */
    @Synchronized fun reject(feature: String) {
        rejected.add(feature)
        if (feature == "ProgressBarHook") rejected.add("AutoCleanModeHook")
    }

    /** 有效配置只允许本次安装范围内的热更新；启动设置采用明确冻结的字段。 */
    @Synchronized fun apply(requested: ConfigSnapshot): ConfigSnapshot {
        val values = requested.configuration().toMutableMap()
        for ((feature, switches) in ConfigSchema.featureSwitches) {
            if (feature == "SpeedOptions") continue
            if (feature !in features || feature in rejected) switches.forEach { values[it] = false }
        }
        if ("AutoCleanModeHook" !in features || "AutoCleanModeHook" in rejected) values["clean_mode_show_progress_bar"] = false
        if (!downloadRepair) values["download_force_no_watermark"] = false
        for ((feature, keys) in startupKeys) {
            if (feature !in features || feature in rejected) continue
            val original = checkNotNull(startup).configuration()
            for (key in keys) {
                if (key in original) values[key] = original.getValue(key) else values.remove(key)
            }
        }
        val importRevision = if ("PlaybackSpeedHook" in features) checkNotNull(startup).importRevision else requested.importRevision
        return ConfigSnapshot(values, requested.revision, importRevision)
    }

    /** 无关配置版本变化不要求重启，只比较该功能影响运行的字段。 */
    @Synchronized fun requiresRestart(feature: String, requested: ConfigSnapshot): Boolean {
        val owner = if (feature == "SpeedOptions") "PlaybackSpeedHook" else feature
        if (owner == "DownloadHook" && !downloadRepair && requested.boolean("download_force_no_watermark")) return true
        if (owner !in features) return enabled(feature, requested)
        val keys = startupKeys[owner] ?: return false
        val original = checkNotNull(startup)
        if (owner == "PlaybackSpeedHook" && original.importRevision != requested.importRevision) return true
        return keys.any { value(original, it) != value(requested, it) }
    }

    fun appliedRevision(feature: String, requested: ConfigSnapshot): Long =
        if (feature in startupKeys && requiresRestart(feature, requested)) checkNotNull(startup).revision
        else requested.revision

    companion object {
        val platform = setOf("SimHook", "LocaleHook", "TimeZoneHook", "GpsHook")
        val directHost = setOf("StatusBarHook", "VideoDurationAlertHook")

        fun enabled(feature: String, snapshot: ConfigSnapshot): Boolean = when (feature) {
            "ProgressBarHook" -> snapshot.boolean("always_show_progress_bar") || snapshot.boolean("clean_mode_on_play")
            else -> ConfigSchema.featureSwitches.getValue(feature).any { snapshot.boolean(it) }
        }

        private fun value(snapshot: ConfigSnapshot, key: String): Any? =
            if (key in ConfigSchema.booleanDefaults) snapshot.boolean(key) else snapshot.string(key)

        private val startupKeys = mapOf(
            "SimHook" to setOf("sim_spoof_enabled", "target_region", "sim_operator_code", "sim_operator_name"),
            "LocaleHook" to setOf("language_spoof_enabled", "language_follow_region", "target_region", "custom_language"),
            "TimeZoneHook" to setOf("timezone_spoof_enabled", "timezone_follow_region", "target_region", "custom_timezone"),
            "GpsHook" to setOf("gps_spoof_enabled", "gps_follow_region", "target_region", "custom_latitude", "custom_longitude"),
            "PlaybackSpeedHook" to setOf("fixed_speed_enabled", "speed_expand_enabled", "fixed_speed_value", "speed_expand_list"),
            // 当前沉浸修改没有成对恢复全部宿主布局/背景，明确按会话应用。
            "ImmersiveFullScreenHook" to setOf("immersive_full_screen"),
        )
        private val fixedSpeedSymbols = setOf(HostSymbol.PLAYER_CONTROLLER, HostSymbol.PLAYER_MANAGER, HostSymbol.SPEED_MANAGER)
        private val speedMenuSymbols = setOf(HostSymbol.SPEED_OPTIONS)
        private val symbolDependencies = mapOf(
            "LayoutCleanupHook" to setOf(HostSymbol.LAYOUT_TOP_TABS, HostSymbol.LAYOUT_TOOLBAR, HostSymbol.LAYOUT_BOTTOM_ITEM),
            "DownloadHook" to setOf(HostSymbol.DOWNLOAD_SOURCE),
            "FeedFilterHook" to setOf(HostSymbol.COLD_FEED, HostSymbol.PRELOADED_FEED, HostSymbol.OFFLINE_RECOVERY,
                HostSymbol.FEED_ADAPTER, HostSymbol.RECOMMEND_ADAPTER, HostSymbol.RECOMMEND_MODEL),
            "CommentTranslateHook" to setOf(HostSymbol.COMMENT_TRANSLATION),
            "VideoTranslateHook" to setOf(HostSymbol.DESCRIPTION_TRANSLATION, HostSymbol.TRANSLATION_REVERSE),
            "CommentCopyHook" to setOf(HostSymbol.COMMENT_COPY),
            "AuthorLocationHook" to setOf(HostSymbol.AUTHOR_LOCATION),
            "ProgressBarHook" to setOf(HostSymbol.SEEK_BAR, HostSymbol.DARK_LAYER, HostSymbol.SEEK_CONTROLLER),
            "AutoCleanModeHook" to setOf(HostSymbol.SEEK_BAR, HostSymbol.VIDEO_CELL, HostSymbol.PLAYER_CONTROLLER, HostSymbol.PLAY_BUTTON),
            "ImmersiveFullScreenHook" to setOf(HostSymbol.RESERVED_AREA, HostSymbol.FEED_ADAPTION, HostSymbol.PHOTO_LAYOUT),
            "AutoScrollHook" to setOf(HostSymbol.SETTINGS, HostSymbol.SEARCH_AUTO_SCROLL,
                HostSymbol.AUTO_SCROLL_MENU, HostSymbol.AUTO_SCROLL_PLAYBACK, HostSymbol.AUTO_SCROLL_CONTEXT,
                HostSymbol.AUTO_SCROLL_REGISTRATION, HostSymbol.AUTO_SCROLL_REGISTER),
            "MusicUnlockHook" to setOf(HostSymbol.MUTE_INFO),
        )
    }
}
