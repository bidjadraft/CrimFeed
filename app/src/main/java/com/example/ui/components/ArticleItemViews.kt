package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.R
import com.example.data.local.ArticleWithFeed
import com.example.data.model.FeedLayoutMode
import com.example.ui.theme.IbmPlexArabicFamily
import com.example.ui.theme.PlusJakartaFamily

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun RefreshFeedsIconButton(
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    testTag: String = "btn_refresh_feeds",
    isSyncing: Boolean = isRefreshing
) {
    val activeSpinning = isRefreshing || isSyncing
    IconButton(
        onClick = onRefresh,
        enabled = enabled,
        modifier = Modifier.testTag(testTag)
    ) {
        key(activeSpinning) {
            if (activeSpinning) {
                val infiniteTransition = rememberInfiniteTransition(label = "refresh_icon_spin")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "refresh_icon_angle"
                )
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.action_refresh_feeds),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .graphicsLayer { rotationZ = rotation }
                        .testTag("${testTag}_spinning")
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.action_refresh_feeds),
                    tint = LocalContentColor.current,
                    modifier = Modifier.testTag("${testTag}_idle")
                )
            }
        }
    }
}

@Composable
fun LayoutModeIconButton(
    layoutMode: FeedLayoutMode,
    onCycleLayout: () -> Unit,
    testTag: String = "btn_cycle_layout"
) {
    IconButton(
        onClick = onCycleLayout,
        modifier = Modifier.testTag(testTag)
    ) {
        val icon = when (layoutMode) {
            FeedLayoutMode.CARD -> Icons.Outlined.ViewAgenda
            FeedLayoutMode.LIST -> Icons.AutoMirrored.Outlined.ViewList
            FeedLayoutMode.THUMBNAIL_GRID -> Icons.Outlined.GridView
            FeedLayoutMode.MAGAZINE -> Icons.AutoMirrored.Outlined.ViewQuilt
        }
        Icon(icon, contentDescription = stringResource(R.string.settings_layout))
    }
}

