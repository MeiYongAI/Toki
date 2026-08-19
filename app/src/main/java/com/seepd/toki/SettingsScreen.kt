package com.seepd.toki

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val GITHUB_URL = "https://github.com/MeiYongAI/Toki"
private const val TELEGRAM_URL = "https://t.me/toki_lsposed"
private const val ISSUE_URL =
    "https://github.com/MeiYongAI/Toki/issues/new?template=bug_report.yml"

private enum class SettingsDialog {
    NONE,
    LANGUAGE,
    CLEAR_TIKTOK_CACHE,
    RESET_SETTINGS,
    REGION,
    MEDIA_DIRECTORY,
    DURATION,
    PLAYBACK_SPEED,
    VIEW_RANGE,
    LIKE_RANGE,
    PAGE_PURIFICATION,
    GPS_COORDINATES,
}

private enum class MediaDirectoryTarget {
    VIDEO,
    PICTURE,
    GIF,
}

private enum class SettingsDestination(@param:StringRes val title: Int) {
    HOME(R.string.settings_home),
    GENERAL(R.string.settings_general),
    FEED(R.string.settings_feed),
    DOWNLOADS(R.string.settings_downloads),
}

private enum class PagePurificationOption(@param:StringRes val title: Int) {
    AUTHOR_AVATAR(R.string.purify_author_avatar),
    AUTHOR_INFO(R.string.purify_author_info),
    FOLLOW_BUTTON(R.string.purify_follow_button),
    VIDEO_DESCRIPTION(R.string.purify_video_description),
    VIDEO_TAGS(R.string.purify_video_tags),
    MUSIC_TITLE(R.string.purify_music_title),
    MUSIC_COVER(R.string.purify_music_cover),
    LIKE_BUTTON(R.string.purify_like_button),
    COMMENT_BUTTON(R.string.purify_comment_button),
    FAVORITE_BUTTON(R.string.purify_favorite_button),
    SHARE_BUTTON(R.string.purify_share_button),
    DUET_BUTTON(R.string.purify_duet_button),
    STITCH_BUTTON(R.string.purify_stitch_button),
    QUICK_DM(R.string.purify_quick_dm),
    STORY_TAGS(R.string.purify_story_tags),
    COLLAB_LABEL(R.string.purify_collab_label),
    COMMERCIAL_LABELS(R.string.purify_commercial_labels),
    CREATIVE_TOOL_ANCHORS(R.string.purify_creative_tool_anchors),
    MOVIE_ANIME_ANCHORS(R.string.purify_movie_anime_anchors),
    GAME_ANCHORS(R.string.purify_game_anchors),
    INCENTIVE_SHARE(R.string.purify_incentive_share),
    TAKO(R.string.purify_tako),
    CONTENT_SEARCH(R.string.purify_content_search),
    SAFETY_WARNING(R.string.purify_safety_warning),
    TRANSLATION_CONTROLS(R.string.purify_translation_controls),
    STATUS_BAR(R.string.purify_status_bar),
    LIVE_ENTRY(R.string.purify_live_entry),
    TOP_NAVIGATION(R.string.purify_top_navigation),
    SEARCH_ENTRY(R.string.purify_search_entry),
    BOTTOM_NAVIGATION(R.string.purify_bottom_navigation),
    VIDEO_PROGRESS_BAR(R.string.purify_video_progress_bar),
    ;

    fun isSelected(state: SettingsUiState): Boolean = when (this) {
        AUTHOR_AVATAR -> state.hideAuthorAvatar
        AUTHOR_INFO -> state.hideAuthorInfo
        FOLLOW_BUTTON -> state.hideFollowButton
        VIDEO_DESCRIPTION -> state.hideVideoDescription
        VIDEO_TAGS -> state.hideVideoTags
        MUSIC_TITLE -> state.hideMusicTitle
        MUSIC_COVER -> state.hideMusicCover
        LIKE_BUTTON -> state.hideLikeButton
        COMMENT_BUTTON -> state.hideCommentButton
        FAVORITE_BUTTON -> state.hideFavoriteButton
        SHARE_BUTTON -> state.hideShareButton
        DUET_BUTTON -> state.hideDuetButton
        STITCH_BUTTON -> state.hideStitchButton
        QUICK_DM -> state.hideQuickDm
        STORY_TAGS -> state.hideStoryTags
        COLLAB_LABEL -> state.hideCollabLabel
        COMMERCIAL_LABELS -> state.hideCommercialLabels
        CREATIVE_TOOL_ANCHORS -> state.hideCreativeToolAnchors
        MOVIE_ANIME_ANCHORS -> state.hideMovieAnimeAnchors
        GAME_ANCHORS -> state.hideGameAnchors
        INCENTIVE_SHARE -> state.hideIncentiveShare
        TAKO -> state.hideTako
        CONTENT_SEARCH -> state.hideContentSearch
        SAFETY_WARNING -> state.hideSafetyWarning
        TRANSLATION_CONTROLS -> state.hideTranslationControls
        STATUS_BAR -> state.hideStatusBar
        LIVE_ENTRY -> state.hideLiveEntry
        TOP_NAVIGATION -> state.hideTopNavigation
        SEARCH_ENTRY -> state.hideSearchEntry
        BOTTOM_NAVIGATION -> state.hideBottomNavigation
        VIDEO_PROGRESS_BAR -> state.hideVideoProgressBar
    }

