package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.example.data.local.FeedFilterEntity
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.model.FeedWithUnreadCount
import com.example.data.model.ReaderAiMode

@Composable
fun AddFeedDialog(
    customLists: List<CustomListEntity>,
    isAdding: Boolean,
    canSummarize: Boolean = false,
    onDismiss: () -> Unit,
    onAddFeed: (
        url: String,
        listName: String,
        autoTranslate: Boolean,
        defaultReaderMode: ReaderAiMode,
        notifyEnabled: Boolean
    ) -> Unit,
    onCreateCustomList: (
        name: String,
        showInAllLists: Boolean,
        syncEnabled: Boolean,
        onCreated: (String) -> Unit
    ) -> Unit
) {
    var feedUrl by remember { mutableStateOf("") }
    var selectedListName by remember { mutableStateOf("") }
    var autoTranslate by remember { mutableStateOf(false) }
    var notifyEnabled by remember { mutableStateOf(false) }
    var selectedReaderMode by remember { mutableStateOf(ReaderAiMode.NONE) }
    var showInlineNewListDialog by remember { mutableStateOf(false) }

    ModalScrimCard(
        onDismissRequest = { if (!isAdding) onDismiss() },
        zIndex = 50f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_add_feed_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .focusProperties { canFocus = true }
                    .focusTarget()
            )

            DialogUrlInputFieldWithNotification(
                value = feedUrl,
                onValueChange = { feedUrl = it },
                placeholder = stringResource(R.string.hint_feed_url),
                enabled = !isAdding,
                notifyEnabled = notifyEnabled,
                onToggleNotify = { notifyEnabled = !notifyEnabled },
                testTag = "input_feed_url"
            )

            CustomListSelectorSection(
                customLists = customLists,
                selectedListName = selectedListName,
                enabled = !isAdding,
                showTitle = false,
                onSelectList = { selectedListName = it },
                onNewListClick = { showInlineNewListDialog = true }
            )

            AutoTranslateSwitchCard(
                checked = autoTranslate,
                enabled = !isAdding,
                onCheckedChange = { autoTranslate = it },
                testTag = "switch_add_feed_auto_translate"
            )

            ArticleAiModeSelectorSection(
                selectedMode = selectedReaderMode,
                enabled = !isAdding,
                canSummarize = canSummarize,
                onSelectMode = { selectedReaderMode = it },
                tagPrefix = "chip_add_feed_mode_"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, enabled = !isAdding) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onAddFeed(
                            feedUrl.trim(),
                            selectedListName.trim(),
                            autoTranslate,
                            selectedReaderMode,
                            notifyEnabled
                        )
                    },
                    enabled = feedUrl.isNotBlank() && !isAdding,
                    modifier = Modifier.testTag("confirm_add_feed_button")
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.btn_add))
                }
            }
        }
    }

    if (showInlineNewListDialog) {
        CustomListDialog(
            initialName = "",
            isRename = false,
            onDismiss = { showInlineNewListDialog = false },
            onConfirm = { createdName, listShowInAll, listSyncEnabled ->
                onCreateCustomList(createdName, listShowInAll, listSyncEnabled) { clean ->
                    selectedListName = clean
                }
                showInlineNewListDialog = false
            }
        )
    }
}

@Composable
private fun DialogUrlInputFieldWithNotification(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    notifyEnabled: Boolean,
    onToggleNotify: () -> Unit,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (isFocused) 1.5.dp else 1.dp,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleNotify,
                enabled = enabled,
                modifier = Modifier
                    .size(38.dp)
                    .testTag("btn_add_feed_toggle_notify")
            ) {
                Icon(
                    imageVector = if (notifyEnabled) Icons.Filled.Notifications else Icons.Outlined.NotificationsOff,
                    contentDescription = stringResource(if (notifyEnabled) R.string.feed_action_disable_notify else R.string.feed_action_enable_notify),
                    tint = if (notifyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
                    .testTag(testTag)
            )
        }
    }
}

private sealed interface ListChipOption {
    data object None : ListChipOption
    data class Named(val name: String) : ListChipOption
    data object CreateNew : ListChipOption
}

