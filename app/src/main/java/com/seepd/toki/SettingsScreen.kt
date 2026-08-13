package com.seepd.toki

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

private enum class SettingsDialog {
    NONE,
    REGION,
    MEDIA_DIRECTORY_ERROR,
    DURATION,
    PLAYBACK_SPEED,
    VIEW_RANGE,
    LIKE_RANGE,
    RESTART,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsApp(
    state: SettingsUiState,
    onUpdate: ((SettingsUiState) -> SettingsUiState) -> Unit,
    restartStatus: RootRestartStatus,
    onRestartTikTok: () -> Unit,
    onDismissRestart: () -> Unit,
) {
    var dialogName by rememberSaveable { mutableStateOf(SettingsDialog.NONE.name) }
    var menuExpanded by remember { mutableStateOf(false) }
    val activeDialog = SettingsDialog.valueOf(dialogName)
    val closeDialog = { dialogName = SettingsDialog.NONE.name }
    val videoDirectoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        updateMediaDirectory(uri, onUpdate, { state, path -> state.copy(videoLocation = path) }) {
            dialogName = SettingsDialog.MEDIA_DIRECTORY_ERROR.name
        }
    }
    val pictureDirectoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        updateMediaDirectory(uri, onUpdate, { state, path -> state.copy(picLocation = path) }) {
            dialogName = SettingsDialog.MEDIA_DIRECTORY_ERROR.name
        }
    }
    val gifDirectoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        updateMediaDirectory(uri, onUpdate, { state, path -> state.copy(gifLocation = path) }) {
            dialogName = SettingsDialog.MEDIA_DIRECTORY_ERROR.name
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.restart_tiktok)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDismissRestart()
                                    dialogName = SettingsDialog.RESTART.name
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        SettingsContent(
            state = state,
            onUpdate = onUpdate,
            onDialog = { dialogName = it.name },
            onPickVideoDirectory = {
                videoDirectoryPicker.launch(initialMediaDirectoryUri(state.videoLocation))
            },
            onPickPictureDirectory = {
                pictureDirectoryPicker.launch(initialMediaDirectoryUri(state.picLocation))
            },
            onPickGifDirectory = {
                gifDirectoryPicker.launch(initialMediaDirectoryUri(state.gifLocation))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        )
    }

    when (activeDialog) {
        SettingsDialog.NONE -> Unit
        SettingsDialog.REGION -> RegionDialog(
            selected = state.region,
            onSelect = {
                onUpdate { current -> current.copy(region = it) }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.MEDIA_DIRECTORY_ERROR -> MediaDirectoryErrorDialog(
            onDismiss = closeDialog,
        )
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
            initialMinimum = state.viewsMin,
            initialMaximum = state.viewsMax,
            onSave = { range ->
                onUpdate { current ->
                    current.copy(viewsMin = range.minimum, viewsMax = range.maximum)
                }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.LIKE_RANGE -> RangeDialog(
            title = R.string.likes_range,
            initialMinimum = state.likesMin,
            initialMaximum = state.likesMax,
            onSave = { range ->
                onUpdate { current ->
                    current.copy(likesMin = range.minimum, likesMax = range.maximum)
                }
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SettingsDialog.RESTART -> RestartDialog(
            status = restartStatus,
            onConfirm = onRestartTikTok,
            onDismiss = {
                closeDialog()
                onDismissRestart()
            },
        )
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onUpdate: ((SettingsUiState) -> SettingsUiState) -> Unit,
    onDialog: (SettingsDialog) -> Unit,
    onPickVideoDirectory: () -> Unit,
    onPickPictureDirectory: () -> Unit,
    onPickGifDirectory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsList(modifier) {
        item(key = "common_group") {
            SettingsGroup {
                ValueSettingRow(
                    title = stringResource(R.string.region),
                    value = "${state.region.displayName} (${state.region.code})",
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
                    title = stringResource(R.string.force_region),
                    summary = stringResource(R.string.force_region_summary),
                    checked = state.forceRegion,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(forceRegion = checked) }
                    },
                )
                GroupDivider()
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
                ValueSettingRow(
                    title = stringResource(R.string.default_playback_speed),
                    value = formatPlaybackSpeed(state.defaultPlaybackSpeed),
                    onClick = { onDialog(SettingsDialog.PLAYBACK_SPEED) },
                )
                GroupDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.anti_burn_in),
                    summary = stringResource(R.string.anti_burn_in_summary),
                    checked = state.antiBurnIn,
                    onCheckedChange = { checked ->
                        onUpdate { it.copy(antiBurnIn = checked) }
                    },
                )
            }
        }

        item(key = "download_creation_group") {
            SettingsGroup {
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
                GroupDivider()
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

        item(key = "filters_group") {
            SettingsGroup {
                GroupDivider()
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
                    value = stringResource(R.string.seconds_value, state.longPostSeconds),
                    enabled = state.hideLongPosts,
                    onClick = { onDialog(SettingsDialog.DURATION) },
                )
                GroupDivider()
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
                    value = formatRange(state.viewsMin, state.viewsMax),
                    enabled = state.filterViewsLikes,
                    onClick = { onDialog(SettingsDialog.VIEW_RANGE) },
                )
                GroupDivider()
                ValueSettingRow(
                    title = stringResource(R.string.likes_range),
                    value = formatRange(state.likesMin, state.likesMax),
                    enabled = state.filterViewsLikes,
                    onClick = { onDialog(SettingsDialog.LIKE_RANGE) },
                )
            }
        }
    }
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
                top = 4.dp,
                bottom = 24.dp,
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
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (summary == null) 56.dp else 72.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (summary == null) 64.dp else 80.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
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
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(20.dp),
        )
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
    val regions = remember(normalizedQuery) {
        RegionPreset.values().filter { preset ->
            normalizedQuery.isEmpty() ||
                preset.displayName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
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
                    text = stringResource(R.string.region_result_count, regions.size),
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
                                        preset.displayName,
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
private fun MediaDirectoryErrorDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_directory_error_title)) },
        text = { Text(stringResource(R.string.media_directory_error_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

private fun initialMediaDirectoryUri(relativePath: String): Uri? {
    val normalizedPath = SettingsInput.normalizeMediaDirectory(relativePath).value ?: return null
    return DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_AUTHORITY,
        "primary:$normalizedPath",
    )
}

private fun updateMediaDirectory(
    uri: Uri?,
    onUpdate: ((SettingsUiState) -> SettingsUiState) -> Unit,
    transform: (SettingsUiState, String) -> SettingsUiState,
    onInvalid: () -> Unit,
) {
    if (uri == null) {
        return
    }

    val documentId = if (uri.authority == EXTERNAL_STORAGE_AUTHORITY) {
        runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    } else {
        null
    }
    val relativePath = documentId?.let(SettingsInput::mediaDirectoryFromDocumentId)
    if (relativePath == null) {
        onInvalid()
        return
    }

    onUpdate { current -> transform(current, relativePath) }
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
                            text = playbackSpeedOptionLabel(speed),
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

@Composable
private fun RangeDialog(
    @StringRes title: Int,
    initialMinimum: Long,
    initialMaximum: Long?,
    onSave: (NumericRange) -> Unit,
    onDismiss: () -> Unit,
) {
    var minimum by rememberSaveable(initialMinimum) { mutableStateOf(initialMinimum.toString()) }
    var maximum by rememberSaveable(initialMaximum) {
        mutableStateOf(initialMaximum?.toString().orEmpty())
    }
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
                    onValueChange = { minimum = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.minimum_value)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = validation.error == RangeInputError.INVALID_MINIMUM,
                )
                OutlinedTextField(
                    value = maximum,
                    onValueChange = { maximum = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.maximum_value_optional)) },
                    placeholder = { Text(stringResource(R.string.unlimited)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = validation.error == RangeInputError.INVALID_MAXIMUM ||
                        validation.error == RangeInputError.INVALID_ORDER,
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.range_rule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
private fun RestartDialog(
    status: RootRestartStatus,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val running = status == RootRestartStatus.RUNNING
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = {
            Text(
                when (status) {
                    RootRestartStatus.IDLE -> stringResource(R.string.restart_tiktok_title)
                    RootRestartStatus.RUNNING -> stringResource(R.string.restart_tiktok_running)
                    RootRestartStatus.SUCCESS -> stringResource(R.string.restart_tiktok_success_title)
                    RootRestartStatus.NO_ROOT -> stringResource(R.string.restart_tiktok_no_root_title)
                    RootRestartStatus.FAILED -> stringResource(R.string.restart_tiktok_failed_title)
                    RootRestartStatus.TIMEOUT -> stringResource(R.string.restart_tiktok_timeout_title)
                },
            )
        },
        text = {
            when (status) {
                RootRestartStatus.IDLE -> Text(stringResource(R.string.restart_tiktok_message))
                RootRestartStatus.RUNNING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.restart_tiktok_running_message))
                }
                RootRestartStatus.SUCCESS -> Text(stringResource(R.string.restart_tiktok_success))
                RootRestartStatus.NO_ROOT -> Text(stringResource(R.string.restart_tiktok_no_root))
                RootRestartStatus.FAILED -> Text(stringResource(R.string.restart_tiktok_failed))
                RootRestartStatus.TIMEOUT -> Text(stringResource(R.string.restart_tiktok_timeout))
            }
        },
        confirmButton = {
            when (status) {
                RootRestartStatus.IDLE -> Button(onClick = onConfirm) {
                    Text(stringResource(R.string.restart_tiktok_confirm))
                }
                RootRestartStatus.RUNNING -> Unit
                RootRestartStatus.SUCCESS,
                RootRestartStatus.NO_ROOT,
                RootRestartStatus.FAILED,
                RootRestartStatus.TIMEOUT -> TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        },
        dismissButton = {
            if (status == RootRestartStatus.IDLE) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
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
private fun formatRange(minimum: Long, maximum: Long?): String {
    val formatter = remember { NumberFormat.getIntegerInstance() }
    val upper = maximum?.let(formatter::format) ?: stringResource(R.string.unlimited)
    return stringResource(R.string.range_value, formatter.format(minimum), upper)
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

private fun playbackSpeedOptionLabel(speed: Float): String =
    if (PlaybackSpeed.sanitize(speed) == PlaybackSpeed.DEFAULT) {
        "关闭（1.0x）"
    } else {
        formatPlaybackSpeed(speed)
    }
