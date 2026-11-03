package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import com.example.data.local.FeedFilterEntity
import com.example.ui.components.FeedRegexFilterDialog
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.local.ArticleWithFeed
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedWithCount
import com.example.data.model.FeedLayoutMode
import com.example.data.model.ReaderAiMode
import com.example.ui.components.AdaptiveArticlesStream
import com.example.ui.components.AddFeedDialog
import com.example.ui.components.AllFeedsActionSheet
import com.example.ui.components.AllListsActionSheet
import com.example.ui.components.CustomListActionSheet
import com.example.ui.components.CustomListDialog
import com.example.ui.components.DeleteCustomListDialog
import com.example.ui.components.EditFeedDialog
import com.example.ui.components.FeedAvatarIcon
import com.example.ui.components.FeedLongPressActionSheet
import com.example.ui.components.FeedOptionsBubblePopup
import com.example.ui.components.LayoutModeIconButton
import com.example.ui.components.ListOptionsBubblePopup
import com.example.ui.components.RefreshFeedsIconButton
import com.example.ui.viewmodel.FeedSyncProgress
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    feeds: List<FeedWithCount>,
    customLists: List<CustomListEntity>,
    selectedFeedId: Long?,
    selectedListName: String?,
    articles: List<ArticleWithFeed>,
    layoutMode: FeedLayoutMode,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    isIconSyncing: Boolean,
    isPullSyncing: Boolean,
    syncingFeedIds: Set<Long>,
    syncProgress: FeedSyncProgress,
    isAddingFeed: Boolean,
    isImportingOpml: Boolean,
    translatingIds: Set<Long>,
    canSummarize: Boolean = false,
    showTranslateButton: Boolean = true,
    onScrollPositionChanged: (Int, Int) -> Unit,
    onSelectFeed: (Long?) -> Unit,
    onSelectCustomList: (String?) -> Unit,
    onCreateCustomList: (String, Boolean, Boolean, (String) -> Unit) -> Unit,
    onRenameCustomList: (CustomListEntity, String, Boolean, Boolean) -> Unit,
    onRefreshCustomList: (CustomListEntity) -> Unit,
    onToggleCustomListSyncEnabled: (CustomListEntity) -> Unit,
    onToggleCustomListShowInAllLists: (CustomListEntity) -> Unit,
    onReorderCustomLists: (List<Long>) -> Unit,
    onDeleteCustomList: (CustomListEntity, Boolean) -> Unit,
    onImportOpmlClick: () -> Unit,
    onCycleLayout: () -> Unit,
    onOpenSearch: (String) -> Unit,
    onSyncFromIcon: () -> Unit,
    onSyncFromPull: () -> Unit,
    onRefreshSingleFeed: (FeedEntity) -> Unit,
    onAddFeed: (
        url: String,
        listName: String,
        autoTranslate: Boolean,
        defaultReaderMode: ReaderAiMode,
        notifyEnabled: Boolean,
        onSuccess: () -> Unit
    ) -> Unit,
    onEditFeed: (FeedEntity, String, String, Boolean, ReaderAiMode, Boolean, Boolean, Boolean) -> Unit,
    onToggleFeedSyncEnabled: (FeedEntity) -> Unit,
    onToggleFeedPinned: (FeedEntity) -> Unit,
    onToggleFeedShowInAllFeeds: (FeedEntity) -> Unit,
    onToggleFeedShowInAllLists: (FeedEntity) -> Unit = {},
    onReorderFeeds: (List<Long>) -> Unit,
    onToggleFeedAutoTranslate: (FeedEntity) -> Unit,
    onSetFeedReaderMode: (FeedEntity, ReaderAiMode) -> Unit = { _, _ -> },
    onMarkFeedAllRead: (FeedEntity) -> Unit,
    onMarkAllReadGlobal: () -> Unit,
    onDeleteFeed: (FeedEntity) -> Unit,
    onOpenArticle: (Long) -> Unit,
    onTranslateTitle: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    onToggleSave: (ArticleWithFeed) -> Unit,
    onShare: (ArticleWithFeed) -> Unit,
    notifiedArticles: List<ArticleWithFeed> = emptyList(),
    notifiedUnreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    onToggleFeedNotify: (FeedEntity) -> Unit = {},
    onMarkAllNotifiedRead: () -> Unit = {},
    onClearNotifiedArticles: () -> Unit = {},
    onToggleCustomListPinned: (CustomListEntity) -> Unit = {},
    onToggleCustomListAutoTranslate: (CustomListEntity) -> Unit = {},
    onToggleCustomListShowInFeeds: (CustomListEntity) -> Unit = {},
    onOpenFeedsAndListsSettings: (openAllFeeds: Boolean) -> Unit = {},
    onOpenStandaloneFeeds: () -> Unit = { onOpenFeedsAndListsSettings(true) },
    onOpenStandaloneLists: () -> Unit = { onOpenFeedsAndListsSettings(false) },
    onEmitMessage: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var longPressedFeedId by remember { mutableStateOf<Long?>(null) }
    var editingFeed by remember { mutableStateOf<FeedEntity?>(null) }
    var longPressedListId by remember { mutableStateOf<Long?>(null) }
    var renamingList by remember { mutableStateOf<CustomListEntity?>(null) }
    var deletingList by remember { mutableStateOf<CustomListEntity?>(null) }
    var showAllListsSheet by remember { mutableStateOf(false) }
    var showAllFeedsSheet by remember { mutableStateOf(false) }
    var bubbleFeedId by remember { mutableStateOf<Long?>(null) }
    var bubbleListId by remember { mutableStateOf<Long?>(null) }
    var deletingFeedConfirm by remember { mutableStateOf<FeedEntity?>(null) }

    val longPressedFeed = remember(feeds, longPressedFeedId) {
        feeds.find { it.feed.id == longPressedFeedId }?.feed
    }
    val longPressedList = remember(customLists, longPressedListId) {
        customLists.find { it.id == longPressedListId }
    }

    BackHandler(enabled = bubbleFeedId != null || bubbleListId != null) {
        bubbleFeedId = null
        bubbleListId = null
    }

    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val layoutDirection = LocalLayoutDirection.current
    val axisSign = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val coroutineScope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied_feed_url)
    val pinnedReorderHint = stringResource(R.string.reorder_pinned_only_empty_hint)

    val disabledListNamesLower = remember(customLists) {
        customLists
            .asSequence()
            .filter { !it.showInAllLists }
            .map { it.name.trim().lowercase(Locale.ROOT) }
            .toHashSet()
    }

    val syncDisabledListNamesLower = remember(customLists) {
        customLists
            .asSequence()
            .filter { !it.syncEnabled }
            .map { it.name.trim().lowercase(Locale.ROOT) }
            .toHashSet()
    }

    val syncingListNamesLower = remember(feeds, syncingFeedIds) {
        if (syncingFeedIds.isEmpty()) {
            emptySet()
        } else {
            feeds
                .asSequence()
                .filter { it.feed.id in syncingFeedIds && it.feed.groupName.isNotBlank() }
                .map { it.feed.groupName.trim().lowercase(Locale.ROOT) }
                .toHashSet()
        }
    }

    val visibleFeeds = remember(feeds, selectedListName) {
        if (selectedListName.isNullOrBlank()) {
            feeds
        } else {
            feeds.filter { it.feed.groupName.equals(selectedListName, ignoreCase = true) }
        }
    }

    val pinnedVisibleCount = remember(visibleFeeds) {
        visibleFeeds.count { it.feed.isPinned }
    }

    val allListsUnreadCount = remember(feeds, disabledListNamesLower) {
        feeds.sumOf { item ->
            val feed = item.feed
            val inEnabledList = feed.groupName.isBlank() ||
                feed.groupName.trim().lowercase(Locale.ROOT) !in disabledListNamesLower
            if (feed.showInAllFeeds && inEnabledList) item.unreadCount else 0
        }
    }

    val customListUnreadCounts = remember(feeds) {
        val countsByListLower = HashMap<String, Int>()
        for (item in feeds) {
            val groupKey = item.feed.groupName.trim().lowercase(Locale.ROOT)
            if (groupKey.isNotEmpty() && item.feed.showInAllLists) {
                countsByListLower[groupKey] = (countsByListLower[groupKey] ?: 0) + item.unreadCount
            }
        }
        countsByListLower
    }

    val totalUnreadInAll = remember(visibleFeeds, selectedListName, disabledListNamesLower) {
        visibleFeeds.sumOf { item ->
            val feed = item.feed
            val includedInAll = if (!selectedListName.isNullOrBlank()) {
                feed.showInAllLists
            } else {
                feed.showInAllFeeds && (
                    feed.groupName.isBlank() ||
                        feed.groupName.trim().lowercase(Locale.ROOT) !in disabledListNamesLower
                    )
            }
            if (includedInAll) item.unreadCount else 0
        }
    }

    val hasUnreadArticles = remember(articles) { articles.any { !it.article.isRead } }

    val customListsRowState = rememberLazyListState()
    val feedsRowState = rememberLazyListState()
    var pendingScrollToFeedId by remember { mutableStateOf<Long?>(null) }
    var pendingScrollToListId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(visibleFeeds, pendingScrollToFeedId) {
        val targetId = pendingScrollToFeedId ?: return@LaunchedEffect
        val idx = visibleFeeds.indexOfFirst { it.feed.id == targetId }
        if (idx >= 0) {
            feedsRowState.animateScrollToItem(idx + 1)
            bubbleFeedId = targetId
            pendingScrollToFeedId = null
        }
    }

    LaunchedEffect(customLists, pendingScrollToListId) {
        val targetId = pendingScrollToListId ?: return@LaunchedEffect
        val idx = customLists.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            customListsRowState.animateScrollToItem(idx + 1)
            bubbleListId = targetId
            pendingScrollToListId = null
        }
    }

    val isProgressVisible = syncProgress.isVisible && syncProgress.totalCount > 0
    val fabBottomPadding by animateDpAsState(
        targetValue = if (isProgressVisible) 58.dp else 18.dp,
        label = "fab_bottom_padding"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RssFeed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val targetFeed = feeds.find { it.feed.id == selectedFeedId }?.feed
                            if (targetFeed != null) {
                                onMarkFeedAllRead(targetFeed)
                            } else {
                                onMarkAllReadGlobal()
                            }
                        },
                        enabled = hasUnreadArticles,
                        modifier = Modifier.testTag("btn_mark_all_read_top")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = stringResource(R.string.feed_action_mark_all_read),
                            tint = if (hasUnreadArticles) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    LayoutModeIconButton(
                        layoutMode = layoutMode,
                        onCycleLayout = onCycleLayout,
                        testTag = "btn_cycle_layout"
                    )
                    IconButton(
                        onClick = { onOpenSearch("") },
                        modifier = Modifier.testTag("btn_top_search")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.action_search)
                        )
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("btn_top_notifications")
                    ) {
                        BadgedBox(
                            badge = {
                                if (notifiedUnreadCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ) {
                                        Text(
                                            text = if (notifiedUnreadCount > 99) "99+" else "$notifiedUnreadCount",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (notifiedUnreadCount > 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                                contentDescription = stringResource(R.string.title_notifications),
                                tint = if (notifiedUnreadCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )

            // Custom Lists Filter Bar
            if (customLists.isNotEmpty()) {
                LazyRow(
                    state = customListsRowState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("custom_lists_bar")
                ) {
                    item(key = "all_custom_lists_pill") {
                        var showAllListsMenu by remember { mutableStateOf(false) }
                        CustomListPill(
                            name = stringResource(R.string.filter_all_lists),
                            count = allListsUnreadCount,
                            countTag = "chip_list_count_all",
                            selected = selectedListName == null,
                            showFolderIcon = false,
                            isHiddenFromAll = false,
                            isSyncDisabled = false,
                            isSyncingList = false,
                            isReorderMode = false,
                            isDragging = false,
                            onClick = { onSelectCustomList(null) },
                            onLongClick = null,
                            onOptionsClick = { showAllListsMenu = true },
                            bubblePopup = {
                                DropdownMenu(
                                    expanded = showAllListsMenu,
                                    onDismissRequest = { showAllListsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_edit_lists)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showAllListsMenu = false
                                            onOpenStandaloneLists()
                                        },
                                        modifier = Modifier.testTag("menu_item_edit_lists")
                                    )
                                }
                            },
                            modifier = Modifier.testTag("chip_list_all")
                        )
                    }

                    items(customLists, key = { it.id }) { customList ->
                        val isSelected = selectedListName?.equals(customList.name, ignoreCase = true) == true
                        val listCount = customListUnreadCounts[customList.name.trim().lowercase(Locale.ROOT)] ?: 0
                        val listKeyLower = customList.name.trim().lowercase(Locale.ROOT)
                        CustomListPill(
                            name = customList.name,
                            count = listCount,
                            countTag = "chip_list_count_${customList.name}",
                            selected = isSelected,
                            showFolderIcon = true,
                            isPinned = customList.isPinned,
                            isHiddenFromAll = !customList.showInAllLists,
                            isSyncDisabled = !customList.syncEnabled,
                            isSyncingList = listKeyLower in syncingListNamesLower,
                            isReorderMode = false,
                            isDragging = false,
                            onClick = { onSelectCustomList(customList.name) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                longPressedListId = customList.id
                            },
                            onOptionsClick = { bubbleListId = if (bubbleListId == customList.id) null else customList.id },
                            bubblePopup = {
                                if (bubbleListId == customList.id) {
                                    ListOptionsBubblePopup(
                                        list = customList,
                                        onDismiss = { bubbleListId = null },
                                        onToggleShowInFeeds = { onToggleCustomListShowInFeeds(customList) },
                                        onToggleShowInAllLists = { onToggleCustomListShowInAllLists(customList) },
                                        onToggleListSync = { onToggleCustomListSyncEnabled(customList) },
                                        onTogglePinned = {
                                            pendingScrollToListId = customList.id
                                            bubbleListId = customList.id
                                            onToggleCustomListPinned(customList)
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.testTag("chip_list_${customList.name}")
                        )
                    }

                }
            }

            // Feeds Switcher Bar
            if (feeds.isNotEmpty()) {
                LazyRow(
                    state = feedsRowState,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("feed_switcher_bar")
                ) {
                    item(key = "all_feeds_pill") {
                        var showAllFeedsMenu by remember { mutableStateOf(false) }
                        FeedSwitcherPill(
                            title = stringResource(R.string.filter_all_feeds),
                            iconUrl = null,
                            isPinned = false,
                            isHiddenFromAll = false,
                            isSyncDisabled = false,
                            autoTranslate = false,
                            isSyncingSingle = false,
                            isReorderMode = false,
                            interactive = true,
                            isDragging = false,
                            count = totalUnreadInAll,
                            selected = selectedFeedId == null,
                            onClick = { onSelectFeed(null) },
                            onLongClick = null,
                            onOptionsClick = { showAllFeedsMenu = true },
                            bubblePopup = {
                                DropdownMenu(
                                    expanded = showAllFeedsMenu,
                                    onDismissRequest = { showAllFeedsMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_edit_feeds)) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            showAllFeedsMenu = false
                                            onOpenStandaloneFeeds()
                                        },
                                        modifier = Modifier.testTag("menu_item_edit_feeds")
                                    )
                                }
                            },
                            modifier = Modifier.testTag("feed_pill_all")
                        )
                    }

                    items(visibleFeeds, key = { it.feed.id }) { feedItem ->
                        val feed = feedItem.feed
                        val isParentListSyncDisabled = feed.groupName.isNotBlank() &&
                            feed.groupName.trim().lowercase(Locale.ROOT) in syncDisabledListNamesLower

                        FeedSwitcherPill(
                            title = feed.title,
                            iconUrl = feed.iconUrl,
                            isPinned = feed.isPinned,
                            isHiddenFromAll = !feed.showInAllFeeds,
                            isSyncDisabled = !feed.syncEnabled || isParentListSyncDisabled,
                            autoTranslate = feed.autoTranslate,
                            notifyEnabled = feed.notifyEnabled,
                            isSyncingSingle = feed.id in syncingFeedIds,
                            isReorderMode = false,
                            interactive = true,
                            isDragging = false,
                            count = feedItem.unreadCount,
                            selected = selectedFeedId == feed.id,
                            onClick = { onSelectFeed(feed.id) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                longPressedFeedId = feed.id
                            },
                            onOptionsClick = { bubbleFeedId = if (bubbleFeedId == feed.id) null else feed.id },
                            bubblePopup = {
                                if (bubbleFeedId == feed.id) {
                                    FeedOptionsBubblePopup(
                                        feed = feed,
                                        canSummarize = canSummarize,
                                        onDismiss = { bubbleFeedId = null },
                                        onToggleShowInAllFeeds = { onToggleFeedShowInAllFeeds(feed) },
                                        onToggleAutoTranslate = { onToggleFeedAutoTranslate(feed) },
                                        onToggleNotify = { onToggleFeedNotify(feed) },
                                        onTogglePinned = {
                                            pendingScrollToFeedId = feed.id
                                            bubbleFeedId = feed.id
                                            onToggleFeedPinned(feed)
                                        },
                                        onToggleFeedSync = { onToggleFeedSyncEnabled(feed) },
                                        onSetReaderMode = { mode -> onSetFeedReaderMode(feed, mode) }
                                    )
                                }
                            },
                            modifier = Modifier.testTag("feed_pill_${feed.id}")
                        )
                    }

                }
            }

            PullToRefreshBox(
                isRefreshing = isPullSyncing || isImportingOpml,
                onRefresh = onSyncFromPull,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("home_pull_to_refresh")
            ) {
                when {
                    feeds.isEmpty() -> {
                        EmptyFeedsWelcomeState(
                            isAdding = isAddingFeed,
                            isImportingOpml = isImportingOpml,
                            onOpenAddDialog = { showAddDialog = true },
                            onImportOpmlClick = onImportOpmlClick
                        )
                    }
                    articles.isEmpty() -> {
                        AllCaughtUpState(onSync = onSyncFromPull)
                    }
                    else -> {
                        val streamKey = "home_${selectedListName.orEmpty().lowercase(Locale.ROOT)}_${selectedFeedId ?: "all"}"
                        AdaptiveArticlesStream(
                            articles = articles,
                            layoutMode = layoutMode,
                            translatingIds = translatingIds,
                            onOpenArticle = onOpenArticle,
                            onTranslateTitle = onTranslateTitle,
                            onToggleRead = { onToggleRead(it) },
                            onSearchRelated = { post ->
                                val seed = post.article.title.split(" ")
                                    .firstOrNull { w -> w.length > 4 }
                                    .orEmpty()
                                onOpenSearch(seed)
                            },
                            onToggleSave = onToggleSave,
                            onShare = onShare,
                            showTranslateButton = showTranslateButton,
                            dimReadArticles = selectedFeedId != null,
                            enableNewPostsPill = true,
                            streamKey = streamKey,
                            initialScrollIndex = initialScrollIndex,
                            initialScrollOffset = initialScrollOffset,
                            onScrollPositionChanged = onScrollPositionChanged
                        )
                    }
                }
            }

            // Bottom Live Feed Sync Progress Bar (e.g., 8/10 -> 10/10)
            AnimatedVisibility(
                visible = isProgressVisible,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                BottomFeedSyncProgressBar(progress = syncProgress)
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(AbsoluteAlignment.BottomLeft)
                .padding(start = 18.dp, bottom = fabBottomPadding)
                .testTag("fab_add_feed_bottom_left")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.action_add_feed)
            )
        }

        if (showAddDialog) {
        AddFeedDialog(
            customLists = customLists,
            isAdding = isAddingFeed,
            canSummarize = canSummarize,
            onDismiss = { showAddDialog = false },
            onAddFeed = { url, listName, autoTrans, readerMode, notifyEnabled ->
                onAddFeed(url, listName, autoTrans, readerMode, notifyEnabled) {
                    showAddDialog = false
                }
            },
            onCreateCustomList = onCreateCustomList
        )
    }

    if (showAllListsSheet) {
        AllListsActionSheet(
            customLists = customLists,
            feedCounts = customListUnreadCounts,
            onDismiss = { showAllListsSheet = false },
            onEditList = { list -> renamingList = list },
            onTogglePin = onToggleCustomListPinned,
            onToggleDisable = onToggleCustomListSyncEnabled,
            onDeleteList = { list -> deletingList = list },
            onReorderLists = onReorderCustomLists
        )
    }

    if (showAllFeedsSheet) {
        AllFeedsActionSheet(
            feeds = visibleFeeds,
            onDismiss = { showAllFeedsSheet = false },
            onTogglePin = onToggleFeedPinned,
            onToggleDisable = { feed -> onToggleFeedSyncEnabled(feed) },
            onEditFeed = { feed -> editingFeed = feed },
            onDeleteFeed = onDeleteFeed,
            onReorderFeeds = { reorderedIds -> onReorderFeeds(reorderedIds) }
        )
    }

    longPressedList?.let { targetList ->
        CustomListActionSheet(
            customList = targetList,
            onDismiss = { longPressedListId = null },
            onRenameRequested = { renamingList = targetList },
            onRefreshList = { onRefreshCustomList(targetList) },
            onDeleteRequested = { deletingList = targetList }
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

    longPressedFeed?.let { feed ->
        FeedLongPressActionSheet(
            feed = feed,
            onDismiss = { longPressedFeedId = null },
            onEditRequested = { editingFeed = feed },
            onRefreshFeed = { onRefreshSingleFeed(feed) },
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(feed.url))
                onEmitMessage(copiedMessage)
            },
            onDelete = { deletingFeedConfirm = feed }
        )
    }

    deletingFeedConfirm?.let { feed ->
        AlertDialog(
            onDismissRequest = { deletingFeedConfirm = null },
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
                        deletingFeedConfirm = null
                    },
                    modifier = Modifier.testTag("btn_confirm_delete_feed_home")
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
                    onClick = { deletingFeedConfirm = null },
                    modifier = Modifier.testTag("btn_cancel_delete_feed_home")
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    editingFeed?.let { feed ->
        EditFeedDialog(
            feed = feed,
            customLists = customLists,
            canSummarize = canSummarize,
            onDismiss = { editingFeed = null },
            onToggleNotify = onToggleFeedNotify,
            onCreateCustomList = onCreateCustomList,
            onConfirm = { newTitle, newListName, autoTrans, readerMode, showInFeeds, showInLists, syncEnabled ->
                onEditFeed(feed, newTitle, newListName, autoTrans, readerMode, showInFeeds, showInLists, syncEnabled)
            }
        )
    }
    }
}

@Composable
private fun BottomFeedSyncProgressBar(progress: FeedSyncProgress) {
    val safeTotal = progress.totalCount.coerceAtLeast(1)
    val safeChecked = progress.checkedCount.coerceIn(0, safeTotal)
    val isComplete = safeChecked >= safeTotal
    val fraction = safeChecked.toFloat() / safeTotal.toFloat()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bottom_sync_progress_bar")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isComplete) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!isComplete && progress.currentFeedTitle.isNotBlank()) {
                    FeedAvatarIcon(
                        iconUrl = progress.currentFeedIconUrl,
                        feedTitle = progress.currentFeedTitle,
                        size = 18.dp
                    )
                }
                Text(
                    text = if (isComplete) {
                        stringResource(R.string.sync_feeds_completed)
                    } else if (progress.currentFeedTitle.isNotBlank()) {
                        progress.currentFeedTitle
                    } else {
                        stringResource(R.string.sync_checking_feeds)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (!isComplete && progress.currentFeedTitle.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$safeChecked/$safeTotal",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("sync_progress_counter")
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomListPill(
    name: String,
    count: Int,
    countTag: String? = null,
    selected: Boolean,
    showFolderIcon: Boolean,
    isPinned: Boolean = false,
    isHiddenFromAll: Boolean,
    isSyncDisabled: Boolean,
    isSyncingList: Boolean,
    isReorderMode: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onOptionsClick: (() -> Unit)? = null,
    bubblePopup: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isDragging -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isDragging -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        isHiddenFromAll -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        isDragging || isReorderMode -> MaterialTheme.colorScheme.primary
        isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        selected -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val clickModifier = if (isReorderMode) {
        Modifier
    } else if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .then(clickModifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isReorderMode) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        } else if (isSyncingList) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (showFolderIcon) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
        if (isPinned) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 130.dp)
        )
        if (isSyncDisabled) {
            Icon(
                imageVector = Icons.Outlined.SyncDisabled,
                contentDescription = "Auto-refresh disabled",
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        if (isHiddenFromAll) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = "Hidden from All Lists",
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        val badgeBgColor = when {
            selected && count > 0 -> MaterialTheme.colorScheme.secondary
            count > 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
        }
        val badgeTextColor = when {
            selected && count > 0 -> MaterialTheme.colorScheme.onSecondary
            count > 0 -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> contentColor.copy(alpha = 0.65f)
        }
        Surface(
            shape = CircleShape,
            color = badgeBgColor
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = badgeTextColor,
                modifier = Modifier
                    .padding(horizontal = 5.dp, vertical = 0.5.dp)
                    .then(if (countTag != null) Modifier.testTag(countTag) else Modifier)
            )
        }
        if (onOptionsClick != null && !isReorderMode) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = onOptionsClick
                        )
                        .testTag("btn_list_options_${name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                bubblePopup()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedSwitcherPill(
    title: String,
    iconUrl: String?,
    isPinned: Boolean,
    isHiddenFromAll: Boolean,
    isSyncDisabled: Boolean,
    autoTranslate: Boolean,
    notifyEnabled: Boolean = false,
    isSyncingSingle: Boolean,
    isReorderMode: Boolean,
    interactive: Boolean,
    isDragging: Boolean,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onOptionsClick: (() -> Unit)? = null,
    bubblePopup: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isDragging -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        isDragging || selected -> MaterialTheme.colorScheme.onPrimaryContainer
        isHiddenFromAll -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        isDragging || isReorderMode || selected -> MaterialTheme.colorScheme.primary
        isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val clickModifier = if (!interactive || isReorderMode) {
        Modifier
    } else if (onLongClick != null) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, borderColor, CircleShape)
            .then(clickModifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isReorderMode) {
            Icon(
                imageVector = Icons.Outlined.DragIndicator,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
        } else if (isSyncingSingle) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (iconUrl != null) {
            FeedAvatarIcon(iconUrl = iconUrl, feedTitle = title, size = 16.dp)
        }
        if (isPinned) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 130.dp)
        )
        if (isSyncDisabled) {
            Icon(
                imageVector = Icons.Outlined.SyncDisabled,
                contentDescription = "Auto-refresh disabled",
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        if (isHiddenFromAll) {
            Icon(
                imageVector = Icons.Outlined.VisibilityOff,
                contentDescription = "Hidden from All Feeds",
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
        }
        if (autoTranslate) {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = "Auto-Translate active",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
        if (notifyEnabled) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications active",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 0.5.dp)
                )
            }
        }
        if (onOptionsClick != null && !isReorderMode) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = onOptionsClick
                        )
                        .testTag("btn_feed_options_${title}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                bubblePopup()
            }
        }
    }
}

@Composable
private fun EmptyFeedsWelcomeState(
    isAdding: Boolean,
    isImportingOpml: Boolean,
    onOpenAddDialog: () -> Unit,
    onImportOpmlClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("empty_feeds_scroll"),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item(key = "empty_feeds_welcome") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.RssFeed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.empty_feeds_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.empty_feeds_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenAddDialog,
                        enabled = !isAdding && !isImportingOpml,
                        modifier = Modifier.testTag("btn_empty_add_feed")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_add_feed))
                    }

                    OutlinedButton(
                        onClick = onImportOpmlClick,
                        enabled = !isAdding && !isImportingOpml,
                        modifier = Modifier.testTag("btn_empty_import_opml")
                    ) {
                        if (isImportingOpml) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import_opml))
                    }
                }
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun AllCaughtUpState(onSync: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("empty_unread_scroll"),
        contentPadding = PaddingValues(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        item(key = "all_caught_up") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.DoneAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.empty_unread_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.empty_unread_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                AssistChip(
                    onClick = onSync,
                    label = { Text(stringResource(R.string.action_sync)) },
                    modifier = Modifier.testTag("chip_empty_sync")
                )
            }
        }
    }
}
