package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedWithCount
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.BackupSelection
import com.example.data.model.KeyVerificationState
import com.example.data.model.PendingImportPreview
import com.example.data.model.ReaderAiMode
import com.example.data.model.SummaryProvider
import com.example.data.model.TargetLanguage
import com.example.data.model.TitleTranslationProvider
import com.example.ui.screens.settings.AboutSettingsPage
import com.example.ui.screens.settings.ApiKeysSettingsPage
import com.example.ui.screens.settings.AppearanceSettingsPage
import com.example.ui.screens.settings.BackupRestoreSettingsPage
import com.example.ui.screens.settings.FeedsAndListsSettingsPage
import com.example.ui.screens.settings.IntegrationSettingsPage
import com.example.ui.screens.settings.SettingsHubCard
import com.example.ui.screens.settings.TranslateSettingsPage

enum class SettingsSubPage {
    HUB,
    APPEARANCE,
    FEEDS_LISTS,
    API_KEYS,
    TRANSLATE,
    INTEGRATION,
    BACKUP_RESTORE,
    ABOUT
}

fun crimsonPageEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = 150))

fun crimsonPageExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(durationMillis = 150))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    feeds: List<FeedWithCount>,
    customLists: List<CustomListEntity>,
    backupSelection: BackupSelection,
    ollamaKeyStatus: KeyVerificationState,
    geminiKeyStatus: KeyVerificationState,
    deeplKeyStatus: KeyVerificationState,
    isRefreshingModels: Boolean,
    isRefreshingOllamaModels: Boolean,
    isImportingBackup: Boolean,
    hasPendingImportBackup: Boolean = false,
    pendingImportPreview: PendingImportPreview? = null,
    externalActivePage: SettingsSubPage? = null,
    onExternalActivePageChange: ((SettingsSubPage) -> Unit)? = null,
    resetToHubSignal: Int = 0,
    onUpdateBackupSelection: (BackupSelection) -> Unit,
    onExportBackupClick: () -> Unit,
    onImportBackupClick: () -> Unit,
    onConfirmPendingImport: (BackupSelection) -> Unit = {},
    onConfirmPendingImportWithFeeds: ((BackupSelection, Set<String>?) -> Unit)? = null,
    onCancelPendingImport: () -> Unit = {},
    onExportOpmlClick: () -> Unit,
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
    feedsAndListsInitialAll: Boolean = false,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectLanguage: (TargetLanguage) -> Unit,
    onSelectTitleProvider: (TitleTranslationProvider) -> Unit,
    onSelectSummaryProvider: (SummaryProvider) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectOllamaModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onRefreshOllamaModels: () -> Unit,
    onToggleCustomTranslatePrompt: (Boolean) -> Unit = {},
    onUpdateCustomTranslatePrompt: (String) -> Unit = {},
    onToggleCustomSummarizePrompt: (Boolean) -> Unit = {},
    onUpdateCustomSummarizePrompt: (String) -> Unit = {},
    onVerifyOllamaKey: (String) -> Unit,
    onVerifyGeminiKey: (String) -> Unit,
    onVerifyDeeplKey: (String) -> Unit,
    onSaveOrReplaceSingleKey: (String, String) -> Unit,
    onDeleteSingleKey: (String) -> Unit,
    onToggleTelegramEnabled: (Boolean) -> Unit,
    onSaveTelegramConfig: (String, String) -> Unit,
    onTestTelegramBot: (String) -> Unit,
    onEmitMessage: (String) -> Unit
) {
    var internalActivePage by rememberSaveable { mutableStateOf(SettingsSubPage.HUB) }
    val activePage = externalActivePage ?: internalActivePage

    fun setActivePage(page: SettingsSubPage) {
        if (onExternalActivePageChange != null) {
            onExternalActivePageChange(page)
        } else {
            internalActivePage = page
        }
    }

    LaunchedEffect(resetToHubSignal) {
        if (resetToHubSignal > 0) {
            setActivePage(SettingsSubPage.HUB)
        }
    }

    AnimatedContent(
        targetState = activePage,
        transitionSpec = {
            (crimsonPageEnterTransition() togetherWith crimsonPageExitTransition())
                .using(SizeTransform(clip = false))
        },
        label = "settings_subpage_transition",
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            SettingsSubPage.APPEARANCE -> {
                AppearanceSettingsPage(
                    selectedTheme = settings.themeMode,
                    onSelectTheme = onSelectTheme,
                    onBack = { setActivePage(SettingsSubPage.HUB) }
                )
            }

            SettingsSubPage.FEEDS_LISTS -> {
                FeedsAndListsSettingsPage(
                    feeds = feeds,
                    customLists = customLists,
                    canSummarize = settings.canSummarizeWithAi,
                    initialViewingAllFeeds = feedsAndListsInitialAll,
                    resetSignal = resetToHubSignal,
                    onCreateCustomList = onCreateCustomList,
                    onRenameCustomList = onRenameCustomList,
                    onDeleteCustomList = onDeleteCustomList,
                    onToggleCustomListPinned = onToggleCustomListPinned,
                    onToggleCustomListNotify = onToggleCustomListNotify,
                    onReorderCustomLists = onReorderCustomLists,
                    onEditFeed = onEditFeed,
                    onToggleFeedPinned = onToggleFeedPinned,
                    onToggleFeedNotify = onToggleFeedNotify,
                    onReorderFeeds = onReorderFeeds,
                    onDeleteFeed = onDeleteFeed,
                    onAddFeed = onAddFeed,
                    onEmitMessage = onEmitMessage,
                    onBack = { setActivePage(SettingsSubPage.HUB) }
                )
            }

            SettingsSubPage.API_KEYS -> {
                ApiKeysSettingsPage(
                    settings = settings,
                    ollamaKeyStatus = ollamaKeyStatus,
                    geminiKeyStatus = geminiKeyStatus,
                    deeplKeyStatus = deeplKeyStatus,
                    onVerifyOllamaKey = onVerifyOllamaKey,
                    onVerifyGeminiKey = onVerifyGeminiKey,
                    onVerifyDeeplKey = onVerifyDeeplKey,
                    onSaveOrReplaceKey = onSaveOrReplaceSingleKey,
                    onDeleteKey = onDeleteSingleKey,
                    onBack = { setActivePage(SettingsSubPage.HUB) }
                )
            }

            SettingsSubPage.TRANSLATE -> {
                TranslateSettingsPage(
                    settings = settings,
                    isRefreshingGeminiModels = isRefreshingModels,
                    isRefreshingOllamaModels = isRefreshingOllamaModels,
                    onSelectLanguage = onSelectLanguage,
                    onSelectTranslator = onSelectTitleProvider,
                    onSelectSummaryProvider = onSelectSummaryProvider,
                    onSelectGeminiModel = onSelectModel,
                    onSelectOllamaModel = onSelectOllamaModel,
                    onRefreshGeminiModels = onRefreshModels,
                    onRefreshOllamaModels = onRefreshOllamaModels,
                    onToggleCustomTranslatePrompt = onToggleCustomTranslatePrompt,
                    onUpdateCustomTranslatePrompt = onUpdateCustomTranslatePrompt,
                    onToggleCustomSummarizePrompt = onToggleCustomSummarizePrompt,
                    onUpdateCustomSummarizePrompt = onUpdateCustomSummarizePrompt,
                    onBack = { setActivePage(SettingsSubPage.HUB) }
                )
            }

            SettingsSubPage.INTEGRATION -> {
                IntegrationSettingsPage(
                    settings = settings,
                    onToggleTelegramEnabled = onToggleTelegramEnabled,
                    onSaveTelegramConfig = onSaveTelegramConfig,
                    onTestTelegramBot = onTestTelegramBot,
                    onBack = { setActivePage(SettingsSubPage.HUB) }
                )
            }

            SettingsSubPage.BACKUP_RESTORE -> {
                BackupRestoreSettingsPage(
                    backupSelection = backupSelection,
                    isImportingBackup = isImportingBackup,
                    hasPendingImportBackup = hasPendingImportBackup,
                    pendingImportPreview = pendingImportPreview,
                    onUpdateBackupSelection = onUpdateBackupSelection,
                    onExportBackupClick = onExportBackupClick,
                    onImportBackupClick = onImportBackupClick,
                    onConfirmPendingImport = onConfirmPendingImport,
                    onConfirmPendingImportWithFeeds = onConfirmPendingImportWithFeeds,
                    onCancelPendingImport = onCancelPendingImport,
                    onExportOpmlClick = onExportOpmlClick,
                    onBack = { setActivePage(SettingsSubPage.HUB) }
                )
            }

            SettingsSubPage.ABOUT -> {
                AboutSettingsPage(
                    onBack = { setActivePage(SettingsSubPage.HUB) }
                )
            }

            SettingsSubPage.HUB -> {

                Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = stringResource(R.string.nav_settings),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 640.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Appearance
                        SettingsHubCard(
                            title = stringResource(R.string.settings_appearance),
                            subtitle = stringResource(R.string.settings_appearance_sub, settings.themeMode.label),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            testTag = "settings_hub_appearance",
                            onClick = { setActivePage(SettingsSubPage.APPEARANCE) }
                        )

                        // 2. Feeds & Lists
                        SettingsHubCard(
                            title = stringResource(R.string.settings_feeds_lists),
                            subtitle = stringResource(
                                R.string.settings_feeds_lists_sub,
                                customLists.size,
                                feeds.size
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            testTag = "settings_hub_feeds_lists",
                            onClick = { setActivePage(SettingsSubPage.FEEDS_LISTS) }
                        )

                        // 3. API Keys
                        SettingsHubCard(
                            title = stringResource(R.string.settings_api_keys),
                            subtitle = stringResource(R.string.settings_api_keys_sub),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            testTag = "settings_hub_api_keys",
                            onClick = { setActivePage(SettingsSubPage.API_KEYS) }
                        )

                        // 4. Translate & Summarize
                        SettingsHubCard(
                            title = stringResource(R.string.settings_translate_languages),
                            subtitle = stringResource(
                                R.string.settings_translate_sub,
                                settings.targetLanguage.englishName,
                                settings.titleProvider.label,
                                settings.summaryProvider.label
                            ),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            testTag = "settings_hub_translate",
                            onClick = { setActivePage(SettingsSubPage.TRANSLATE) }
                        )

                        // 6. Integration
                        SettingsHubCard(
                            title = stringResource(R.string.settings_integration),
                            subtitle = if (settings.telegramEnabled) {
                                stringResource(R.string.settings_integration_sub_on)
                            } else {
                                stringResource(R.string.settings_integration_sub_off)
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_telegram),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            testTag = "settings_hub_integration",
                            onClick = { setActivePage(SettingsSubPage.INTEGRATION) }
                        )

                        // 7. Backup & Restore
                        SettingsHubCard(
                            title = stringResource(R.string.settings_backup_restore),
                            subtitle = stringResource(R.string.settings_backup_restore_sub),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.ImportExport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            testTag = "settings_hub_backup",
                            onClick = { setActivePage(SettingsSubPage.BACKUP_RESTORE) }
                        )

                        // 8. About
                        SettingsHubCard(
                            title = stringResource(R.string.settings_about),
                            subtitle = stringResource(R.string.settings_about_sub, BuildConfig.VERSION_NAME),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            testTag = "settings_hub_about",
                            onClick = { setActivePage(SettingsSubPage.ABOUT) }
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    }
}

