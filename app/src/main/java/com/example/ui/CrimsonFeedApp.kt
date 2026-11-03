package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.data.local.ArticleWithFeed
import com.example.data.model.SessionResumeState
import com.example.data.model.SummaryProvider
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ReadHistoryScreen
import com.example.ui.screens.ReaderScreen
import com.example.ui.screens.SavedArticlesScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SettingsSubPage
import com.example.ui.screens.crimsonPageEnterTransition
import com.example.ui.screens.crimsonPageExitTransition
import com.example.ui.screens.settings.SmartImportSelectionDialog
import com.example.ui.theme.CrimsonFeedTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.screens.settings.FeedsAndListsSettingsPage
import com.example.ui.screens.settings.FeedsAndListsViewMode
import com.example.ui.viewmodel.SnackbarUndoAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object RouteFeeds
@Serializable object RouteSaved
@Serializable object RouteRead
@Serializable object RouteSettings
@Serializable object RouteSearch
@Serializable object RouteNotifications
@Serializable object RouteStandaloneFeeds
@Serializable object RouteStandaloneLists
@Serializable data class RouteReader(val articleId: Long)

private data class NavTabItem(
    val route: Any,
    val tabKey: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun CrimsonFeedApp(
    viewModel: MainViewModel = viewModel()
) {
    val isInitialBootReady by viewModel.isInitialBootReady.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val sessionResume by viewModel.sessionResumeState.collectAsStateWithLifecycle()
    val feeds by viewModel.feedsState.collectAsStateWithLifecycle()
    val customLists by viewModel.customListsState.collectAsStateWithLifecycle()
    val selectedFeedId by viewModel.selectedFeedId.collectAsStateWithLifecycle()
    val selectedListName by viewModel.selectedListName.collectAsStateWithLifecycle()
    val homeArticles by viewModel.homeArticlesState.collectAsStateWithLifecycle()
    val savedArticles by viewModel.savedArticlesState.collectAsStateWithLifecycle()
    val readArticles by viewModel.readArticlesState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResultsState.collectAsStateWithLifecycle()
    val isIconSyncing by viewModel.isIconSyncing.collectAsStateWithLifecycle()
    val isPullSyncing by viewModel.isPullSyncing.collectAsStateWithLifecycle()
    val syncingFeedIds by viewModel.syncingFeedIds.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val isAddingFeed by viewModel.isAddingFeed.collectAsStateWithLifecycle()
    val isImportingOpml by viewModel.isImportingOpml.collectAsStateWithLifecycle()
    val isRefreshingModels by viewModel.isRefreshingModels.collectAsStateWithLifecycle()
    val isRefreshingOllamaModels by viewModel.isRefreshingOllamaModels.collectAsStateWithLifecycle()
    val translatingIds by viewModel.translatingCardIds.collectAsStateWithLifecycle()
    val readerState by viewModel.readerState.collectAsStateWithLifecycle()
    val telegramPreview by viewModel.telegramPreview.collectAsStateWithLifecycle()
    val geminiKeyStatus by viewModel.geminiKeyStatus.collectAsStateWithLifecycle()
    val ollamaKeyStatus by viewModel.ollamaKeyStatus.collectAsStateWithLifecycle()
    val deeplKeyStatus by viewModel.deeplKeyStatus.collectAsStateWithLifecycle()
    val backupSelection by viewModel.backupSelectionState.collectAsStateWithLifecycle()
    val pendingImportPreview by viewModel.pendingImportPreview.collectAsStateWithLifecycle()
    val notifiedArticles by viewModel.notifiedArticlesState.collectAsStateWithLifecycle()
    val notifiedUnreadCount by viewModel.notifiedUnreadCountState.collectAsStateWithLifecycle()

    CrimsonFeedTheme(themeMode = settings.themeMode) {
        if (!isInitialBootReady) {
            // Render clean background surface for ~20ms while Room & DataStore preload,
            // completely preventing any flash of an empty page or wrong tab on launch.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {}
            return@CrimsonFeedTheme
        }

        val snackbarHostState = remember { SnackbarHostState() }
        val navController = rememberNavController()
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner, viewModel) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> viewModel.onAppStarted()
                    Lifecycle.Event.ON_STOP -> viewModel.onAppStopped()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val initialStartDestination = remember {
            when (sessionResume.lastTab) {
                SessionResumeState.TAB_SAVED -> RouteSaved
                SessionResumeState.TAB_READ -> RouteRead
                SessionResumeState.TAB_SETTINGS -> RouteSettings
                else -> RouteFeeds
            }
        }

        // Zero-permission SAF launchers for OPML Import & Export
        val importOpmlLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.handleSmartImportFromUri(context, uri)
            }
        }

        val exportOpmlLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/xml")
        ) { uri ->
            if (uri != null) {
                viewModel.exportOpmlToUri(context, uri)
            }
        }

        val exportBackupLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                viewModel.exportBackupToUri(context, uri)
            }
        }

        val importBackupLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.handleSmartImportFromUri(context, uri)
            }
        }

        fun triggerImportOpml() {
            importOpmlLauncher.launch(arrayOf("*/*"))
        }

        fun triggerExportOpml() {
            exportOpmlLauncher.launch("crimsonfeed-subscriptions.opml")
        }

        fun triggerExportBackup() {
            exportBackupLauncher.launch("crimsonfeed-backup.json")
        }

        fun triggerImportBackup() {
            importBackupLauncher.launch(arrayOf("*/*"))
        }

        LaunchedEffect(Unit) {
            viewModel.snackbarEvents.collect { event ->
                snackbarHostState.currentSnackbarData?.dismiss()
                if (event.actionLabel != null && event.timeoutMs != null) {
                    val timerJob = launch {
                        delay(event.timeoutMs)
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Indefinite
                    )
                    timerJob.cancel()
                    if (result == SnackbarResult.ActionPerformed) {
                        when (event.undoAction) {
                            SnackbarUndoAction.CLEAR_READ -> viewModel.undoClearReadHistory()
                            SnackbarUndoAction.DELETE_FEED -> viewModel.undoDeleteFeed()
                            SnackbarUndoAction.NONE -> Unit
                        }
                    }
                } else {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }

        val navTabs = remember {
            listOf(
                NavTabItem(
                    RouteFeeds,
                    SessionResumeState.TAB_FEEDS,
                    R.string.nav_feeds,
                    Icons.Filled.RssFeed,
                    Icons.Outlined.RssFeed,
                    "nav_tab_feeds"
                ),
                NavTabItem(
                    RouteSaved,
                    SessionResumeState.TAB_SAVED,
                    R.string.nav_saved,
                    Icons.Filled.Bookmark,
                    Icons.Outlined.BookmarkBorder,
                    "nav_tab_saved"
                ),
                NavTabItem(
                    RouteRead,
                    SessionResumeState.TAB_READ,
                    R.string.nav_read,
                    Icons.Filled.History,
                    Icons.Outlined.History,
                    "nav_tab_read"
                ),
                NavTabItem(
                    RouteSettings,
                    SessionResumeState.TAB_SETTINGS,
                    R.string.nav_settings,
                    Icons.Filled.Settings,
                    Icons.Outlined.Settings,
                    "nav_tab_settings"
                )
            )
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDest = backStackEntry?.destination
        val isFullScreenOverlay = currentDest?.hasRoute<RouteReader>() == true ||
            currentDest?.hasRoute<RouteSearch>() == true ||
            currentDest?.hasRoute<RouteNotifications>() == true ||
            currentDest?.hasRoute<RouteStandaloneFeeds>() == true ||
            currentDest?.hasRoute<RouteStandaloneLists>() == true

        var settingsSubPage by rememberSaveable { mutableStateOf(SettingsSubPage.HUB) }
        var settingsResetSignal by rememberSaveable { mutableIntStateOf(0) }
        var feedsAndListsOpenAllDetail by rememberSaveable { mutableStateOf(false) }

        fun openFeedsAndListsSettings(openAllFeeds: Boolean) {
            settingsSubPage = SettingsSubPage.FEEDS_LISTS
            feedsAndListsOpenAllDetail = openAllFeeds
            navController.navigate(RouteSettings) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun navigateTopTab(tab: NavTabItem) {
            viewModel.saveLastTab(tab.tabKey)
            if (tab.route == RouteSettings && currentDest?.hasRoute<RouteSettings>() == true) {
                settingsSubPage = SettingsSubPage.HUB
                feedsAndListsOpenAllDetail = false
                settingsResetSignal++
                return
            }
            if (tab.route == RouteSettings) {
                feedsAndListsOpenAllDetail = false
            }
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun openReader(articleId: Long) {
            viewModel.openArticleInReader(articleId)
            navController.navigate(RouteReader(articleId))
        }

        fun openSearch(
            seed: String = "",
            filterUnread: Boolean = false,
            filterRead: Boolean = false,
            filterSaved: Boolean = false
        ) {
            viewModel.prepareSearch(
                seedQuery = seed,
                filterUnread = filterUnread,
                filterRead = filterRead,
                filterSaved = filterSaved
            )
            navController.navigate(RouteSearch) {
                launchSingleTop = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 680.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.testTag("app_snackbar_host")
                    )
                },
                bottomBar = {
                    if (!isExpandedScreen && !isFullScreenOverlay) {
                        NavigationBar {
                            navTabs.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                val label = stringResource(tab.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen && !isFullScreenOverlay) {
                        NavigationRail {
                            navTabs.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                val label = stringResource(tab.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = initialStartDestination,
                            enterTransition = { crimsonPageEnterTransition() },
                            exitTransition = { crimsonPageExitTransition() },
                            popEnterTransition = { crimsonPageEnterTransition() },
                            popExitTransition = { crimsonPageExitTransition() }
                        ) {
                            composable<RouteFeeds> {
                                HomeScreen(
                                    feeds = feeds,
                                    customLists = customLists,
                                    selectedFeedId = selectedFeedId,
                                    selectedListName = selectedListName,
                                    articles = homeArticles,
                                    layoutMode = settings.layoutMode,
                                    initialScrollIndex = sessionResume.homeScrollIndex,
                                    initialScrollOffset = sessionResume.homeScrollOffset,
                                    isIconSyncing = isIconSyncing,
                                    isPullSyncing = isPullSyncing,
                                    syncingFeedIds = syncingFeedIds,
                                    syncProgress = syncProgress,
                                    isAddingFeed = isAddingFeed,
                                    isImportingOpml = isImportingOpml,
                                    translatingIds = translatingIds,
                                    canSummarize = settings.canSummarizeWithAi,
                                    onScrollPositionChanged = { idx, off ->
                                        viewModel.saveScrollPosition(SessionResumeState.TAB_FEEDS, idx, off)
                                    },
                                    onSelectFeed = viewModel::selectFeed,
                                    onSelectCustomList = viewModel::selectCustomList,
                                    onCreateCustomList = viewModel::createCustomList,
                                    onRenameCustomList = viewModel::renameCustomList,
                                    onRefreshCustomList = viewModel::syncCustomListFeeds,
                                    onToggleCustomListSyncEnabled = viewModel::toggleCustomListSyncEnabled,
                                    onToggleCustomListShowInAllLists = viewModel::toggleCustomListShowInAllLists,
                                    onReorderCustomLists = viewModel::reorderCustomLists,
                                    onDeleteCustomList = viewModel::deleteCustomList,
                                    onImportOpmlClick = ::triggerImportOpml,
                                    onCycleLayout = viewModel::cycleLayoutMode,
                                    onOpenSearch = { seed -> openSearch(seed = seed) },
                                    onSyncFromIcon = viewModel::syncAllFeedsFromIcon,
                                    onSyncFromPull = viewModel::syncAllFeedsFromPull,
                                    onRefreshSingleFeed = viewModel::syncSingleFeed,
                                    onAddFeed = { url, list, autoTranslate, readerMode, notify, onSuccess ->
                                        viewModel.addFeed(
                                            url = url,
                                            groupName = list,
                                            autoTranslate = autoTranslate,
                                            defaultReaderMode = readerMode,
                                            notifyEnabled = notify,
                                            onSuccess = onSuccess
                                        )
                                    },
                                    onEditFeed = viewModel::updateFeedDetails,
                                    onToggleFeedSyncEnabled = viewModel::toggleFeedSyncEnabled,
                                    onToggleFeedPinned = viewModel::toggleFeedPinned,
                                    onToggleFeedShowInAllFeeds = viewModel::toggleFeedShowInAllFeeds,
                                    onToggleFeedShowInAllLists = viewModel::toggleFeedShowInAllLists,
                                    onReorderFeeds = viewModel::reorderFeeds,
                                    onToggleFeedAutoTranslate = viewModel::toggleFeedAutoTranslate,
                                    onMarkFeedAllRead = viewModel::markFeedAllRead,
                                    onMarkAllReadGlobal = viewModel::markAllArticlesRead,
                                    onDeleteFeed = viewModel::deleteFeed,
                                    onOpenArticle = ::openReader,
                                    onTranslateTitle = viewModel::translateCardHeadline,
                                    onToggleRead = { item ->
                                        viewModel.toggleArticleRead(item.article.id, !item.article.isRead)
                                    },
                                    onToggleSave = { item ->
                                        viewModel.toggleArticleSaved(item.article.id, !item.article.isSaved)
                                    },
                                    onShare = { item -> shareArticle(context, item) },
                                    notifiedArticles = notifiedArticles,
                                    notifiedUnreadCount = notifiedUnreadCount,
                                    onOpenNotifications = {
                                        navController.navigate(RouteNotifications)
                                    },
                                    onToggleFeedNotify = viewModel::toggleFeedNotify,
                                    onMarkAllNotifiedRead = viewModel::markAllNotifiedArticlesRead,
                                    onClearNotifiedArticles = viewModel::clearNotifiedArticles,
                                    onToggleCustomListPinned = viewModel::toggleCustomListPinned,
                                    onToggleCustomListAutoTranslate = viewModel::toggleCustomListAutoTranslate,
                                    onSetFeedReaderMode = viewModel::setFeedReaderMode,
                                    onOpenStandaloneFeeds = {
                                        navController.navigate(RouteStandaloneFeeds) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenStandaloneLists = {
                                        navController.navigate(RouteStandaloneLists) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenFeedsAndListsSettings = ::openFeedsAndListsSettings,
                                    onEmitMessage = viewModel::emitMessage
                                )
                            }

                            composable<RouteSaved> {
                                SavedArticlesScreen(
                                    articles = savedArticles,
                                    layoutMode = settings.layoutMode,
                                    initialScrollIndex = sessionResume.savedScrollIndex,
                                    initialScrollOffset = sessionResume.savedScrollOffset,
                                    translatingIds = translatingIds,
                                    notifiedUnreadCount = notifiedUnreadCount,
                                    onOpenNotifications = {
                                        navController.navigate(RouteNotifications)
                                    },
                                    onMarkAllRead = viewModel::markAllArticlesRead,
                                    onScrollPositionChanged = { idx, off ->
                                        viewModel.saveScrollPosition(SessionResumeState.TAB_SAVED, idx, off)
                                    },
                                    onCycleLayout = viewModel::cycleLayoutMode,
                                    onOpenSearch = { openSearch(filterSaved = true) },
                                    onOpenArticle = ::openReader,
                                    onTranslateTitle = viewModel::translateCardHeadline,
                                    onToggleRead = { item ->
                                        viewModel.toggleArticleRead(item.article.id, !item.article.isRead)
                                    },
                                    onSearchRelated = { post ->
                                        val seed = post.article.title.split(" ")
                                            .firstOrNull { w -> w.length > 4 }
                                            .orEmpty()
                                        openSearch(seed = seed, filterSaved = true)
                                    },
                                    onToggleSave = { item ->
                                        viewModel.toggleArticleSaved(item.article.id, !item.article.isSaved)
                                    },
                                    onShare = { item -> shareArticle(context, item) }
                                )
                            }

                            composable<RouteRead> {
                                ReadHistoryScreen(
                                    articles = readArticles,
                                    layoutMode = settings.layoutMode,
                                    initialScrollIndex = sessionResume.readScrollIndex,
                                    initialScrollOffset = sessionResume.readScrollOffset,
                                    translatingIds = translatingIds,
                                    notifiedUnreadCount = notifiedUnreadCount,
                                    onOpenNotifications = {
                                        navController.navigate(RouteNotifications)
                                    },
                                    onScrollPositionChanged = { idx, off ->
                                        viewModel.saveScrollPosition(SessionResumeState.TAB_READ, idx, off)
                                    },
                                    onCycleLayout = viewModel::cycleLayoutMode,
                                    onOpenSearch = { openSearch(filterRead = true) },
                                    onClearHistory = viewModel::clearReadHistory,
                                    onOpenArticle = ::openReader,
                                    onTranslateTitle = viewModel::translateCardHeadline,
                                    onToggleRead = { item ->
                                        viewModel.toggleArticleRead(item.article.id, !item.article.isRead)
                                    },
                                    onSearchRelated = { post ->
                                        val seed = post.article.title.split(" ")
                                            .firstOrNull { w -> w.length > 4 }
                                            .orEmpty()
                                        openSearch(seed = seed, filterRead = true)
                                    },
                                    onToggleSave = { item ->
                                        viewModel.toggleArticleSaved(item.article.id, !item.article.isSaved)
                                    },
                                    onShare = { item -> shareArticle(context, item) }
                                )
                            }

                            composable<RouteSettings> {
                                SettingsScreen(
                                    settings = settings,
                                    feeds = feeds,
                                    customLists = customLists,
                                    backupSelection = backupSelection,
                                    ollamaKeyStatus = ollamaKeyStatus,
                                    geminiKeyStatus = geminiKeyStatus,
                                    deeplKeyStatus = deeplKeyStatus,
                                    isRefreshingModels = isRefreshingModels,
                                    isRefreshingOllamaModels = isRefreshingOllamaModels,
                                    isImportingBackup = isImportingOpml,
                                    hasPendingImportBackup = false,
                                    pendingImportPreview = null,
                                    externalActivePage = settingsSubPage,
                                    onExternalActivePageChange = { settingsSubPage = it },
                                    resetToHubSignal = settingsResetSignal,
                                    onUpdateBackupSelection = viewModel::updateBackupSelection,
                                    onExportBackupClick = ::triggerExportBackup,
                                    onImportBackupClick = ::triggerImportBackup,
                                    onConfirmPendingImport = { sel ->
                                        viewModel.confirmPendingBackupImport(selection = sel)
                                    },
                                    onConfirmPendingImportWithFeeds = { sel, urls ->
                                        viewModel.confirmPendingBackupImport(selection = sel, selectedFeedUrls = urls)
                                    },
                                    onCancelPendingImport = viewModel::cancelPendingBackupImport,
                                    onExportOpmlClick = ::triggerExportOpml,
                                    onCreateCustomList = viewModel::createCustomList,
                                    onRenameCustomList = viewModel::renameCustomList,
                                    onDeleteCustomList = viewModel::deleteCustomList,
                                    onToggleCustomListPinned = viewModel::toggleCustomListPinned,
                                    onToggleCustomListNotify = viewModel::toggleCustomListNotify,
                                    onReorderCustomLists = viewModel::reorderCustomLists,
                                    onEditFeed = viewModel::updateFeedDetails,
                                    onToggleFeedPinned = viewModel::toggleFeedPinned,
                                    onToggleFeedNotify = viewModel::toggleFeedNotify,
                                    onAddFeed = { url, list, autoTranslate, readerMode, notify, onSuccess ->
                                        viewModel.addFeed(
                                            url = url,
                                            groupName = list,
                                            autoTranslate = autoTranslate,
                                            defaultReaderMode = readerMode,
                                            notifyEnabled = notify,
                                            onSuccess = onSuccess
                                        )
                                    },
                                    onReorderFeeds = viewModel::reorderFeeds,
                                    onDeleteFeed = viewModel::deleteFeed,
                                    feedsAndListsInitialAll = feedsAndListsOpenAllDetail,
                                    onSelectTheme = viewModel::setThemeMode,
                                    onSelectLanguage = viewModel::setTargetLanguage,
                                    onSelectTitleProvider = viewModel::setTitleProvider,
                                    onSelectSummaryProvider = viewModel::setSummaryProvider,
                                    onSelectModel = viewModel::selectGeminiModel,
                                    onSelectOllamaModel = viewModel::selectOllamaModel,
                                    onRefreshModels = viewModel::refreshFreeModelsManually,
                                    onRefreshOllamaModels = viewModel::refreshFreeOllamaModelsManually,
                                    onToggleCustomTranslatePrompt = viewModel::setUseCustomTranslatePrompt,
                                    onUpdateCustomTranslatePrompt = viewModel::setCustomTranslatePrompt,
                                    onToggleCustomSummarizePrompt = viewModel::setUseCustomSummarizePrompt,
                                    onUpdateCustomSummarizePrompt = viewModel::setCustomSummarizePrompt,
                                    onVerifyOllamaKey = viewModel::verifyOllamaKey,
                                    onVerifyGeminiKey = viewModel::verifyGeminiKey,
                                    onVerifyDeeplKey = viewModel::verifyDeeplKey,
                                    onSaveOrReplaceSingleKey = viewModel::updateSingleApiKey,
                                    onDeleteSingleKey = viewModel::deleteSingleApiKey,
                                    onToggleTelegramEnabled = viewModel::setTelegramEnabled,
                                    onSaveTelegramConfig = { tgToken, tgChannel ->
                                        viewModel.saveApiAndTelegramConfig(
                                            geminiKey = settings.geminiApiKeyOverride,
                                            ollamaKey = settings.ollamaApiKeyOverride,
                                            deeplKey = settings.deeplApiKeyOverride,
                                            telegramToken = tgToken,
                                            telegramChannel = tgChannel
                                        )
                                    },
                                    onTestTelegramBot = viewModel::testTelegramBotConnection,
                                    onEmitMessage = viewModel::emitMessage
                                )
                            }

                            composable<RouteSearch>(
                                enterTransition = { EnterTransition.None },
                                exitTransition = { ExitTransition.None },
                                popEnterTransition = { EnterTransition.None },
                                popExitTransition = { ExitTransition.None }
                            ) {
                                SearchScreen(
                                    searchState = searchState,
                                    results = searchResults,
                                    feeds = feeds,
                                    layoutMode = settings.layoutMode,
                                    translatingIds = translatingIds,
                                    onBack = {
                                        if (navController.currentDestination?.hasRoute<RouteSearch>() == true) {
                                            navController.popBackStack()
                                        }
                                    },
                                    onQueryChange = viewModel::updateSearchQuery,
                                    onSelectAllFilter = viewModel::resetSearchFiltersToAll,
                                    onToggleUnreadFilter = viewModel::toggleSearchUnread,
                                    onToggleReadFilter = viewModel::toggleSearchRead,
                                    onToggleSavedFilter = viewModel::toggleSearchSaved,
                                    onToggleFeedSelection = viewModel::toggleSearchFeedSelection,
                                    onToggleFeedExclusion = viewModel::toggleSearchFeedExclusion,
                                    onClearFeedFilters = viewModel::clearSearchFeedFilters,
                                    onOpenArticle = ::openReader,
                                    onTranslateTitle = viewModel::translateCardHeadline,
                                    onToggleRead = { item ->
                                        viewModel.toggleArticleRead(item.article.id, !item.article.isRead)
                                    },
                                    onToggleSave = { item ->
                                        viewModel.toggleArticleSaved(item.article.id, !item.article.isSaved)
                                    },
                                    onShare = { item -> shareArticle(context, item) }
                                )
                            }

                            composable<RouteNotifications> {
                                NotificationsScreen(
                                    articles = notifiedArticles,
                                    layoutMode = settings.layoutMode,
                                    translatingIds = translatingIds,
                                    onBack = {
                                        if (navController.currentDestination?.hasRoute<RouteNotifications>() == true) {
                                            navController.popBackStack()
                                        }
                                    },
                                    onCycleLayout = viewModel::cycleLayoutMode,
                                    onOpenSearch = { openSearch() },
                                    onMarkAllRead = viewModel::markAllNotifiedArticlesRead,
                                    onOpenArticle = ::openReader,
                                    onTranslateTitle = viewModel::translateCardHeadline,
                                    onToggleRead = { item ->
                                        viewModel.toggleArticleRead(item.article.id, !item.article.isRead)
                                    },
                                    onSearchRelated = { post ->
                                        val seed = post.article.title.split(" ")
                                            .firstOrNull { w -> w.length > 4 }
                                            .orEmpty()
                                        openSearch(seed = seed)
                                    },
                                    onToggleSave = { item ->
                                        viewModel.toggleArticleSaved(item.article.id, !item.article.isSaved)
                                    },
                                    onShare = { item -> shareArticle(context, item) }
                                )
                            }

                            composable<RouteReader> { entry ->
                                val route = entry.toRoute<RouteReader>()
                                val initialCachedArticle = remember(route.articleId) {
                                    viewModel.getCachedArticle(route.articleId)
                                }
                                val liveArticle by remember(route.articleId) {
                                    viewModel.observeSingleArticle(route.articleId)
                                }
                                .collectAsStateWithLifecycle(initialValue = initialCachedArticle)

                                val feedId = liveArticle?.article?.feedId
                                val feedFilters by remember(feedId) {
                                    if (feedId != null) viewModel.observeFiltersForFeed(feedId)
                                    else kotlinx.coroutines.flow.flowOf(emptyList())
                                }.collectAsStateWithLifecycle(initialValue = emptyList())

                                ReaderScreen(
                                    item = liveArticle,
                                    readerState = readerState,
                                    summaryProvider = settings.summaryProvider,
                                    currentTranslator = settings.titleProvider,
                                    canSummarize = settings.canSummarizeWithAi,
                                    hasDeeplKey = settings.effectiveDeeplKey.isNotBlank(),
                                    hasGeminiKey = settings.effectiveGeminiKey.isNotBlank(),
                                    hasOllamaKey = settings.effectiveOllamaKey.isNotBlank(),
                                    feedFilters = feedFilters,
                                    telegramEnabled = settings.telegramEnabled,
                                    telegramPreview = telegramPreview,
                                    onBack = {
                                        viewModel.dismissTelegramPreview()
                                        if (navController.currentDestination?.hasRoute<RouteReader>() == true) {
                                            navController.popBackStack()
                                        }
                                    },
                                    onSwitchAiMode = { targetMode, forceRefresh ->
                                        viewModel.switchReaderAiMode(route.articleId, targetMode, forceRefresh)
                                    },
                                    onSelectTranslator = viewModel::setTitleProvider,
                                    onSelectSummaryProvider = viewModel::setSummaryProvider,
                                    onToggleSummaryProvider = {
                                        val next = if (settings.summaryProvider == SummaryProvider.GEMINI) {
                                            SummaryProvider.OLLAMA
                                        } else {
                                            SummaryProvider.GEMINI
                                        }
                                        viewModel.setSummaryProvider(next)
                                    },
                                    onAddFeedFilter = { pat: String, isRegex: Boolean ->
                                        feedId?.let { fid -> viewModel.addFeedFilter(fid, pat, isRegex) }
                                    },
                                    onDeleteFeedFilter = viewModel::removeFeedFilter,
                                    onToggleSave = { shouldSave ->
                                        viewModel.toggleArticleSaved(route.articleId, shouldSave)
                                    },
                                    onShare = { item -> shareArticle(context, item) },
                                    onOpenTelegramPreview = {
                                        val hasImg = !liveArticle?.article?.imageUrl.isNullOrBlank()
                                        viewModel.openTelegramPreview(route.articleId, hasImg)
                                    },
                                    onDismissTelegramPreview = viewModel::dismissTelegramPreview,
                                    onRegenerateTelegramDraft = {
                                        viewModel.regenerateTelegramDraft(route.articleId)
                                    },
                                    onUpdateTelegramDraft = viewModel::updateTelegramDraftText,
                                    onToggleTelegramPhoto = viewModel::toggleTelegramIncludePhoto,
                                    onSendTelegram = viewModel::sendTelegramPost
                                )
                            }

                            composable<RouteStandaloneFeeds> {
                                FeedsAndListsSettingsPage(
                                    feeds = feeds,
                                    customLists = customLists,
                                    canSummarize = settings.canSummarizeWithAi,
                                    viewMode = FeedsAndListsViewMode.FEEDS_ONLY,
                                    onCreateCustomList = viewModel::createCustomList,
                                    onRenameCustomList = viewModel::renameCustomList,
                                    onDeleteCustomList = viewModel::deleteCustomList,
                                    onToggleCustomListPinned = viewModel::toggleCustomListPinned,
                                    onToggleCustomListNotify = viewModel::toggleCustomListNotify,
                                    onReorderCustomLists = viewModel::reorderCustomLists,
                                    onEditFeed = viewModel::updateFeedDetails,
                                    onToggleFeedPinned = viewModel::toggleFeedPinned,
                                    onToggleFeedNotify = viewModel::toggleFeedNotify,
                                    onAddFeed = { url, list, autoTranslate, readerMode, notify, onSuccess ->
                                        viewModel.addFeed(
                                            url = url,
                                            groupName = list,
                                            autoTranslate = autoTranslate,
                                            defaultReaderMode = readerMode,
                                            notifyEnabled = notify,
                                            onSuccess = onSuccess
                                        )
                                    },
                                    onReorderFeeds = viewModel::reorderFeeds,
                                    onDeleteFeed = viewModel::deleteFeed,
                                    onEmitMessage = viewModel::emitMessage,
                                    onBack = {
                                        if (navController.currentDestination?.hasRoute<RouteStandaloneFeeds>() == true) {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }

                            composable<RouteStandaloneLists> {
                                FeedsAndListsSettingsPage(
                                    feeds = feeds,
                                    customLists = customLists,
                                    canSummarize = settings.canSummarizeWithAi,
                                    viewMode = FeedsAndListsViewMode.LISTS_ONLY,
                                    onCreateCustomList = viewModel::createCustomList,
                                    onRenameCustomList = viewModel::renameCustomList,
                                    onDeleteCustomList = viewModel::deleteCustomList,
                                    onToggleCustomListPinned = viewModel::toggleCustomListPinned,
                                    onToggleCustomListNotify = viewModel::toggleCustomListNotify,
                                    onReorderCustomLists = viewModel::reorderCustomLists,
                                    onEditFeed = viewModel::updateFeedDetails,
                                    onToggleFeedPinned = viewModel::toggleFeedPinned,
                                    onToggleFeedNotify = viewModel::toggleFeedNotify,
                                    onAddFeed = { url, list, autoTranslate, readerMode, notify, onSuccess ->
                                        viewModel.addFeed(
                                            url = url,
                                            groupName = list,
                                            autoTranslate = autoTranslate,
                                            defaultReaderMode = readerMode,
                                            notifyEnabled = notify,
                                            onSuccess = onSuccess
                                        )
                                    },
                                    onReorderFeeds = viewModel::reorderFeeds,
                                    onDeleteFeed = viewModel::deleteFeed,
                                    onEmitMessage = viewModel::emitMessage,
                                    onBack = {
                                        if (navController.currentDestination?.hasRoute<RouteStandaloneLists>() == true) {
                                            navController.popBackStack()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            pendingImportPreview?.let { preview ->
                SmartImportSelectionDialog(
                    preview = preview,
                    onDismiss = viewModel::cancelPendingBackupImport,
                    onConfirm = { selectedCategories, selectedFeedUrls ->
                        viewModel.confirmPendingBackupImport(
                            selection = selectedCategories,
                            selectedFeedUrls = selectedFeedUrls
                        )
                    }
                )
            }
        }
    }
}

private fun shareArticle(context: Context, item: ArticleWithFeed) {
    val a = item.article
    val title = if (a.showTranslatedTitle && !a.translatedTitle.isNullOrBlank()) {
        a.translatedTitle
    } else {
        a.title
    }
    val payload = buildString {
        append(title)
        if (a.link.isNotBlank()) {
            append("\n")
            append(a.link)
        }
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, payload)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share"))
}