@OptIn(FlowPreview::class)
@Composable
fun AdaptiveArticlesStream(
    articles: List<ArticleWithFeed>,
    layoutMode: FeedLayoutMode,
    translatingIds: Set<Long>,
    onOpenArticle: (Long) -> Unit,
    onTranslateTitle: (ArticleWithFeed) -> Unit,
    onToggleRead: (ArticleWithFeed) -> Unit,
    onSearchRelated: (ArticleWithFeed) -> Unit,
    onToggleSave: (ArticleWithFeed) -> Unit,
    onShare: (ArticleWithFeed) -> Unit,
    showTranslateButton: Boolean = true,
    dimReadArticles: Boolean = false,
    enableNewPostsPill: Boolean = false,
    streamKey: String = "default",
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollPositionChanged: ((Int, Int) -> Unit)? = null,
    bottomContentPadding: Dp = 88.dp,
    modifier: Modifier = Modifier
) {
    val safeStartIndex = if (articles.isEmpty()) 0 else initialScrollIndex.coerceIn(0, articles.lastIndex)
    val safeStartOffset = if (articles.isEmpty()) 0 else initialScrollOffset.coerceAtLeast(0)

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = safeStartIndex,
        initialFirstVisibleItemScrollOffset = safeStartOffset
    )
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = safeStartIndex,
        initialFirstVisibleItemScrollOffset = safeStartOffset
    )

    val coroutineScope = rememberCoroutineScope()
    val latestArticles by rememberUpdatedState(articles)

    var activeStreamKey by remember { mutableStateOf(streamKey) }
    var baselineUnreadIds by remember {
        mutableStateOf(
            articles.asSequence().filter { !it.article.isRead }.map { it.article.id }.toSet()
        )
    }
    var userScrolledAwayFromTop by remember {
        mutableStateOf(safeStartIndex > 0 || safeStartOffset > 120)
    }

    if (activeStreamKey != streamKey) {
        activeStreamKey = streamKey
        baselineUnreadIds = articles.asSequence().filter { !it.article.isRead }.map { it.article.id }.toSet()
        userScrolledAwayFromTop = false
    }

    val currentFirstIndex by remember(layoutMode) {
        derivedStateOf {
            if (layoutMode == FeedLayoutMode.THUMBNAIL_GRID) gridState.firstVisibleItemIndex
            else listState.firstVisibleItemIndex
        }
    }
    val currentScrollOffset by remember(layoutMode) {
        derivedStateOf {
            if (layoutMode == FeedLayoutMode.THUMBNAIL_GRID) gridState.firstVisibleItemScrollOffset
            else listState.firstVisibleItemScrollOffset
        }
    }
    val isScrolledDown by remember {
        derivedStateOf {
            currentFirstIndex > 0 || currentScrollOffset > 120
        }
    }

    LaunchedEffect(listState, gridState, layoutMode, streamKey) {
        snapshotFlow {
            val inProgress = if (layoutMode == FeedLayoutMode.THUMBNAIL_GRID) {
                gridState.isScrollInProgress
            } else {
                listState.isScrollInProgress
            }
            Triple(currentFirstIndex, currentScrollOffset, inProgress)
        }.collect { (idx, off, inProgress) ->
            val down = idx > 0 || off > 120
            if (!down) {
                userScrolledAwayFromTop = false
                val currentUnread = latestArticles.asSequence()
                    .filter { !it.article.isRead }
                    .map { it.article.id }
                    .toSet()
                if (!baselineUnreadIds.containsAll(currentUnread)) {
                    baselineUnreadIds = baselineUnreadIds + currentUnread
                }
            } else if (inProgress || latestArticles.none { !it.article.isRead && it.article.id !in baselineUnreadIds }) {
                userScrolledAwayFromTop = true
            }
        }
    }

    val topUnreadArticleId = articles.firstOrNull { !it.article.isRead }?.article?.id
    var previousTopUnreadArticleId by remember { mutableStateOf(topUnreadArticleId) }

    LaunchedEffect(topUnreadArticleId, articles.size, layoutMode) {
        val oldTopId = previousTopUnreadArticleId
        if (!userScrolledAwayFromTop) {
            val currentUnread = articles.asSequence()
                .filter { !it.article.isRead }
                .map { it.article.id }
                .toSet()
            if (!baselineUnreadIds.containsAll(currentUnread)) {
                baselineUnreadIds = baselineUnreadIds + currentUnread
            }
            if (topUnreadArticleId != null && oldTopId != null && topUnreadArticleId != oldTopId) {
                val movedIndex = articles.indexOfFirst { !it.article.isRead && it.article.id == oldTopId }
                if (movedIndex > 0) {
                    if (layoutMode == FeedLayoutMode.THUMBNAIL_GRID) {
                        if (!gridState.isScrollInProgress) {
                            gridState.scrollToItem(0, 0)
                            onScrollPositionChanged?.invoke(0, 0)
                        }
                    } else {
                        if (!listState.isScrollInProgress) {
                            listState.scrollToItem(0, 0)
                            onScrollPositionChanged?.invoke(0, 0)
                        }
                    }
                }
            }
        }
        previousTopUnreadArticleId = topUnreadArticleId
    }

    val newPostsAboveCount = if (enableNewPostsPill && userScrolledAwayFromTop && isScrolledDown) {
        val upperBound = (currentFirstIndex + 1).coerceAtMost(articles.size)
        articles.subList(0, upperBound).count { item ->
            !item.article.isRead && item.article.id !in baselineUnreadIds
        }
    } else {
        0
    }

    if (onScrollPositionChanged != null) {
        LaunchedEffect(listState, layoutMode) {
            if (layoutMode != FeedLayoutMode.THUMBNAIL_GRID) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .debounce(250L)
                    .collect { (idx, off) ->
                        onScrollPositionChanged(idx, off)
                    }
            }
        }
        LaunchedEffect(gridState, layoutMode) {
            if (layoutMode == FeedLayoutMode.THUMBNAIL_GRID) {
                snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                    .distinctUntilChanged()
                    .debounce(250L)
                    .collect { (idx, off) ->
                        onScrollPositionChanged(idx, off)
                    }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (layoutMode) {
            FeedLayoutMode.THUMBNAIL_GRID -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("articles_grid"),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(articles, key = { "${it.article.isRead}_${it.article.id}" }) { item ->
                    ThumbnailGridArticleItem(
                        item = item,
                        isTranslating = item.article.id in translatingIds,
                        isDimmed = dimReadArticles && item.article.isRead,
                        showTranslateButton = showTranslateButton,
                        onOpen = { onOpenArticle(item.article.id) },
                        onTranslate = { onTranslateTitle(item) },
                        onToggleRead = { onToggleRead(item) },
                        onSearchRelated = { onSearchRelated(item) },
                        onToggleSave = { onToggleSave(item) },
                        onShare = { onShare(item) }
                    )
                }
            }
        }

        FeedLayoutMode.CARD -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("articles_card_list"),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(articles, key = { "${it.article.isRead}_${it.article.id}" }) { item ->
                    CardArticleItem(
                        item = item,
                        isTranslating = item.article.id in translatingIds,
                        isDimmed = dimReadArticles && item.article.isRead,
                        showTranslateButton = showTranslateButton,
                        onOpen = { onOpenArticle(item.article.id) },
                        onTranslate = { onTranslateTitle(item) },
                        onToggleRead = { onToggleRead(item) },
                        onSearchRelated = { onSearchRelated(item) },
                        onToggleSave = { onToggleSave(item) },
                        onShare = { onShare(item) }
                    )
                }
            }
        }

        FeedLayoutMode.LIST -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("articles_compact_list"),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(articles, key = { "${it.article.isRead}_${it.article.id}" }) { item ->
                    CompactListArticleItem(
                        item = item,
                        isTranslating = item.article.id in translatingIds,
                        isDimmed = dimReadArticles && item.article.isRead,
                        showTranslateButton = showTranslateButton,
                        onOpen = { onOpenArticle(item.article.id) },
                        onTranslate = { onTranslateTitle(item) },
                        onToggleRead = { onToggleRead(item) },
                        onSearchRelated = { onSearchRelated(item) },
                        onToggleSave = { onToggleSave(item) },
                        onShare = { onShare(item) }
                    )
                }
            }
        }

        FeedLayoutMode.MAGAZINE -> {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("articles_magazine_list"),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = bottomContentPadding
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(articles, key = { _, it -> "${it.article.isRead}_${it.article.id}" }) { index, item ->
                    val isDimmed = dimReadArticles && item.article.isRead
                    if (index == 0) {
                        MagazineHeroArticleItem(
                            item = item,
                            isTranslating = item.article.id in translatingIds,
                            isDimmed = isDimmed,
                            showTranslateButton = showTranslateButton,
                            onOpen = { onOpenArticle(item.article.id) },
                            onTranslate = { onTranslateTitle(item) },
                            onToggleRead = { onToggleRead(item) },
                            onSearchRelated = { onSearchRelated(item) },
                            onToggleSave = { onToggleSave(item) },
                            onShare = { onShare(item) }
                        )
                    } else {
                        CompactListArticleItem(
                            item = item,
                            isTranslating = item.article.id in translatingIds,
                            isDimmed = isDimmed,
                            showTranslateButton = showTranslateButton,
                            onOpen = { onOpenArticle(item.article.id) },
                            onTranslate = { onTranslateTitle(item) },
                            onToggleRead = { onToggleRead(item) },
                            onSearchRelated = { onSearchRelated(item) },
                            onToggleSave = { onToggleSave(item) },
                            onShare = { onShare(item) }
                        )
                    }
                }
            }
        }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = newPostsAboveCount > 0,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
        ) {
            val pillLabel = if (newPostsAboveCount == 1) {
                stringResource(R.string.new_posts_pill_single)
            } else {
                stringResource(R.string.new_posts_pill_plural, newPostsAboveCount)
            }
            Surface(
                onClick = {
                    userScrolledAwayFromTop = false
                    baselineUnreadIds = baselineUnreadIds + latestArticles.asSequence()
                        .filter { !it.article.isRead }
                        .map { it.article.id }
                        .toSet()
                    coroutineScope.launch {
                        if (layoutMode == FeedLayoutMode.THUMBNAIL_GRID) {
                            if (gridState.firstVisibleItemIndex > 10) {
                                gridState.scrollToItem(6, 0)
                            }
                            gridState.animateScrollToItem(0, 0)
                        } else {
                            if (listState.firstVisibleItemIndex > 10) {
                                listState.scrollToItem(6, 0)
                            }
                            listState.animateScrollToItem(0, 0)
                        }
                        onScrollPositionChanged?.invoke(0, 0)
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 6.dp,
                tonalElevation = 4.dp,
                modifier = Modifier.testTag("pill_new_posts")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = pillLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun CardArticleItem(
    item: ArticleWithFeed,
    isTranslating: Boolean,
    isDimmed: Boolean = false,
    showTranslateButton: Boolean = true,
    onOpen: () -> Unit,
    onTranslate: () -> Unit,
    onToggleRead: () -> Unit,
    onSearchRelated: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit
) {
    val article = item.article
    val displayTitle = resolvedHeadline(item)

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isDimmed) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDimmed) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("article_card_${article.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!article.imageUrl.isNullOrBlank()) {
                ArticleRemoteImage(
                    imageUrl = article.imageUrl,
                    contentDescription = article.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .alpha(if (isDimmed) 0.45f else 1f)
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeedSourceHeaderRow(item = item, isDimmed = isDimmed)

                HeadlineText(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    isDimmed = isDimmed
                )

                PostActionBar(
                    articleId = article.id,
                    showTranslateButton = showTranslateButton,
                    isTranslatedActive = article.showTranslatedTitle && !article.translatedTitle.isNullOrBlank(),
                    isTranslating = isTranslating,
                    isRead = article.isRead,
                    isSaved = article.isSaved,
                    onTranslate = onTranslate,
                    onToggleRead = onToggleRead,
                    onSearchRelated = onSearchRelated,
                    onToggleSave = onToggleSave,
                    onShare = onShare
                )
            }
        }
    }
}

@Composable
fun CompactListArticleItem(
    item: ArticleWithFeed,
    isTranslating: Boolean,
    isDimmed: Boolean = false,
    showTranslateButton: Boolean = true,
    onOpen: () -> Unit,
    onTranslate: () -> Unit,
    onToggleRead: () -> Unit,
    onSearchRelated: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit
) {
    val article = item.article
    val displayTitle = resolvedHeadline(item)

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isDimmed) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("article_list_${article.id}")
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FeedSourceHeaderRow(item = item, isDimmed = isDimmed)
                    HeadlineText(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 3,
                        isDimmed = isDimmed
                    )
                }

                if (!article.imageUrl.isNullOrBlank()) {
                    ArticleRemoteImage(
                        imageUrl = article.imageUrl,
                        contentDescription = article.title,
                        modifier = Modifier
                            .size(86.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .alpha(if (isDimmed) 0.45f else 1f)
                    )
                }
            }

            PostActionBar(
                articleId = article.id,
                showTranslateButton = showTranslateButton,
                isTranslatedActive = article.showTranslatedTitle && !article.translatedTitle.isNullOrBlank(),
                isTranslating = isTranslating,
                isRead = article.isRead,
                isSaved = article.isSaved,
                onTranslate = onTranslate,
                onToggleRead = onToggleRead,
                onSearchRelated = onSearchRelated,
                onToggleSave = onToggleSave,
                onShare = onShare
            )
        }
    }
}

