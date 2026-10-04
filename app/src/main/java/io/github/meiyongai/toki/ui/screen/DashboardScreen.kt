package io.github.meiyongai.toki.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import io.github.meiyongai.toki.ui.component.stableDialogHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.meiyongai.toki.R
import java.util.Locale
import io.github.meiyongai.toki.hook.AuthorLocationHook
import io.github.meiyongai.toki.hook.AutoCleanModeHook
import io.github.meiyongai.toki.hook.AutoScrollHook
import io.github.meiyongai.toki.hook.CommentCopyHook
import io.github.meiyongai.toki.hook.DownloadHook
import io.github.meiyongai.toki.hook.FeedFilterHook
import io.github.meiyongai.toki.hook.ImmersiveFullScreenHook
import io.github.meiyongai.toki.hook.MusicUnlockHook
import io.github.meiyongai.toki.hook.PlaybackSpeedHook
import io.github.meiyongai.toki.hook.ProgressBarHook
import io.github.meiyongai.toki.hook.StatusBarHook
import io.github.meiyongai.toki.hook.VideoDurationAlertHook
import io.github.meiyongai.toki.hook.VideoTranslateHook
import io.github.meiyongai.toki.model.KeywordCategory
import io.github.meiyongai.toki.model.KeywordFilterConfig
import io.github.meiyongai.toki.model.RegionPreset
import io.github.meiyongai.toki.model.RegionPresets
import io.github.meiyongai.toki.provider.ConfigClient
import io.github.meiyongai.toki.ui.component.CompactPreferenceActions
import io.github.meiyongai.toki.ui.component.CompactPreferenceCategories
import io.github.meiyongai.toki.ui.component.CompactPreferenceSwitch
import io.github.meiyongai.toki.ui.component.MorphingPreferenceContainer
import io.github.meiyongai.toki.ui.component.PreferenceSection
import io.github.meiyongai.toki.ui.component.SwitchPreferenceItem
import io.github.meiyongai.toki.ui.component.getGroupedShape

/**
 * 模块管理端功能配置页全景视图。
 *
 * 采用与 [HomeScreen] 完全统一的原生垂直滚动体系（[Column] 搭配 [verticalScroll]），
 * 在初次加载时一次性完成所有静态卡片的测量布局，消除滚动帧在主线程即时动态组合的卡顿与丢帧，
 * 承载地区、语言、时区、定位、倍速、过滤与体验增强等全量功能开关及交互弹窗。
 *
 * Args:
 *     scrollState (ScrollState): 功能页由上层保留的滚动状态。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.MainActivity.MainAppScreen`: 底部导航栏切换至功能页时的内容渲染。
 */
