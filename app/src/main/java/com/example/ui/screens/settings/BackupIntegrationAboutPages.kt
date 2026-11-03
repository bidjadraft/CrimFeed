package com.example.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.BackupSelection
import com.example.data.model.ImportCandidateFeed
import com.example.data.model.PendingImportPreview

@Composable
fun IntegrationSettingsPage(
    settings: AppSettings,
    onToggleTelegramEnabled: (Boolean) -> Unit,
    onSaveTelegramConfig: (tgToken: String, tgChannel: String) -> Unit,
    onTestTelegramBot: (String) -> Unit,
    onBack: () -> Unit
) {
    var tgTokenInput by remember(settings.telegramBotTokenOverride) {
        mutableStateOf(settings.telegramBotTokenOverride)
    }
    var tgChannelInput by remember(settings.telegramChannelIdOverride) {
        mutableStateOf(settings.telegramChannelIdOverride)
    }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }

    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_integration),
        onBack = onBack
    ) {
        SettingsSectionCard(
            title = stringResource(R.string.settings_telegram),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_telegram),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (settings.telegramEnabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onToggleTelegramEnabled(!settings.telegramEnabled) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_telegram_enable),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_telegram_enable_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = settings.telegramEnabled,
                        onCheckedChange = onToggleTelegramEnabled,
                        modifier = Modifier.testTag("switch_enable_telegram")
                    )
                }
            }

            AnimatedVisibility(visible = settings.telegramEnabled) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = tgTokenInput,
                        onValueChange = { tgTokenInput = it },
                        label = { Text(stringResource(R.string.settings_bot_token)) },
                        singleLine = true,
                        visualTransformation = if (tokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    imageVector = if (tokenVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (tokenVisible) {
                                        stringResource(R.string.action_hide_key)
                                    } else {
                                        stringResource(R.string.action_show_key)
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_telegram_token")
                    )

                    OutlinedTextField(
                        value = tgChannelInput,
                        onValueChange = { tgChannelInput = it },
                        label = { Text(stringResource(R.string.settings_channel_id)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_telegram_channel")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onTestTelegramBot(tgTokenInput) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_verify_telegram_bot")
                        ) {
                            Text(stringResource(R.string.settings_test_bot))
                        }

                        Button(
                            onClick = { onSaveTelegramConfig(tgTokenInput, tgChannelInput) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_save_telegram_config")
                        ) {
                            Text(stringResource(R.string.btn_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupRestoreSettingsPage(
    backupSelection: BackupSelection,
    isImportingBackup: Boolean,
    hasPendingImportBackup: Boolean = false,
    pendingImportPreview: PendingImportPreview? = null,
    onUpdateBackupSelection: (BackupSelection) -> Unit,
    onExportBackupClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    onConfirmPendingImport: (BackupSelection) -> Unit = {},
    onConfirmPendingImportWithFeeds: ((BackupSelection, Set<String>?) -> Unit)? = null,
    onCancelPendingImport: () -> Unit = {},
    onExportOpmlClick: () -> Unit,
    onBack: () -> Unit
) {
    var showExportModal by remember { mutableStateOf(false) }
    var exportSelection by remember {
        mutableStateOf(
            BackupSelection(
                includeFeeds = true,
                includeLists = true,
                includeSaved = false,
                includeRead = false
            )
        )
    }

    val effectiveImportPreview = remember(pendingImportPreview, hasPendingImportBackup) {
        pendingImportPreview ?: if (hasPendingImportBackup) {
            PendingImportPreview(
                rawContent = "",
                isAppFormatBackup = true
            )
        } else {
            null
        }
    }

    fun openExportModal() {
        exportSelection = BackupSelection(
            includeFeeds = true,
            includeLists = true,
            includeSaved = false,
            includeRead = false
        )
        showExportModal = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsSubPageScaffold(
            title = stringResource(R.string.settings_backup_restore),
            onBack = onBack
        ) {
            SettingsSectionCard(
                title = stringResource(R.string.settings_backup_restore),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.ImportExport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportBackupClick,
                        enabled = !isImportingBackup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_import_backup")
                    ) {
                        if (isImportingBackup) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.FileOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import_backup))
                    }

                    Button(
                        onClick = ::openExportModal,
                        enabled = !isImportingBackup,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_export_backup")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_export_backup))
                    }
                }
            }
        }

        if (effectiveImportPreview != null) {
            SmartImportSelectionDialog(
                preview = effectiveImportPreview,
                onDismiss = onCancelPendingImport,
                onConfirm = { chosenSelection, chosenFeedUrls ->
                    onUpdateBackupSelection(chosenSelection)
                    if (onConfirmPendingImportWithFeeds != null) {
                        onConfirmPendingImportWithFeeds(chosenSelection, chosenFeedUrls)
                    } else {
                        onConfirmPendingImport(chosenSelection)
                    }
                }
            )
        } else if (showExportModal) {
            BackHandler(onBack = { showExportModal = false })
            val scrimInteraction = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(50f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.56f))
                        .clickable(
                            interactionSource = scrimInteraction,
                            indication = null,
                            onClick = { showExportModal = false }
                        )
                )
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .pointerInput(Unit) { detectTapGestures {} }
                        .testTag("dialog_backup_options")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_export_backup_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.backup_restore_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BackupCheckboxRow(
                                label = stringResource(R.string.backup_option_feeds),
                                icon = Icons.Outlined.RssFeed,
                                checked = exportSelection.includeFeeds,
                                rowTag = "row_backup_feeds",
                                checkboxTag = "checkbox_backup_feeds",
                                onCheckedChange = {
                                    exportSelection = exportSelection.copy(includeFeeds = it)
                                }
                            )
                            BackupCheckboxRow(
                                label = stringResource(R.string.backup_option_lists),
                                icon = Icons.Outlined.Folder,
                                checked = exportSelection.includeLists,
                                rowTag = "row_backup_lists",
                                checkboxTag = "checkbox_backup_lists",
                                onCheckedChange = {
                                    exportSelection = exportSelection.copy(includeLists = it)
                                }
                            )
                            BackupCheckboxRow(
                                label = stringResource(R.string.backup_option_read),
                                icon = Icons.Outlined.History,
                                checked = exportSelection.includeRead,
                                rowTag = "row_backup_read",
                                checkboxTag = "checkbox_backup_read",
                                onCheckedChange = {
                                    exportSelection = exportSelection.copy(includeRead = it)
                                }
                            )
                            BackupCheckboxRow(
                                label = stringResource(R.string.backup_option_saved),
                                icon = Icons.Outlined.BookmarkBorder,
                                checked = exportSelection.includeSaved,
                                rowTag = "row_backup_saved",
                                checkboxTag = "checkbox_backup_saved",
                                onCheckedChange = {
                                    exportSelection = exportSelection.copy(includeSaved = it)
                                }
                            )
                            BackupCheckboxRow(
                                label = stringResource(R.string.backup_option_filters),
                                icon = Icons.Outlined.FilterAlt,
                                checked = exportSelection.includeFilters,
                                rowTag = "row_backup_filters",
                                checkboxTag = "checkbox_backup_filters",
                                onCheckedChange = {
                                    exportSelection = exportSelection.copy(includeFilters = it)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showExportModal = false },
                                modifier = Modifier.testTag("btn_cancel_backup_dialog")
                            ) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onUpdateBackupSelection(exportSelection)
                                    showExportModal = false
                                    onExportBackupClick()
                                },
                                enabled = exportSelection.hasAnySelection,
                                modifier = Modifier.testTag("btn_confirm_backup_dialog")
                            ) {
                                Text(stringResource(R.string.action_export_backup))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmartImportSelectionDialog(
    preview: PendingImportPreview,
    onDismiss: () -> Unit,
    onConfirm: (selection: BackupSelection, selectedFeedUrls: Set<String>?) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val scrimInteraction = remember { MutableInteractionSource() }

    var dialogSelection by remember(preview) {
        mutableStateOf(
            BackupSelection(
                includeFeeds = true,
                includeLists = true,
                includeSaved = false,
                includeRead = false
            )
        )
    }
    var selectedFeedUrls by remember(preview) {
        mutableStateOf(preview.feeds.map { it.normalizedUrl }.toSet())
    }

    val allFeedUrls = remember(preview) {
        preview.feeds.map { it.normalizedUrl }.toSet()
    }
    val allFeedsSelected = preview.feeds.isNotEmpty() && selectedFeedUrls.size == allFeedUrls.size

    val canConfirm = if (preview.isAppFormatBackup) {
        val feedsSatisfied = dialogSelection.includeFeeds &&
            (preview.feeds.isEmpty() || selectedFeedUrls.isNotEmpty())
        feedsSatisfied ||
            dialogSelection.includeLists ||
            dialogSelection.includeSaved ||
            dialogSelection.includeRead
    } else {
        selectedFeedUrls.isNotEmpty() || (preview.feeds.isEmpty() && preview.listsCount > 0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(65f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss
                )
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures {} }
                .testTag("dialog_backup_options")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(
                        if (preview.isAppFormatBackup) R.string.dialog_import_backup_title
                        else R.string.dialog_import_opml_title
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(
                        if (preview.isAppFormatBackup) R.string.backup_restore_description
                        else R.string.import_opml_select_feeds_subtitle
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (preview.isAppFormatBackup) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BackupCheckboxRow(
                            label = stringResource(R.string.backup_option_feeds),
                            icon = Icons.Outlined.RssFeed,
                            checked = dialogSelection.includeFeeds,
                            rowTag = "row_backup_feeds",
                            checkboxTag = "checkbox_backup_feeds",
                            onCheckedChange = {
                                dialogSelection = dialogSelection.copy(includeFeeds = it)
                            }
                        )
                        BackupCheckboxRow(
                            label = stringResource(R.string.backup_option_lists),
                            icon = Icons.Outlined.Folder,
                            checked = dialogSelection.includeLists,
                            rowTag = "row_backup_lists",
                            checkboxTag = "checkbox_backup_lists",
                            onCheckedChange = {
                                dialogSelection = dialogSelection.copy(includeLists = it)
                            }
                        )
                        BackupCheckboxRow(
                            label = stringResource(R.string.backup_option_read),
                            icon = Icons.Outlined.History,
                            checked = dialogSelection.includeRead,
                            rowTag = "row_backup_read",
                            checkboxTag = "checkbox_backup_read",
                            onCheckedChange = {
                                dialogSelection = dialogSelection.copy(includeRead = it)
                            }
                        )
                        BackupCheckboxRow(
                            label = stringResource(R.string.backup_option_saved),
                            icon = Icons.Outlined.BookmarkBorder,
                            checked = dialogSelection.includeSaved,
                            rowTag = "row_backup_saved",
                            checkboxTag = "checkbox_backup_saved",
                            onCheckedChange = {
                                dialogSelection = dialogSelection.copy(includeSaved = it)
                            }
                        )
                        BackupCheckboxRow(
                            label = stringResource(R.string.backup_option_filters) + if (preview.filtersCount > 0) " (${preview.filtersCount})" else "",
                            icon = Icons.Outlined.FilterAlt,
                            checked = dialogSelection.includeFilters,
                            rowTag = "row_backup_filters",
                            checkboxTag = "checkbox_backup_filters",
                            onCheckedChange = {
                                dialogSelection = dialogSelection.copy(includeFilters = it)
                            }
                        )
                    }
                }

                val showFeedsList = preview.feeds.isNotEmpty() &&
                    (!preview.isAppFormatBackup || dialogSelection.includeFeeds)

                if (showFeedsList) {
                    if (preview.isAppFormatBackup) {
                        HorizontalDivider()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                R.string.import_feeds_available_header,
                                selectedFeedUrls.size,
                                preview.feeds.size
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = {
                                selectedFeedUrls = if (allFeedsSelected) emptySet() else allFeedUrls
                            },
                            modifier = Modifier.testTag("btn_import_toggle_all_feeds")
                        ) {
                            Text(
                                text = stringResource(
                                    if (allFeedsSelected) R.string.import_deselect_all
                                    else R.string.import_select_all
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (preview.isAppFormatBackup) 200.dp else 300.dp)
                            .testTag("list_import_candidate_feeds"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            items = preview.feeds,
                            key = { _, item -> item.normalizedUrl }
                        ) { index, feed ->
                            val isChecked = feed.normalizedUrl in selectedFeedUrls
                            ImportFeedChecklistRow(
                                feed = feed,
                                checked = isChecked,
                                index = index,
                                onCheckedChange = { next ->
                                    selectedFeedUrls = if (next) {
                                        selectedFeedUrls + feed.normalizedUrl
                                    } else {
                                        selectedFeedUrls - feed.normalizedUrl
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_cancel_backup_dialog")
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (preview.isAppFormatBackup) {
                                val effectiveSelection = if (preview.feeds.isNotEmpty() && selectedFeedUrls.isEmpty()) {
                                    dialogSelection.copy(includeFeeds = false)
                                } else {
                                    dialogSelection
                                }
                                val allowed = if (preview.feeds.isNotEmpty()) selectedFeedUrls else null
                                onConfirm(effectiveSelection, allowed)
                            } else {
                                val opmlSelection = BackupSelection(
                                    includeFeeds = selectedFeedUrls.isNotEmpty(),
                                    includeLists = true,
                                    includeSaved = false,
                                    includeRead = false
                                )
                                val allowed = if (preview.feeds.isNotEmpty()) selectedFeedUrls else null
                                onConfirm(opmlSelection, allowed)
                            }
                        },
                        enabled = canConfirm,
                        modifier = Modifier.testTag("btn_confirm_backup_dialog")
                    ) {
                        Text(stringResource(R.string.action_import_backup))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportFeedChecklistRow(
    feed: ImportCandidateFeed,
    checked: Boolean,
    index: Int,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .testTag("row_import_feed_$index")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.RssFeed,
                contentDescription = null,
                tint = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feed.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = if (feed.groupName.isNotBlank()) {
                    "${feed.groupName} • ${feed.url}"
                } else {
                    feed.url
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag("checkbox_import_feed_$index")
            )
        }
    }
}

@Composable
private fun BackupCheckboxRow(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    rowTag: String,
    checkboxTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .testTag(rowTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(checkboxTag)
            )
        }
    }
}

@Composable
fun AboutSettingsPage(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    fun openExternalLink(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    SettingsSubPageScaffold(
        title = stringResource(R.string.settings_about),
        onBack = onBack
    ) {
        // Brand Identity Card
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.RssFeed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Social & Creator Links Card
        SettingsSectionCard(
            title = stringResource(R.string.about_links_header),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            AboutExternalLinkRow(
                label = stringResource(R.string.about_youtube_label),
                handle = stringResource(R.string.about_youtube_handle),
                iconSlot = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_youtube),
                        contentDescription = null,
                        tint = Color(0xFFFF0033),
                        modifier = Modifier.size(22.dp)
                    )
                },
                testTag = "about_link_youtube",
                onClick = { openExternalLink("https://www.youtube.com/@bidjadraft") }
            )

            AboutExternalLinkRow(
                label = stringResource(R.string.about_telegram_label),
                handle = stringResource(R.string.about_telegram_handle),
                iconSlot = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_telegram),
                        contentDescription = null,
                        tint = Color(0xFF2AABEE),
                        modifier = Modifier.size(22.dp)
                    )
                },
                testTag = "about_link_telegram",
                onClick = { openExternalLink("https://t.me/Bidjadraft") }
            )

            AboutExternalLinkRow(
                label = stringResource(R.string.about_website_label),
                handle = stringResource(R.string.about_website_handle),
                iconSlot = {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                },
                testTag = "about_link_website",
                onClick = { openExternalLink("https://bidjadraft.github.io") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("about_version_text")
        )
    }
}

@Composable
private fun AboutExternalLinkRow(
    label: String,
    handle: String,
    iconSlot: @Composable () -> Unit,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            iconSlot()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = handle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