    fun update(state: SettingsUiState, selected: Boolean): SettingsUiState = when (this) {
        AUTHOR_AVATAR -> state.copy(hideAuthorAvatar = selected)
        AUTHOR_INFO -> state.copy(hideAuthorInfo = selected)
        FOLLOW_BUTTON -> state.copy(hideFollowButton = selected)
        VIDEO_DESCRIPTION -> state.copy(hideVideoDescription = selected)
        VIDEO_TAGS -> state.copy(hideVideoTags = selected)
        MUSIC_TITLE -> state.copy(hideMusicTitle = selected)
        MUSIC_COVER -> state.copy(hideMusicCover = selected)
        LIKE_BUTTON -> state.copy(hideLikeButton = selected)
        COMMENT_BUTTON -> state.copy(hideCommentButton = selected)
        FAVORITE_BUTTON -> state.copy(hideFavoriteButton = selected)
        SHARE_BUTTON -> state.copy(hideShareButton = selected)
        DUET_BUTTON -> state.copy(hideDuetButton = selected)
        STITCH_BUTTON -> state.copy(hideStitchButton = selected)
        QUICK_DM -> state.copy(hideQuickDm = selected)
        STORY_TAGS -> state.copy(hideStoryTags = selected)
        COLLAB_LABEL -> state.copy(hideCollabLabel = selected)
        COMMERCIAL_LABELS -> state.copy(hideCommercialLabels = selected)
        CREATIVE_TOOL_ANCHORS -> state.copy(hideCreativeToolAnchors = selected)
        MOVIE_ANIME_ANCHORS -> state.copy(hideMovieAnimeAnchors = selected)
        GAME_ANCHORS -> state.copy(hideGameAnchors = selected)
        INCENTIVE_SHARE -> state.copy(hideIncentiveShare = selected)
        TAKO -> state.copy(hideTako = selected)
        CONTENT_SEARCH -> state.copy(hideContentSearch = selected)
        SAFETY_WARNING -> state.copy(hideSafetyWarning = selected)
        TRANSLATION_CONTROLS -> state.copy(hideTranslationControls = selected)
        STATUS_BAR -> state.copy(hideStatusBar = selected)
        LIVE_ENTRY -> state.copy(hideLiveEntry = selected)
        TOP_NAVIGATION -> state.copy(hideTopNavigation = selected)
        SEARCH_ENTRY -> state.copy(hideSearchEntry = selected)
        BOTTOM_NAVIGATION -> state.copy(hideBottomNavigation = selected)
        VIDEO_PROGRESS_BAR -> state.copy(hideVideoProgressBar = selected)
    }
}

