package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ArticleWithFeed
import com.example.data.model.FeedLayoutMode
import com.example.ui.components.AdaptiveArticlesStream
import com.example.ui.components.LayoutModeIconButton
import com.example.ui.components.SeamlessSearchTopBar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedArticlesScreen(
    articles: List<ArticleWithFeed>,
    layoutMode: FeedLayoutMode,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    translatingIds: Set<Long>,
    showTranslateButton: Boolean = true,
    notifiedUnreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    onMarkAllRead: (() -> Unit)? = null,
    onScrollPositionChanged: (Int, Int) -> Unit,
    onCycleLayout: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenArticle: (Long) -> Unit,
    onTranslateTitle: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    onSearchRelated: (ArticleWithFeed) -> Unit,
    onToggleSave: (ArticleWithFeed) -> Unit,
    onShare: (ArticleWithFeed) -> Unit
) {
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    val filteredArticles = remember(articles, searchQuery) {
        if (searchQuery.isBlank()) {
            articles
        } else {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            articles.filter { item ->
                item.article.title.lowercase(Locale.getDefault()).contains(q) ||
                    item.article.translatedTitle.orEmpty().lowercase(Locale.getDefault()).contains(q) ||
                    item.article.rawContent.lowercase(Locale.getDefault()).contains(q) ||
                    item.feedTitle.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSearchActive) {
            SeamlessSearchTopBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    isSearchActive = false
                    searchQuery = ""
                },
                inputTestTag = "input_saved_search",
                backButtonTestTag = "btn_saved_search_back",
                clearButtonTestTag = "btn_clear_saved_search",
                autoFocusKey = isSearchActive
            )
        } else {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.nav_saved),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Exact order matching Home and Read: Mark All Read, Layout, Search, Notifications
                    val hasUnread = articles.any { !it.article.isRead }
                    IconButton(
                        onClick = { onMarkAllRead?.invoke() },
                        enabled = hasUnread && onMarkAllRead != null,
                        modifier = Modifier.testTag("btn_saved_mark_all_read")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DoneAll,
                            contentDescription = stringResource(R.string.feed_action_mark_all_read),
                            tint = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                    LayoutModeIconButton(
                        layoutMode = layoutMode,
                        onCycleLayout = onCycleLayout,
                        testTag = "btn_saved_cycle_layout"
                    )
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.testTag("btn_saved_search")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.action_search)
                        )
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("btn_saved_notifications")
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
        }
        if (articles.isEmpty()) {
            SimpleCollectionEmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = stringResource(R.string.empty_saved_title),
                subtitle = stringResource(R.string.empty_saved_subtitle)
            )
        } else if (filteredArticles.isEmpty()) {
            SimpleCollectionEmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.empty_search_results),
                subtitle = stringResource(R.string.search_placeholder)
            )
        } else {
            AdaptiveArticlesStream(
                articles = filteredArticles,
                layoutMode = layoutMode,
                translatingIds = translatingIds,
                onOpenArticle = onOpenArticle,
                onTranslateTitle = onTranslateTitle,
                onToggleRead = onToggleRead,
                onSearchRelated = onSearchRelated,
                onToggleSave = onToggleSave,
                onShare = onShare,
                showTranslateButton = showTranslateButton,
                initialScrollIndex = initialScrollIndex,
                initialScrollOffset = initialScrollOffset,
                onScrollPositionChanged = onScrollPositionChanged,
                bottomContentPadding = 24.dp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadHistoryScreen(
    articles: List<ArticleWithFeed>,
    layoutMode: FeedLayoutMode,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    translatingIds: Set<Long>,
    showTranslateButton: Boolean = true,
    notifiedUnreadCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
    onScrollPositionChanged: (Int, Int) -> Unit,
    onCycleLayout: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onClearHistory: () -> Unit,
    onOpenArticle: (Long) -> Unit,
    onTranslateTitle: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    onSearchRelated: (ArticleWithFeed) -> Unit,
    onToggleSave: (ArticleWithFeed) -> Unit,
    onShare: (ArticleWithFeed) -> Unit
) {
    var showConfirmClearDialog by remember { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    val filteredArticles = remember(articles, searchQuery) {
        if (searchQuery.isBlank()) {
            articles
        } else {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            articles.filter { item ->
                item.article.title.lowercase(Locale.getDefault()).contains(q) ||
                    item.article.translatedTitle.orEmpty().lowercase(Locale.getDefault()).contains(q) ||
                    item.article.rawContent.lowercase(Locale.getDefault()).contains(q) ||
                    item.feedTitle.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSearchActive) {
            SeamlessSearchTopBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    isSearchActive = false
                    searchQuery = ""
                },
                inputTestTag = "input_read_search",
                backButtonTestTag = "btn_read_search_back",
                clearButtonTestTag = "btn_clear_read_search",
                autoFocusKey = isSearchActive
            )
        } else {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.nav_read),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Exact order: Clear Read (مكان البحث مع العداد), Layout (الشكل), Search (مكان التنظيف), Notifications
                    IconButton(
                        onClick = { showConfirmClearDialog = true },
                        enabled = articles.isNotEmpty(),
                        modifier = Modifier.testTag("btn_clear_read_history")
                    ) {
                        BadgedBox(
                            badge = {
                                if (articles.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(
                                            text = if (articles.size > 99) "99+" else "${articles.size}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.action_clear_read),
                                tint = if (articles.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                    }
                    LayoutModeIconButton(
                        layoutMode = layoutMode,
                        onCycleLayout = onCycleLayout,
                        testTag = "btn_read_cycle_layout"
                    )
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.testTag("btn_read_search")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.action_search)
                        )
                    }
                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier.testTag("btn_read_notifications")
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
        }
        if (articles.isEmpty()) {
            SimpleCollectionEmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.empty_read_title),
                subtitle = stringResource(R.string.empty_read_subtitle)
            )
        } else if (filteredArticles.isEmpty()) {
            SimpleCollectionEmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.empty_search_results),
                subtitle = stringResource(R.string.search_placeholder)
            )
        } else {
            AdaptiveArticlesStream(
                articles = filteredArticles,
                layoutMode = layoutMode,
                translatingIds = translatingIds,
                onOpenArticle = onOpenArticle,
                onTranslateTitle = onTranslateTitle,
                onToggleRead = onToggleRead,
                onSearchRelated = onSearchRelated,
                onToggleSave = onToggleSave,
                onShare = onShare,
                showTranslateButton = showTranslateButton,
                initialScrollIndex = initialScrollIndex,
                initialScrollOffset = initialScrollOffset,
                onScrollPositionChanged = onScrollPositionChanged,
                bottomContentPadding = 24.dp
            )
        }
    }

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_read_title)) },
            text = { Text(stringResource(R.string.dialog_clear_read_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClearDialog = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("btn_confirm_clear_read")
                ) {
                    Text(stringResource(R.string.btn_clear))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmClearDialog = false },
                    modifier = Modifier.testTag("btn_cancel_clear_read")
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
internal fun SimpleCollectionEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(46.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
