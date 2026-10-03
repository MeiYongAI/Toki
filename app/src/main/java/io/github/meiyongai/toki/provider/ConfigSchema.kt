package io.github.meiyongai.toki.provider

/** 功能开关、默认值与配置文件校验的共同定义，不包含诊断或宿主缓存。 */
object ConfigSchema {
    val publicMediaDirectories = setOf("DCIM", "Movies", "Pictures", "Music", "Download",
        "Documents", "Alarms", "Audiobooks", "Notifications", "Podcasts", "Ringtones")
    val featureSwitches: Map<String, List<String>> = linkedMapOf(
        "SimHook" to listOf("sim_spoof_enabled"),
        "LocaleHook" to listOf("language_spoof_enabled"),
        "TimeZoneHook" to listOf("timezone_spoof_enabled"),
        "GpsHook" to listOf("gps_spoof_enabled"),
        "PlaybackSpeedHook" to listOf("fixed_speed_enabled", "speed_expand_enabled"),
        "SpeedOptions" to listOf("speed_expand_enabled"),
        "CommentTranslateHook" to listOf("comment_translate_enabled"),
        "VideoTranslateHook" to listOf("video_translate_enabled"),
        "CommentCopyHook" to listOf("copy_comment_text_only"),
        "AuthorLocationHook" to listOf("show_author_location"),
        "ProgressBarHook" to listOf("always_show_progress_bar", "clean_mode_show_progress_bar"),
        "AutoCleanModeHook" to listOf("clean_mode_on_play"),
        "ImmersiveFullScreenHook" to listOf("immersive_full_screen"),
        "AutoScrollHook" to listOf("auto_scroll_unlock"),
        "DownloadHook" to listOf("download_force_no_watermark", "download_path_enabled"),
        "MusicUnlockHook" to listOf("music_unlock"),
        "StatusBarHook" to listOf("status_bar_hidden"),
        "VideoDurationAlertHook" to listOf("video_duration_alert_enabled"),
        "FeedFilterHook" to listOf("feed_remove_ads", "feed_remove_live", "feed_remove_image",
            "feed_filter_views_enabled", "feed_filter_likes_enabled", "feed_filter_duration_enabled",
            "feed_filter_keywords_enabled", "feed_remove_offline", "feed_remove_ai_generated",
            "feed_remove_topic_recommendations", "feed_remove_creator_recommendations")
    )
    val booleanDefaults: Map<String, Boolean> = featureSwitches.values.flatten().associateWith { false } +
        listOf("language_follow_region", "timezone_follow_region", "gps_follow_region").associateWith { true }
    private val rangePrefixes = listOf("feed_filter_views", "feed_filter_likes", "feed_filter_duration")
    private val stringKeys = setOf("target_region", "sim_operator_code", "sim_operator_name",
        "custom_language", "custom_timezone", "custom_latitude", "custom_longitude",
        "fixed_speed_value", "speed_expand_list", "download_video_path", "download_image_path",
        "video_duration_alert_threshold", "feed_filter_keywords_json") +
        rangePrefixes.flatMap { listOf("${it}_min", "${it}_max") }
    val keys: Set<String> = booleanDefaults.keys + stringKeys

    /**
     * 返回唯一的布尔默认值，未知键立即报告以避免拼写错误被解释为关闭。
     * @param key 功能或从属选项键。
     * @return 总开关默认 false，从属地区联动选项默认 true。
     * Callers: ConfigClient.getBoolean、ConfigSchemaTest。
     */
    fun booleanDefault(key: String): Boolean = booleanDefaults.getValue(key)

    /**
     * 严格校验整个功能配置，任何字段错误均拒绝整批数据，不修改输入。
     * @param values 文件解析或配置存储接收的键值集合，可省略未显式设置的项。
     * @return 已校验的独立副本；关键词结构由 ConfigArchive 统一校验。
     * Callers: ConfigArchive.validate、ConfigSchemaTest。
     */
    fun validate(values: Map<String, Any>): Map<String, Any> {
        for ((key, value) in values) {
            require(key in keys) { "不支持的配置项：$key" }
            if (key in booleanDefaults) {
                require(value is Boolean) { "$key 必须为布尔值" }
                continue
            }
            require(value is String) { "$key 必须为字符串" }
            require(value.length <= if (key == "feed_filter_keywords_json") 131072 else 4096) { "$key 内容过长" }
            when (key) {
                "target_region" -> require(value.matches(Regex("[A-Za-z]{2}"))) { "国家代码必须为两个英文字母" }
                "sim_operator_code" -> require(value.matches(Regex("[0-9]{5,6}"))) { "运营商代码必须为五至六位数字" }
                "sim_operator_name" -> require(value.isNotBlank()) { "运营商名称不能为空" }
                "custom_language" -> require(value.matches(Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*"))) { "语言代码格式不正确" }
                "custom_timezone" -> require(value in java.util.TimeZone.getAvailableIDs()) { "时区标识不受支持" }
                "custom_latitude" -> number(key, value, -90.0, 90.0)
                "custom_longitude" -> number(key, value, -180.0, 180.0)
                "fixed_speed_value" -> number(key, value, 0.1, 3.0)
                "speed_expand_list" -> {
                    val speeds = value.split(',')
                    require(speeds.size in 1..100) { "倍速列表数量不正确" }
                    speeds.forEach { number(key, it, 0.1, 3.0) }
                }
                "video_duration_alert_threshold" -> {
                    val minutes = number(key, value, 0.0, Double.MAX_VALUE / 60000)
                    require(minutes > 0) { "时长提示阈值必须大于零" }
                }
                "download_video_path", "download_image_path" -> require(
                    value.substringBefore('/') in publicMediaDirectories && '/' in value &&
                    !value.startsWith('/') && !value.contains('\\') &&
                        value.split('/').all { it.isNotBlank() && it == it.trim() && it != "." && it != ".." } &&
                        value.none { it.isISOControl() } && ':' !in value
                ) { "下载目录必须为有效的相对路径" }
            }
        }
        for (prefix in rangePrefixes) {
            val min = range(values, "${prefix}_min")
            val max = range(values, "${prefix}_max")
            require(max == 0L || min <= max) { "$prefix 的下限不能大于上限" }
            if (prefix == "feed_filter_duration") {
                require(min <= Long.MAX_VALUE / 1000 && max <= Long.MAX_VALUE / 1000) { "视频时长超出支持范围" }
            }
        }
        return values.toMap()
    }

    /**
     * 校验非负整数范围端点，零表示不限。
     * @param values 完整配置。
     * @param key 端点名称。
     * @return 有效端点值。
     * Callers: validate。
     */
    private fun range(values: Map<String, Any>, key: String): Long {
        val text = values[key] as String? ?: return 0
        val value = text.toLongOrNull()
        require(value != null && value >= 0) { "$key 必须为非负整数" }
        return value
    }

    /**
     * 校验有限数值与闭区间。
     * @param key 配置名称。
     * @param text 待解析的数值。
     * @param min 最小值。
     * @param max 最大值。
     * @return 有限且合法的数值。
     * Callers: validate。
     */
    private fun number(key: String, text: String, min: Double, max: Double): Double {
        val value = text.toDoubleOrNull()
        require(value != null && value.isFinite() && value in min..max) { "$key 数值不合法" }
        return value
    }
}