@Composable
fun ThumbnailGridArticleItem(
    item: ArticleWithFeed,
    isTranslating: Boolean,
    isDimmed: Boolean = false,
    showTranslateButton: Boolean = true,
    onOpen: () -> Unit,
    onTranslate: () -> Unit,
    onToggleRead: () -> Unit,
    onSearchRelated: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit
) {
    val article = item.article
    val displayTitle = resolvedHeadline(item)

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isDimmed) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("article_grid_${article.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!article.imageUrl.isNullOrBlank()) {
                ArticleRemoteImage(
                    imageUrl = article.imageUrl,
                    contentDescription = article.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .alpha(if (isDimmed) 0.45f else 1f)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .alpha(if (isDimmed) 0.45f else 1f)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    FeedAvatarIcon(
                        iconUrl = item.feedIconUrl,
                        feedTitle = item.feedTitle,
                        size = 36.dp
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FeedSourceHeaderRow(item = item, compact = true, isDimmed = isDimmed)
                HeadlineText(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    isDimmed = isDimmed
                )
                PostActionBar(
                    articleId = article.id,
                    showTranslateButton = showTranslateButton,
                    isTranslatedActive = article.showTranslatedTitle && !article.translatedTitle.isNullOrBlank(),
                    isTranslating = isTranslating,
                    isRead = article.isRead,
                    isSaved = article.isSaved,
                    onTranslate = onTranslate,
                    onToggleRead = onToggleRead,
                    onSearchRelated = onSearchRelated,
                    onToggleSave = onToggleSave,
                    onShare = onShare,
                    compact = true
                )
            }
        }
    }
}

