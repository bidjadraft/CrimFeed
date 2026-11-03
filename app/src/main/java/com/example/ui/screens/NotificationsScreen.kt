package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
fun NotificationsScreen(
    articles: List<ArticleWithFeed>,
    layoutMode: FeedLayoutMode,
    translatingIds: Set<Long>,
    onBack: () -> Unit,
    onCycleLayout: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onMarkAllRead: () -> Unit,
    onOpenArticle: (Long) -> Unit,
    onTranslateTitle: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    onToggleSave: (ArticleWithFeed) -> Unit,
    onShare: (ArticleWithFeed) -> Unit,
    onSearchRelated: (ArticleWithFeed) -> Unit = {}
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
                inputTestTag = "input_notification_search",
                backButtonTestTag = "btn_notification_search_back",
                clearButtonTestTag = "btn_clear_notification_search",
                autoFocusKey = isSearchActive
            )
        } else {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("btn_notifications_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.btn_close)
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.title_notifications),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Exact order: Mark Read (مكان البحث), Layout (الشكل), Search (مكان مقروء)
                    val hasUnread = articles.any { !it.article.isRead }
                    IconButton(
                        onClick = onMarkAllRead,
                        enabled = hasUnread,
                        modifier = Modifier.testTag("btn_notifications_mark_all_read")
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
                        testTag = "btn_notifications_cycle_layout"
                    )
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.testTag("btn_notifications_search")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.action_search)
                        )
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
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.empty_notifications_title),
                subtitle = stringResource(R.string.empty_notifications_subtitle)
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
                bottomContentPadding = 24.dp
            )
        }
    }
}