@Composable
fun DashboardScreen(
    scrollState: ScrollState = rememberScrollState()
) {
    val strings = LocalContext.current.resources
    val context = LocalContext.current

    // 1. 地区与 SIM 卡伪装状态
    var isSimSpoofEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "sim_spoof_enabled"))
    }
    var regionCode by remember {
        mutableStateOf(ConfigClient.getString(context, "target_region", defaultValue = "JP") ?: "JP")
    }
    var operatorCode by remember {
        mutableStateOf(ConfigClient.getString(context, "sim_operator_code", defaultValue = "44010") ?: "44010")
    }
    var operatorName by remember {
        mutableStateOf(ConfigClient.getString(context, "sim_operator_name", defaultValue = "NTT DOCOMO") ?: "NTT DOCOMO")
    }

    // 2. 系统语言伪装状态
    var isLanguageSpoofEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "language_spoof_enabled"))
    }
    var languageFollowRegion by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "language_follow_region"))
    }
    var customLanguage by remember {
        mutableStateOf(ConfigClient.getString(context, "custom_language", defaultValue = "ja-JP") ?: "ja-JP")
    }

    // 3. 系统时区伪装状态
    var isTimeZoneSpoofEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "timezone_spoof_enabled"))
    }
    var timezoneFollowRegion by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "timezone_follow_region"))
    }
    var customTimeZone by remember {
        mutableStateOf(ConfigClient.getString(context, "custom_timezone", defaultValue = "Asia/Tokyo") ?: "Asia/Tokyo")
    }

    // 4. 系统 GPS 伪装状态
    var isGpsSpoofEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "gps_spoof_enabled"))
    }
    var gpsFollowRegion by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "gps_follow_region"))
    }
    var customLatitude by remember {
        mutableStateOf(ConfigClient.getString(context, "custom_latitude", defaultValue = "35.6762") ?: "35.6762")
    }
    var customLongitude by remember {
        mutableStateOf(ConfigClient.getString(context, "custom_longitude", defaultValue = "139.6503") ?: "139.6503")
    }

    // 5. 固定播放倍速与拓展倍速状态
    var isSpeedSpoofEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "fixed_speed_enabled"))
    }
    var isSpeedExpandEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "speed_expand_enabled"))
    }
    var customSpeedListStr by remember {
        mutableStateOf(ConfigClient.getString(context, "speed_expand_list", defaultValue = "0.5,1.0,1.5,2.0") ?: "0.5,1.0,1.5,2.0")
    }
    var showAddSpeedDialog by remember { mutableStateOf(false) }
    var editingSpeedTarget by remember { mutableStateOf<Float?>(null) }

    val parsedSpeedList = remember(customSpeedListStr) {
        PlaybackSpeedHook.parseSpeedList(customSpeedListStr)
    }

    // 6. 评论区一键翻译状态
    var isCommentTranslateEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, "comment_translate_enabled"))
    }

    // 6.1 视频文案原生翻译状态
    var isVideoTranslateEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, VideoTranslateHook.KEY_VIDEO_TRANSLATE_ENABLED))
    }

    // 7. 复制评论仅复制正文状态
    var isCopyCommentTextOnlyEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, CommentCopyHook.KEY_COPY_COMMENT_TEXT_ONLY))
    }

    // 8. 显示作者位置状态
    var isAuthorLocationEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, AuthorLocationHook.KEY_SHOW_AUTHOR_LOCATION))
    }

    // 9. 总是显示进度条状态
    var isAlwaysShowProgressBarEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, ProgressBarHook.KEY_ALWAYS_SHOW_PROGRESS_BAR))
    }

    // 10. 播放时自动清屏与保留进度条状态
    var isCleanModeOnPlayEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, AutoCleanModeHook.KEY_CLEAN_MODE_ON_PLAY))
    }
    var isCleanShowProgressBarEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, ProgressBarHook.KEY_CLEAN_SHOW_PROGRESS_BAR))
    }

    // 11. 全屏沉浸播放状态
    var isImmersiveFullScreenEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, ImmersiveFullScreenHook.KEY_IMMERSIVE_FULL_SCREEN))
    }

    // 12. 自动滚动解锁状态
    var isAutoScrollUnlockEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, AutoScrollHook.KEY_AUTO_SCROLL_UNLOCK))
    }

    // 13. 信息流广告移除状态
    var isFeedRemoveAdsEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_FEED_REMOVE_ADS))
    }

    // 14. 媒体保存增强状态（无水印下载 + 自定义保存路径）
    var isDownloadForceEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, DownloadHook.KEY_DOWNLOAD_FORCE_NO_WATERMARK))
    }
    var isDownloadPathEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, DownloadHook.KEY_DOWNLOAD_PATH_ENABLED))
    }
    var downloadVideoPath by remember {
        mutableStateOf(
            ConfigClient.getString(context, DownloadHook.KEY_DOWNLOAD_VIDEO_PATH, defaultValue = DownloadHook.DEFAULT_VIDEO_RELATIVE_PATH)
                ?: DownloadHook.DEFAULT_VIDEO_RELATIVE_PATH
        )
    }
    var downloadImagePath by remember {
        mutableStateOf(
            ConfigClient.getString(context, DownloadHook.KEY_DOWNLOAD_IMAGE_PATH, defaultValue = DownloadHook.DEFAULT_IMAGE_RELATIVE_PATH)
                ?: DownloadHook.DEFAULT_IMAGE_RELATIVE_PATH
        )
    }
    var showDownloadPathDialog by remember { mutableStateOf(false) }

    // 15. 音频限制解锁状态
    var isMusicUnlockEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, MusicUnlockHook.KEY_MUSIC_UNLOCK))
    }

    // 16. 推荐流直播过滤状态
    var isFeedRemoveLiveEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_FEED_REMOVE_LIVE))
    }

    // 17. 推荐流图文过滤状态
    var isFeedRemoveImageEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_FEED_REMOVE_IMAGE))
    }

    // 18. 浏览数过滤状态
    var isViewFilterEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_FILTER_VIEWS_ENABLED))
    }
    var viewMin by remember {
        mutableStateOf(ConfigClient.getString(context, FeedFilterHook.KEY_FILTER_VIEWS_MIN, defaultValue = "0") ?: "0")
    }
    var viewMax by remember {
        mutableStateOf(ConfigClient.getString(context, FeedFilterHook.KEY_FILTER_VIEWS_MAX, defaultValue = "0") ?: "0")
    }
    var showViewRangeDialog by remember { mutableStateOf(false) }

    // 19. 点赞数过滤状态
    var isLikeFilterEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_FILTER_LIKES_ENABLED))
    }
    var likeMin by remember {
        mutableStateOf(ConfigClient.getString(context, FeedFilterHook.KEY_FILTER_LIKES_MIN, defaultValue = "0") ?: "0")
    }
    var likeMax by remember {
        mutableStateOf(ConfigClient.getString(context, FeedFilterHook.KEY_FILTER_LIKES_MAX, defaultValue = "0") ?: "0")
    }
    var showLikeRangeDialog by remember { mutableStateOf(false) }

    // 20. 视频时长过滤状态
    var isDurationFilterEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_FILTER_DURATION_ENABLED))
    }
    var durationMin by remember {
        mutableStateOf(ConfigClient.getString(context, FeedFilterHook.KEY_FILTER_DURATION_MIN, defaultValue = "0") ?: "0")
    }
    var durationMax by remember {
        mutableStateOf(ConfigClient.getString(context, FeedFilterHook.KEY_FILTER_DURATION_MAX, defaultValue = "0") ?: "0")
    }
    var showDurationRangeDialog by remember { mutableStateOf(false) }

    // 21. 视频关键词过滤状态
    var isKeywordFilterEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_FILTER_KEYWORDS_ENABLED))
    }
    var keywordJsonStr by remember {
        mutableStateOf(ConfigClient.getString(context, FeedFilterHook.KEY_FILTER_KEYWORDS_JSON, defaultValue = "{}") ?: "{}")
    }
    var showKeywordListDialog by remember { mutableStateOf(false) }

    val parsedKeywordConfig = remember(keywordJsonStr) {
        FeedFilterHook.parseKeywordConfig(keywordJsonStr)
    }

    // 22. 禁止插入离线视频状态
    var isBlockOfflineVideoEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_REMOVE_OFFLINE))
    }
    var isRemoveAiGeneratedEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_REMOVE_AI_GENERATED))
    }
    var isRemoveTopicRecommendationsEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_REMOVE_TOPIC_RECOMMENDATIONS))
    }
    var isRemoveCreatorRecommendationsEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, FeedFilterHook.KEY_REMOVE_CREATOR_RECOMMENDATIONS))
    }

    // 21. 隐藏系统状态栏状态
    var isStatusBarHidden by remember {
        mutableStateOf(ConfigClient.getBoolean(context, StatusBarHook.KEY_STATUS_BAR_HIDDEN))
    }

    // 22. 长视频时长提示状态
    var isDurationAlertEnabled by remember {
        mutableStateOf(ConfigClient.getBoolean(context, VideoDurationAlertHook.KEY_VIDEO_DURATION_ALERT_ENABLED))
    }
    var durationAlertThreshold by remember {
        mutableStateOf(
            ConfigClient.getString(context, VideoDurationAlertHook.KEY_VIDEO_DURATION_ALERT_THRESHOLD, defaultValue = VideoDurationAlertHook.DEFAULT_THRESHOLD_MINUTES_STRING)
                ?: VideoDurationAlertHook.DEFAULT_THRESHOLD_MINUTES_STRING
        )
    }
    var showDurationAlertDialog by remember { mutableStateOf(false) }

    // 弹窗显隐控制
    var showSelectDialog by remember { mutableStateOf(false) }
    var showCustomSimDialog by remember { mutableStateOf(false) }
    var showCustomLangDialog by remember { mutableStateOf(false) }
    var showCustomTzDialog by remember { mutableStateOf(false) }
    var showCustomGpsDialog by remember { mutableStateOf(false) }

    // 记忆化派生状态
    val currentPreset = remember(regionCode) {
        RegionPresets.findByIso(regionCode)
    }
    val effectiveLanguage = remember(regionCode, languageFollowRegion, customLanguage) {
        if (languageFollowRegion) {
            RegionPresets.resolveLanguageForIso(regionCode)
        } else {
            customLanguage
        }
    }
    val effectiveTimeZone = remember(regionCode, timezoneFollowRegion, customTimeZone) {
        if (timezoneFollowRegion) {
            RegionPresets.resolveTimeZoneForIso(regionCode)
        } else {
            customTimeZone
        }
    }
    val effectiveLatitude = remember(regionCode, gpsFollowRegion, customLatitude) {
        if (gpsFollowRegion) {
            RegionPresets.resolveLatitudeForIso(regionCode)
        } else {
            customLatitude.toDoubleOrNull() ?: 35.6762
        }
    }
    val effectiveLongitude = remember(regionCode, gpsFollowRegion, customLongitude) {
        if (gpsFollowRegion) {
            RegionPresets.resolveLongitudeForIso(regionCode)
        } else {
            customLongitude.toDoubleOrNull() ?: 139.6503
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // === 分组 1: 地区与环境伪装 (共 4 项) ===
        PreferenceSection(title = strings.getString(R.string.feature_region_section)) {
            SimSpoofCard(
                shape = getGroupedShape(0, 4),
                enabled = isSimSpoofEnabled,
                onEnabledChange = { state ->
                    isSimSpoofEnabled = state
                    ConfigClient.putBoolean(context, "sim_spoof_enabled", state)
                },
                regionCode = regionCode,
                operatorCode = operatorCode,
                operatorName = operatorName,
                currentPreset = currentPreset,
                onSelectPresetClick = { showSelectDialog = true },
                onCustomSimClick = { showCustomSimDialog = true }
            )

            LanguageSpoofCard(
                shape = getGroupedShape(1, 4),
                enabled = isLanguageSpoofEnabled,
                onEnabledChange = { state ->
                    isLanguageSpoofEnabled = state
                    ConfigClient.putBoolean(context, "language_spoof_enabled", state)
                },
                followRegion = languageFollowRegion,
                onFollowRegionChange = { state ->
                    languageFollowRegion = state
                    ConfigClient.putBoolean(context, "language_follow_region", state)
                },
                effectiveLanguage = effectiveLanguage,
                onCustomLanguageClick = { showCustomLangDialog = true }
            )

            TimeZoneSpoofCard(
                shape = getGroupedShape(2, 4),
                enabled = isTimeZoneSpoofEnabled,
                onEnabledChange = { state ->
                    isTimeZoneSpoofEnabled = state
                    ConfigClient.putBoolean(context, "timezone_spoof_enabled", state)
                },
                followRegion = timezoneFollowRegion,
                onFollowRegionChange = { state ->
                    timezoneFollowRegion = state
                    ConfigClient.putBoolean(context, "timezone_follow_region", state)
                },
                effectiveTimeZone = effectiveTimeZone,
                onCustomTimeZoneClick = { showCustomTzDialog = true }
            )

            GpsSpoofCard(
                shape = getGroupedShape(3, 4),
                enabled = isGpsSpoofEnabled,
                onEnabledChange = { state ->
                    isGpsSpoofEnabled = state
                    ConfigClient.putBoolean(context, "gps_spoof_enabled", state)
                },
                followRegion = gpsFollowRegion,
                onFollowRegionChange = { state ->
                    gpsFollowRegion = state
                    ConfigClient.putBoolean(context, "gps_follow_region", state)
                },
                effectiveLatitude = effectiveLatitude,
                effectiveLongitude = effectiveLongitude,
                onCustomGpsClick = { showCustomGpsDialog = true }
            )
        }

        // === 分组 2: 播放与交互体验 (共 7 项) ===
        PreferenceSection(title = strings.getString(R.string.feature_playback_section)) {
            PlaybackSpeedCard(
                shape = getGroupedShape(0, 7),
                fixedSpeedEnabled = isSpeedSpoofEnabled,
                onFixedSpeedChange = { state ->
                    isSpeedSpoofEnabled = state
                    ConfigClient.putBoolean(context, "fixed_speed_enabled", state)
                },
                expandEnabled = isSpeedExpandEnabled,
                onExpandChange = { state ->
                    isSpeedExpandEnabled = state
                    ConfigClient.putBoolean(context, "speed_expand_enabled", state)
                },
                speedList = parsedSpeedList,
                onSpeedClick = { speedVal -> editingSpeedTarget = speedVal },
                onAddSpeedClick = { showAddSpeedDialog = true },
                onResetDefaultClick = {
                    val defaultStr = "0.5,1.0,1.5,2.0"
                    customSpeedListStr = defaultStr
                    ConfigClient.putString(context, "speed_expand_list", defaultStr)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(1, 7),
                title = strings.getString(R.string.feature_progress),
                summary = strings.getString(R.string.feature_progress_summary),
                checked = isAlwaysShowProgressBarEnabled,
                onCheckedChange = { state ->
                    isAlwaysShowProgressBarEnabled = state
                    ConfigClient.putBoolean(context, ProgressBarHook.KEY_ALWAYS_SHOW_PROGRESS_BAR, state)
                }
            )

            CleanModeCard(
                shape = getGroupedShape(2, 7),
                cleanModeEnabled = isCleanModeOnPlayEnabled,
                onCleanModeChange = { state ->
                    isCleanModeOnPlayEnabled = state
                    ConfigClient.putBoolean(context, AutoCleanModeHook.KEY_CLEAN_MODE_ON_PLAY, state)
                },
                showProgressBar = isCleanShowProgressBarEnabled,
                onShowProgressBarChange = { state ->
                    isCleanShowProgressBarEnabled = state
                    ConfigClient.putBoolean(context, ProgressBarHook.KEY_CLEAN_SHOW_PROGRESS_BAR, state)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(3, 7),
                title = strings.getString(R.string.feature_immersive),
                summary = strings.getString(R.string.feature_immersive_summary),
                checked = isImmersiveFullScreenEnabled,
                onCheckedChange = { state ->
                    isImmersiveFullScreenEnabled = state
                    ConfigClient.putBoolean(context, ImmersiveFullScreenHook.KEY_IMMERSIVE_FULL_SCREEN, state)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(4, 7),
                title = strings.getString(R.string.feature_autoplay),
                summary = strings.getString(R.string.feature_autoplay_summary),
                checked = isAutoScrollUnlockEnabled,
                onCheckedChange = { state ->
                    isAutoScrollUnlockEnabled = state
                    ConfigClient.putBoolean(context, AutoScrollHook.KEY_AUTO_SCROLL_UNLOCK, state)
                }
            )

            VideoDurationAlertCard(
                shape = getGroupedShape(5, 7),
                enabled = isDurationAlertEnabled,
                onEnabledChange = { state ->
                    isDurationAlertEnabled = state
                    ConfigClient.putBoolean(context, VideoDurationAlertHook.KEY_VIDEO_DURATION_ALERT_ENABLED, state)
                },
                threshold = durationAlertThreshold,
                onClick = { showDurationAlertDialog = true }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(6, 7),
                title = strings.getString(R.string.feature_status_bar),
                checked = isStatusBarHidden,
                onCheckedChange = { state ->
                    isStatusBarHidden = state
                    ConfigClient.putBoolean(context, StatusBarHook.KEY_STATUS_BAR_HIDDEN, state)
                }
            )
        }

        LayoutCleanupSection()

        // === 分组 3: 内容下载与操作增强 (共 7 项) ===
        PreferenceSection(title = strings.getString(R.string.feature_media_section)) {
            SwitchPreferenceItem(
                shape = getGroupedShape(0, 7),
                title = strings.getString(R.string.feature_download),
                summary = strings.getString(R.string.feature_download_summary),
                checked = isDownloadForceEnabled,
                onCheckedChange = { state ->
                    isDownloadForceEnabled = state
                    ConfigClient.putBoolean(context, DownloadHook.KEY_DOWNLOAD_FORCE_NO_WATERMARK, state)
                }
            )

            DownloadPathCard(
                shape = getGroupedShape(1, 7),
                enabled = isDownloadPathEnabled,
                onEnabledChange = { state ->
                    isDownloadPathEnabled = state
                    ConfigClient.putBoolean(context, DownloadHook.KEY_DOWNLOAD_PATH_ENABLED, state)
                },
                videoPath = downloadVideoPath,
                imagePath = downloadImagePath,
                onClick = { showDownloadPathDialog = true }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(2, 7),
                title = strings.getString(R.string.feature_music),
                summary = strings.getString(R.string.feature_music_summary),
                checked = isMusicUnlockEnabled,
                onCheckedChange = { state ->
                    isMusicUnlockEnabled = state
                    ConfigClient.putBoolean(context, MusicUnlockHook.KEY_MUSIC_UNLOCK, state)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(3, 7),
                title = strings.getString(R.string.feature_author_region),
                summary = strings.getString(R.string.feature_author_region_summary),
                checked = isAuthorLocationEnabled,
                onCheckedChange = { state ->
                    isAuthorLocationEnabled = state
                    ConfigClient.putBoolean(context, AuthorLocationHook.KEY_SHOW_AUTHOR_LOCATION, state)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(4, 7),
                title = strings.getString(R.string.feature_copy_comment),
                checked = isCopyCommentTextOnlyEnabled,
                onCheckedChange = { state ->
                    isCopyCommentTextOnlyEnabled = state
                    ConfigClient.putBoolean(context, CommentCopyHook.KEY_COPY_COMMENT_TEXT_ONLY, state)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(5, 7),
                title = strings.getString(R.string.feature_video_translation),
                summary = strings.getString(R.string.feature_video_translation_summary),
                checked = isVideoTranslateEnabled,
                onCheckedChange = { state ->
                    isVideoTranslateEnabled = state
                    ConfigClient.putBoolean(context, VideoTranslateHook.KEY_VIDEO_TRANSLATE_ENABLED, state)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(6, 7),
                title = strings.getString(R.string.feature_comment_translation),
                summary = strings.getString(R.string.feature_comment_translation_summary),
                checked = isCommentTranslateEnabled,
                onCheckedChange = { state ->
                    isCommentTranslateEnabled = state
                    ConfigClient.putBoolean(context, "comment_translate_enabled", state)
                }
            )
        }

        // === 分组 4: 动态流与精准过滤 (共 11 项) ===
        PreferenceSection(title = strings.getString(R.string.feature_filter_section)) {
            SwitchPreferenceItem(
                shape = getGroupedShape(0, 11),
                title = strings.getString(R.string.feature_remove_ads),
                checked = isFeedRemoveAdsEnabled,
                onCheckedChange = { state ->
                    isFeedRemoveAdsEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_FEED_REMOVE_ADS, state)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(1, 11),
                title = strings.getString(R.string.feature_remove_live),
                checked = isFeedRemoveLiveEnabled,
                onCheckedChange = { state ->
                    isFeedRemoveLiveEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_FEED_REMOVE_LIVE, state)
                }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(2, 11),
                title = strings.getString(R.string.feature_remove_photos),
                checked = isFeedRemoveImageEnabled,
                onCheckedChange = { state ->
                    isFeedRemoveImageEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_FEED_REMOVE_IMAGE, state)
                }
            )

            FilterRangeCard(
                shape = getGroupedShape(3, 11),
                title = strings.getString(R.string.feature_filter_views),
                enabled = isViewFilterEnabled,
                onEnabledChange = { state ->
                    isViewFilterEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_FILTER_VIEWS_ENABLED, state)
                },
                minVal = viewMin,
                maxVal = viewMax,
                unitName = strings.getString(R.string.filter_metric_views),
                onClick = { showViewRangeDialog = true }
            )

            FilterRangeCard(
                shape = getGroupedShape(4, 11),
                title = strings.getString(R.string.feature_filter_likes),
                enabled = isLikeFilterEnabled,
                onEnabledChange = { state ->
                    isLikeFilterEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_FILTER_LIKES_ENABLED, state)
                },
                minVal = likeMin,
                maxVal = likeMax,
                unitName = strings.getString(R.string.filter_metric_likes),
                onClick = { showLikeRangeDialog = true }
            )

            FilterRangeCard(
                shape = getGroupedShape(5, 11),
                title = strings.getString(R.string.feature_filter_duration),
                enabled = isDurationFilterEnabled,
                onEnabledChange = { state ->
                    isDurationFilterEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_FILTER_DURATION_ENABLED, state)
                },
                minVal = durationMin,
                maxVal = durationMax,
                unitName = strings.getString(R.string.filter_metric_duration),
                unitSuffix = strings.getString(R.string.filter_unit_seconds),
                onClick = { showDurationRangeDialog = true }
            )

            FilterKeywordsCard(
                shape = getGroupedShape(6, 11),
                title = strings.getString(R.string.feature_filter_keywords),
                enabled = isKeywordFilterEnabled,
                onEnabledChange = { state ->
                    isKeywordFilterEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_FILTER_KEYWORDS_ENABLED, state)
                },
                config = parsedKeywordConfig,
                onClick = { showKeywordListDialog = true }
            )

            SwitchPreferenceItem(
                shape = getGroupedShape(7, 11),
                title = strings.getString(R.string.feature_block_offline),
                summary = strings.getString(R.string.feature_block_offline_summary),
                checked = isBlockOfflineVideoEnabled,
                onCheckedChange = { state ->
                    isBlockOfflineVideoEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_REMOVE_OFFLINE, state)
                }
            )
            SwitchPreferenceItem(
                shape = getGroupedShape(8, 11),
                title = strings.getString(R.string.feature_remove_ai),
                summary = strings.getString(R.string.feature_remove_ai_summary),
                checked = isRemoveAiGeneratedEnabled,
                onCheckedChange = { state ->
                    isRemoveAiGeneratedEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_REMOVE_AI_GENERATED, state)
                }
            )
            SwitchPreferenceItem(
                shape = getGroupedShape(9, 11),
                title = strings.getString(R.string.feature_filter_topics),
                checked = isRemoveTopicRecommendationsEnabled,
                onCheckedChange = { state ->
                    isRemoveTopicRecommendationsEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_REMOVE_TOPIC_RECOMMENDATIONS, state)
                }
            )
            SwitchPreferenceItem(
                shape = getGroupedShape(10, 11),
                title = strings.getString(R.string.feature_filter_creators),
                checked = isRemoveCreatorRecommendationsEnabled,
                onCheckedChange = { state ->
                    isRemoveCreatorRecommendationsEnabled = state
                    ConfigClient.putBoolean(context, FeedFilterHook.KEY_REMOVE_CREATOR_RECOMMENDATIONS, state)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // === 弹窗集合 ===
    if (showSelectDialog) {
        RegionSelectionDialog(
            currentIso = regionCode,
            onDismiss = { showSelectDialog = false },
            onSelected = { preset ->
                regionCode = preset.isoCode
                operatorCode = preset.operatorCode
                operatorName = preset.operatorName
                ConfigClient.putString(context, "target_region", preset.isoCode)
                ConfigClient.putString(context, "sim_operator_code", preset.operatorCode)
                ConfigClient.putString(context, "sim_operator_name", preset.operatorName)
                showSelectDialog = false
            }
        )
    }

    if (showCustomSimDialog) {
        CustomSimDialog(
            initialIso = regionCode,
            initialCode = operatorCode,
            initialName = operatorName,
            onDismiss = { showCustomSimDialog = false },
            onSaved = { iso, code, name ->
                regionCode = iso
                operatorCode = code
                operatorName = name
                ConfigClient.putString(context, "target_region", iso)
                ConfigClient.putString(context, "sim_operator_code", code)
                ConfigClient.putString(context, "sim_operator_name", name)
                showCustomSimDialog = false
            }
        )
    }

    if (showCustomLangDialog) {
        CustomLanguageDialog(
            initialLanguage = customLanguage,
            onDismiss = { showCustomLangDialog = false },
            onSaved = { lang ->
                customLanguage = lang
                ConfigClient.putString(context, "custom_language", lang)
                showCustomLangDialog = false
            }
        )
    }

    if (showCustomTzDialog) {
        CustomTimeZoneDialog(
            initialTimeZone = customTimeZone,
            onDismiss = { showCustomTzDialog = false },
            onSaved = { tz ->
                customTimeZone = tz
                ConfigClient.putString(context, "custom_timezone", tz)
                showCustomTzDialog = false
            }
        )
    }

    if (showCustomGpsDialog) {
        CustomGpsDialog(
            initialLatitude = customLatitude,
            initialLongitude = customLongitude,
            onDismiss = { showCustomGpsDialog = false },
            onSaved = { lat, lng ->
                customLatitude = lat
                customLongitude = lng
                ConfigClient.putString(context, "custom_latitude", lat)
                ConfigClient.putString(context, "custom_longitude", lng)
                showCustomGpsDialog = false
            }
        )
    }

    if (showViewRangeDialog) {
        ViewFilterRangeDialog(
            initialMin = viewMin,
            initialMax = viewMax,
            onDismiss = { showViewRangeDialog = false },
            onSaved = { min, max ->
                val minSan = FeedFilterHook.parseViewMin(min).toString()
                val maxSan = FeedFilterHook.parseViewMax(max).toString()
                viewMin = minSan
                viewMax = maxSan
                ConfigClient.putStrings(context, mapOf(
                    FeedFilterHook.KEY_FILTER_VIEWS_MIN to minSan,
                    FeedFilterHook.KEY_FILTER_VIEWS_MAX to maxSan))
                showViewRangeDialog = false
                Toast.makeText(context, strings.getString(R.string.filter_views_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showLikeRangeDialog) {
        LikeFilterRangeDialog(
            initialMin = likeMin,
            initialMax = likeMax,
            onDismiss = { showLikeRangeDialog = false },
            onSaved = { min, max ->
                val minSan = FeedFilterHook.parseLikeMin(min).toString()
                val maxSan = FeedFilterHook.parseLikeMax(max).toString()
                likeMin = minSan
                likeMax = maxSan
                ConfigClient.putStrings(context, mapOf(
                    FeedFilterHook.KEY_FILTER_LIKES_MIN to minSan,
                    FeedFilterHook.KEY_FILTER_LIKES_MAX to maxSan))
                showLikeRangeDialog = false
                Toast.makeText(context, strings.getString(R.string.filter_likes_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDurationRangeDialog) {
        DurationFilterRangeDialog(
            initialMin = durationMin,
            initialMax = durationMax,
            onDismiss = { showDurationRangeDialog = false },
            onSaved = { min, max ->
                val minSan = FeedFilterHook.parseDurationMin(min).toString()
                val maxSan = FeedFilterHook.parseDurationMax(max).toString()
                durationMin = minSan
                durationMax = maxSan
                ConfigClient.putStrings(context, mapOf(
                    FeedFilterHook.KEY_FILTER_DURATION_MIN to minSan,
                    FeedFilterHook.KEY_FILTER_DURATION_MAX to maxSan))
                showDurationRangeDialog = false
                Toast.makeText(context, strings.getString(R.string.filter_duration_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showKeywordListDialog) {
        KeywordListDialog(
            config = parsedKeywordConfig,
            onDismiss = { showKeywordListDialog = false },
            onUpdateConfig = { newConfig ->
                val json = FeedFilterHook.serializeKeywordConfig(newConfig)
                keywordJsonStr = json
                ConfigClient.putString(context, FeedFilterHook.KEY_FILTER_KEYWORDS_JSON, json)
            }
        )
    }

    if (showDownloadPathDialog) {
        DownloadPathDialog(
            initialVideoPath = downloadVideoPath,
            initialImagePath = downloadImagePath,
            onDismiss = { showDownloadPathDialog = false },
            onSaved = { vPath, iPath ->
                val sanitizedVideo = DownloadHook.sanitizeRelativePath(vPath, DownloadHook.DEFAULT_VIDEO_RELATIVE_PATH)
                val sanitizedImage = DownloadHook.sanitizeRelativePath(iPath, DownloadHook.DEFAULT_IMAGE_RELATIVE_PATH)
                downloadVideoPath = sanitizedVideo
                downloadImagePath = sanitizedImage
                ConfigClient.putString(context, DownloadHook.KEY_DOWNLOAD_VIDEO_PATH, sanitizedVideo)
                ConfigClient.putString(context, DownloadHook.KEY_DOWNLOAD_IMAGE_PATH, sanitizedImage)
                showDownloadPathDialog = false
                Toast.makeText(context, strings.getString(R.string.download_path_saved), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddSpeedDialog) {
        AddSpeedDialog(
            onDismiss = { showAddSpeedDialog = false },
            onAdded = { newSpeed ->
                val updated = (parsedSpeedList + newSpeed).distinct().sorted()
                val newStr = updated.joinToString(",")
                customSpeedListStr = newStr
                ConfigClient.putString(context, "speed_expand_list", newStr)
                showAddSpeedDialog = false
            }
        )
    }

    editingSpeedTarget?.let { targetSpeed ->
        EditSpeedDialog(
            currentSpeed = targetSpeed,
            canDelete = parsedSpeedList.size > 1,
            onDismiss = { editingSpeedTarget = null },
            onDelete = {
                val updated = parsedSpeedList.filter { it != targetSpeed }
                val newStr = updated.joinToString(",")
                customSpeedListStr = newStr
                ConfigClient.putString(context, "speed_expand_list", newStr)
                editingSpeedTarget = null
            },
            onSaved = { oldSpeed, newSpeed ->
                val updated = parsedSpeedList.map { if (it == oldSpeed) newSpeed else it }.distinct().sorted()
                val newStr = updated.joinToString(",")
                customSpeedListStr = newStr
                ConfigClient.putString(context, "speed_expand_list", newStr)
                editingSpeedTarget = null
            }
        )
    }

    if (showDurationAlertDialog) {
        VideoDurationAlertDialog(
            initialThreshold = durationAlertThreshold,
            onDismiss = { showDurationAlertDialog = false },
            onSaved = { newThreshold ->
                durationAlertThreshold = newThreshold
                ConfigClient.putString(context, VideoDurationAlertHook.KEY_VIDEO_DURATION_ALERT_THRESHOLD, newThreshold)
                showDurationAlertDialog = false
            }
        )
    }
}

/**
 * 地区与 SIM 卡伪装卡片组件。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     enabled (Boolean): 伪装功能总开关当前状态。
 *     onEnabledChange (Function1<Boolean, Unit>): 总开关状态变化回调。
 *     regionCode (String): 当前目标国家/地区代号。
 *     operatorCode (String): 当前运营商代码。
 *     operatorName (String): 当前运营商显示名称。
 *     currentPreset (RegionPreset?): 匹配的预设国家实体，若为自定义参数则为 null。
 *     onSelectPresetClick (Function0<Unit>): 点击“选择预设”按钮的回调。
 *     onCustomSimClick (Function0<Unit>): 点击“自定义参数”按钮的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页顶部地区卡片渲染。
 */
@Composable
private fun SimSpoofCard(
    shape: Shape,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    regionCode: String,
    operatorCode: String,
    operatorName: String,
    currentPreset: RegionPreset?,
    onSelectPresetClick: () -> Unit,
    onCustomSimClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(
                title = strings.getString(R.string.region_spoof_title),
                checked = enabled,
                onCheckedChange = onEnabledChange
            )

            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = strings.getString(R.string.region_current),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val currentFlag = currentPreset?.flagEmoji ?: RegionPresets.isoToFlagEmoji(regionCode)
                    Text(
                        text = strings.getString(R.string.region_target, currentFlag, if (currentPreset == null) strings.getString(R.string.common_custom) else Locale.forLanguageTag("und-$regionCode").getDisplayCountry(strings.configuration.locales[0]), regionCode.uppercase(Locale.ROOT)),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = strings.getString(R.string.region_operator, operatorName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = strings.getString(R.string.region_operator_code, operatorCode),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    CompactPreferenceActions(
                        firstLabel = strings.getString(R.string.region_select_preset),
                        onFirstClick = onSelectPresetClick,
                        secondLabel = strings.getString(R.string.region_custom_parameters),
                        onSecondClick = onCustomSimClick,
                        firstOutlined = false,
                        spacing = 8.dp
                    )
                }
            }
        }
    }
}

/**
 * 语言伪装卡片组件。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     enabled (Boolean): 伪装功能总开关状态。
 *     onEnabledChange (Function1<Boolean, Unit>): 总开关状态变化回调。
 *     followRegion (Boolean): 是否跟随地区设置开关状态。
 *     onFollowRegionChange (Function1<Boolean, Unit>): 跟随地区开关变化回调。
 *     effectiveLanguage (String): 当前最终生效的语言代码。
 *     onCustomLanguageClick (Function0<Unit>): 点击自定义语言按钮的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页语言卡片渲染。
 */
@Composable
private fun LanguageSpoofCard(
    shape: Shape,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    followRegion: Boolean,
    onFollowRegionChange: (Boolean) -> Unit,
    effectiveLanguage: String,
    onCustomLanguageClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(
                title = strings.getString(R.string.language_spoof),
                checked = enabled,
                onCheckedChange = onEnabledChange
            )

            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    CompactPreferenceSwitch(
                        title = strings.getString(R.string.environment_follow_region),
                        checked = followRegion,
                        onCheckedChange = onFollowRegionChange
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.getString(R.string.language_effective, effectiveLanguage),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AnimatedVisibility(visible = !followRegion) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onCustomLanguageClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.getString(R.string.language_custom))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 时区伪装卡片组件。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     enabled (Boolean): 伪装功能总开关状态。
 *     onEnabledChange (Function1<Boolean, Unit>): 总开关状态变化回调。
 *     followRegion (Boolean): 是否跟随地区设置。
 *     onFollowRegionChange (Function1<Boolean, Unit>): 跟随地区开关变化回调。
 *     effectiveTimeZone (String): 当前最终生效的时区标识。
 *     onCustomTimeZoneClick (Function0<Unit>): 点击自定义时区按钮的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页时区卡片渲染。
 */
@Composable
private fun TimeZoneSpoofCard(
    shape: Shape,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    followRegion: Boolean,
    onFollowRegionChange: (Boolean) -> Unit,
    effectiveTimeZone: String,
    onCustomTimeZoneClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(
                title = strings.getString(R.string.timezone_spoof),
                checked = enabled,
                onCheckedChange = onEnabledChange
            )

            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    CompactPreferenceSwitch(
                        title = strings.getString(R.string.environment_follow_region),
                        checked = followRegion,
                        onCheckedChange = onFollowRegionChange
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.getString(R.string.timezone_effective, effectiveTimeZone),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AnimatedVisibility(visible = !followRegion) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onCustomTimeZoneClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.getString(R.string.timezone_custom))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 位置定位伪装卡片组件。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     enabled (Boolean): 伪装功能总开关状态。
 *     onEnabledChange (Function1<Boolean, Unit>): 总开关状态变化回调。
 *     followRegion (Boolean): 是否跟随地区设置。
 *     onFollowRegionChange (Function1<Boolean, Unit>): 跟随地区开关变化回调。
 *     effectiveLatitude (Double): 当前最终生效的纬度数值。
 *     effectiveLongitude (Double): 当前最终生效的经度数值。
 *     onCustomGpsClick (Function0<Unit>): 点击自定义经纬度按钮的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页 GPS 卡片渲染。
 */
@Composable
private fun GpsSpoofCard(
    shape: Shape,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    followRegion: Boolean,
    onFollowRegionChange: (Boolean) -> Unit,
    effectiveLatitude: Double,
    effectiveLongitude: Double,
    onCustomGpsClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(
                title = strings.getString(R.string.gps_spoof),
                checked = enabled,
                onCheckedChange = onEnabledChange
            )

            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    CompactPreferenceSwitch(
                        title = strings.getString(R.string.environment_follow_region),
                        checked = followRegion,
                        onCheckedChange = onFollowRegionChange
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.getString(R.string.gps_effective, effectiveLatitude.toString(), effectiveLongitude.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    AnimatedVisibility(visible = !followRegion) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onCustomGpsClick,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(strings.getString(R.string.gps_custom))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 固定播放倍速与倍速拓展卡片组件。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     fixedSpeedEnabled (Boolean): 固定播放倍速总开关状态。
 *     onFixedSpeedChange (Function1<Boolean, Unit>): 固定倍速开关变化回调。
 *     expandEnabled (Boolean): 自定义倍速拓展开关状态。
 *     onExpandChange (Function1<Boolean, Unit>): 倍速拓展开关变化回调。
 *     speedList (List<Float>): 当前解析出的倍速浮点数值列表。
 *     onSpeedClick (Function1<Float, Unit>): 点击指定倍速标签时的回调。
 *     onAddSpeedClick (Function0<Unit>): 点击添加倍速按钮时的回调。
 *     onResetDefaultClick (Function0<Unit>): 点击恢复默认倍速按钮时的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页播放倍速卡片渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSpeedCard(
    shape: Shape,
    fixedSpeedEnabled: Boolean,
    onFixedSpeedChange: (Boolean) -> Unit,
    expandEnabled: Boolean,
    onExpandChange: (Boolean) -> Unit,
    speedList: List<Float>,
    onSpeedClick: (Float) -> Unit,
    onAddSpeedClick: () -> Unit,
    onResetDefaultClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(
                title = strings.getString(R.string.speed_fixed),
                checked = fixedSpeedEnabled,
                onCheckedChange = onFixedSpeedChange,
                summary = strings.getString(R.string.speed_fixed_summary)
            )

            AnimatedVisibility(visible = fixedSpeedEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    CompactPreferenceSwitch(
                        title = strings.getString(R.string.speed_expand),
                        checked = expandEnabled,
                        onCheckedChange = onExpandChange
                    )

                    AnimatedVisibility(visible = expandEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.getString(R.string.speed_options_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                speedList.forEach { speedVal ->
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.clickable { onSpeedClick(speedVal) }
                                    ) {
                                        Text(
                                            text = if (speedVal % 1f == 0f) "${speedVal.toInt()}x" else "${speedVal}x",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            CompactPreferenceActions(
                                firstLabel = strings.getString(R.string.speed_add_option),
                                onFirstClick = onAddSpeedClick,
                                secondLabel = strings.getString(R.string.speed_restore),
                                onSecondClick = onResetDefaultClick,
                                firstOutlined = true,
                                spacing = 10.dp
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * 自动清屏复合功能配置卡片组件。
 *
 * 整合“播放时自动清屏”主开关与“保留进度条显示”子功能，
 * 开启自动清屏后展开非缩进的规范项配置。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     cleanModeEnabled (Boolean): 播放时自动清屏开关状态。
 *     onCleanModeChange (Function1<Boolean, Unit>): 自动清屏开关变化回调。
 *     showProgressBar (Boolean): 清屏时保留进度条显示开关状态。
 *     onShowProgressBarChange (Function1<Boolean, Unit>): 保留进度条开关变化回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页播放与交互体验配置项渲染。
 */
@Composable
private fun CleanModeCard(
    shape: Shape,
    cleanModeEnabled: Boolean,
    onCleanModeChange: (Boolean) -> Unit,
    showProgressBar: Boolean,
    onShowProgressBarChange: (Boolean) -> Unit
) {
    val strings = LocalContext.current.resources
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(
                title = strings.getString(R.string.clean_on_play),
                checked = cleanModeEnabled,
                onCheckedChange = onCleanModeChange
            )

            AnimatedVisibility(visible = cleanModeEnabled) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    CompactPreferenceSwitch(
                        title = strings.getString(R.string.clean_keep_progress),
                        checked = showProgressBar,
                        onCheckedChange = onShowProgressBarChange
                    )
                }
            }
        }
    }
}


/**
 * 自定义保存路径设置卡片组件。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     enabled (Boolean): 自定义保存路径功能开关状态。
 *     onEnabledChange (Function1<Boolean, Unit>): 功能开关变化回调。
 *     videoPath (String): 当前视频相对保存路径。
 *     imagePath (String): 当前图片相对保存路径。
 *     onClick (Function0<Unit>): 点击卡片编辑路径时的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页保存路径卡片渲染。
 */
@Composable
private fun DownloadPathCard(
    shape: Shape,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    videoPath: String,
    imagePath: String,
    onClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    MorphingPreferenceContainer(
        shape = shape,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(
                title = strings.getString(R.string.download_paths),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                summary = strings.getString(R.string.download_paths_summary, videoPath, imagePath)
            )
        }
    }
}

/**
 * 范围过滤设置卡片组件（浏览数与点赞数）。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     title (String): 过滤功能标题（如“浏览数过滤”）。
 *     enabled (Boolean): 过滤功能开关状态。
 *     onEnabledChange (Function1<Boolean, Unit>): 过滤开关状态变化回调。
 *     minVal (String): 当前区间下限字符。
 *     maxVal (String): 当前区间上限字符。
 *     unitName (String): 数值对应业务指标名称（如“播放数”、“点赞数”）。
 *     unitSuffix (String): 数值单位后缀；计数指标为空，时长使用秒。
 *     onClick (Function0<Unit>): 点击卡片编辑区间的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页浏览数与点赞数卡片渲染。
 */
@Composable
private fun FilterRangeCard(
    shape: Shape,
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    minVal: String,
    maxVal: String,
    unitName: String,
    unitSuffix: String = "",
    onClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    MorphingPreferenceContainer(
        shape = shape,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            val min = minVal.toLongOrNull()?.takeIf { it > 0 }
            val max = maxVal.toLongOrNull()?.takeIf { it > 0 }
            val rangeText = when {
                min != null && max != null -> strings.getString(R.string.filter_range_both, unitName, min, max, unitSuffix)
                min != null -> strings.getString(R.string.filter_range_min, unitName, min, unitSuffix)
                max != null -> strings.getString(R.string.filter_range_max, unitName, max, unitSuffix)
                else -> strings.getString(R.string.filter_range_unset)
            }
            CompactPreferenceSwitch(
                title = title,
                checked = enabled,
                onCheckedChange = onEnabledChange,
                summary = rangeText
            )
        }
    }
}

/**
 * 关键词过滤卡片组件。
 *
 * 展示文案、标签、作者三类规则的统计摘要，并提供快捷开关及点击弹窗入口。
 *
 * Args:
 *     shape (Shape): 自适应圆角几何形状。
 *     title (String): 卡片标题文本。
 *     enabled (Boolean): 关键词过滤功能总开关状态。
 *     onEnabledChange (Function1<Boolean, Unit>): 总开关状态变更回调。
 *     config (KeywordFilterConfig): 关键词多分类配置实体。
 *     onClick (Function0<Unit>): 点击卡片展开管理弹窗的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 推荐流精准过滤卡片渲染。
 */
@Composable
private fun FilterKeywordsCard(
    shape: Shape,
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    config: KeywordFilterConfig,
    onClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    MorphingPreferenceContainer(
        shape = shape,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            val summaryText = if (config.totalCount == 0) {
                strings.getString(R.string.keyword_unset)
            } else {
                val parts = buildList {
                    if (config.desc.isNotEmpty()) add(strings.getString(R.string.keyword_description_count, config.desc.size))
                    if (config.tag.isNotEmpty()) add(strings.getString(R.string.keyword_tag_count, config.tag.size))
                    if (config.author.isNotEmpty()) add(strings.getString(R.string.keyword_author_count, config.author.size))
                }
                strings.getString(R.string.keyword_summary, parts.joinToString(" · "))
            }
            CompactPreferenceSwitch(
                title = title,
                checked = enabled,
                onCheckedChange = onEnabledChange,
                summary = summaryText
            )
        }
    }
}

/**
 * 长视频时长提示设置卡片组件。
 *
 * Args:
 *     shape (Shape): 紧凑自适应圆角几何形状。
 *     enabled (Boolean): 时长提示功能开关状态。
 *     onEnabledChange (Function1<Boolean, Unit>): 功能开关状态变化回调。
 *     threshold (String): 当前已配置的阈值分钟数字符串。
 *     onClick (Function0<Unit>): 点击卡片配置阈值时的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 功能页长视频时长提示卡片渲染。
 */
@Composable
private fun VideoDurationAlertCard(
    shape: Shape,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    threshold: String,
    onClick: () -> Unit
) {
    val strings = LocalContext.current.resources
    MorphingPreferenceContainer(
        shape = shape,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            CompactPreferenceSwitch(
                title = strings.getString(R.string.duration_alert_title),
                checked = enabled,
                onCheckedChange = onEnabledChange,
                summary = strings.getString(R.string.duration_alert_summary, threshold)
            )
        }
    }
}


/**
 * 自定义保存路径编辑弹窗。
 *
 * Args:
 *     initialVideoPath (String): 当前已配置的视频相对路径。
 *     initialImagePath (String): 当前已配置的图片相对路径。
 *     onDismiss (Function0<Unit>): 关闭弹窗回调。
 *     onSaved (Function2<String, String, Unit>): 提交路径回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击自定义保存路径卡片时展示。
 */
@Composable
fun DownloadPathDialog(
    initialVideoPath: String,
    initialImagePath: String,
    onDismiss: () -> Unit,
    onSaved: (videoPath: String, imagePath: String) -> Unit
) {
    val strings = LocalContext.current.resources
    var videoPathInput by remember { mutableStateOf(initialVideoPath) }
    var imagePathInput by remember { mutableStateOf(initialImagePath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.download_paths)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = videoPathInput,
                    onValueChange = { videoPathInput = it },
                    label = { Text(strings.getString(R.string.download_video_path)) },
                    placeholder = { Text(DownloadHook.DEFAULT_VIDEO_RELATIVE_PATH) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = imagePathInput,
                    onValueChange = { imagePathInput = it },
                    label = { Text(strings.getString(R.string.download_image_path)) },
                    placeholder = { Text(DownloadHook.DEFAULT_IMAGE_RELATIVE_PATH) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.getString(R.string.download_path_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaved(videoPathInput, imagePathInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(strings.getString(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 浏览数过滤范围编辑弹窗。
 *
 * Args:
 *     initialMin (String): 当前下限值。
 *     initialMax (String): 当前上限值。
 *     onDismiss (Function0<Unit>): 关闭弹窗回调。
 *     onSaved (Function2<String, String, Unit>): 提交范围回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击浏览数过滤卡片时展示。
 */
@Composable
fun ViewFilterRangeDialog(
    initialMin: String,
    initialMax: String,
    onDismiss: () -> Unit,
    onSaved: (String, String) -> Unit
) {
    val strings = LocalContext.current.resources
    var minInput by remember { mutableStateOf(initialMin) }
    var maxInput by remember { mutableStateOf(initialMax) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.views_range_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = minInput,
                    onValueChange = { minInput = it },
                    label = { Text(strings.getString(R.string.views_range_min)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = maxInput,
                    onValueChange = { maxInput = it },
                    label = { Text(strings.getString(R.string.views_range_max)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.getString(R.string.views_range_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaved(minInput, maxInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(strings.getString(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 点赞数过滤范围编辑弹窗。
 *
 * Args:
 *     initialMin (String): 当前下限值。
 *     initialMax (String): 当前上限值。
 *     onDismiss (Function0<Unit>): 关闭弹窗回调。
 *     onSaved (Function2<String, String, Unit>): 提交范围回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击点赞数过滤卡片时展示。
 */
@Composable
fun LikeFilterRangeDialog(
    initialMin: String,
    initialMax: String,
    onDismiss: () -> Unit,
    onSaved: (String, String) -> Unit
) {
    val strings = LocalContext.current.resources
    var minInput by remember { mutableStateOf(initialMin) }
    var maxInput by remember { mutableStateOf(initialMax) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.likes_range_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = minInput,
                    onValueChange = { minInput = it },
                    label = { Text(strings.getString(R.string.likes_range_min)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = maxInput,
                    onValueChange = { maxInput = it },
                    label = { Text(strings.getString(R.string.likes_range_max)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.getString(R.string.likes_range_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaved(minInput, maxInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(strings.getString(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 视频时长过滤范围编辑弹窗。
 *
 * Args:
 *     initialMin (String): 当前下限值（秒）。
 *     initialMax (String): 当前上限值（秒）。
 *     onDismiss (Function0<Unit>): 关闭弹窗回调。
 *     onSaved (Function2<String, String, Unit>): 提交范围回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击视频时长过滤卡片时展示。
 */
@Composable
fun DurationFilterRangeDialog(
    initialMin: String,
    initialMax: String,
    onDismiss: () -> Unit,
    onSaved: (String, String) -> Unit
) {
    val strings = LocalContext.current.resources
    var minInput by remember { mutableStateOf(initialMin) }
    var maxInput by remember { mutableStateOf(initialMax) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.duration_range_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = minInput,
                    onValueChange = { minInput = it },
                    label = { Text(strings.getString(R.string.duration_range_min)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = maxInput,
                    onValueChange = { maxInput = it },
                    label = { Text(strings.getString(R.string.duration_range_max)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strings.getString(R.string.duration_range_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaved(minInput, maxInput) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(strings.getString(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 关键词多分类（文案、标签、作者）管理与编辑弹窗组件。
 *
 * 提供分段选项卡切换三类独立规则列表，输入框内嵌对齐添加控件，
 * 支持快捷回车提交及单项/批量移除，确保过滤条件分类管理。
 *
 * Args:
 *     config (KeywordFilterConfig): 当前关键词多分类配置实体。
 *     onDismiss (Function0<Unit>): 弹窗关闭回调。
 *     onUpdateConfig (Function1<KeywordFilterConfig, Unit>): 规则更新持久化回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击关键词过滤卡片时展示。
 */
@Composable
fun KeywordListDialog(
    config: KeywordFilterConfig,
    onDismiss: () -> Unit,
    onUpdateConfig: (KeywordFilterConfig) -> Unit
) {
    val strings = LocalContext.current.resources
    var selectedCategory by remember { mutableStateOf(KeywordCategory.DESC) }
    var inputKeyword by remember { mutableStateOf("") }

    val currentList = config.getList(selectedCategory)

    val handleAdd = {
        val raw = inputKeyword.trim()
        val sanitized = when (selectedCategory) {
            KeywordCategory.DESC -> raw
            KeywordCategory.TAG -> raw.removePrefix("#").trim()
            KeywordCategory.AUTHOR -> raw.removePrefix("@").trim()
        }
        if (sanitized.isNotEmpty() && !currentList.contains(sanitized)) {
            val updatedList = currentList + sanitized
            onUpdateConfig(config.updateCategory(selectedCategory, updatedList))
            inputKeyword = ""
        }
    }

    val canAdd = inputKeyword.trim().let { raw ->
        val sanitized = when (selectedCategory) {
            KeywordCategory.DESC -> raw
            KeywordCategory.TAG -> raw.removePrefix("#").trim()
            KeywordCategory.AUTHOR -> raw.removePrefix("@").trim()
        }
        sanitized.isNotEmpty() && !currentList.contains(sanitized)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(strings.getString(R.string.keyword_manage))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().stableDialogHeight(440.dp).verticalScroll(rememberScrollState())) {
                // 1. 根据完整分类名称测量尺寸，保留圆角滑动指示器。
                val categories = KeywordCategory.entries
                CompactPreferenceCategories(
                    labels = categories.map { category ->
                        val categoryTitle = strings.getString(category.titleResource)
                        val count = config.getList(category).size
                        if (count > 0) strings.getString(R.string.keyword_category_count, categoryTitle, count)
                            else categoryTitle
                    },
                    selectedIndex = selectedCategory.ordinal,
                    onSelectedIndexChange = { index ->
                        selectedCategory = categories[index]
                        inputKeyword = ""
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 模式说明文案
                val hintDescription = when (selectedCategory) {
                    KeywordCategory.DESC -> strings.getString(R.string.keyword_description_hint)
                    KeywordCategory.TAG -> strings.getString(R.string.keyword_tag_hint)
                    KeywordCategory.AUTHOR -> strings.getString(R.string.keyword_author_hint)
                }
                Text(
                    text = hintDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. 输入框（内嵌 trailingIcon 绝对居中对齐）
                val placeholderText = when (selectedCategory) {
                    KeywordCategory.DESC -> strings.getString(R.string.keyword_description_input)
                    KeywordCategory.TAG -> strings.getString(R.string.keyword_tag_input)
                    KeywordCategory.AUTHOR -> strings.getString(R.string.keyword_author_input)
                }

                OutlinedTextField(
                    value = inputKeyword,
                    onValueChange = { inputKeyword = it },
                    placeholder = {
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        val iconVector = when (selectedCategory) {
                            KeywordCategory.DESC -> Icons.Outlined.Description
                            KeywordCategory.TAG -> Icons.Outlined.Tag
                            KeywordCategory.AUTHOR -> Icons.Outlined.Person
                        }
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = handleAdd,
                            enabled = canAdd
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddCircle,
                                contentDescription = strings.getString(R.string.keyword_add),
                                tint = if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { handleAdd() }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 当前分类规则列表
                if (currentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.getString(R.string.keyword_empty, strings.getString(selectedCategory.titleResource)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(currentList) { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val prefix = when (selectedCategory) {
                                        KeywordCategory.DESC -> ""
                                        KeywordCategory.TAG -> "#"
                                        KeywordCategory.AUTHOR -> "@"
                                    }
                                    Text(
                                        text = "$prefix$item",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            val updated = currentList.filter { it != item }
                                            onUpdateConfig(config.updateCategory(selectedCategory, updated))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = strings.getString(R.string.keyword_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_done))
            }
        }
    )
}

/**
 * 预设国家与地区选择与即时搜索弹窗。
 *
 * Args:
 *     currentIso (String): 当前激活的 ISO 国家代号。
 *     onDismiss (Function0<Unit>): 弹窗关闭回调。
 *     onSelected (Function1<RegionPreset, Unit>): 选中指定预设实体时的回调函数。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击地区卡片“选择预设”时展示。
 */
@Composable
fun RegionSelectionDialog(
    currentIso: String,
    onDismiss: () -> Unit,
    onSelected: (RegionPreset) -> Unit
) {
    val strings = LocalContext.current.resources
    var searchQuery by remember { mutableStateOf("") }
    val locale = strings.configuration.locales[0]
    val filteredList = remember(searchQuery, locale) {
        val query = searchQuery.trim()
        val namedMatches = RegionPresets.search(query).toSet()
        RegionPresets.search("").filter { preset ->
            preset in namedMatches ||
                Locale.forLanguageTag("und-${preset.isoCode}").getDisplayCountry(locale)
                    .contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.region_select_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().stableDialogHeight(460.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(strings.getString(R.string.region_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredList) { preset ->
                        val isSelected = preset.isoCode.equals(currentIso, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(preset) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${preset.flagEmoji}  ${Locale.forLanguageTag("und-${preset.isoCode}").getDisplayCountry(strings.configuration.locales[0])} (${preset.englishName})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = strings.getString(R.string.region_operator_details, preset.operatorName, preset.operatorCode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = preset.isoCode,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_close))
            }
        }
    )
}

/**
 * 自定义地区与 SIM 卡配置弹窗。
 *
 * Args:
 *     initialIso (String): 初始国家/地区代码。
 *     initialCode (String): 初始运营商代码。
 *     initialName (String): 初始运营商显示名称。
 *     onDismiss (Function0<Unit>): 弹窗关闭回调。
 *     onSaved (Function3<String, String, String, Unit>): 保存回调，依次传出 (iso, operatorCode, operatorName)。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击地区卡片“自定义参数”时展示。
 */
@Composable
fun CustomSimDialog(
    initialIso: String,
    initialCode: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSaved: (String, String, String) -> Unit
) {
    val strings = LocalContext.current.resources
    var iso by remember { mutableStateOf(initialIso) }
    var code by remember { mutableStateOf(initialCode) }
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.region_custom_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.getString(R.string.region_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = iso,
                    onValueChange = { iso = it.trim().uppercase() },
                    label = { Text(strings.getString(R.string.region_code_input)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.trim() },
                    label = { Text(strings.getString(R.string.region_operator_code_input)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.getString(R.string.region_operator_name_input)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalIso = iso.ifBlank { "JP" }
                    val finalCode = code.ifBlank { "00000" }
                    val finalName = name.ifBlank { "$finalIso Carrier" }
                    onSaved(finalIso, finalCode, finalName)
                }
            ) {
                Text(strings.getString(R.string.common_save_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 自定义系统语言配置弹窗。
 * 预设名称与语言代码在空间不足时整项换行，避免将代码挤成窄列。
 *
 * Args:
 *     initialLanguage (String): 当前配置的语言代码。
 *     onDismiss (Function0<Unit>): 弹窗关闭回调。
 *     onSaved (Function1<String, Unit>): 保存语言代码时的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击自定义语言按钮时展示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomLanguageDialog(
    initialLanguage: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val strings = LocalContext.current.resources
    var langTag by remember { mutableStateOf(initialLanguage) }
    val commonLangs = listOf(
        "ja-JP" to "日本語",
        "zh-TW" to "繁體中文 (台灣)",
        "zh-HK" to "繁體中文 (香港)",
        "en-US" to "English (US)",
        "ko-KR" to "한국어",
        "th-TH" to "ไทย",
        "vi-VN" to "Tiếng Việt",
        "id-ID" to "Bahasa Indonesia"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.language_custom)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.getString(R.string.language_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = langTag,
                    onValueChange = { langTag = it.trim() },
                    label = { Text(strings.getString(R.string.language_code_input)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = strings.getString(R.string.language_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(commonLangs) { (code, name) ->
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { langTag = code }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(text = name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalLang = langTag.ifBlank { "ja-JP" }
                    onSaved(finalLang)
                }
            ) {
                Text(strings.getString(R.string.common_save_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 自定义系统时区配置弹窗。
 * 预设城市说明与时区标识在空间不足时整项换行，保留完整标识。
 *
 * Args:
 *     initialTimeZone (String): 当前配置的时区标识。
 *     onDismiss (Function0<Unit>): 弹窗关闭回调。
 *     onSaved (Function1<String, Unit>): 保存时区标识时的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击自定义时区按钮时展示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomTimeZoneDialog(
    initialTimeZone: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val strings = LocalContext.current.resources
    var tzId by remember { mutableStateOf(initialTimeZone) }
    val commonTzs = listOf(
        "Asia/Tokyo" to strings.getString(R.string.timezone_city, strings.getString(R.string.city_tokyo), "GMT+9"),
        "Asia/Taipei" to strings.getString(R.string.timezone_city, strings.getString(R.string.city_taipei), "GMT+8"),
        "Asia/Hong_Kong" to strings.getString(R.string.timezone_city, strings.getString(R.string.city_hong_kong), "GMT+8"),
        "America/New_York" to strings.getString(R.string.timezone_city, strings.getString(R.string.city_new_york), "EST/EDT"),
        "Europe/London" to strings.getString(R.string.timezone_city, strings.getString(R.string.city_london), "GMT/BST"),
        "Asia/Seoul" to strings.getString(R.string.timezone_city, strings.getString(R.string.city_seoul), "GMT+9"),
        "Asia/Bangkok" to strings.getString(R.string.timezone_city, strings.getString(R.string.city_bangkok), "GMT+7"),
        "Asia/Singapore" to strings.getString(R.string.timezone_city, strings.getString(R.string.city_singapore), "GMT+8")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.timezone_custom)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.getString(R.string.timezone_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tzId,
                    onValueChange = { tzId = it.trim() },
                    label = { Text(strings.getString(R.string.timezone_input)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = strings.getString(R.string.timezone_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(commonTzs) { (id, desc) ->
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tzId = id }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(text = desc, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = id,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTz = tzId.ifBlank { "Asia/Tokyo" }
                    onSaved(finalTz)
                }
            ) {
                Text(strings.getString(R.string.common_save_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 自定义位置经纬度坐标配置弹窗。
 * 预设城市名称与坐标在空间不足时整项换行，避免长名称挤压坐标。
 *
 * Args:
 *     initialLatitude (String): 当前配置的纬度字符串。
 *     initialLongitude (String): 当前配置的经度字符串。
 *     onDismiss (Function0<Unit>): 弹窗关闭回调。
 *     onSaved (Function2<String, String, Unit>): 保存经纬度字符串时的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击自定义经纬度按钮时展示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomGpsDialog(
    initialLatitude: String,
    initialLongitude: String,
    onDismiss: () -> Unit,
    onSaved: (String, String) -> Unit
) {
    val strings = LocalContext.current.resources
    var lat by remember { mutableStateOf(initialLatitude) }
    var lng by remember { mutableStateOf(initialLongitude) }
    val commonCities = listOf(
        Triple(strings.getString(R.string.city_tokyo), "35.6762", "139.6503"),
        Triple(strings.getString(R.string.city_taipei), "25.0330", "121.5654"),
        Triple(strings.getString(R.string.city_hong_kong), "22.3193", "114.1694"),
        Triple(strings.getString(R.string.city_seoul), "37.5665", "126.9780"),
        Triple(strings.getString(R.string.city_new_york), "40.7128", "-74.0060"),
        Triple(strings.getString(R.string.city_london), "51.5074", "-0.1278"),
        Triple(strings.getString(R.string.city_bangkok), "13.7563", "100.5018"),
        Triple(strings.getString(R.string.city_singapore), "1.3521", "103.8198")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.gps_custom_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.getString(R.string.gps_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it.trim() },
                    label = { Text(strings.getString(R.string.gps_latitude)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it.trim() },
                    label = { Text(strings.getString(R.string.gps_longitude)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = strings.getString(R.string.gps_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(commonCities) { (cityName, cLat, cLng) ->
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    lat = cLat
                                    lng = cLng
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(text = cityName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "$cLat, $cLng",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalLat = lat.toDoubleOrNull()?.toString() ?: "35.6762"
                    val finalLng = lng.toDoubleOrNull()?.toString() ?: "139.6503"
                    onSaved(finalLat, finalLng)
                }
            ) {
                Text(strings.getString(R.string.common_save_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 添加自定义播放倍速选项对话框。
 *
 * Args:
 *     onDismiss (Function0<Unit>): 对话框取消或关闭时的回调。
 *     onAdded (Function1<Float, Unit>): 成功确认有效倍速数值时的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 用户点击添加倍速按钮时展示。
 */
@Composable
private fun AddSpeedDialog(
    onDismiss: () -> Unit,
    onAdded: (Float) -> Unit
) {
    val strings = LocalContext.current.resources
    var speedText by remember { mutableStateOf("") }
    var isInputError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.speed_add_title)) },
        text = {
            Column(Modifier.fillMaxWidth().stableDialogHeight(140.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val parsed = speedText.toFloatOrNull()
                val isOutOfRange = parsed != null && (parsed < PlaybackSpeedHook.MIN_SPEED || parsed > PlaybackSpeedHook.MAX_SPEED)

                OutlinedTextField(
                    value = speedText,
                    onValueChange = {
                        speedText = it.trim().replace('。', '.')
                        isInputError = false
                    },
                    label = { Text(strings.getString(R.string.speed_input)) },
                    placeholder = { Text("0.1 ~ 3.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = isInputError || isOutOfRange,
                    supportingText = {
                        if (isInputError) {
                            Text(strings.getString(R.string.speed_invalid), color = MaterialTheme.colorScheme.error)
                        } else if (isOutOfRange) {
                            Text(strings.getString(R.string.speed_out_of_range, PlaybackSpeedHook.MIN_SPEED.toString(), PlaybackSpeedHook.MAX_SPEED.toString()), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(strings.getString(R.string.speed_range, PlaybackSpeedHook.MIN_SPEED.toString(), PlaybackSpeedHook.MAX_SPEED.toString()))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = speedText.toFloatOrNull()
                    if (parsed == null) {
                        isInputError = true
                    } else if (parsed >= PlaybackSpeedHook.MIN_SPEED && parsed <= PlaybackSpeedHook.MAX_SPEED) {
                        onAdded(parsed)
                    }
                },
                enabled = speedText.isNotBlank() && speedText.toFloatOrNull()?.let { it in PlaybackSpeedHook.MIN_SPEED..PlaybackSpeedHook.MAX_SPEED } == true
            ) {
                Text(strings.getString(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 修改现有播放倍速选项对话框。
 *
 * Args:
 *     currentSpeed (Float): 当前选定待修改的原倍速。
 *     canDelete (Boolean): 当前是否允许执行删除。
 *     onDismiss (Function0<Unit>): 对话框取消或关闭时的回调。
 *     onDelete (Function0<Unit>): 用户确认删除当前档位时的回调。
 *     onSaved (Function2<Float, Float, Unit>): 成功修改确认时的回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 用户点击当前倍速 Chip 标签时展示。
 */
@Composable
private fun EditSpeedDialog(
    currentSpeed: Float,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSaved: (oldSpeed: Float, newSpeed: Float) -> Unit
) {
    val strings = LocalContext.current.resources
    var speedText by remember {
        mutableStateOf(if (currentSpeed % 1f == 0f) currentSpeed.toInt().toString() else currentSpeed.toString())
    }
    var isInputError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.speed_edit_title)) },
        text = {
            Column(Modifier.fillMaxWidth().stableDialogHeight(140.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val parsed = speedText.toFloatOrNull()
                val isOutOfRange = parsed != null && (parsed < PlaybackSpeedHook.MIN_SPEED || parsed > PlaybackSpeedHook.MAX_SPEED)

                OutlinedTextField(
                    value = speedText,
                    onValueChange = {
                        speedText = it.trim().replace('。', '.')
                        isInputError = false
                    },
                    label = { Text(strings.getString(R.string.speed_input)) },
                    placeholder = { Text("0.1 ~ 3.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = isInputError || isOutOfRange,
                    supportingText = {
                        if (isInputError) {
                            Text(strings.getString(R.string.speed_invalid), color = MaterialTheme.colorScheme.error)
                        } else if (isOutOfRange) {
                            Text(strings.getString(R.string.speed_out_of_range, PlaybackSpeedHook.MIN_SPEED.toString(), PlaybackSpeedHook.MAX_SPEED.toString()), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(strings.getString(R.string.speed_range, PlaybackSpeedHook.MIN_SPEED.toString(), PlaybackSpeedHook.MAX_SPEED.toString()))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = speedText.toFloatOrNull()
                    if (parsed == null) {
                        isInputError = true
                    } else if (parsed >= PlaybackSpeedHook.MIN_SPEED && parsed <= PlaybackSpeedHook.MAX_SPEED) {
                        onSaved(currentSpeed, parsed)
                    }
                },
                enabled = speedText.isNotBlank() && speedText.toFloatOrNull()?.let { it in PlaybackSpeedHook.MIN_SPEED..PlaybackSpeedHook.MAX_SPEED } == true
            ) {
                Text(strings.getString(R.string.common_save))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(strings.getString(R.string.common_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(strings.getString(R.string.common_cancel))
                }
            }
        }
    )
}

/**
 * 视频播放时长提示阈值配置弹窗。
 *
 * Args:
 *     initialThreshold (String): 当前已配置的阈值分钟数字符串。
 *     onDismiss (Function0<Unit>): 关闭弹窗回调。
 *     onSaved (Function1<String, Unit>): 保存新阈值字符串回调。
 *
 * Returns:
 *     Unit: 无返回值。
 *
 * Callers:
 *     - `io.github.meiyongai.toki.ui.screen.DashboardScreen`: 点击「长视频时长提示」卡片时展示。
 */
@Composable
fun VideoDurationAlertDialog(
    initialThreshold: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val strings = LocalContext.current.resources
    var thresholdInput by remember { mutableStateOf(initialThreshold) }
    var isInputError by remember { mutableStateOf(false) }
    val quickPresets = listOf("1", "3", "5", "10", "15")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.duration_threshold_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().stableDialogHeight(280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = strings.getString(R.string.duration_threshold_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val parsed = thresholdInput.trim().replace('。', '.').toDoubleOrNull()
                val isInvalid = parsed == null || parsed <= 0.0

                OutlinedTextField(
                    value = thresholdInput,
                    onValueChange = {
                        thresholdInput = it.trim().replace('。', '.')
                        isInputError = false
                    },
                    label = { Text(strings.getString(R.string.duration_threshold_input)) },
                    placeholder = { Text(strings.getString(R.string.duration_threshold_example)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = isInputError || isInvalid,
                    supportingText = {
                        if (isInputError || isInvalid) {
                            Text(strings.getString(R.string.duration_threshold_invalid), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(strings.getString(R.string.duration_threshold_current, thresholdInput))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = strings.getString(R.string.duration_threshold_presets),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPresets.forEach { preset ->
                        val isSelected = thresholdInput == preset
                        OutlinedButton(
                            onClick = {
                                thresholdInput = preset
                                isInputError = false
                            },
                            colors = if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = thresholdInput.trim().replace('。', '.').toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        isInputError = true
                    } else {
                        val cleanString = if (parsed % 1.0 == 0.0) parsed.toInt().toString() else parsed.toString()
                        onSaved(cleanString)
                    }
                }
            ) {
                Text(strings.getString(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.common_cancel))
            }
        }
    )
}

/**
 * 将关键词分类映射到界面资源，保持过滤配置中的分类标识不变。
 * @receiver 当前关键词分类。
 * @return 对应分类名称的字符串资源标识。
 * Callers: KeywordListDialog。
 */
private val KeywordCategory.titleResource: Int
    get() = when (this) {
        KeywordCategory.DESC -> R.string.keyword_description
        KeywordCategory.TAG -> R.string.keyword_tag
        KeywordCategory.AUTHOR -> R.string.keyword_author
    }