@Composable
internal fun SettingsApp(
    state: SettingsUiState,
    homeState: HomeUiState,
    onUpdate: ((SettingsUiState) -> SettingsUiState) -> Unit,
    restartStatus: RootActionStatus,
    cacheClearStatus: RootActionStatus,
    onRestartTikTok: () -> Unit,
    onClearTikTokCache: () -> Unit,
    onRestartStatusConsumed: () -> Unit,
    onCacheClearStatusConsumed: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onResetSettings: () -> Unit,
) {
    var dialogName by rememberSaveable { mutableStateOf(SettingsDialog.NONE.name) }
    var mediaDirectoryTargetName by rememberSaveable {
        mutableStateOf(MediaDirectoryTarget.VIDEO.name)
    }
    val restartSnackbarHostState = remember { SnackbarHostState() }
    val activeDialog = SettingsDialog.valueOf(dialogName)
    val closeDialog = { dialogName = SettingsDialog.NONE.name }
    val restartMessage = when (restartStatus) {
        RootActionStatus.SUCCESS -> stringResource(R.string.restart_tiktok_success)
        RootActionStatus.NO_ROOT -> stringResource(R.string.root_access_required)
        RootActionStatus.FAILED -> stringResource(R.string.restart_tiktok_failed)
        RootActionStatus.TIMEOUT -> stringResource(R.string.root_action_timeout)
        RootActionStatus.IDLE,
        RootActionStatus.RUNNING -> null
    }
    LaunchedEffect(restartMessage) {
        if (restartMessage != null) {
            restartSnackbarHostState.showSnackbar(restartMessage)
            onRestartStatusConsumed()
        }
    }
    val cacheClearMessage = when (cacheClearStatus) {
        RootActionStatus.SUCCESS -> stringResource(R.string.clear_tiktok_cache_success)
        RootActionStatus.NO_ROOT -> stringResource(R.string.root_access_required)
        RootActionStatus.FAILED -> stringResource(R.string.clear_tiktok_cache_failed)
        RootActionStatus.TIMEOUT -> stringResource(R.string.root_action_timeout)
        RootActionStatus.IDLE,
        RootActionStatus.RUNNING -> null
    }
    LaunchedEffect(cacheClearMessage) {
        if (cacheClearMessage != null) {
            restartSnackbarHostState.showSnackbar(cacheClearMessage)
            onCacheClearStatusConsumed()
        }
    }
    val openMediaDirectory = { target: MediaDirectoryTarget ->
        mediaDirectoryTargetName = target.name
        dialogName = SettingsDialog.MEDIA_DIRECTORY.name
    }
    var destinationName by rememberSaveable { mutableStateOf(SettingsDestination.HOME.name) }
    val destination = SettingsDestination.valueOf(destinationName)
    val useNavigationRail = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp
    }

    SettingsNavigationLayout(
        destination = destination,
        useNavigationRail = useNavigationRail,
        onDestinationSelected = { destinationName = it.name },
        snackbarHostState = restartSnackbarHostState,
    ) { contentModifier ->
        SettingsContent(
            destination = destination,
            state = state,
            homeState = homeState,
            restartStatus = restartStatus,
            cacheClearStatus = cacheClearStatus,
            onUpdate = onUpdate,
            onDialog = { dialogName = it.name },
            onRestartTikTok = onRestartTikTok,
            onPickVideoDirectory = { openMediaDirectory(MediaDirectoryTarget.VIDEO) },
            onPickPictureDirectory = { openMediaDirectory(MediaDirectoryTarget.PICTURE) },
            onPickGifDirectory = { openMediaDirectory(MediaDirectoryTarget.GIF) },
            modifier = contentModifier,
        )
    }

    when (activeDialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.LANGUAGE -> LanguageDialog(
            selected = homeState.language,
            onSelect = { language ->
                closeDialog()
                onLanguageSelected(language)
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.CLEAR_TIKTOK_CACHE -> ConfirmationDialog(
            title = R.string.clear_tiktok_cache,
            message = R.string.clear_tiktok_cache_confirmation,
            confirmLabel = R.string.clear_cache,
            onConfirm = {
                closeDialog()
                onClearTikTokCache()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.RESET_SETTINGS -> ConfirmationDialog(
            title = R.string.reset_settings,
            message = R.string.reset_settings_confirmation,
            confirmLabel = R.string.reset,
            onConfirm = {
                closeDialog()
                onResetSettings()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.REGION -> RegionDialog(
            selected = state.region,
            onSelect = {
                onUpdate { current -> current.copy(region = it) }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.GPS_COORDINATES -> GpsCoordinatesDialog(
            initialLatitude = state.gpsLatitude,
            initialLongitude = state.gpsLongitude,
            onSave = { latitude, longitude ->
                onUpdate { current ->
                    current.copy(gpsLatitude = latitude, gpsLongitude = longitude)
                }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.MEDIA_DIRECTORY -> {
            val target = MediaDirectoryTarget.valueOf(mediaDirectoryTargetName)
            val currentPath = when (target) {
                MediaDirectoryTarget.VIDEO -> state.videoLocation
                MediaDirectoryTarget.PICTURE -> state.picLocation
                MediaDirectoryTarget.GIF -> state.gifLocation
            }
            val title = when (target) {
                MediaDirectoryTarget.VIDEO -> stringResource(R.string.video_location)
                MediaDirectoryTarget.PICTURE -> stringResource(R.string.pic_location)
                MediaDirectoryTarget.GIF -> stringResource(R.string.gif_location)
            }
            MediaDirectoryDialog(
                title = title,
                initialPath = currentPath,
                onSave = { path ->
                    onUpdate { current ->
                        when (target) {
                            MediaDirectoryTarget.VIDEO -> current.copy(videoLocation = path)
                            MediaDirectoryTarget.PICTURE -> current.copy(picLocation = path)
                            MediaDirectoryTarget.GIF -> current.copy(gifLocation = path)
                        }
                    }
                    closeDialog()
                },
                onDismiss = closeDialog,
            )
        }
        SettingsDialog.DURATION -> DurationDialog(
            initialValue = state.longPostSeconds,
            onSave = { seconds ->
                onUpdate { current -> current.copy(longPostSeconds = seconds) }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.PLAYBACK_SPEED -> PlaybackSpeedDialog(
            selected = state.defaultPlaybackSpeed,
            onSelect = { speed ->
                onUpdate { current -> current.copy(defaultPlaybackSpeed = speed) }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.VIEW_RANGE -> RangeDialog(
            title = R.string.views_range,
            initialMinimum = state.viewsMinInput,
            initialMaximum = state.viewsMaxInput,
            onSave = { range ->
                onUpdate { current ->
                    current.copy(
                        viewsMin = range.minimum,
                        viewsMax = range.maximum,
                        viewsMinInput = range.minimumInput,
                        viewsMaxInput = range.maximumInput,
                    )
                }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.LIKE_RANGE -> RangeDialog(
            title = R.string.likes_range,
            initialMinimum = state.likesMinInput,
            initialMaximum = state.likesMaxInput,
            onSave = { range ->
                onUpdate { current ->
                    current.copy(
                        likesMin = range.minimum,
                        likesMax = range.maximum,
                        likesMinInput = range.minimumInput,
                        likesMaxInput = range.maximumInput,
                    )
                }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.PAGE_PURIFICATION -> PagePurificationDialog(
            state = state,
            onSave = { selectedOptions ->
                onUpdate { current ->
                    PagePurificationOption.entries.fold(current) { updated, option ->
                        option.update(updated, option in selectedOptions)
                    }
                }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
    }
}

@Composable
private fun SettingsNavigationLayout(
    destination: SettingsDestination,
    useNavigationRail: Boolean,
    onDestinationSelected: (SettingsDestination) -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable (Modifier) -> Unit,
) {
    if (useNavigationRail) {
        Row(Modifier.fillMaxSize()) {
            SettingsNavigationRail(
                destination = destination,
                onDestinationSelected = onDestinationSelected,
            )
            SettingsScaffold(
                modifier = Modifier.weight(1f),
                destination = destination,
                snackbarHostState = snackbarHostState,
                content = content,
            )
        }
    } else {
        SettingsScaffold(
            destination = destination,
            snackbarHostState = snackbarHostState,
            bottomBar = {
                SettingsBottomNavigation(
                    destination = destination,
                    onDestinationSelected = onDestinationSelected,
                )
            },
            content = content,
        )
    }
}

@Composable
private fun SettingsBottomNavigation(
    destination: SettingsDestination,
    onDestinationSelected: (SettingsDestination) -> Unit,
) {
    NavigationBar {
        SettingsDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = item == destination,
                onClick = { onDestinationSelected(item) },
                icon = { SettingsDestinationIcon(item) },
                label = { Text(stringResource(item.title)) },
            )
        }
    }
}

@Composable
private fun SettingsNavigationRail(
    destination: SettingsDestination,
    onDestinationSelected: (SettingsDestination) -> Unit,
) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        SettingsDestination.entries.forEach { item ->
            NavigationRailItem(
                selected = item == destination,
                onClick = { onDestinationSelected(item) },
                icon = { SettingsDestinationIcon(item) },
                label = { Text(stringResource(item.title)) },
            )
        }
    }
}

@Composable
private fun SettingsDestinationIcon(destination: SettingsDestination) {
    val imageVector = when (destination) {
        SettingsDestination.HOME -> Icons.Outlined.Home
        SettingsDestination.GENERAL -> Icons.Outlined.Settings
        SettingsDestination.FEED -> Icons.Outlined.PlayArrow
        SettingsDestination.DOWNLOADS -> Icons.AutoMirrored.Outlined.List
    }
    Icon(
        imageVector = imageVector,
        contentDescription = null,
    )
}

@Composable
private fun SettingsScaffold(
    modifier: Modifier = Modifier,
    destination: SettingsDestination,
    snackbarHostState: SnackbarHostState,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopAppBar(destination = destination)
        },
        bottomBar = bottomBar,
    ) { contentPadding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopAppBar(
    destination: SettingsDestination,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(destination.title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SettingsContent(
    destination: SettingsDestination,
    state: SettingsUiState,
    homeState: HomeUiState,
    restartStatus: RootActionStatus,
    cacheClearStatus: RootActionStatus,
    onUpdate: ((SettingsUiState) -> SettingsUiState) -> Unit,
    onDialog: (SettingsDialog) -> Unit,
    onRestartTikTok: () -> Unit,
    onPickVideoDirectory: () -> Unit,
    onPickPictureDirectory: () -> Unit,
    onPickGifDirectory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val uriHandler = LocalUriHandler.current
    val rootActionRunning = restartStatus == RootActionStatus.RUNNING ||
        cacheClearStatus == RootActionStatus.RUNNING
    SettingsList(modifier) {
        when (destination) {
            SettingsDestination.HOME -> {
                item(key = "home_status_group") {
                    SettingsGroup {
                        SettingsSectionHeader(R.string.home_module_status)
                        StatusSettingRow(
                            title = stringResource(R.string.home_lsposed_service),
                            value = if (homeState.moduleConnected) {
                                stringResource(R.string.home_status_connected)
                            } else {
                                stringResource(R.string.home_status_disconnected)
                            },
                            positive = homeState.moduleConnected,
                        )
                        GroupDivider()
                        StatusSettingRow(
                            title = "TikTok",
                            value = tikTokStatusText(homeState),
                            positive = homeState.tikTokInstalled &&
                                homeState.tikTokVersion == ModuleConfig.TESTED_TIKTOK_VERSION,
                        )
                    }
                }

                item(key = "home_actions_group") {
                    SettingsGroup {
                        SettingsSectionHeader(R.string.home_quick_actions)
                        HomeActionRow(
                            title = stringResource(R.string.restart_tiktok),
                            summary = stringResource(R.string.restart_tiktok_summary),
                            icon = Icons.Outlined.Refresh,
                            running = restartStatus == RootActionStatus.RUNNING,
                            enabled = homeState.tikTokInstalled && !rootActionRunning,
                            onClick = onRestartTikTok,
                        )
                        GroupDivider()
                        HomeActionRow(
                            title = stringResource(R.string.clear_tiktok_cache),
                            summary = stringResource(R.string.clear_tiktok_cache_summary),
                            icon = Icons.Outlined.Clear,
                            running = cacheClearStatus == RootActionStatus.RUNNING,
                            enabled = homeState.tikTokInstalled && !rootActionRunning,
                            onClick = { onDialog(SettingsDialog.CLEAR_TIKTOK_CACHE) },
                        )
                    }
                }

                item(key = "home_settings_group") {
                    SettingsGroup {
                        SettingsSectionHeader(R.string.home_toki_settings)
                        ValueSettingRow(
                            title = stringResource(R.string.app_language),
                            value = appLanguageLabel(homeState.language),
                            onClick = { onDialog(SettingsDialog.LANGUAGE) },
                        )
                        GroupDivider()
                        HomeActionRow(
                            title = stringResource(R.string.reset_settings),
                            summary = stringResource(R.string.reset_settings_summary),
                            icon = Icons.Outlined.Refresh,
                            onClick = { onDialog(SettingsDialog.RESET_SETTINGS) },
                        )
                    }
                }

                item(key = "home_project_group") {
                    SettingsGroup {
                        SettingsSectionHeader(R.string.home_project)
                        ValueSettingRow(
                            title = "GitHub",
                            value = "MeiYongAI/Toki",
                            onClick = { uriHandler.openUri(GITHUB_URL) },
                        )
                        GroupDivider()
                        ValueSettingRow(
                            title = "Telegram",
                            value = "@toki_lsposed",
                            onClick = { uriHandler.openUri(TELEGRAM_URL) },
                        )
                        GroupDivider()
                        ValueSettingRow(
                            title = stringResource(R.string.report_issue),
                            value = "GitHub Issues",
                            onClick = { uriHandler.openUri(ISSUE_URL) },
                        )
                    }
                }
            }

            SettingsDestination.GENERAL -> {
                item(key = "common_group") {
                    SettingsGroup {
                SettingsSectionHeader(R.string.settings_section_region)
                ValueSettingRow(
                    title = stringResource(R.string.region),
                    value = "${state.region.localizedDisplayName(locale)} (${state.region.code})",
                    onClick = { onDialog(SettingsDialog.REGION) },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.enable_region_spoof),
                    summary = stringResource(R.string.enable_region_spoof_summary),
                    checked = state.regionSpoof,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(regionSpoof = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.language_spoof),
                    summary = stringResource(R.string.language_spoof_summary),
                    checked = state.languageSpoof,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(languageSpoof = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.timezone_spoof),
                    summary = stringResource(R.string.timezone_spoof_summary),
                    checked = state.timeZoneSpoof,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(timeZoneSpoof = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.skip_startup_login),
                    summary = stringResource(R.string.skip_startup_login_summary),
                    checked = state.skipStartupLogin,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(skipStartupLogin = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.force_region),
                    summary = stringResource(R.string.force_region_summary),
                    checked = state.forceRegion,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(forceRegion = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.gps_spoof),
                    summary = stringResource(R.string.gps_spoof_summary),
                    checked = state.gpsSpoof,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(gpsSpoof = checked) }
                    },
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.gps_coordinates),
                    value = "${state.gpsLatitude}, ${state.gpsLongitude}",
                    enabled = state.gpsSpoof,
                    onClick = { onDialog(SettingsDialog.GPS_COORDINATES) },
                )
                SettingsSectionHeader(R.string.settings_section_playback)
                SwitchSettingRow(
                    title = stringResource(R.string.auto_translate_comments),
                    summary = stringResource(R.string.auto_translate_comments_summary),
                    checked = state.autoTranslateComments,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(autoTranslateComments = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.disable_loop),
                    summary = stringResource(R.string.disable_loop_summary),
                    checked = state.disableLoop,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(disableLoop = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.always_show_video_progress_bar),
                    summary = stringResource(R.string.always_show_video_progress_bar_summary),
                    checked = state.alwaysShowVideoProgressBar,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(alwaysShowVideoProgressBar = checked) }
                    },
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.default_playback_speed),
                    value = formatPlaybackSpeed(state.defaultPlaybackSpeed),
                    onClick = { onDialog(SettingsDialog.PLAYBACK_SPEED) },
                )
                    }
                }
            }

            SettingsDestination.DOWNLOADS -> {
                item(key = "download_creation_group") {
                    SettingsGroup {
                SettingsSectionHeader(R.string.settings_section_storage)
                SwitchSettingRow(
                    title = stringResource(R.string.remove_download_restrictions),
                    summary = stringResource(R.string.remove_download_restrictions_summary),
                    checked = state.downloadRestrictions,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(downloadRestrictions = checked) }
                    },
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.video_location),
                    value = state.videoLocation,
                    onClick = onPickVideoDirectory,
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.pic_location),
                    value = state.picLocation,
                    onClick = onPickPictureDirectory,
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.gif_location),
                    value = state.gifLocation,
                    onClick = onPickGifDirectory,
                )
                SettingsSectionHeader(R.string.settings_section_creation)
                SwitchSettingRow(
                    title = stringResource(R.string.allow_duet),
                    summary = stringResource(R.string.allow_duet_summary),
                    checked = state.allowDuet,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(allowDuet = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.allow_stitch),
                    summary = stringResource(R.string.allow_stitch_summary),
                    checked = state.allowStitch,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(allowStitch = checked) }
                    },
                )
                    }
                }
            }

            SettingsDestination.FEED -> {
                item(key = "filters_group") {
                    SettingsGroup {
                SettingsSectionHeader(R.string.settings_section_content_filter)
                SwitchSettingRow(
                    title = stringResource(R.string.hide_feed_ads),
                    summary = stringResource(R.string.hide_feed_ads_summary),
                    checked = state.hideFeedAds,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(hideFeedAds = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.hide_live),
                    summary = stringResource(R.string.hide_live_summary),
                    checked = state.hideLive,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(hideLive = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.hide_images),
                    summary = stringResource(R.string.hide_images_summary),
                    checked = state.hideImages,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(hideImages = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.hide_ai_generated),
                    summary = stringResource(R.string.hide_ai_generated_summary),
                    checked = state.hideAiGenerated,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(hideAiGenerated = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.hide_trending_topics),
                    summary = stringResource(R.string.hide_trending_topics_summary),
                    checked = state.hideTrendingTopics,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(hideTrendingTopics = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.hide_content_classification),
                    summary = stringResource(R.string.hide_content_classification_summary),
                    checked = state.hideContentClassification,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(hideContentClassification = checked) }
                    },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.hide_long_posts),
                    summary = stringResource(R.string.hide_long_posts_summary),
                    checked = state.hideLongPosts,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(hideLongPosts = checked) }
                    },
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.long_post_seconds),
                    value = pluralStringResource(
                        R.plurals.seconds_value,
                        state.longPostSeconds,
                        state.longPostSeconds,
                    ),
                    enabled = state.hideLongPosts,
                    onClick = { onDialog(SettingsDialog.DURATION) },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.disable_offline_cold_cache_with_network),
                    summary = stringResource(
                        R.string.disable_offline_cold_cache_with_network_summary,
                    ),
                    checked = state.disableOfflineColdCacheWithNetwork,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(disableOfflineColdCacheWithNetwork = checked) }
                    },
                )
                SettingsSectionHeader(R.string.settings_section_metric_filter)
                SwitchSettingRow(
                    title = stringResource(R.string.filter_views_likes),
                    summary = stringResource(R.string.filter_views_likes_summary),
                    checked = state.filterViewsLikes,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(filterViewsLikes = checked) }
                    },
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.views_range),
                    value = formatRange(state.viewsMinInput, state.viewsMaxInput),
                    enabled = state.filterViewsLikes,
                    onClick = { onDialog(SettingsDialog.VIEW_RANGE) },
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.likes_range),
                    value = formatRange(state.likesMinInput, state.likesMaxInput),
                    enabled = state.filterViewsLikes,
                    onClick = { onDialog(SettingsDialog.LIKE_RANGE) },
                )
                    }
                }

                item(key = "feed_display_group") {
                    SettingsGroup {
                        SettingsSectionHeader(R.string.settings_section_feed_display)
                        SwitchSettingRow(
                            title = stringResource(R.string.show_author_location),
                            summary = stringResource(R.string.show_author_location_summary),
                            checked = state.showAuthorLocation,
                            onCheckedChange = { checked ->
                                onUpdate { it.copy(showAuthorLocation = checked) }
                            },
                        )
                    }
                }

                item(key = "page_purification_group") {
                    SettingsGroup {
                        SettingsSectionHeader(R.string.settings_section_page_purification)
                        ValueSettingRow(
                            title = stringResource(R.string.page_purification_choose_items),
                            value = pagePurificationSelectionSummary(state),
                            onClick = { onDialog(SettingsDialog.PAGE_PURIFICATION) },
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun tikTokStatusText(state: HomeUiState): String {
    if (!state.tikTokInstalled) {
        return stringResource(R.string.home_tiktok_not_installed)
    }
    val version = state.tikTokVersion ?: stringResource(R.string.home_version_unknown)
    return if (state.tikTokVersion == ModuleConfig.TESTED_TIKTOK_VERSION) {
        stringResource(R.string.home_tiktok_tested_version, version)
    } else {
        stringResource(R.string.home_tiktok_untested_version, version)
    }
}

@Composable
private fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.app_language_system)
    AppLanguage.ENGLISH -> "English"
    AppLanguage.CHINESE -> "中文"
}

@Composable
private fun pagePurificationSelectionSummary(state: SettingsUiState): String {
    val selectedCount = PagePurificationOption.entries.count { it.isSelected(state) }
    return if (selectedCount == 0) {
        stringResource(R.string.page_purification_none_selected)
    } else {
        pluralStringResource(
            R.plurals.page_purification_selected_count,
            selectedCount,
            selectedCount,
        )
    }
}

@Composable
private fun PagePurificationDialog(
    state: SettingsUiState,
    onSave: (Set<PagePurificationOption>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedOptions = remember(state) {
        mutableStateListOf<PagePurificationOption>().apply {
            addAll(PagePurificationOption.entries.filter { it.isSelected(state) })
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_purification)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
            ) {
                items(
                    items = PagePurificationOption.entries,
                    key = { option -> option.name },
                ) { option ->
                    val checked = option in selectedOptions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { selected ->
                                    if (selected) {
                                        selectedOptions.add(option)
                                    } else {
                                        selectedOptions.remove(option)
                                    }
                                },
                            )
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(option.title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedOptions.toSet()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun LanguageDialog(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .selectable(
                                selected = language == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(language) },
                            )
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(
                            selected = language == selected,
                            onClick = null,
                        )
                        Text(
                            text = appLanguageLabel(language),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ConfirmationDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes confirmLabel: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(confirmLabel))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SettingsList(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(modifier) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.Top,
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        content = content,
    )
}

@Composable
private fun SettingsSectionHeader(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun GroupDivider() {
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun StatusSettingRow(
    title: String,
    value: String,
    positive: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = value,
                color = if (positive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    summary: String? = null,
    running: Boolean = false,
    enabled: Boolean = true,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (summary == null) 64.dp else 76.dp)
                .clickable(enabled = enabled && !running, onClick = onClick)
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else contentColor,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = contentColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val summaryColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (summary == null) 60.dp else 80.dp)
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = summaryColor,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ValueSettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val summaryColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    val valueColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (summary == null) 68.dp else 84.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = valueColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = summaryColor,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RegionDialog(
    selected: RegionPreset,
    onSelect: (RegionPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val windowHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp()
    }
    val maxListHeight = (windowHeight - 220.dp)
        .coerceIn(96.dp, 320.dp)
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val locale = LocalLocale.current.platformLocale
    val regions = remember(normalizedQuery, locale) {
        RegionPreset.values().filter { preset ->
            normalizedQuery.isEmpty() ||
                preset.displayName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                preset.localizedDisplayName(locale).lowercase(Locale.ROOT).contains(normalizedQuery) ||
                preset.code.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                preset.operatorName.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(selected, normalizedQuery) {
        if (regions.isNotEmpty()) {
            val selectedIndex = regions.indexOf(selected)
            val targetIndex = if (normalizedQuery.isEmpty()) {
                selectedIndex.coerceAtLeast(0)
            } else {
                0
            }
            listState.scrollToItem(targetIndex)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.region_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.region_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = stringResource(R.string.clear_search),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.region_result_count,
                        regions.size,
                        regions.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (regions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.region_no_match),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxListHeight)
                            .selectableGroup(),
                    ) {
                        items(regions, key = { it.code }) { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = preset == selected,
                                        role = Role.RadioButton,
                                        onClick = { onSelect(preset) },
                                    )
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = preset == selected,
                                    onClick = null,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        preset.localizedDisplayName(locale),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "${preset.code} · ${preset.operatorName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun GpsCoordinatesDialog(
    initialLatitude: String,
    initialLongitude: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var latitude by rememberSaveable(initialLatitude) { mutableStateOf(initialLatitude) }
    var longitude by rememberSaveable(initialLongitude) { mutableStateOf(initialLongitude) }
    val parsedLatitude = remember(latitude) {
        SettingsInput.parseCoordinate(latitude, -90.0, 90.0)
    }
    val parsedLongitude = remember(longitude) {
        SettingsInput.parseCoordinate(longitude, -180.0, 180.0)
    }
    val valid = parsedLatitude != null && parsedLongitude != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gps_coordinates)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it.filter { char ->
                        char.isDigit() || char == '-' || char == '.'
                    } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.gps_latitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = parsedLatitude == null,
                    supportingText = {
                        if (parsedLatitude == null) {
                            Text(stringResource(R.string.gps_coordinate_invalid))
                        }
                    },
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it.filter { char ->
                        char.isDigit() || char == '-' || char == '.'
                    } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.gps_longitude)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = parsedLongitude == null,
                    supportingText = {
                        if (parsedLongitude == null) {
                            Text(stringResource(R.string.gps_coordinate_invalid))
                        }
                    },
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(latitude.trim(), longitude.trim())
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun MediaDirectoryDialog(
    title: String,
    initialPath: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by rememberSaveable(initialPath) { mutableStateOf(initialPath) }
    val validation = remember(path) { SettingsInput.normalizeMediaDirectory(path) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.media_directory_path)) },
                placeholder = { Text(stringResource(R.string.media_directory_example)) },
                supportingText = {
                    Text(
                        if (validation.error == null) {
                            stringResource(R.string.media_directory_hint)
                        } else {
                            stringResource(R.string.media_directory_invalid)
                        },
                    )
                },
                isError = validation.error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { validation.value?.let(onSave) },
                ),
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                enabled = validation.value != null,
                onClick = { validation.value?.let(onSave) },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DurationDialog(
    initialValue: Int,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue.toString()) }
    val parsed = remember(value) { SettingsInput.validateDuration(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.long_post_seconds)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.duration_seconds)) },
                suffix = { Text(stringResource(R.string.seconds_unit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = parsed == null,
                supportingText = {
                    if (parsed == null) Text(stringResource(R.string.positive_number_required))
                },
            )
        },
        confirmButton = {
            Button(enabled = parsed != null, onClick = { parsed?.let(onSave) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun PlaybackSpeedDialog(
    selected: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val normalizedSelected = PlaybackSpeed.sanitize(selected)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.default_playback_speed_dialog_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                PlaybackSpeed.supportedValues().forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = speed == normalizedSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(speed) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = speed == normalizedSelected,
                            onClick = null,
                        )
                        Text(
                            text = formatPlaybackSpeed(speed),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(
    @StringRes title: Int,
    initialMinimum: String,
    initialMaximum: String,
    onSave: (NumericRange) -> Unit,
    onDismiss: () -> Unit,
) {
    var minimum by rememberSaveable(initialMinimum) { mutableStateOf(initialMinimum) }
    var maximum by rememberSaveable(initialMaximum) { mutableStateOf(initialMaximum) }
    val validation = remember(minimum, maximum) {
        SettingsInput.validateRange(minimum, maximum)
    }
    val error = rangeErrorText(validation.error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minimum,
                    onValueChange = { minimum = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.minimum_value)) },
                    placeholder = { Text("20K") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    isError = validation.error == RangeInputError.INVALID_MINIMUM,
                )
                OutlinedTextField(
                    value = maximum,
                    onValueChange = { maximum = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.maximum_value_optional)) },
                    placeholder = { Text(stringResource(R.string.unlimited)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    isError = validation.error == RangeInputError.INVALID_MAXIMUM ||
                        validation.error == RangeInputError.INVALID_ORDER,
                )
                Text(
                    text = error ?: stringResource(R.string.range_rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = validation.value != null,
                onClick = { validation.value?.let(onSave) },
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun rangeErrorText(error: RangeInputError?): String? = when (error) {
    RangeInputError.INVALID_MINIMUM -> stringResource(R.string.minimum_invalid_error)
    RangeInputError.INVALID_MAXIMUM -> stringResource(R.string.maximum_invalid_error)
    RangeInputError.INVALID_ORDER -> stringResource(R.string.range_order_error)
    null -> null
}

@Composable
private fun formatRange(minimum: String, maximum: String): String {
    val upper = maximum.ifBlank { stringResource(R.string.unlimited) }
    return stringResource(R.string.range_value, minimum, upper)
}

private fun formatPlaybackSpeed(speed: Float): String {
    val normalized = PlaybackSpeed.sanitize(speed)
    return when (normalized) {
        1.0f -> "1.0x"
        1.25f -> "1.25x"
        1.5f -> "1.5x"
        1.75f -> "1.75x"
        2.0f -> "2.0x"
        else -> "1.0x"
    }
}
