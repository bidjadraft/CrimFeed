package com.example.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedWithCount
import com.example.data.model.ReaderAiMode
import com.example.ui.components.AddFeedDialog
import com.example.ui.components.CustomListDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.ui.components.DeleteCustomListDialog
import com.example.ui.components.EditFeedDialog
import com.example.ui.components.FeedAvatarIcon

enum class FeedsAndListsViewMode {
    FEEDS_ONLY,
    LISTS_ONLY,
    SETTINGS_TABS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedsAndListsSettingsPage(
    feeds: List<FeedWithCount>,
    customLists: List<CustomListEntity>,
    canSummarize: Boolean = false,
    viewMode: FeedsAndListsViewMode = FeedsAndListsViewMode.SETTINGS_TABS,
    initialViewingAllFeeds: Boolean = false,
    resetSignal: Int = 0,
    onCreateCustomList: (String, Boolean, Boolean, (String) -> Unit) -> Unit,
    onRenameCustomList: (CustomListEntity, String, Boolean, Boolean) -> Unit,
    onDeleteCustomList: (CustomListEntity, Boolean) -> Unit,
    onToggleCustomListPinned: (CustomListEntity) -> Unit = {},
    onToggleCustomListNotify: (CustomListEntity) -> Unit = {},
    onReorderCustomLists: (List<Long>) -> Unit = {},
    onEditFeed: (FeedEntity, String, String, Boolean, ReaderAiMode, Boolean, Boolean, Boolean) -> Unit,
    onToggleFeedPinned: (FeedEntity) -> Unit = {},
    onToggleFeedNotify: (FeedEntity) -> Unit = {},
    onReorderFeeds: (List<Long>) -> Unit = {},
    onDeleteFeed: (FeedEntity) -> Unit,
    onAddFeed: (
        url: String,
        listName: String,
        autoTranslate: Boolean,
        defaultReaderMode: ReaderAiMode,
        notifyEnabled: Boolean,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, _, _ -> },
    onEmitMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedListId by rememberSaveable { mutableStateOf<Long?>(null) }
    var viewingUngrouped by rememberSaveable { mutableStateOf(false) }
    var selectedSettingsTab by rememberSaveable { mutableIntStateOf(if (initialViewingAllFeeds) 0 else 0) }
    var isFeedsSearchActive by rememberSaveable { mutableStateOf(false) }
    var feedsSearchQuery by rememberSaveable { mutableStateOf("") }
    var isListsSearchActive by rememberSaveable { mutableStateOf(false) }
    var listsSearchQuery by rememberSaveable { mutableStateOf("") }
    var showAddFeedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            selectedListId = null
            viewingUngrouped = false
            selectedSettingsTab = 0
            isFeedsSearchActive = false
            feedsSearchQuery = ""
            isListsSearchActive = false
            listsSearchQuery = ""
        }
    }

    var showCreateListDialog by remember { mutableStateOf(false) }
    var renamingList by remember { mutableStateOf<CustomListEntity?>(null) }
    var deletingList by remember { mutableStateOf<CustomListEntity?>(null) }
    var editingFeed by remember { mutableStateOf<FeedEntity?>(null) }
    var feedToDeleteConfirm by remember { mutableStateOf<FeedEntity?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val copiedMessage = stringResource(R.string.copied_feed_url)

    val activeList = remember(customLists, selectedListId) {
        selectedListId?.let { id -> customLists.find { it.id == id } }
    }

    if (selectedListId != null && activeList == null) {
        selectedListId = null
    }

    val ungroupedFeeds = remember(feeds) {
        feeds.filter { it.feed.groupName.isBlank() }
    }

    val isDetailView = selectedListId != null || viewingUngrouped

    val scaffoldTitle = when {
        viewingUngrouped -> stringResource(R.string.feeds_lists_ungrouped_title)
        activeList != null -> activeList.name
        viewMode == FeedsAndListsViewMode.FEEDS_ONLY -> stringResource(R.string.feeds_lists_all_title)
        viewMode == FeedsAndListsViewMode.LISTS_ONLY -> stringResource(R.string.feeds_lists_lists_title)
        else -> stringResource(R.string.settings_feeds_lists)
    }

    val handleBack = {
        if (isDetailView) {
            selectedListId = null
            viewingUngrouped = false
        } else {
            onBack()
        }
    }

    val isShowingFeeds = isDetailView ||
        viewMode == FeedsAndListsViewMode.FEEDS_ONLY ||
        (viewMode == FeedsAndListsViewMode.SETTINGS_TABS && selectedSettingsTab == 0)

    // Feeds data resolution
    val rawFeeds = remember(feeds, ungroupedFeeds, activeList, viewingUngrouped, isShowingFeeds) {
        when {
            viewingUngrouped -> ungroupedFeeds
            activeList != null -> {
                val targetName = activeList.name.trim()
                feeds.filter { it.feed.groupName.equals(targetName, ignoreCase = true) }
            }
            else -> feeds
        }
    }

    val displayedFeeds = remember(rawFeeds, feedsSearchQuery) {
        if (feedsSearchQuery.isBlank()) {
            rawFeeds
        } else {
            val q = feedsSearchQuery.trim()
            rawFeeds.filter {
                it.feed.title.contains(q, ignoreCase = true) ||
                    it.feed.url.contains(q, ignoreCase = true)
            }
        }
    }

    val workingFeeds = remember(displayedFeeds) {
        mutableStateListOf<FeedWithCount>().apply { addAll(displayedFeeds) }
    }
    var draggedFeedId by remember { mutableStateOf<Long?>(null) }
    var feedDraggedOffsetY by remember { mutableFloatStateOf(0f) }
    var measuredFeedHeightPx by remember { mutableFloatStateOf(0f) }
    val defaultFeedHeightPx = with(density) { 68.dp.toPx() }

    LaunchedEffect(displayedFeeds, draggedFeedId) {
        if (draggedFeedId == null) {
            workingFeeds.clear()
            workingFeeds.addAll(displayedFeeds)
        }
    }

    fun commitFeedsReorder() {
        val pinnedIds = workingFeeds.filter { it.feed.isPinned }.map { it.feed.id }
        val pinnedSet = pinnedIds.toHashSet()
        val queue = ArrayDeque(pinnedIds)
        val mergedIds = feeds.map { existing ->
            if (existing.feed.id in pinnedSet && queue.isNotEmpty()) {
                queue.removeFirst()
            } else {
                existing.feed.id
            }
        }
        onReorderFeeds(mergedIds)
    }

    // Lists data resolution & reordering
    val workingLists = remember(customLists) {
        mutableStateListOf<CustomListEntity>().apply { addAll(customLists) }
    }
    var draggedListId by remember { mutableStateOf<Long?>(null) }
    var listDraggedOffsetY by remember { mutableFloatStateOf(0f) }
    var measuredListHeightPx by remember { mutableFloatStateOf(0f) }
    val defaultListHeightPx = with(density) { 68.dp.toPx() }

    LaunchedEffect(customLists, draggedListId) {
        if (draggedListId == null) {
            workingLists.clear()
            workingLists.addAll(customLists)
        }
    }

    val filteredWorkingLists = remember(workingLists, listsSearchQuery) {
        if (listsSearchQuery.isBlank()) {
            workingLists
        } else {
            val q = listsSearchQuery.trim()
            workingLists.filter { it.name.contains(q, ignoreCase = true) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentSearchActive = if (isShowingFeeds) isFeedsSearchActive else isListsSearchActive
        val currentSearchQuery = if (isShowingFeeds) feedsSearchQuery else listsSearchQuery
        val currentSearchPlaceholder = if (isShowingFeeds) {
            stringResource(R.string.search_feeds_placeholder)
        } else {
            stringResource(R.string.search_placeholder)
        }
        val currentSearchTag = if (isShowingFeeds) "input_feeds_search" else "input_lists_search"
        val handleCloseSearch = {
            if (isShowingFeeds) {
                isFeedsSearchActive = false
                feedsSearchQuery = ""
            } else {
                isListsSearchActive = false
                listsSearchQuery = ""
            }
        }

        SettingsSubPageScaffold(
            title = scaffoldTitle,
            onBack = handleBack,
            isSearchActive = currentSearchActive,
            searchQuery = currentSearchQuery,
            onSearchQueryChange = { q ->
                if (isShowingFeeds) feedsSearchQuery = q else listsSearchQuery = q
            },
            searchPlaceholder = currentSearchPlaceholder,
            searchTestTag = currentSearchTag,
            onCloseSearch = handleCloseSearch,
            actions = {
                if (isDetailView) {
                    IconButton(
                        onClick = {
                            isFeedsSearchActive = !isFeedsSearchActive
                            if (!isFeedsSearchActive) feedsSearchQuery = ""
                        },
                        modifier = Modifier.testTag("btn_detail_search_feeds")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.action_search),
                            tint = if (isFeedsSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (activeList != null) {
                        IconButton(
                            onClick = { renamingList = activeList },
                            modifier = Modifier.testTag("btn_detail_rename_list")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.list_action_rename)
                            )
                        }
                        IconButton(
                            onClick = { deletingList = activeList },
                            modifier = Modifier.testTag("btn_detail_delete_list")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.list_action_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    if (isShowingFeeds) {
                        IconButton(
                            onClick = {
                                isFeedsSearchActive = !isFeedsSearchActive
                                if (!isFeedsSearchActive) feedsSearchQuery = ""
                            },
                            modifier = Modifier.testTag("btn_feeds_search")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.action_search),
                                tint = if (isFeedsSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                isListsSearchActive = !isListsSearchActive
                                if (!isListsSearchActive) listsSearchQuery = ""
                            },
                            modifier = Modifier.testTag("btn_lists_search")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.action_search),
                                tint = if (isListsSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        ) {
            // Show Tabs row when inside Settings Feeds & Lists overview
            if (!isDetailView && viewMode == FeedsAndListsViewMode.SETTINGS_TABS) {
                val isFeedsTabSelected = selectedSettingsTab == 0
                val isListsTabSelected = selectedSettingsTab == 1
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("tabs_settings_feeds_lists")
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { selected = isFeedsTabSelected }
                                .clickable(role = Role.Tab) {
                                    selectedSettingsTab = 0
                                    isListsSearchActive = false
                                    listsSearchQuery = ""
                                }
                                .padding(top = 12.dp)
                                .testTag("tab_settings_feeds")
                        ) {
                            Text(
                                text = stringResource(R.string.nav_feeds),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isFeedsTabSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isFeedsTabSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(
                                        color = if (isFeedsTabSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                    )
                                    .testTag(
                                        if (isFeedsTabSelected) {
                                            "active_tab_indicator_feeds"
                                        } else {
                                            "inactive_tab_indicator_feeds"
                                        }
                                    )
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { selected = isListsTabSelected }
                                .clickable(role = Role.Tab) {
                                    selectedSettingsTab = 1
                                    isFeedsSearchActive = false
                                    feedsSearchQuery = ""
                                }
                                .padding(top = 12.dp)
                                .testTag("tab_settings_lists")
                        ) {
                            Text(
                                text = stringResource(R.string.feeds_lists_lists_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isListsTabSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isListsTabSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(
                                        color = if (isListsTabSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                    )
                                    .testTag(
                                        if (isListsTabSelected) {
                                            "active_tab_indicator_lists"
                                        } else {
                                            "inactive_tab_indicator_lists"
                                        }
                                    )
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
            }

            AnimatedContent(
                targetState = isShowingFeeds,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(durationMillis = 150)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 150)))
                        .using(SizeTransform(clip = false))
                },
                label = "feeds_lists_view_transition"
            ) { showingFeeds ->
                if (showingFeeds) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RssFeed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${stringResource(R.string.nav_feeds)} (${displayedFeeds.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = { showAddFeedDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_add_feed_in_bar")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.dialog_add_feed_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (displayedFeeds.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(
                                if (activeList != null) R.string.feeds_lists_empty_feeds_in_list else R.string.empty_feeds_title
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                } else {
                    workingFeeds.forEachIndexed { index, feedItem ->
                        val feed = feedItem.feed
                        key(feed.id) {
                            val isPinned = feed.isPinned
                            val isBeingDragged = draggedFeedId == feed.id

                            // Drag-reorder exclusively for pinned feeds via handle
                            val feedCardModifier = if (isPinned) {
                                Modifier
                                    .zIndex(if (isBeingDragged) 10f else 0f)
                                    .onGloballyPositioned { coordinates ->
                                        if (measuredFeedHeightPx == 0f && coordinates.size.height > 0) {
                                            val spacingPx = with(density) { 8.dp.toPx() }
                                            measuredFeedHeightPx = coordinates.size.height.toFloat() + spacingPx
                                        }
                                    }
                                    .graphicsLayer {
                                        translationY = if (isBeingDragged) feedDraggedOffsetY else 0f
                                        scaleX = if (isBeingDragged) 1.025f else 1f
                                        scaleY = if (isBeingDragged) 1.025f else 1f
                                    }
                            } else {
                                Modifier
                            }

                            val feedHandleModifier = if (isPinned) {
                                Modifier.pointerInput(feed.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedFeedId = feed.id
                                            feedDraggedOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            commitFeedsReorder()
                                            draggedFeedId = null
                                            feedDraggedOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedFeedId = null
                                            feedDraggedOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            feedDraggedOffsetY += dragAmount.y
                                            val currentIdx = workingFeeds.indexOfFirst { it.feed.id == feed.id }
                                            val lastPinnedIdx = workingFeeds.indexOfLast { it.feed.isPinned }.coerceAtLeast(0)
                                            if (currentIdx != -1) {
                                                val sH = if (measuredFeedHeightPx > 0f) measuredFeedHeightPx else defaultFeedHeightPx
                                                if (feedDraggedOffsetY > sH * 0.5f && currentIdx < lastPinnedIdx) {
                                                    val item = workingFeeds.removeAt(currentIdx)
                                                    workingFeeds.add(currentIdx + 1, item)
                                                    feedDraggedOffsetY -= sH
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else if (feedDraggedOffsetY < -sH * 0.5f && currentIdx > 0) {
                                                    val item = workingFeeds.removeAt(currentIdx)
                                                    workingFeeds.add(currentIdx - 1, item)
                                                    feedDraggedOffsetY += sH
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            }
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }

                            FeedRowCard(
                                feed = feed,
                                isDragging = isBeingDragged,
                                dragHandleModifier = feedHandleModifier,
                                modifier = feedCardModifier,
                                onTogglePin = { onToggleFeedPinned(feed) },
                                onToggleNotify = { onToggleFeedNotify(feed) },
                                onCopyUrl = {
                                    clipboardManager.setText(AnnotatedString(feed.url))
                                    onEmitMessage(copiedMessage)
                                },
                                onEdit = { editingFeed = feed },
                                onDelete = { feedToDeleteConfirm = feed }
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // Header bar matching All feeds bar: Lists count on start, + button on opposite side
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${stringResource(R.string.feeds_lists_lists_title)} (${filteredWorkingLists.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = { showCreateListDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("btn_settings_create_list")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.btn_create),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Note: The "All" card containing all feeds is omitted per user instruction!
                if (filteredWorkingLists.isEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.feeds_lists_empty_lists),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                } else {
                    filteredWorkingLists.forEachIndexed { index, customList ->
                        key(customList.id) {
                            val countInList = feeds.count {
                                it.feed.groupName.equals(customList.name.trim(), ignoreCase = true)
                            }
                            val isBeingDragged = draggedListId == customList.id

                            val listFeedsForNotify = remember(feeds, customList.name) {
                                feeds.filter { it.feed.groupName.equals(customList.name.trim(), ignoreCase = true) }
                            }
                            val isListNotified = remember(listFeedsForNotify) {
                                listFeedsForNotify.isNotEmpty() && listFeedsForNotify.any { it.feed.notifyEnabled }
                            }

                            val listCardModifier = Modifier
                                .zIndex(if (isBeingDragged) 10f else 0f)
                                .onGloballyPositioned { coordinates ->
                                    if (measuredListHeightPx == 0f && coordinates.size.height > 0) {
                                        val spacingPx = with(density) { 8.dp.toPx() }
                                        measuredListHeightPx = coordinates.size.height.toFloat() + spacingPx
                                    }
                                }
                                .graphicsLayer {
                                    translationY = if (isBeingDragged) listDraggedOffsetY else 0f
                                    scaleX = if (isBeingDragged) 1.025f else 1f
                                    scaleY = if (isBeingDragged) 1.025f else 1f
                                }

                            val listHandleModifier = Modifier.pointerInput(customList.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggedListId = customList.id
                                        listDraggedOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        val hasChanged = workingLists.map { it.id } != customLists.map { it.id }
                                        if (hasChanged) {
                                            onReorderCustomLists(workingLists.map { it.id })
                                        }
                                        draggedListId = null
                                        listDraggedOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggedListId = null
                                        listDraggedOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        listDraggedOffsetY += dragAmount.y
                                        val currentIdx = workingLists.indexOfFirst { it.id == customList.id }
                                        if (currentIdx != -1) {
                                            val sH = if (measuredListHeightPx > 0f) measuredListHeightPx else defaultListHeightPx
                                            if (listDraggedOffsetY > sH * 0.5f && currentIdx < workingLists.lastIndex) {
                                                val item = workingLists.removeAt(currentIdx)
                                                workingLists.add(currentIdx + 1, item)
                                                listDraggedOffsetY -= sH
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            } else if (listDraggedOffsetY < -sH * 0.5f && currentIdx > 0) {
                                                val item = workingLists.removeAt(currentIdx)
                                                workingLists.add(currentIdx - 1, item)
                                                listDraggedOffsetY += sH
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    }
                                )
                            }

                            ListRowCard(
                                customList = customList,
                                countInList = countInList,
                                isNotified = isListNotified,
                                isDragging = isBeingDragged,
                                dragHandleModifier = listHandleModifier,
                                modifier = listCardModifier,
                                onClick = { selectedListId = customList.id },
                                onTogglePin = { onToggleCustomListPinned(customList) },
                                onToggleNotify = { onToggleCustomListNotify(customList) },
                                onEdit = { renamingList = customList },
                                onDelete = { deletingList = customList }
                            )
                        }
                    }
                }

                if (ungroupedFeeds.isNotEmpty()) {
                    Card(
                        onClick = { viewingUngrouped = true },
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_settings_list_ungrouped")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.RssFeed,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.feeds_lists_ungrouped_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = formatFeedCount(ungroupedFeeds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = ungroupedFeeds.size.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

        if (showAddFeedDialog) {
            AddFeedDialog(
                customLists = customLists,
                isAdding = false,
                canSummarize = canSummarize,
                onDismiss = { showAddFeedDialog = false },
                onAddFeed = { url, listName, autoTranslate, defaultReaderMode, notifyEnabled ->
                    onAddFeed(url, listName, autoTranslate, defaultReaderMode, notifyEnabled) {
                        showAddFeedDialog = false
                    }
                },
                onCreateCustomList = { name, showInAllLists, syncEnabled, onCreated ->
                    onCreateCustomList(name, showInAllLists, syncEnabled, onCreated)
                }
            )
        }

        if (showCreateListDialog) {
            CustomListDialog(
                initialName = "",
                isRename = false,
                onDismiss = { showCreateListDialog = false },
                onConfirm = { newName, showInAllLists, syncEnabled ->
                    onCreateCustomList(newName, showInAllLists, syncEnabled) {}
                    showCreateListDialog = false
                }
            )
        }

        renamingList?.let { targetList ->
            CustomListDialog(
                initialName = targetList.name,
                initialShowInAllLists = targetList.showInAllLists,
                initialSyncEnabled = targetList.syncEnabled,
                isRename = true,
                onDismiss = { renamingList = null },
                onConfirm = { newName, showInAllLists, syncEnabled ->
                    onRenameCustomList(targetList, newName, showInAllLists, syncEnabled)
                    renamingList = null
                }
            )
        }

        deletingList?.let { targetList ->
            val feedCountInList = feeds.count {
                it.feed.groupName.equals(targetList.name.trim(), ignoreCase = true)
            }
            DeleteCustomListDialog(
                customList = targetList,
                feedCount = feedCountInList,
                onDismiss = { deletingList = null },
                onConfirmDelete = { deleteFeedsInside ->
                    onDeleteCustomList(targetList, deleteFeedsInside)
                    deletingList = null
                }
            )
        }

        editingFeed?.let { targetFeed ->
            EditFeedDialog(
                feed = targetFeed,
                customLists = customLists,
                canSummarize = canSummarize,
                onDismiss = { editingFeed = null },
                onCreateCustomList = onCreateCustomList,
                onConfirm = { newTitle, newListName, autoTrans, readerMode, showInFeeds, showInLists, syncEnabled ->
                    onEditFeed(
                        targetFeed,
                        newTitle,
                        newListName,
                        autoTrans,
                        readerMode,
                        showInFeeds,
                        showInLists,
                        syncEnabled
                    )
                    editingFeed = null
                }
            )
        }

        feedToDeleteConfirm?.let { feed ->
            AlertDialog(
                onDismissRequest = { feedToDeleteConfirm = null },
                title = { Text(stringResource(R.string.dialog_confirm_delete_feed_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.dialog_confirm_delete_feed_message,
                            feed.title
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteFeed(feed)
                            feedToDeleteConfirm = null
                        },
                        modifier = Modifier.testTag("btn_confirm_delete_feed_settings")
                    ) {
                        Text(
                            stringResource(R.string.btn_delete),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { feedToDeleteConfirm = null },
                        modifier = Modifier.testTag("btn_cancel_delete_feed_settings")
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun ListRowCard(
    customList: CustomListEntity,
    countInList: Int,
    isNotified: Boolean,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleNotify: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_settings_list_${customList.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag indicator 6-dots icon for reordering lists in Settings
            Box(
                modifier = dragHandleModifier
                    .size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DragIndicator,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = customList.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (customList.isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = formatFeedCount(countInList),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("text_settings_list_count_${customList.id}")
                    )
                }
            }

            // Trailing actions: Pin (تثبيت), Notifications (اشعارات), and More (⋮)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.testTag("btn_list_pin_${customList.id}")
                ) {
                    Icon(
                        imageVector = if (customList.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(if (customList.isPinned) R.string.list_action_unpin else R.string.list_action_pin),
                        tint = if (customList.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleNotify,
                    modifier = Modifier.testTag("btn_list_notify_${customList.id}")
                ) {
                    Icon(
                        imageVector = if (isNotified) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = stringResource(R.string.title_notifications),
                        tint = if (isNotified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                var showListMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showListMenu = true },
                        modifier = Modifier.testTag("btn_list_more_${customList.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showListMenu,
                        onDismissRequest = { showListMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_edit_list)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showListMenu = false
                                onEdit()
                            },
                            modifier = Modifier.testTag("menu_item_rename_list_${customList.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.menu_delete_list),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showListMenu = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("menu_item_delete_list_${customList.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedRowCard(
    feed: FeedEntity,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    onTogglePin: () -> Unit,
    onToggleNotify: () -> Unit,
    onCopyUrl: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 6.dp else 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_list_feed_${feed.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag indicator 6-dots icon ONLY for pinned feeds in Settings
            if (feed.isPinned) {
                Box(
                    modifier = dragHandleModifier
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragIndicator,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeedAvatarIcon(
                    iconUrl = feed.iconUrl,
                    feedTitle = feed.title,
                    size = 36.dp
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = feed.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (feed.isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = feed.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Trailing actions: Pin (تثبيت), Notifications (اشعارات), and More (⋮)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.testTag("btn_feed_pin_${feed.id}")
                ) {
                    Icon(
                        imageVector = if (feed.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(if (feed.isPinned) R.string.feed_action_unpin else R.string.feed_action_pin),
                        tint = if (feed.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleNotify,
                    modifier = Modifier.testTag("btn_feed_notify_${feed.id}")
                ) {
                    Icon(
                        imageVector = if (feed.notifyEnabled) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                        contentDescription = stringResource(R.string.title_notifications),
                        tint = if (feed.notifyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                var showFeedMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showFeedMenu = true },
                        modifier = Modifier.testTag("btn_feed_more_${feed.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showFeedMenu,
                        onDismissRequest = { showFeedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_edit_feed)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showFeedMenu = false
                                onEdit()
                            },
                            modifier = Modifier.testTag("menu_item_edit_feed_${feed.id}")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_copy_feed)) },
                            leadingIcon = {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showFeedMenu = false
                                onCopyUrl()
                            },
                            modifier = Modifier.testTag("menu_item_copy_feed_${feed.id}")
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.menu_delete_feed),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showFeedMenu = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("menu_item_delete_feed_${feed.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatFeedCount(count: Int): String {
    return if (count == 1) {
        stringResource(R.string.feeds_lists_feed_count_single)
    } else {
        stringResource(R.string.feeds_lists_feed_count_plural, count)
    }
}
