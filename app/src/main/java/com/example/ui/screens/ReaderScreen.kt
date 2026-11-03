package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.ArticleWithFeed
import com.example.data.local.FeedFilterEntity
import com.example.data.model.ReaderAiMode
import com.example.data.model.SummaryProvider
import com.example.data.model.TitleTranslationProvider
import com.example.ui.components.ArticleReaderShimmer
import com.example.ui.components.ArticleRemoteImage
import com.example.ui.components.FeedAvatarIcon
import com.example.ui.components.FeedRegexFilterDialog
import com.example.ui.components.TelegramDraftShimmer
import com.example.ui.theme.IbmPlexArabicFamily
import com.example.ui.theme.PlusJakartaFamily
import com.example.ui.viewmodel.ReaderUiState
import com.example.ui.viewmodel.TelegramPreviewState

private data class SelectionMenuData(
    val rect: Rect,
    val onCopy: (() -> Unit)?,
    val onSelectAll: (() -> Unit)?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    item: ArticleWithFeed?,
    readerState: ReaderUiState,
    summaryProvider: SummaryProvider = SummaryProvider.GEMINI,
    currentTranslator: TitleTranslationProvider = TitleTranslationProvider.DEEPL_WEB,
    canSummarize: Boolean = true,
    hasDeeplKey: Boolean = false,
    hasGeminiKey: Boolean = false,
    hasOllamaKey: Boolean = false,
    feedFilters: List<FeedFilterEntity> = emptyList(),
    telegramEnabled: Boolean,
    telegramPreview: TelegramPreviewState,
    onBack: () -> Unit,
    onSwitchAiMode: (ReaderAiMode, Boolean) -> Unit,
    onSelectTranslator: (TitleTranslationProvider) -> Unit = {},
    onSelectSummaryProvider: (SummaryProvider) -> Unit = {},
    onToggleSummaryProvider: () -> Unit = {},
    onAddFeedFilter: (pattern: String, isRegex: Boolean) -> Unit = { _, _ -> },
    onDeleteFeedFilter: (Long) -> Unit = {},
    onToggleSave: (Boolean) -> Unit,
    onShare: (ArticleWithFeed) -> Unit,
    onOpenTelegramPreview: () -> Unit,
    onDismissTelegramPreview: () -> Unit,
    onRegenerateTelegramDraft: () -> Unit,
    onUpdateTelegramDraft: (String) -> Unit,
    onToggleTelegramPhoto: (Boolean) -> Unit,
    onSendTelegram: (String?) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var showTranslatorSheet by remember { mutableStateOf(false) }
    var showSummaryProviderSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("btn_reader_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            title = {
                if (item != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FeedAvatarIcon(
                            iconUrl = item.feedIconUrl,
                            feedTitle = item.feedTitle,
                            size = 24.dp
                        )
                        Column {
                            Text(
                                text = item.feedTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (readerState.activeModelUsed.isNotBlank()) {
                                Text(
                                    text = readerState.activeModelUsed,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            actions = {
                if (item != null) {
                    if (telegramEnabled) {
                        IconButton(
                            onClick = onOpenTelegramPreview,
                            modifier = Modifier.testTag("btn_reader_telegram")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_telegram),
                                contentDescription = stringResource(R.string.action_telegram),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onToggleSave(!item.article.isSaved) },
                        modifier = Modifier.testTag("btn_reader_save")
                    ) {
                        Icon(
                            imageVector = if (item.article.isSaved) {
                                Icons.Filled.Bookmark
                            } else {
                                Icons.Outlined.BookmarkBorder
                            },
                            contentDescription = stringResource(R.string.action_save),
                            tint = if (item.article.isSaved) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(
                        onClick = { onShare(item) },
                        modifier = Modifier.testTag("btn_reader_share")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(R.string.action_share)
                        )
                    }
                    if (item.article.link.startsWith("http")) {
                        IconButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(item.article.link))
                                    )
                                }
                            },
                            modifier = Modifier.testTag("btn_reader_browser")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = stringResource(R.string.action_open_browser)
                            )
                        }
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            )
        )

        if (item == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val article = item.article
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
            ) {
                // 1. Article Image directly at the top
                if (!article.imageUrl.isNullOrBlank()) {
                    ArticleRemoteImage(
                        imageUrl = article.imageUrl,
                        contentDescription = article.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }

                // 2. Directly below image: NO title! Inline AI Swap button + Shimmer / Clean Body Text
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReaderAiMode.entries.forEach { mode ->
                            val isSelected = readerState.activeMode == mode
                            val chipLabel = when (mode) {
                                ReaderAiMode.NONE -> stringResource(R.string.feed_mode_none)
                                ReaderAiMode.TRANSLATE -> "${stringResource(R.string.feed_mode_translate)} (${currentTranslator.label})"
                                ReaderAiMode.SUMMARIZE -> if (summaryProvider == SummaryProvider.OLLAMA) {
                                    stringResource(R.string.btn_summarize_article_ollama)
                                } else {
                                    stringResource(R.string.feed_mode_summarize)
                                }
                            }
                            val chipIcon = when (mode) {
                                ReaderAiMode.NONE -> Icons.Outlined.RssFeed
                                ReaderAiMode.TRANSLATE -> Icons.Outlined.Translate
                                ReaderAiMode.SUMMARIZE -> Icons.Outlined.AutoAwesome
                            }
                            val canSummarizeWithKey = hasGeminiKey || hasOllamaKey
                            val isSummarizeDisabled = mode == ReaderAiMode.SUMMARIZE && !canSummarizeWithKey
                            val isChipEnabled = !readerState.isLoading && !isSummarizeDisabled

                            val containerColor = when {
                                isSummarizeDisabled -> MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            }
                            val contentColor = when {
                                isSummarizeDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            val borderColor = when {
                                isSummarizeDisabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = containerColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(
                                        enabled = isChipEnabled,
                                        onClick = {
                                            if (mode == ReaderAiMode.TRANSLATE && isSelected) {
                                                onSwitchAiMode(mode, true)
                                            } else if (!isSelected) {
                                                onSwitchAiMode(mode, false)
                                            }
                                        }
                                    )
                                    .testTag("chip_reader_mode_${mode.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        start = 12.dp,
                                        end = if (mode == ReaderAiMode.NONE) 12.dp else 4.dp,
                                        top = 4.dp,
                                        bottom = 4.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.Check else chipIcon,
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = chipLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = contentColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (mode == ReaderAiMode.TRANSLATE) {
                                        IconButton(
                                            onClick = { showTranslatorSheet = true },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("btn_reader_translate_menu")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = stringResource(R.string.dialog_select_translator_title),
                                                tint = contentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else if (mode == ReaderAiMode.SUMMARIZE) {
                                        IconButton(
                                            onClick = { showSummaryProviderSheet = true },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .testTag("btn_reader_summarize_menu")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = stringResource(R.string.dialog_select_summary_provider_title),
                                                tint = contentColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val rawArticleText = readerState.displayedText.ifBlank { article.rawContent }
                    val displayedBodyText = rawArticleText

                    when {
                        readerState.isLoading -> {
                            ArticleReaderShimmer()
                        }

                        readerState.errorMessage != null -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = readerState.errorMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    OutlinedButton(
                                        onClick = { onSwitchAiMode(readerState.activeMode, true) }
                                    ) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.btn_retry))
                                    }
                                }
                            }
                            if (displayedBodyText.isNotBlank()) {
                                SelectionContainer {
                                    ReaderCleanBodyText(text = displayedBodyText)
                                }
                            }
                        }

                        else -> {
                            SelectionContainer {
                                ReaderCleanBodyText(text = displayedBodyText)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

    if (telegramPreview.isVisible && item != null) {
        TelegramPublishBottomSheet(
            imageUrl = item.article.imageUrl,
            state = telegramPreview,
            onDismiss = onDismissTelegramPreview,
            onRegenerate = onRegenerateTelegramDraft,
            onDraftChange = onUpdateTelegramDraft,
            onTogglePhoto = onToggleTelegramPhoto,
            onSend = { onSendTelegram(item.article.imageUrl) }
        )
    }

    if (showTranslatorSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showTranslatorSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.dialog_select_translator_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                androidx.compose.material3.HorizontalDivider()
                val sortedTranslators = remember(hasDeeplKey, hasGeminiKey, hasOllamaKey) {
                    TitleTranslationProvider.entries.sortedBy { provider ->
                        val enabled = when (provider) {
                            TitleTranslationProvider.DEEPL_API -> hasDeeplKey
                            TitleTranslationProvider.GEMINI -> hasGeminiKey
                            TitleTranslationProvider.OLLAMA -> hasOllamaKey
                            else -> true
                        }
                        if (enabled) 0 else 1
                    }
                }
                sortedTranslators.forEach { provider ->
                    val isSelected = currentTranslator == provider
                    val isEnabled = when (provider) {
                        TitleTranslationProvider.DEEPL_API -> hasDeeplKey
                        TitleTranslationProvider.GEMINI -> hasGeminiKey
                        TitleTranslationProvider.OLLAMA -> hasOllamaKey
                        else -> true
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                text = provider.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isEnabled) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        supportingContent = if (provider.requiresApiKey) {
                            {
                                Text(
                                    text = if (!isEnabled) stringResource(R.string.translator_key_required) else "API Key",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!isEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else null,
                        trailingContent = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) {
                                onSelectTranslator(provider)
                                showTranslatorSheet = false
                                onSwitchAiMode(ReaderAiMode.TRANSLATE, true)
                            }
                            .testTag("translator_option_${provider.name}")
                    )
                }
            }
        }
    }

    if (showSummaryProviderSheet) {
        val sortedSummaryProviders = remember(hasGeminiKey, hasOllamaKey) {
            SummaryProvider.entries.sortedBy { provider ->
                val enabled = when (provider) {
                    SummaryProvider.GEMINI -> hasGeminiKey
                    SummaryProvider.OLLAMA -> hasOllamaKey
                }
                if (enabled) 0 else 1
            }
        }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSummaryProviderSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.dialog_select_summary_provider_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                androidx.compose.material3.HorizontalDivider()
                sortedSummaryProviders.forEach { provider ->
                    val isSelected = summaryProvider == provider
                    val isEnabled = when (provider) {
                        SummaryProvider.GEMINI -> hasGeminiKey
                        SummaryProvider.OLLAMA -> hasOllamaKey
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                text = when (provider) {
                                    SummaryProvider.GEMINI -> "Gemini (Google AI)"
                                    SummaryProvider.OLLAMA -> "Ollama (Local AI)"
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isEnabled) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = when (provider) {
                                        SummaryProvider.GEMINI -> "Cloud AI summarizer with structured output"
                                        SummaryProvider.OLLAMA -> "Local network Ollama server"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!isEnabled) {
                                    Text(
                                        text = stringResource(R.string.translator_key_required),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        trailingContent = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isEnabled) {
                                onSelectSummaryProvider(provider)
                                showSummaryProviderSheet = false
                                onSwitchAiMode(ReaderAiMode.SUMMARIZE, true)
                            }
                            .testTag("summary_provider_option_${provider.name}")
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderCleanBodyText(text: String) {
    val hasArabic = text.any { it.code in 0x0600..0x06FF }
    Text(
        text = text,
        style = TextStyle(
            fontFamily = if (hasArabic) IbmPlexArabicFamily else PlusJakartaFamily,
            fontSize = 17.5.sp,
            lineHeight = 30.sp,
            textDirection = TextDirection.Content
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reader_clean_body_text")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelegramPublishBottomSheet(
    imageUrl: String?,
    state: TelegramPreviewState,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit,
    onDraftChange: (String) -> Unit,
    onTogglePhoto: (Boolean) -> Unit,
    onSend: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = { if (!state.isSending) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_telegram),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.telegram_sheet_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (!imageUrl.isNullOrBlank()) {
                if (state.includePhoto) {
                    ArticleRemoteImage(
                        imageUrl = imageUrl,
                        contentDescription = "Telegram preview photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(165.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.telegram_include_photo),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = state.includePhoto,
                        onCheckedChange = onTogglePhoto
                    )
                }
            }

            if (state.isDrafting) {
                TelegramDraftShimmer(modifier = Modifier.padding(vertical = 8.dp))
            } else {
                OutlinedTextField(
                    value = state.draftText,
                    onValueChange = onDraftChange,
                    minLines = 5,
                    maxLines = 10,
                    textStyle = TextStyle(
                        fontFamily = IbmPlexArabicFamily,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        textDirection = TextDirection.Content
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_telegram_preview_text")
                )
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = !state.isDrafting && !state.isSending,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.telegram_regenerate))
                }

                Button(
                    onClick = onSend,
                    enabled = !state.isDrafting && !state.isSending && state.draftText.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_confirm_send_telegram")
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_telegram),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.telegram_send))
                }
            }
        }
    }
}