@Composable
private fun CustomListSelectorSection(
    customLists: List<CustomListEntity>,
    selectedListName: String,
    enabled: Boolean,
    showTitle: Boolean = true,
    onSelectList: (String) -> Unit,
    onNewListClick: () -> Unit
) {
    val chipItems = remember(customLists, selectedListName) {
        buildList {
            add(ListChipOption.None)
            customLists.forEach { add(ListChipOption.Named(it.name)) }
            if (selectedListName.isNotBlank() &&
                customLists.none { it.name.equals(selectedListName, ignoreCase = true) }
            ) {
                add(ListChipOption.Named(selectedListName))
            }
            add(ListChipOption.CreateNew)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.label_custom_list_optional),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("row_custom_lists_horizontal")
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            chipItems.forEach { item ->
                when (item) {
                    ListChipOption.None -> {
                        val isNone = selectedListName.isBlank()
                        FilterChip(
                            selected = isNone,
                            onClick = { onSelectList("") },
                            enabled = enabled,
                            label = {
                                Text(
                                    text = stringResource(R.string.list_none),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            leadingIcon = if (isNone) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("chip_select_list_none")
                        )
                    }

                    is ListChipOption.Named -> {
                        val listName = item.name
                        val isSelected = selectedListName.equals(listName, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectList(if (isSelected) "" else listName) },
                            enabled = enabled,
                            label = {
                                Text(
                                    text = listName,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("chip_select_list_$listName")
                        )
                    }

                    ListChipOption.CreateNew -> {
                        AssistChip(
                            onClick = onNewListClick,
                            enabled = enabled,
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.action_new_list).removePrefix("+ "),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.testTag("chip_create_custom_list_inline")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleAiModeSelectorSection(
    selectedMode: ReaderAiMode,
    enabled: Boolean = true,
    canSummarize: Boolean = false,
    onSelectMode: (ReaderAiMode) -> Unit,
    tagPrefix: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.feed_article_mode_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReaderAiMode.entries.forEach { mode ->
                val isSelected = selectedMode == mode
                val isChipEnabled = enabled && (mode != ReaderAiMode.SUMMARIZE || canSummarize)
                val labelText = when (mode) {
                    ReaderAiMode.NONE -> stringResource(R.string.feed_mode_none)
                    ReaderAiMode.TRANSLATE -> stringResource(R.string.feed_mode_translate)
                    ReaderAiMode.SUMMARIZE -> stringResource(R.string.feed_mode_summarize)
                }
                val iconVec = when (mode) {
                    ReaderAiMode.NONE -> Icons.Outlined.RssFeed
                    ReaderAiMode.TRANSLATE -> Icons.Outlined.Translate
                    ReaderAiMode.SUMMARIZE -> Icons.Outlined.AutoAwesome
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectMode(mode) },
                    enabled = isChipEnabled,
                    label = {
                        Text(
                            text = labelText,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.Check else iconVec,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("$tagPrefix${mode.name}")
                )
            }
        }
    }
}

@Composable
private fun AutoTranslateSwitchCard(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.feed_auto_translate_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.feed_auto_translate_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            M3PillSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun ShowInAllSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (checked) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            M3PillSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun SyncModeSelectorSection(
    syncEnabled: Boolean,
    enabled: Boolean = true,
    onSyncModeChange: (Boolean) -> Unit,
    autoChipTag: String,
    manualChipTag: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.sync_mode_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = syncEnabled,
                onClick = { onSyncModeChange(true) },
                enabled = enabled,
                label = {
                    Text(
                        text = stringResource(R.string.sync_mode_automatic),
                        fontWeight = if (syncEnabled) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (syncEnabled) Icons.Filled.Check else Icons.Outlined.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag(autoChipTag)
            )
            FilterChip(
                selected = !syncEnabled,
                onClick = { onSyncModeChange(false) },
                enabled = enabled,
                label = {
                    Text(
                        text = stringResource(R.string.sync_mode_manual),
                        fontWeight = if (!syncEnabled) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (!syncEnabled) Icons.Filled.Check else Icons.Outlined.SyncDisabled,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag(manualChipTag)
            )
        }
    }
}

@Composable
private fun M3PillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val trackColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val borderColor = if (checked) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val thumbColor = if (checked) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = CircleShape,
        color = trackColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Surface(
                shape = CircleShape,
                color = thumbColor,
                modifier = Modifier.size(if (checked) 22.dp else 16.dp)
            ) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedLongPressActionSheet(
    feed: FeedEntity,
    onDismiss: () -> Unit,
    onEditRequested: () -> Unit,
    onRefreshFeed: () -> Unit,
    onCopyUrl: () -> Unit = {},
    onDelete: () -> Unit,
    onTogglePinned: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeedAvatarIcon(iconUrl = feed.iconUrl, feedTitle = feed.title, size = 32.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = feed.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = feed.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            SheetActionRow(
                icon = Icons.Outlined.Edit,
                title = stringResource(R.string.feed_action_rename),
                tag = "sheet_edit_feed",
                onClick = {
                    onDismiss()
                    onEditRequested()
                }
            )
            SheetActionRow(
                icon = Icons.Outlined.Refresh,
                title = stringResource(R.string.feed_action_refresh_single),
                tag = "sheet_refresh_single_feed",
                onClick = {
                    onDismiss()
                    onRefreshFeed()
                }
            )
            SheetActionRow(
                icon = Icons.Outlined.ContentCopy,
                title = stringResource(R.string.menu_copy_feed),
                tag = "sheet_copy_feed_url",
                onClick = {
                    onDismiss()
                    onCopyUrl()
                }
            )
            SheetActionRow(
                icon = Icons.Outlined.DeleteOutline,
                title = stringResource(R.string.feed_action_delete),
                tag = "sheet_delete_feed",
                isDestructive = true,
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomListActionSheet(
    customList: CustomListEntity,
    onDismiss: () -> Unit,
    onRenameRequested: () -> Unit,
    onRefreshList: (() -> Unit)? = null,
    onDeleteRequested: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = customList.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SheetActionRow(
                icon = Icons.Outlined.Edit,
                title = stringResource(R.string.list_action_rename),
                tag = "sheet_rename_custom_list",
                onClick = {
                    onDismiss()
                    onRenameRequested()
                }
            )
            if (onRefreshList != null) {
                SheetActionRow(
                    icon = Icons.Outlined.Refresh,
                    title = stringResource(R.string.list_action_refresh_manual),
                    tag = "sheet_refresh_custom_list",
                    onClick = {
                        onDismiss()
                        onRefreshList()
                    }
                )
            }
            SheetActionRow(
                icon = Icons.Outlined.DeleteOutline,
                title = stringResource(R.string.list_action_delete),
                tag = "sheet_delete_custom_list",
                isDestructive = true,
                onClick = {
                    onDismiss()
                    onDeleteRequested()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllListsActionSheet(
    customLists: List<CustomListEntity>,
    feedCounts: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit,
    onEditList: (CustomListEntity) -> Unit,
    onTogglePin: (CustomListEntity) -> Unit = {},
    onToggleDisable: (CustomListEntity) -> Unit = {},
    onDeleteList: (CustomListEntity) -> Unit,
    onReorderLists: (List<Long>) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val workingLists = remember(customLists) { mutableStateListOf<CustomListEntity>().apply { addAll(customLists) } }
    var draggedListId by remember { mutableStateOf<Long?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var measuredRowHeightPx by remember { mutableFloatStateOf(0f) }
    val defaultRowHeightPx = with(density) { 56.dp.toPx() }

    LaunchedEffect(customLists, draggedListId) {
        if (draggedListId == null) {
            workingLists.clear()
            workingLists.addAll(customLists)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .testTag("sheet_all_lists_container")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.filter_all_lists),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${workingLists.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                workingLists.forEachIndexed { index, list ->
                    key(list.id) {
                        val count = feedCounts[list.name.trim().lowercase(java.util.Locale.ROOT)] ?: 0
                        val isBeingDragged = draggedListId == list.id
                        val isListDisabled = !list.syncEnabled

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBeingDragged) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else if (isListDisabled) {
                                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            tonalElevation = if (isBeingDragged) 8.dp else 0.dp,
                            shadowElevation = if (isBeingDragged) 6.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isBeingDragged) 10f else 0f)
                                .onGloballyPositioned { coordinates ->
                                    if (measuredRowHeightPx == 0f && coordinates.size.height > 0) {
                                        val spacingPx = with(density) { 6.dp.toPx() }
                                        measuredRowHeightPx = coordinates.size.height.toFloat() + spacingPx
                                    }
                                }
                                .graphicsLayer {
                                    translationY = if (isBeingDragged) draggedOffsetY else 0f
                                    scaleX = if (isBeingDragged) 1.025f else 1f
                                    scaleY = if (isBeingDragged) 1.025f else 1f
                                }
                                .pointerInput(list.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedListId = list.id
                                            draggedOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            val hasChanged = workingLists.map { it.id } != customLists.map { it.id }
                                            if (hasChanged) {
                                                onReorderLists(workingLists.map { it.id })
                                            }
                                            draggedListId = null
                                            draggedOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedListId = null
                                            draggedOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            draggedOffsetY += dragAmount.y
                                            val currentIdx = workingLists.indexOfFirst { it.id == list.id }
                                            if (currentIdx != -1) {
                                                val H = if (measuredRowHeightPx > 0f) measuredRowHeightPx else defaultRowHeightPx
                                                if (draggedOffsetY > H * 0.5f && currentIdx < workingLists.lastIndex) {
                                                    val item = workingLists.removeAt(currentIdx)
                                                    workingLists.add(currentIdx + 1, item)
                                                    draggedOffsetY -= H
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else if (draggedOffsetY < -H * 0.5f && currentIdx > 0) {
                                                    val item = workingLists.removeAt(currentIdx)
                                                    workingLists.add(currentIdx - 1, item)
                                                    draggedOffsetY += H
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                        // Enforce LTR container so actions are placed logically
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = if (list.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = list.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isListDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isListDisabled) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.feed_status_disabled),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "$count feeds",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }

                                // Icons ordered: 1. Edit (تعديل), 2. Pin (تثبيت), 3. Disable (تعطيل/مزامنة), 4. Delete (حذف)
                                IconButton(
                                    onClick = { onEditList(list) },
                                    modifier = Modifier.size(32.dp).testTag("btn_edit_list_${list.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit List",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onTogglePin(list) },
                                    modifier = Modifier.size(32.dp).testTag("btn_pin_list_${list.id}")
                                ) {
                                    Icon(
                                        imageVector = if (list.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = "Pin List",
                                        tint = if (list.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleDisable(list) },
                                    modifier = Modifier.size(32.dp).testTag("btn_disable_list_${list.id}")
                                ) {
                                    Icon(
                                        imageVector = if (list.syncEnabled) Icons.Outlined.Sync else Icons.Outlined.SyncDisabled,
                                        contentDescription = "Toggle List Disable",
                                        tint = if (list.syncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteList(list) },
                                    modifier = Modifier.size(32.dp).testTag("btn_delete_list_${list.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete List",
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllFeedsActionSheet(
    feeds: List<FeedWithUnreadCount>,
    onDismiss: () -> Unit,
    onTogglePin: (FeedEntity) -> Unit,
    onToggleDisable: (FeedEntity) -> Unit = {},
    onEditFeed: (FeedEntity) -> Unit,
    onDeleteFeed: (FeedEntity) -> Unit,
    onReorderFeeds: (List<Long>) -> Unit = {},
    onRefreshAllFeeds: () -> Unit = {},
    onMarkAllRead: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val workingFeeds = remember(feeds) { mutableStateListOf<FeedWithUnreadCount>().apply { addAll(feeds) } }
    var draggedFeedId by remember { mutableStateOf<Long?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var measuredRowHeightPx by remember { mutableFloatStateOf(0f) }
    val defaultRowHeightPx = with(density) { 56.dp.toPx() }

    LaunchedEffect(feeds, draggedFeedId) {
        if (draggedFeedId == null) {
            workingFeeds.clear()
            workingFeeds.addAll(feeds)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
                .testTag("sheet_all_feeds_container")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RssFeed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.filter_all),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${workingFeeds.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                workingFeeds.forEachIndexed { index, item ->
                    key(item.feed.id) {
                        val feed = item.feed
                        val isBeingDragged = draggedFeedId == feed.id
                        val isFeedDisabled = !feed.syncEnabled

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isBeingDragged) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            } else if (isFeedDisabled) {
                                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            tonalElevation = if (isBeingDragged) 8.dp else 0.dp,
                            shadowElevation = if (isBeingDragged) 6.dp else 0.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isBeingDragged) 10f else 0f)
                                .onGloballyPositioned { coordinates ->
                                    if (measuredRowHeightPx == 0f && coordinates.size.height > 0) {
                                        val spacingPx = with(density) { 6.dp.toPx() }
                                        measuredRowHeightPx = coordinates.size.height.toFloat() + spacingPx
                                    }
                                }
                                .graphicsLayer {
                                    translationY = if (isBeingDragged) draggedOffsetY else 0f
                                    scaleX = if (isBeingDragged) 1.025f else 1f
                                    scaleY = if (isBeingDragged) 1.025f else 1f
                                }
                                .pointerInput(feed.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedFeedId = feed.id
                                            draggedOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            val hasChanged = workingFeeds.map { it.feed.id } != feeds.map { it.feed.id }
                                            if (hasChanged) {
                                                onReorderFeeds(workingFeeds.map { it.feed.id })
                                            }
                                            draggedFeedId = null
                                            draggedOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedFeedId = null
                                            draggedOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            draggedOffsetY += dragAmount.y
                                            val currentIdx = workingFeeds.indexOfFirst { it.feed.id == feed.id }
                                            if (currentIdx != -1) {
                                                val H = if (measuredRowHeightPx > 0f) measuredRowHeightPx else defaultRowHeightPx
                                                if (draggedOffsetY > H * 0.5f && currentIdx < workingFeeds.lastIndex) {
                                                    val moved = workingFeeds.removeAt(currentIdx)
                                                    workingFeeds.add(currentIdx + 1, moved)
                                                    draggedOffsetY -= H
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else if (draggedOffsetY < -H * 0.5f && currentIdx > 0) {
                                                    val moved = workingFeeds.removeAt(currentIdx)
                                                    workingFeeds.add(currentIdx - 1, moved)
                                                    draggedOffsetY += H
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                        // Enforce LTR container so action icons are placed logically: Edit, Pin, Disable, Delete
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FeedAvatarIcon(iconUrl = feed.iconUrl, feedTitle = feed.title, size = 24.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = feed.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isFeedDisabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isFeedDisabled) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.feed_status_disabled),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (feed.groupName.isNotBlank()) {
                                        Text(
                                            text = feed.groupName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                alpha = if (isFeedDisabled) 0.5f else 0.8f
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }

                                // Ordered actions: 1. Edit (تعديل), 2. Pin (تثبيت), 3. Disable (تعطيل/مزامنة), 4. Delete (حذف)
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onEditFeed(feed)
                                    },
                                    modifier = Modifier.size(32.dp).testTag("btn_edit_feed_${feed.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = "Edit Feed",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onTogglePin(feed) },
                                    modifier = Modifier.size(32.dp).testTag("btn_pin_feed_${feed.id}")
                                ) {
                                    Icon(
                                        imageVector = if (feed.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = "Pin Feed",
                                        tint = if (feed.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onToggleDisable(feed) },
                                    modifier = Modifier.size(32.dp).testTag("btn_disable_feed_${feed.id}")
                                ) {
                                    Icon(
                                        imageVector = if (feed.syncEnabled) Icons.Outlined.Sync else Icons.Outlined.SyncDisabled,
                                        contentDescription = "Toggle Disable Feed",
                                        tint = if (feed.syncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onDeleteFeed(feed)
                                    },
                                    modifier = Modifier.size(32.dp).testTag("btn_delete_feed_${feed.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete Feed",
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
        }
    }
}

@Composable
fun BubblePopupContainer(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.roundToPx() }
    val offsetYPx = with(density) { 8.dp.roundToPx() }

    val positionProvider = remember(marginPx, offsetYPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                var x = if (layoutDirection == LayoutDirection.Rtl) {
                    anchorBounds.right - popupContentSize.width
                } else {
                    anchorBounds.left
                }
                if (x + popupContentSize.width > windowSize.width - marginPx) {
                    x = windowSize.width - popupContentSize.width - marginPx
                }
                if (x < marginPx) {
                    x = marginPx
                }

                val spaceBelow = windowSize.height - anchorBounds.bottom - marginPx
                val y = if (spaceBelow >= popupContentSize.height || spaceBelow >= windowSize.height / 3) {
                    anchorBounds.bottom + offsetYPx
                } else {
                    (anchorBounds.top - popupContentSize.height - offsetYPx).coerceAtLeast(marginPx)
                }

                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            ),
            modifier = modifier
                .widthIn(min = 270.dp, max = 310.dp)
        ) {
            content()
        }
    }
}

@Composable
fun FeedOptionsBubblePopup(
    feed: FeedEntity,
    canSummarize: Boolean,
    onDismiss: () -> Unit,
    onToggleShowInAllFeeds: () -> Unit,
    onToggleAutoTranslate: () -> Unit,
    onToggleNotify: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleFeedSync: () -> Unit,
    onSetReaderMode: (ReaderAiMode) -> Unit
) {
    BubblePopupContainer(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Bubble Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (feed.iconUrl.isNotBlank()) {
                        FeedAvatarIcon(iconUrl = feed.iconUrl, feedTitle = feed.title, size = 20.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.RssFeed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = feed.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.feed_options_bubble_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("btn_close_feed_bubble")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Bubble Toggles
            BubbleToggleRow(
                icon = if (feed.showInAllFeeds) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                title = stringResource(R.string.feed_show_in_feeds_title),
                checked = feed.showInAllFeeds,
                onCheckedChange = { onToggleShowInAllFeeds() }
            )
            BubbleToggleRow(
                icon = Icons.Outlined.Translate,
                title = stringResource(R.string.switch_auto_translate_titles),
                checked = feed.autoTranslate,
                onCheckedChange = { onToggleAutoTranslate() }
            )
            BubbleToggleRow(
                icon = if (feed.notifyEnabled) Icons.Filled.Notifications else Icons.Outlined.NotificationsOff,
                title = stringResource(R.string.title_notifications),
                checked = feed.notifyEnabled,
                onCheckedChange = { onToggleNotify() }
            )
            BubbleToggleRow(
                icon = if (feed.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                title = stringResource(if (feed.isPinned) R.string.feed_action_unpin else R.string.feed_action_pin),
                checked = feed.isPinned,
                onCheckedChange = { onTogglePinned() }
            )
            BubbleToggleRow(
                icon = if (feed.syncEnabled) Icons.Outlined.Sync else Icons.Outlined.SyncDisabled,
                title = stringResource(R.string.title_sync_mode),
                checked = feed.syncEnabled,
                onCheckedChange = { onToggleFeedSync() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // AI Reader Mode Section
            val isAiEnabled = feed.resolvedReaderMode != ReaderAiMode.NONE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = if (canSummarize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.section_reader_mode),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canSummarize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        if (!canSummarize) {
                            Text(
                                text = stringResource(R.string.bubble_api_key_required_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Switch(
                    checked = isAiEnabled && canSummarize,
                    enabled = canSummarize,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onSetReaderMode(ReaderAiMode.TRANSLATE)
                        } else {
                            onSetReaderMode(ReaderAiMode.NONE)
                        }
                    }
                )
            }
            if (isAiEnabled && canSummarize) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = feed.resolvedReaderMode == ReaderAiMode.TRANSLATE,
                        onClick = { onSetReaderMode(ReaderAiMode.TRANSLATE) },
                        label = { Text(stringResource(R.string.feed_mode_translate), style = MaterialTheme.typography.labelMedium) }
                    )
                    FilterChip(
                        selected = feed.resolvedReaderMode == ReaderAiMode.SUMMARIZE,
                        onClick = { onSetReaderMode(ReaderAiMode.SUMMARIZE) },
                        label = { Text(stringResource(R.string.feed_mode_summarize), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
}

@Composable
fun ListOptionsBubblePopup(
    list: CustomListEntity,
    onDismiss: () -> Unit,
    onToggleShowInFeeds: () -> Unit,
    onToggleShowInAllLists: () -> Unit,
    onToggleListSync: () -> Unit,
    onTogglePinned: () -> Unit
) {
    BubblePopupContainer(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Bubble Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = list.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.list_options_bubble_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("btn_close_list_bubble")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Bubble Toggles
            BubbleToggleRow(
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.list_show_in_all_title),
                checked = list.showInAllLists,
                onCheckedChange = { onToggleShowInAllLists() }
            )
            BubbleToggleRow(
                icon = if (list.syncEnabled) Icons.Outlined.Sync else Icons.Outlined.SyncDisabled,
                title = stringResource(R.string.title_sync_mode),
                checked = list.syncEnabled,
                onCheckedChange = { onToggleListSync() }
            )
            BubbleToggleRow(
                icon = if (list.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                title = stringResource(if (list.isPinned) R.string.list_action_unpin else R.string.list_action_pin),
                checked = list.isPinned,
                onCheckedChange = { onTogglePinned() }
            )
        }
    }
}

@Composable
private fun BubbleToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun CustomListDialog(
    initialName: String,
    initialShowInAllLists: Boolean = true,
    initialSyncEnabled: Boolean = true,
    isRename: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, showInAllLists: Boolean, syncEnabled: Boolean) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var showInAllLists by remember(initialName, initialShowInAllLists) { mutableStateOf(initialShowInAllLists) }
    var syncEnabled by remember(initialName, initialSyncEnabled) { mutableStateOf(initialSyncEnabled) }

    ModalScrimCard(
        onDismissRequest = onDismiss,
        zIndex = 60f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (isRename) R.string.dialog_rename_list_title else R.string.dialog_new_list_title
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            DialogInputField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.hint_list_name),
                testTag = "input_custom_list_name"
            )
            ShowInAllSwitchCard(
                title = stringResource(R.string.list_show_in_all_title),
                subtitle = stringResource(R.string.list_show_in_all_subtitle),
                checked = showInAllLists,
                onCheckedChange = { showInAllLists = it },
                testTag = "switch_custom_list_show_in_all"
            )
            SyncModeSelectorSection(
                syncEnabled = syncEnabled,
                onSyncModeChange = { syncEnabled = it },
                autoChipTag = "chip_custom_list_sync_auto",
                manualChipTag = "chip_custom_list_sync_manual"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(name.trim(), showInAllLists, syncEnabled) },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("btn_confirm_custom_list")
                ) {
                    Text(stringResource(if (isRename) R.string.btn_save else R.string.btn_create))
                }
            }
        }
    }
}

@Composable
fun DeleteCustomListDialog(
    customList: CustomListEntity,
    feedCount: Int,
    onDismiss: () -> Unit,
    onConfirmDelete: (deleteFeedsInside: Boolean) -> Unit
) {
    var deleteFeedsInside by remember(customList.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.dialog_delete_list_title, customList.name))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.dialog_delete_list_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (deleteFeedsInside) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { deleteFeedsInside = !deleteFeedsInside }
                        .testTag("row_delete_list_feeds")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteFeedsInside,
                            onCheckedChange = { deleteFeedsInside = it },
                            modifier = Modifier.testTag("checkbox_delete_list_feeds")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dialog_delete_list_also_feeds, feedCount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (deleteFeedsInside) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (deleteFeedsInside) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete(deleteFeedsInside)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("btn_confirm_delete_custom_list")
            ) {
                Text(stringResource(R.string.btn_delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_cancel_delete_custom_list")
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    tag: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(title, color = tint) },
        leadingContent = { Icon(icon, contentDescription = title, tint = tint) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    )
}

@Composable
fun EditFeedDialog(
    feed: FeedEntity,
    customLists: List<CustomListEntity>,
    canSummarize: Boolean = false,
    onDismiss: () -> Unit,
    onToggleNotify: ((FeedEntity) -> Unit)? = null,
    onCreateCustomList: (
        name: String,
        showInAllLists: Boolean,
        syncEnabled: Boolean,
        onCreated: (String) -> Unit
    ) -> Unit,
    onConfirm: (
        newTitle: String,
        newListName: String,
        autoTranslate: Boolean,
        defaultReaderMode: ReaderAiMode,
        showInAllFeeds: Boolean,
        showInAllLists: Boolean,
        syncEnabled: Boolean
    ) -> Unit
) {
    var title by remember(feed) { mutableStateOf(feed.title) }
    var listName by remember(feed) { mutableStateOf(feed.groupName) }
    var autoTranslate by remember(feed) { mutableStateOf(feed.autoTranslate) }
    var notifyEnabled by remember(feed) { mutableStateOf(feed.notifyEnabled) }
    var selectedReaderMode by remember(feed) { mutableStateOf(feed.resolvedReaderMode) }
    var showInlineNewListDialog by remember { mutableStateOf(false) }

    ModalScrimCard(
        onDismissRequest = onDismiss,
        zIndex = 50f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.dialog_edit_feed_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .focusProperties { canFocus = true }
                    .focusTarget()
            )
            DialogUrlInputFieldWithNotification(
                value = title,
                onValueChange = { title = it },
                placeholder = stringResource(R.string.hint_feed_name),
                enabled = true,
                notifyEnabled = notifyEnabled,
                onToggleNotify = {
                    notifyEnabled = !notifyEnabled
                    onToggleNotify?.invoke(feed)
                },
                testTag = "input_edit_feed_name"
            )
            CustomListSelectorSection(
                customLists = customLists,
                selectedListName = listName,
                enabled = true,
                showTitle = false,
                onSelectList = { listName = it },
                onNewListClick = { showInlineNewListDialog = true }
            )
            AutoTranslateSwitchCard(
                checked = autoTranslate,
                enabled = true,
                onCheckedChange = { autoTranslate = it },
                testTag = "switch_edit_feed_auto_translate"
            )
            ArticleAiModeSelectorSection(
                selectedMode = selectedReaderMode,
                enabled = true,
                canSummarize = canSummarize,
                onSelectMode = { selectedReaderMode = it },
                tagPrefix = "chip_edit_feed_mode_"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.btn_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConfirm(
                            title,
                            listName,
                            autoTranslate,
                            selectedReaderMode,
                            feed.showInAllFeeds,
                            feed.showInAllLists,
                            feed.syncEnabled
                        )
                        onDismiss()
                    },
                    modifier = Modifier.testTag("btn_confirm_edit_feed")
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }
    }

    if (showInlineNewListDialog) {
        CustomListDialog(
            initialName = "",
            isRename = false,
            onDismiss = { showInlineNewListDialog = false },
            onConfirm = { createdName, listShowInAll, listSyncEnabled ->
                onCreateCustomList(createdName, listShowInAll, listSyncEnabled) { clean ->
                    listName = clean
                }
                showInlineNewListDialog = false
            }
        )
    }
}

@Composable
private fun DialogInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    enabled: Boolean = true,
    testTag: String
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused }
                    .testTag(testTag)
            )
        }
    }
}

@Composable
private fun ModalScrimCard(
    onDismissRequest: () -> Unit,
    zIndex: Float = 50f,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onDismissRequest)
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.56f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismissRequest
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
        ) {
            content()
        }
    }
}

@Composable
fun FeedRegexFilterDialog(
    feedTitle: String,
    filters: List<FeedFilterEntity>,
    onDismiss: () -> Unit,
    onAddFilter: (pattern: String, isRegex: Boolean) -> Unit = { _, _ -> },
    onDeleteFilter: (Long) -> Unit
) {
    ModalScrimCard(
        onDismissRequest = onDismiss,
        zIndex = 60f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_feed_filters_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "${filters.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = feedTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.filter_dialog_delete_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = stringResource(R.string.backup_option_filters) + " (${filters.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (filters.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_feed_filters),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filters.forEach { filter ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (filter.isRegex) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = if (filter.isRegex) "REGEX" else "TEXT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (filter.isRegex) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = filter.pattern,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { onDeleteFilter(filter.id) },
                                    modifier = Modifier.testTag("btn_delete_filter_${filter.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete Filter",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_close_feed_filters_dialog")
                ) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        }
    }
}