@Composable
fun MagazineHeroArticleItem(
    item: ArticleWithFeed,
    isTranslating: Boolean,
    isDimmed: Boolean = false,
    showTranslateButton: Boolean = true,
    onOpen: () -> Unit,
    onTranslate: () -> Unit,
    onToggleRead: () -> Unit,
    onSearchRelated: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit
) {
    val article = item.article
    val displayTitle = resolvedHeadline(item)

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isDimmed) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.52f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDimmed) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("article_hero_${article.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
                    .alpha(if (isDimmed) 0.52f else 1f)
            ) {
                if (!article.imageUrl.isNullOrBlank()) {
                    ArticleRemoteImage(
                        imageUrl = article.imageUrl,
                        contentDescription = article.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.78f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeedAvatarIcon(iconUrl = item.feedIconUrl, feedTitle = item.feedTitle, size = 20.dp)
                        Text(
                            text = item.feedTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = if (isDimmed) 0.65f else 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HeadlineText(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White.copy(alpha = if (isDimmed) 0.68f else 1f)
                        ),
                        maxLines = 3,
                        isDimmed = isDimmed,
                        overrideColor = Color.White.copy(alpha = if (isDimmed) 0.68f else 1f)
                    )
                }
            }
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                PostActionBar(
                    articleId = article.id,
                    showTranslateButton = showTranslateButton,
                    isTranslatedActive = article.showTranslatedTitle && !article.translatedTitle.isNullOrBlank(),
                    isTranslating = isTranslating,
                    isRead = article.isRead,
                    isSaved = article.isSaved,
                    onTranslate = onTranslate,
                    onToggleRead = onToggleRead,
                    onSearchRelated = onSearchRelated,
                    onToggleSave = onToggleSave,
                    onShare = onShare
                )
            }
        }
    }
}

