package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ArticleWithFeed
import com.example.data.local.FeedWithCount
import com.example.data.model.FeedLayoutMode
import com.example.ui.components.AdaptiveArticlesStream
import com.example.ui.components.SeamlessSearchTopBar
import com.example.ui.viewmodel.SearchFilterState

@Composable
fun SearchScreen(
    searchState: SearchFilterState,
    results: List<ArticleWithFeed>,
    feeds: List<FeedWithCount> = emptyList(),
    layoutMode: FeedLayoutMode,
    translatingIds: Set<Long>,
    showTranslateButton: Boolean = true,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectAllFilter: () -> Unit,
    onToggleUnreadFilter: () -> Unit,
    onToggleReadFilter: () -> Unit,
    onToggleSavedFilter: () -> Unit,
    onToggleFeedSelection: (Long) -> Unit = {},
    onToggleFeedExclusion: (Long) -> Unit = {},
    onClearFeedFilters: () -> Unit = {},
    onOpenArticle: (Long) -> Unit,
    onTranslateTitle: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    onToggleSave: (ArticleWithFeed) -> Unit,
    onShare: (ArticleWithFeed) -> Unit
) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        SeamlessSearchTopBar(
            query = searchState.query,
            onQueryChange = onQueryChange,
            onClose = onBack,
            inputTestTag = "input_search_query",
            backButtonTestTag = "btn_search_back",
            clearButtonTestTag = "btn_clear_search"
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = searchState.isAllSelected,
                        onClick = onSelectAllFilter,
                        label = { Text(stringResource(R.string.filter_all)) },
                        leadingIcon = if (searchState.isAllSelected) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        modifier = Modifier.testTag("search_filter_all")
                    )
                }
                item {
                    FilterChip(
                        selected = searchState.filterUnread,
                        onClick = onToggleUnreadFilter,
                        label = { Text(stringResource(R.string.filter_unread)) },
                        leadingIcon = if (searchState.filterUnread) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        modifier = Modifier.testTag("search_filter_unread")
                    )
                }
                item {
                    FilterChip(
                        selected = searchState.filterRead,
                        onClick = onToggleReadFilter,
                        label = { Text(stringResource(R.string.filter_read)) },
                        leadingIcon = if (searchState.filterRead) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        modifier = Modifier.testTag("search_filter_read")
                    )
                }
                item {
                    FilterChip(
                        selected = searchState.filterSaved,
                        onClick = onToggleSavedFilter,
                        label = { Text(stringResource(R.string.filter_saved)) },
                        leadingIcon = if (searchState.filterSaved) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        modifier = Modifier.testTag("search_filter_saved")
                    )
                }
            }

            if (feeds.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (searchState.selectedFeedIds.isNotEmpty() || searchState.excludedFeedIds.isNotEmpty()) {
                        item(key = "clear_feed_filters") {
                            FilterChip(
                                selected = false,
                                onClick = onClearFeedFilters,
                                label = { Text("Clear feed filters") },
                                leadingIcon = { Icon(Icons.Outlined.Close, contentDescription = null) },
                                modifier = Modifier.testTag("btn_clear_feed_search_filters")
                            )
                        }
                    }
                    items(feeds.size, key = { feeds[it].feed.id }) { index ->
                        val feed = feeds[index].feed
                        val isSelected = feed.id in searchState.selectedFeedIds
                        val isExcluded = feed.id in searchState.excludedFeedIds

                        FilterChip(
                            selected = isSelected || isExcluded,
                            onClick = {
                                if (isSelected) {
                                    onToggleFeedExclusion(feed.id)
                                } else if (isExcluded) {
                                    onToggleFeedExclusion(feed.id)
                                } else {
                                    onToggleFeedSelection(feed.id)
                                }
                            },
                            label = {
                                Text(
                                    text = if (isExcluded) "Excl: ${feed.title}" else feed.title,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else if (isExcluded) {
                                { Icon(Icons.Outlined.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            } else null,
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (isExcluded) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = if (isExcluded) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("search_feed_filter_${feed.id}")
                        )
                    }
                }
            }

            if (results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.empty_search_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                AdaptiveArticlesStream(
                    articles = results,
                    layoutMode = layoutMode,
                    translatingIds = translatingIds,
                    onOpenArticle = onOpenArticle,
                    onTranslateTitle = onTranslateTitle,
                    onToggleRead = onToggleRead,
                    onSearchRelated = { post ->
                        val seed = post.article.title.split(" ")
                            .firstOrNull { w -> w.length > 4 }
                            .orEmpty()
                        if (seed.isNotEmpty()) onQueryChange(seed)
                    },
                    onToggleSave = onToggleSave,
                    onShare = onShare,
                    showTranslateButton = showTranslateButton,
                    bottomContentPadding = 24.dp
                )
            }
        }
    }
}