@Composable
fun FeedSourceHeaderRow(
    item: ArticleWithFeed,
    compact: Boolean = false,
    isDimmed: Boolean = false
) {
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        item.article.publishedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_ALL
    ).toString()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDimmed) 0.58f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FeedAvatarIcon(
            iconUrl = item.feedIconUrl,
            feedTitle = item.feedTitle,
            size = if (compact) 18.dp else 20.dp
        )
        Text(
            text = item.feedTitle,
            style = MaterialTheme.typography.labelMedium,
            color = if (isDimmed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (!compact && item.feedGroupName.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDimmed) 0.4f else 0.7f)
            ) {
                Text(
                    text = item.feedGroupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = relativeTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun FeedAvatarIcon(
    iconUrl: String? = null,
    feedTitle: String,
    size: Dp = 20.dp
) {
    val initial = feedTitle.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "R"
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(iconUrl)
                        .memoryCacheKey("icon:$iconUrl")
                        .diskCacheKey("icon:$iconUrl")
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .size(96, 96)
                        .precision(Precision.INEXACT)
                        .crossfade(true)
                        .build(),
                    contentDescription = feedTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
    }
}

@Composable
fun ArticleRemoteImage(
    imageUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .memoryCacheKey(imageUrl)
            .diskCacheKey(imageUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .precision(Precision.INEXACT)
            .allowHardware(true)
            .crossfade(false)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

@Composable
fun PostActionBar(
    articleId: Long,
    showTranslateButton: Boolean = true,
    isTranslatedActive: Boolean,
    isTranslating: Boolean,
    isRead: Boolean,
    isSaved: Boolean,
    onTranslate: () -> Unit,
    onToggleRead: () -> Unit,
    onSearchRelated: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (compact) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Translate Headline Button (shown only when Translation is Active)
        if (showTranslateButton) {
            IconButton(
                onClick = onTranslate,
                modifier = Modifier.testTag("btn_translate_$articleId")
            ) {
                if (isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = stringResource(R.string.action_translate_title),
                        tint = if (isTranslatedActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Mark as Read Button (right next to Translate)
        IconButton(
            onClick = onToggleRead,
            modifier = Modifier.testTag("btn_mark_read_$articleId")
        ) {
            Icon(
                imageVector = if (isRead) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                contentDescription = if (isRead) {
                    stringResource(R.string.action_mark_unread)
                } else {
                    stringResource(R.string.action_mark_read)
                },
                tint = if (isRead) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }

        // 3. Search Related Button
        if (!compact) {
            IconButton(
                onClick = onSearchRelated,
                modifier = Modifier.testTag("btn_search_post_$articleId")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.action_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 4. Save / Bookmark Button
        IconButton(
            onClick = onToggleSave,
            modifier = Modifier.testTag("btn_save_$articleId")
        ) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (isSaved) {
                    stringResource(R.string.action_unsave)
                } else {
                    stringResource(R.string.action_save)
                },
                tint = if (isSaved) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
        }

        // 5. Share Button
        IconButton(
            onClick = onShare,
            modifier = Modifier.testTag("btn_share_$articleId")
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = stringResource(R.string.action_share),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
fun HeadlineText(
    text: String,
    style: TextStyle,
    maxLines: Int = 3,
    isDimmed: Boolean = false,
    overrideColor: Color? = null
) {
    val containsArabic = text.any { it.code in 0x0600..0x06FF }
    val resolvedColor = overrideColor ?: if (isDimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        style = style.copy(
            fontFamily = if (containsArabic) IbmPlexArabicFamily else PlusJakartaFamily,
            fontWeight = if (isDimmed) FontWeight.Normal else FontWeight.SemiBold,
            textDirection = TextDirection.Content
        ),
        color = resolvedColor,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun resolvedHeadline(item: ArticleWithFeed): String {
    val a = item.article
    return if (a.showTranslatedTitle && !a.translatedTitle.isNullOrBlank()) {
        a.translatedTitle
    } else {
        a.title
    }
}
