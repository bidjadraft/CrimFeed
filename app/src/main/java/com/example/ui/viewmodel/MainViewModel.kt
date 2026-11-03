package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.local.ArticleWithFeed
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedFilterEntity
import com.example.data.local.FeedWithCount
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.ArticleTranslationEngine
import com.example.data.model.BackupSelection
import com.example.data.model.FeedLayoutMode
import com.example.data.model.KeyVerificationState
import com.example.data.model.PendingImportPreview
import com.example.data.model.ReaderAiMode
import com.example.data.model.SessionResumeState
import com.example.data.model.SummaryProvider
import com.example.data.model.TargetLanguage
import com.example.data.model.TitleTranslationProvider
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.DeletedFeedBackup
import com.example.data.repository.RssRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

data class SearchFilterState(
    val query: String = "",
    val filterUnread: Boolean = false,
    val filterRead: Boolean = false,
    val filterSaved: Boolean = false,
    val selectedFeedIds: Set<Long> = emptySet(),
    val excludedFeedIds: Set<Long> = emptySet()
) {
    val isAllSelected: Boolean
        get() = !filterUnread && !filterRead && !filterSaved && selectedFeedIds.isEmpty() && excludedFeedIds.isEmpty()
}

data class ReaderUiState(
    val articleId: Long? = null,
    val activeMode: ReaderAiMode = ReaderAiMode.NONE,
    val isLoading: Boolean = false,
    val displayedText: String = "",
    val errorMessage: String? = null,
    val activeModelUsed: String = ""
)

data class TelegramPreviewState(
    val isVisible: Boolean = false,
    val isDrafting: Boolean = false,
    val isSending: Boolean = false,
    val draftText: String = "",
    val includePhoto: Boolean = true,
    val errorMessage: String? = null
)

enum class SnackbarUndoAction {
    NONE,
    CLEAR_READ,
    DELETE_FEED
}

data class AppSnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val timeoutMs: Long? = null,
    val undoAction: SnackbarUndoAction = SnackbarUndoAction.NONE
)

data class FeedSyncProgress(
    val isVisible: Boolean = false,
    val checkedCount: Int = 0,
    val totalCount: Int = 0,
    val currentFeedTitle: String = "",
    val currentFeedIconUrl: String? = null
)

fun filterAndSortHomeArticles(
    all: List<ArticleWithFeed>,
    customLists: List<CustomListEntity>,
    selectedFeedId: Long?,
    selectedListName: String?
): List<ArticleWithFeed> {
    if (selectedFeedId != null) {
        return all
            .filter { it.article.feedId == selectedFeedId }
            .sortedBy { it.article.isRead }
    }

    val disabledListNamesLower = if (selectedListName.isNullOrBlank()) {
        customLists
            .asSequence()
            .filter { !it.showInAllLists }
            .map { it.name.trim().lowercase(Locale.ROOT) }
            .toHashSet()
    } else {
        emptySet()
    }

    return all.filter { item ->
        if (item.article.isRead) return@filter false

        if (selectedListName.isNullOrBlank()) {
            if (!item.feedShowInAllFeeds) return@filter false
            val groupLower = item.feedGroupName.trim().lowercase(Locale.ROOT)
            groupLower.isEmpty() || groupLower !in disabledListNamesLower
        } else {
            if (!item.feedShowInAllLists) return@filter false
            item.feedGroupName.equals(selectedListName, ignoreCase = true)
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val database = AppDatabase.getInstance(application)
    private val rssRepo = RssRepository(
        dao = database.rssDao(),
        settingsRepository = settingsRepo
    )

    val settingsState: StateFlow<AppSettings> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    val sessionResumeState: StateFlow<SessionResumeState> = settingsRepo.sessionResumeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SessionResumeState()
    )

    val feedsState: StateFlow<List<FeedWithCount>> = rssRepo.feedsWithCountsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val customListsState: StateFlow<List<CustomListEntity>> = rssRepo.customListsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val allArticlesState: StateFlow<List<ArticleWithFeed>> = rssRepo.allArticlesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    private val _isInitialBootReady = MutableStateFlow(false)
    val isInitialBootReady: StateFlow<Boolean> = _isInitialBootReady.asStateFlow()

    private val _selectedFeedId = MutableStateFlow<Long?>(null)
    val selectedFeedId: StateFlow<Long?> = _selectedFeedId.asStateFlow()

    private val _selectedListName = MutableStateFlow<String?>(null)
    val selectedListName: StateFlow<String?> = _selectedListName.asStateFlow()

    private val _isIconSyncing = MutableStateFlow(false)
    val isIconSyncing: StateFlow<Boolean> = _isIconSyncing.asStateFlow()

    private val _isPullSyncing = MutableStateFlow(false)
    val isPullSyncing: StateFlow<Boolean> = _isPullSyncing.asStateFlow()

    private val _syncingFeedIds = MutableStateFlow<Set<Long>>(emptySet())
    val syncingFeedIds: StateFlow<Set<Long>> = _syncingFeedIds.asStateFlow()

    private val _syncProgress = MutableStateFlow(FeedSyncProgress())
    val syncProgress: StateFlow<FeedSyncProgress> = _syncProgress.asStateFlow()

    private val _isAddingFeed = MutableStateFlow(false)
    val isAddingFeed: StateFlow<Boolean> = _isAddingFeed.asStateFlow()

    private val _isImportingOpml = MutableStateFlow(false)
    val isImportingOpml: StateFlow<Boolean> = _isImportingOpml.asStateFlow()

    private val _isRefreshingModels = MutableStateFlow(false)
    val isRefreshingModels: StateFlow<Boolean> = _isRefreshingModels.asStateFlow()

    private val _isRefreshingOllamaModels = MutableStateFlow(false)
    val isRefreshingOllamaModels: StateFlow<Boolean> = _isRefreshingOllamaModels.asStateFlow()

    private val _translatingCardIds = MutableStateFlow<Set<Long>>(emptySet())
    val translatingCardIds: StateFlow<Set<Long>> = _translatingCardIds.asStateFlow()

    private val _searchState = MutableStateFlow(SearchFilterState())
    val searchState: StateFlow<SearchFilterState> = _searchState.asStateFlow()

    private val _readerState = MutableStateFlow(ReaderUiState())
    val readerState: StateFlow<ReaderUiState> = _readerState.asStateFlow()

    private val _telegramPreview = MutableStateFlow(TelegramPreviewState())
    val telegramPreview: StateFlow<TelegramPreviewState> = _telegramPreview.asStateFlow()

    private val _geminiKeyStatus = MutableStateFlow<KeyVerificationState>(KeyVerificationState.Idle)
    val geminiKeyStatus: StateFlow<KeyVerificationState> = _geminiKeyStatus.asStateFlow()

    private val _ollamaKeyStatus = MutableStateFlow<KeyVerificationState>(KeyVerificationState.Idle)
    val ollamaKeyStatus: StateFlow<KeyVerificationState> = _ollamaKeyStatus.asStateFlow()

    private val _deeplKeyStatus = MutableStateFlow<KeyVerificationState>(KeyVerificationState.Idle)
    val deeplKeyStatus: StateFlow<KeyVerificationState> = _deeplKeyStatus.asStateFlow()

    private val _backupSelection = MutableStateFlow(BackupSelection())
    val backupSelectionState: StateFlow<BackupSelection> = _backupSelection.asStateFlow()

    private val _pendingImportContent = MutableStateFlow<String?>(null)
    val pendingImportContent: StateFlow<String?> = _pendingImportContent.asStateFlow()

    private val _pendingImportPreview = MutableStateFlow<PendingImportPreview?>(null)
    val pendingImportPreview: StateFlow<PendingImportPreview?> = _pendingImportPreview.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<AppSnackbarEvent>(extraBufferCapacity = 6)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private var readerAiJob: Job? = null
    private var articlePrefetchJob: Job? = null
    private var syncProgressHideJob: Job? = null
    private val syncMutex = Mutex()
    @Volatile
    private var wasInBackground = false
    @Volatile
    private var lastStoppedAtMs = 0L
    private var lastClearedReadBackup: List<ArticleEntity> = emptyList()
    private var lastDeletedFeedBackup: DeletedFeedBackup? = null
    private var wasDeletedFeedSelected: Boolean = false

    val homeArticlesState: StateFlow<List<ArticleWithFeed>> = combine(
        allArticlesState,
        customListsState,
        _selectedFeedId,
        _selectedListName
    ) { all, lists, feedId, listName ->
        filterAndSortHomeArticles(
            all = all,
            customLists = lists,
            selectedFeedId = feedId,
            selectedListName = listName
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedArticlesState: StateFlow<List<ArticleWithFeed>> = combine(allArticlesState) { (all) ->
        all.filter { it.article.isSaved }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val readArticlesState: StateFlow<List<ArticleWithFeed>> = combine(allArticlesState) { (all) ->
        all.filter { it.article.isRead }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notifiedArticlesState: StateFlow<List<ArticleWithFeed>> = rssRepo
        .observeNotifiedArticles()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notifiedUnreadCountState: StateFlow<Int> = rssRepo
        .observeNotifiedUnreadCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val searchResultsState: StateFlow<List<ArticleWithFeed>> = combine(
        allArticlesState,
        _searchState
    ) { all, filter ->
        val q = filter.query.trim().lowercase()
        all.filter { item ->
            val a = item.article
            val hasReadUnreadFilter = filter.filterUnread || filter.filterRead
            val matchesReadState = when {
                !hasReadUnreadFilter -> true
                filter.filterUnread && filter.filterRead -> true
                filter.filterUnread -> !a.isRead
                else -> a.isRead
            }
            val matchesSavedState = if (filter.filterSaved) a.isSaved else true

            if (!matchesReadState || !matchesSavedState) return@filter false

            if (filter.selectedFeedIds.isNotEmpty() && a.feedId !in filter.selectedFeedIds) return@filter false
            if (filter.excludedFeedIds.isNotEmpty() && a.feedId in filter.excludedFeedIds) return@filter false

            if (q.isEmpty()) {
                true
            } else {
                a.title.lowercase().contains(q) ||
                    (a.translatedTitle?.lowercase()?.contains(q) == true) ||
                    a.rawContent.lowercase().contains(q) ||
                    (a.aiTranslatedBody?.lowercase()?.contains(q) == true) ||
                    (a.aiSummarizedBody?.lowercase()?.contains(q) == true) ||
                    item.feedTitle.lowercase().contains(q) ||
                    item.feedGroupName.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            // Load initial Room + DataStore state BEFORE rendering UI so there is zero empty-screen flash
            val initialSettings = settingsRepo.settingsFlow.first()
            val resume = settingsRepo.sessionResumeFlow.first()
            val initialFeeds = rssRepo.feedsWithCountsFlow.first()
            val initialCustomLists = rssRepo.customListsFlow.first()
            rssRepo.allArticlesFlow.first()

            val validFeedId = resume.selectedFeedId?.takeIf { id -> initialFeeds.any { it.feed.id == id } }
            val validListName = resume.selectedListName?.takeIf { name ->
                initialCustomLists.any { it.name.equals(name, ignoreCase = true) }
            }
            _selectedFeedId.value = validFeedId
            _selectedListName.value = validListName
            _isInitialBootReady.value = true

            // Immediately warm up full readable bodies for any existing short unread articles in Room
            triggerBackgroundArticlePrefetch()

            // Refresh free Gemini & Ollama models lists in background (immediately on first run, then every 12h)
            viewModelScope.launch {
                val twelveHoursMs = 12 * 60 * 60 * 1000L
                val now = System.currentTimeMillis()
                if (initialSettings.lastModelsSyncMs == 0L || now - initialSettings.lastModelsSyncMs > twelveHoursMs) {
                    rssRepo.refreshFreeModels()
                }
                if (initialSettings.lastOllamaModelsSyncMs == 0L ||
                    now - initialSettings.lastOllamaModelsSyncMs > twelveHoursMs
                ) {
                    rssRepo.refreshFreeOllamaModels()
                }
            }

            // Immediately sync all feeds on every app launch while showing the pull-to-refresh spinner
            if (initialFeeds.isNotEmpty()) {
                _isPullSyncing.value = true
                try {
                    syncAllFeedsInternal(initialSettings = initialSettings, isStartupSync = true)
                } finally {
                    _isPullSyncing.value = false
                }
            }
        }
    }

    fun observeSingleArticle(articleId: Long) = rssRepo.observeArticle(articleId)

    fun getCachedArticle(articleId: Long): ArticleWithFeed? =
        allArticlesState.value.find { it.article.id == articleId }

    private fun triggerBackgroundArticlePrefetch() {
        if (articlePrefetchJob?.isActive == true) return
        articlePrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                rssRepo.prefetchShortUnreadArticles(limit = 36)
            }
        }
    }

    fun onAppStopped() {
        wasInBackground = true
        lastStoppedAtMs = System.currentTimeMillis()
    }

    fun onAppStarted() {
        if (!wasInBackground) return
        val stoppedDurationMs = System.currentTimeMillis() - lastStoppedAtMs
        wasInBackground = false
        if (_isInitialBootReady.value &&
            !_isImportingOpml.value &&
            stoppedDurationMs >= 30_000L &&
            feedsState.value.isNotEmpty()
        ) {
            viewModelScope.launch {
                _isPullSyncing.value = true
                try {
                    syncAllFeedsInternal(initialSettings = settingsState.value, isStartupSync = true)
                } finally {
                    _isPullSyncing.value = false
                }
            }
        }
    }

    fun saveLastTab(tabKey: String) {
        viewModelScope.launch {
            settingsRepo.saveLastTab(tabKey)
        }
    }

    fun saveScrollPosition(tabKey: String, index: Int, offset: Int) {
        viewModelScope.launch {
            settingsRepo.saveScrollPosition(tabKey, index, offset)
        }
    }

    fun selectFeed(feedId: Long?) {
        _selectedFeedId.value = feedId
        viewModelScope.launch {
            settingsRepo.saveSelectedFilters(feedId, _selectedListName.value)
            settingsRepo.saveScrollPosition(SessionResumeState.TAB_FEEDS, 0, 0)
        }
    }

    fun selectCustomList(listName: String?) {
        _selectedListName.value = listName
        if (!listName.isNullOrBlank()) {
            val currentId = _selectedFeedId.value
            val matching = feedsState.value.find { it.feed.id == currentId }
            if (matching != null && !matching.feed.groupName.equals(listName, ignoreCase = true)) {
                _selectedFeedId.value = null
            }
        }
        viewModelScope.launch {
            settingsRepo.saveSelectedFilters(_selectedFeedId.value, _selectedListName.value)
            settingsRepo.saveScrollPosition(SessionResumeState.TAB_FEEDS, 0, 0)
        }
    }

    // --- Custom Lists Actions ---

    fun createCustomList(
        rawName: String,
        showInAllLists: Boolean = true,
        syncEnabled: Boolean = true,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            rssRepo.createCustomList(
                rawName = rawName,
                showInAllLists = showInAllLists,
                syncEnabled = syncEnabled
            )
                .onSuccess { cleanName ->
                    emitMessage("Created list \"$cleanName\"")
                    onCreated(cleanName)
                }
                .onFailure { err ->
                    emitMessage(err.message ?: "Could not create list")
                }
        }
    }

    fun renameCustomList(
        list: CustomListEntity,
        newRawName: String,
        showInAllLists: Boolean = list.showInAllLists,
        syncEnabled: Boolean = list.syncEnabled
    ) {
        viewModelScope.launch {
            rssRepo.renameCustomList(
                list = list,
                newRawName = newRawName,
                showInAllLists = showInAllLists,
                syncEnabled = syncEnabled
            )
                .onSuccess { cleanName ->
                    if (_selectedListName.value?.equals(list.name, ignoreCase = true) == true) {
                        _selectedListName.value = cleanName
                        settingsRepo.saveSelectedFilters(_selectedFeedId.value, cleanName)
                    }
                    val msg = if (!list.name.equals(cleanName, ignoreCase = false)) {
                        "Renamed list to \"$cleanName\""
                    } else {
                        "Updated list \"$cleanName\""
                    }
                    emitMessage(msg)
                }
                .onFailure { err ->
                    emitMessage(err.message ?: "Could not rename list")
                }
        }
    }

    fun toggleCustomListShowInAllLists(list: CustomListEntity) {
        viewModelScope.launch {
            val enabled = rssRepo.toggleCustomListShowInAllLists(list)
            emitMessage(
                if (enabled) "\"${list.name}\" enabled in All Lists"
                else "\"${list.name}\" hidden from All Lists"
            )
        }
    }

    fun toggleCustomListSyncEnabled(list: CustomListEntity) {
        viewModelScope.launch {
            val enabled = rssRepo.toggleCustomListSyncEnabled(list)
            emitMessage(
                if (enabled) "Auto-refresh enabled for \"${list.name}\""
                else "Auto-refresh disabled for \"${list.name}\""
            )
        }
    }

    fun toggleCustomListPinned(list: CustomListEntity) {
        viewModelScope.launch {
            val isPinned = rssRepo.toggleCustomListPinned(list)
            emitMessage(
                if (isPinned) "List \"${list.name}\" pinned"
                else "List \"${list.name}\" unpinned"
            )
        }
    }

    fun toggleCustomListNotify(list: CustomListEntity) {
        viewModelScope.launch {
            val listFeeds = feedsState.value.filter {
                it.feed.groupName.equals(list.name.trim(), ignoreCase = true)
            }
            if (listFeeds.isEmpty()) return@launch
            val anyNotified = listFeeds.any { it.feed.notifyEnabled }
            val nextState = !anyNotified
            listFeeds.forEach { item ->
                rssRepo.toggleFeedNotify(item.feed.id, nextState)
            }
            emitMessage(
                if (nextState) "Notifications enabled for \"${list.name}\""
                else "Notifications disabled for \"${list.name}\""
            )
        }
    }

    fun syncCustomListFeeds(list: CustomListEntity) {
        val matchingFeeds = feedsState.value.filter {
            it.feed.groupName.equals(list.name.trim(), ignoreCase = true)
        }
        val matchingFeedIds = matchingFeeds.map { it.feed.id }.toSet()
        if (matchingFeedIds.isNotEmpty() && matchingFeedIds.all { it in _syncingFeedIds.value }) return

        viewModelScope.launch {
            if (matchingFeedIds.isNotEmpty()) {
                _syncingFeedIds.update { it + matchingFeedIds }
            }
            syncProgressHideJob?.cancel()
            val expectedCount = matchingFeeds.count { it.feed.syncEnabled }.let {
                if (it > 0) it else matchingFeeds.size
            }
            if (expectedCount > 0) {
                _syncProgress.value = FeedSyncProgress(
                    isVisible = true,
                    checkedCount = 0,
                    totalCount = expectedCount
                )
            }
            try {
                val res = rssRepo.syncCustomListFeeds(
                    listName = list.name,
                    settings = settingsState.value,
                    onProgress = ::reportFeedSyncProgress
                )
                res.onSuccess { added ->
                    if (added > 0) {
                        triggerBackgroundArticlePrefetch()
                    }
                    if (matchingFeeds.isEmpty() && !_syncProgress.value.isVisible) {
                        emitMessage("No feeds in \"${list.name}\"")
                    } else if (added > 0) {
                        emitMessage("${list.name}: $added new posts")
                    } else {
                        emitMessage("${list.name} is up to date")
                    }
                }.onFailure { err ->
                    emitMessage("${list.name}: ${err.message ?: "Refresh failed"}")
                }
            } finally {
                if (matchingFeedIds.isNotEmpty()) {
                    _syncingFeedIds.update { it - matchingFeedIds }
                }
                val endState = _syncProgress.value
                if (endState.isVisible && endState.totalCount > 0) {
                    _syncProgress.value = endState.copy(
                        isVisible = true,
                        checkedCount = endState.totalCount
                    )
                    scheduleHideSyncProgress()
                }
            }
        }
    }

    fun reorderCustomLists(orderedIds: List<Long>) {
        viewModelScope.launch {
            rssRepo.reorderCustomLists(orderedIds)
        }
    }

    fun deleteCustomList(list: CustomListEntity, deleteFeedsInside: Boolean = false) {
        viewModelScope.launch {
            if (_selectedListName.value?.equals(list.name, ignoreCase = true) == true) {
                _selectedListName.value = null
            }
            if (deleteFeedsInside) {
                val activeFeedId = _selectedFeedId.value
                val selectedFeedInList = activeFeedId != null &&
                    feedsState.value.any {
                        it.feed.id == activeFeedId && it.feed.groupName.equals(list.name, ignoreCase = true)
                    }
                if (selectedFeedInList) {
                    _selectedFeedId.value = null
                }
            }
            settingsRepo.saveSelectedFilters(_selectedFeedId.value, _selectedListName.value)
            rssRepo.deleteCustomList(list, deleteFeedsInside = deleteFeedsInside)
            emitMessage(
                if (deleteFeedsInside) "Deleted list \"${list.name}\" and its feeds"
                else "Deleted list \"${list.name}\" (feeds kept)"
            )
        }
    }

    // --- Backup & Restore (Selective JSON + OPML) ---

    fun updateBackupSelection(selection: BackupSelection) {
        _backupSelection.value = selection
    }

    fun exportBackupToUri(context: Context, targetUri: Uri, selection: BackupSelection = _backupSelection.value) {
        if (!selection.hasAnySelection) {
            emitMessage("Please select at least one item to export")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = rssRepo.exportBackupJson(selection)
                    context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IllegalStateException("Unable to open selected file for writing")
                }
            }
            result.onSuccess {
                emitMessage("Backup exported successfully")
            }.onFailure { err ->
                emitMessage(err.message ?: "Backup export failed")
            }
        }
    }

    private fun executeBackupImportContent(
        rawContent: String,
        selection: BackupSelection,
        allowedFeedUrls: Set<String>? = null
    ) {
        if (_isImportingOpml.value) return
        if (!selection.hasAnySelection) {
            emitMessage("Please select at least one item to import")
            return
        }
        viewModelScope.launch {
            _isImportingOpml.value = true
            syncProgressHideJob?.cancel()
            emitMessage("Importing backup…")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    rssRepo.importBackupContent(
                        rawContent = rawContent,
                        selection = selection,
                        settings = settingsState.value,
                        allowedFeedUrls = allowedFeedUrls,
                        syncAfterFeedImport = true,
                        onProgress = ::reportFeedSyncProgress
                    ).getOrThrow()
                }
            }
            _isImportingOpml.value = false
            scheduleHideSyncProgress()
            result.onSuccess { summary ->
                triggerBackgroundArticlePrefetch()
                emitMessage(summary.toUserMessage())
            }.onFailure { err ->
                emitMessage(err.message ?: "Backup import failed")
            }
        }
    }

    // Unified Smart Import used by Home Empty State, Add Feed Dialog, and Settings Backup & Restore
    fun handleSmartImportFromUri(context: Context, sourceUri: Uri) {
        if (_isImportingOpml.value) return
        viewModelScope.launch {
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = context.contentResolver.openInputStream(sourceUri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("Unable to read selected file")
                    rssRepo.inspectImportContent(raw).getOrThrow()
                }
            }
            readResult.onSuccess { preview ->
                val defaultImportSelection = BackupSelection(
                    includeFeeds = true,
                    includeLists = true,
                    includeSaved = false,
                    includeRead = false
                )
                if (!preview.isAppFormatBackup && preview.feeds.isEmpty()) {
                    executeBackupImportContent(
                        rawContent = preview.rawContent,
                        selection = defaultImportSelection,
                        allowedFeedUrls = null
                    )
                } else {
                    _backupSelection.value = defaultImportSelection
                    _pendingImportContent.value = preview.rawContent
                    _pendingImportPreview.value = preview
                }
            }.onFailure { err ->
                emitMessage(err.message ?: "Backup import failed")
            }
        }
    }

    fun confirmPendingBackupImport(
        selection: BackupSelection = _backupSelection.value,
        selectedFeedUrls: Set<String>? = null
    ) {
        val pending = _pendingImportPreview.value?.rawContent ?: _pendingImportContent.value ?: return
        _pendingImportPreview.value = null
        _pendingImportContent.value = null
        _backupSelection.value = selection
        executeBackupImportContent(
            rawContent = pending,
            selection = selection,
            allowedFeedUrls = selectedFeedUrls
        )
    }

    fun cancelPendingBackupImport() {
        _pendingImportPreview.value = null
        _pendingImportContent.value = null
    }

    fun importBackupFromUri(context: Context, sourceUri: Uri, selection: BackupSelection = _backupSelection.value) {
        if (_isImportingOpml.value) return
        if (!selection.hasAnySelection) {
            emitMessage("Please select at least one item to import")
            return
        }
        viewModelScope.launch {
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(sourceUri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("Unable to read selected file")
                }
            }
            readResult.onSuccess { rawContent ->
                executeBackupImportContent(rawContent = rawContent, selection = selection)
            }.onFailure { err ->
                emitMessage(err.message ?: "Backup import failed")
            }
        }
    }

    fun exportOpmlToUri(context: Context, targetUri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val xml = rssRepo.exportOpmlXml()
                    context.contentResolver.openOutputStream(targetUri, "wt")?.use { out ->
                        out.write(xml.toByteArray(Charsets.UTF_8))
                        out.flush()
                    } ?: throw IllegalStateException("Unable to open selected file for writing")
                }
            }
            result.onSuccess {
                emitMessage("Exported OPML subscriptions")
            }.onFailure { err ->
                emitMessage(err.message ?: "OPML export failed")
            }
        }
    }

    fun importOpmlFromUri(context: Context, sourceUri: Uri) {
        handleSmartImportFromUri(context = context, sourceUri = sourceUri)
    }

    // --- Search ---

    fun prepareSearch(
        seedQuery: String = "",
        filterUnread: Boolean = false,
        filterRead: Boolean = false,
        filterSaved: Boolean = false
    ) {
        _searchState.value = SearchFilterState(
            query = seedQuery,
            filterUnread = filterUnread,
            filterRead = filterRead,
            filterSaved = filterSaved
        )
    }

    fun updateSearchQuery(query: String) {
        _searchState.update { it.copy(query = query) }
    }

    fun resetSearchFiltersToAll() {
        _searchState.update {
            it.copy(filterUnread = false, filterRead = false, filterSaved = false)
        }
    }

    fun toggleSearchUnread() {
        _searchState.update { it.copy(filterUnread = !it.filterUnread) }
    }

    fun toggleSearchRead() {
        _searchState.update { it.copy(filterRead = !it.filterRead) }
    }

    fun toggleSearchSaved() {
        _searchState.update { it.copy(filterSaved = !it.filterSaved) }
    }

    fun toggleSearchFeedSelection(feedId: Long) {
        _searchState.update { current ->
            val nextSelected = if (feedId in current.selectedFeedIds) {
                current.selectedFeedIds - feedId
            } else {
                current.selectedFeedIds + feedId
            }
            current.copy(selectedFeedIds = nextSelected, excludedFeedIds = current.excludedFeedIds - feedId)
        }
    }

    fun toggleSearchFeedExclusion(feedId: Long) {
        _searchState.update { current ->
            val nextExcluded = if (feedId in current.excludedFeedIds) {
                current.excludedFeedIds - feedId
            } else {
                current.excludedFeedIds + feedId
            }
            current.copy(excludedFeedIds = nextExcluded, selectedFeedIds = current.selectedFeedIds - feedId)
        }
    }

    fun clearSearchFeedFilters() {
        _searchState.update { it.copy(selectedFeedIds = emptySet(), excludedFeedIds = emptySet()) }
    }

    // --- Feeds & Articles ---

    fun addFeed(
        url: String,
        groupName: String,
        autoTranslate: Boolean,
        defaultReaderMode: ReaderAiMode = ReaderAiMode.NONE,
        showInAllFeeds: Boolean = true,
        showInAllLists: Boolean = true,
        syncEnabled: Boolean = true,
        notifyEnabled: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        if (url.isBlank() || _isAddingFeed.value) return
        viewModelScope.launch {
            _isAddingFeed.value = true
            val res = rssRepo.addFeed(
                rawUrl = url,
                customGroup = groupName,
                autoTranslate = autoTranslate,
                settings = settingsState.value,
                defaultReaderMode = defaultReaderMode,
                showInAllFeeds = showInAllFeeds,
                showInAllLists = showInAllLists,
                syncEnabled = syncEnabled,
                notifyEnabled = notifyEnabled
            )
            _isAddingFeed.value = false
            res.onSuccess { feed ->
                triggerBackgroundArticlePrefetch()
                val suffix = if (feed.autoTranslate) " (Translate Titles ON)" else ""
                emitMessage("Added ${feed.title}$suffix")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Failed to add feed")
            }
        }
    }

    fun toggleCustomListAutoTranslate(list: CustomListEntity) {
        viewModelScope.launch {
            val listFeeds = feedsState.value.filter {
                it.feed.groupName.equals(list.name.trim(), ignoreCase = true)
            }.map { it.feed }
            if (listFeeds.isEmpty()) {
                emitMessage("No feeds in list \"${list.name}\"")
                return@launch
            }
            val shouldEnable = listFeeds.any { !it.autoTranslate }
            for (feed in listFeeds) {
                rssRepo.updateFeed(feed.copy(autoTranslate = shouldEnable))
                if (shouldEnable) {
                    launch { rssRepo.autoTranslateFeedPosts(feed.id, settingsState.value) }
                }
            }
            emitMessage(
                if (shouldEnable) "Enabled title translation for all feeds in \"${list.name}\""
                else "Disabled title translation for all feeds in \"${list.name}\""
            )
        }
    }

    fun clearNotifiedArticles() {
        viewModelScope.launch {
            rssRepo.markAllNotifiedArticlesRead()
            emitMessage("Cleared priority notifications")
        }
    }

    fun setFeedReaderMode(feed: FeedEntity, mode: ReaderAiMode) {
        viewModelScope.launch {
            rssRepo.updateFeed(feed.copy(defaultReaderMode = mode.name))
            emitMessage("Updated reader mode for \"${feed.title}\" to ${mode.label}")
        }
    }

    /**
     * Triggered ONLY when the user explicitly clicks the top-bar Refresh icon.
     * Spins ONLY the top-bar icon until all posts are fetched, then immediately stops spinning.
     */
    fun syncAllFeedsFromIcon() {
        if (_isIconSyncing.value) return
        viewModelScope.launch {
            _isIconSyncing.value = true
            try {
                syncAllFeedsInternal(initialSettings = settingsState.value, isStartupSync = false)
            } finally {
                _isIconSyncing.value = false
            }
        }
    }

    /**
     * Triggered ONLY when the user pulls down on PullToRefreshBox (or taps Sync in empty state).
     * Shows ONLY the pull-to-refresh indicator — NEVER spins the top-bar Refresh icon.
     */
    fun syncAllFeedsFromPull() {
        if (_isPullSyncing.value) return
        viewModelScope.launch {
            _isPullSyncing.value = true
            try {
                syncAllFeedsInternal(initialSettings = settingsState.value, isStartupSync = false)
            } finally {
                _isPullSyncing.value = false
            }
        }
    }

    /**
     * Refreshes ONLY the single long-pressed feed without touching other feeds or spinning the global icon.
     */
    fun syncSingleFeed(feed: FeedEntity) {
        if (feed.id in _syncingFeedIds.value) return
        viewModelScope.launch {
            _syncingFeedIds.update { it + feed.id }
            try {
                val res = rssRepo.syncSingleFeed(feed, settingsState.value)
                res.onSuccess { added ->
                    if (added > 0) {
                        triggerBackgroundArticlePrefetch()
                        emitMessage("${feed.title}: $added new posts")
                    } else {
                        emitMessage("${feed.title} is up to date")
                    }
                }.onFailure { err ->
                    emitMessage("${feed.title}: ${err.message ?: "Refresh failed"}")
                }
            } finally {
                _syncingFeedIds.update { it - feed.id }
            }
        }
    }

    private fun reportFeedSyncProgress(checked: Int, total: Int, currentFeed: FeedEntity? = null) {
        if (total <= 0) return
        val clamped = checked.coerceIn(0, total)
        _syncProgress.update { current ->
            FeedSyncProgress(
                isVisible = true,
                checkedCount = if (current.isVisible && current.totalCount == total) {
                    maxOf(current.checkedCount, clamped)
                } else {
                    clamped
                },
                totalCount = total,
                currentFeedTitle = currentFeed?.title?.ifBlank { current.currentFeedTitle } ?: current.currentFeedTitle,
                currentFeedIconUrl = currentFeed?.iconUrl ?: current.currentFeedIconUrl
            )
        }
    }

    private fun scheduleHideSyncProgress() {
        syncProgressHideJob?.cancel()
        syncProgressHideJob = viewModelScope.launch {
            delay(900L)
            _syncProgress.update { it.copy(isVisible = false) }
        }
    }

    private fun countActiveSyncableFeeds(): Int {
        val disabledListNamesLower = customListsState.value
            .asSequence()
            .filter { !it.syncEnabled }
            .map { it.name.trim().lowercase(Locale.ROOT) }
            .toHashSet()
        return feedsState.value.count { item ->
            val feed = item.feed
            val listSyncEnabled = feed.groupName.isBlank() ||
                feed.groupName.trim().lowercase(Locale.ROOT) !in disabledListNamesLower
            feed.syncEnabled && listSyncEnabled
        }
    }

    private suspend fun syncAllFeedsInternal(
        initialSettings: AppSettings,
        isStartupSync: Boolean
    ) {
        syncMutex.withLock {
            syncProgressHideJob?.cancel()
            val knownTotal = countActiveSyncableFeeds()
            if (knownTotal > 0) {
                _syncProgress.value = FeedSyncProgress(
                    isVisible = true,
                    checkedCount = 0,
                    totalCount = knownTotal
                )
            } else {
                _syncProgress.value = FeedSyncProgress(isVisible = false, checkedCount = 0, totalCount = 0)
            }
            val activeSettings = settingsState.value.let { current ->
                if (current == AppSettings()) initialSettings else current
            }
            try {
                val result = rssRepo.syncAllFeeds(
                    settings = activeSettings,
                    onProgress = ::reportFeedSyncProgress
                )
                result.onSuccess {
                    triggerBackgroundArticlePrefetch()
                    if (!isStartupSync && feedsState.value.isNotEmpty() && !_syncProgress.value.isVisible) {
                        emitMessage("Auto-refresh is disabled for all feeds")
                    }
                }.onFailure { err ->
                    if (!isStartupSync) {
                        emitMessage(err.message ?: "Sync failed")
                    }
                }
            } finally {
                val endState = _syncProgress.value
                if (endState.isVisible && endState.totalCount > 0) {
                    _syncProgress.value = endState.copy(
                        isVisible = true,
                        checkedCount = endState.totalCount
                    )
                    scheduleHideSyncProgress()
                }
            }
        }
    }

    fun updateFeedDetails(
        feed: FeedEntity,
        newTitle: String,
        newGroup: String,
        autoTranslate: Boolean,
        defaultReaderMode: ReaderAiMode = feed.resolvedReaderMode,
        showInAllFeeds: Boolean = feed.showInAllFeeds,
        showInAllLists: Boolean = feed.showInAllLists,
        syncEnabled: Boolean = feed.syncEnabled
    ) {
        viewModelScope.launch {
            rssRepo.updateFeedDetails(
                feed = feed,
                newTitle = newTitle,
                newGroup = newGroup,
                autoTranslate = autoTranslate,
                settings = settingsState.value,
                defaultReaderMode = defaultReaderMode,
                showInAllFeeds = showInAllFeeds,
                showInAllLists = showInAllLists,
                syncEnabled = syncEnabled
            )
            val msg = if (autoTranslate && !feed.autoTranslate) {
                "Auto-translating ${newTitle.ifBlank { feed.title }}…"
            } else {
                "Feed updated"
            }
            emitMessage(msg)
        }
    }

    fun toggleFeedAutoTranslate(feed: FeedEntity) {
        val next = !feed.autoTranslate
        updateFeedDetails(
            feed = feed,
            newTitle = feed.title,
            newGroup = feed.groupName,
            autoTranslate = next,
            defaultReaderMode = feed.resolvedReaderMode,
            showInAllFeeds = feed.showInAllFeeds,
            showInAllLists = feed.showInAllLists,
            syncEnabled = feed.syncEnabled
        )
    }

    fun toggleFeedShowInAllFeeds(feed: FeedEntity) {
        viewModelScope.launch {
            val enabled = rssRepo.toggleFeedShowInAllFeeds(feed)
            emitMessage(
                if (enabled) "${feed.title} enabled in All Feeds"
                else "${feed.title} hidden from All Feeds"
            )
        }
    }

    fun toggleFeedShowInAllLists(feed: FeedEntity) {
        viewModelScope.launch {
            val enabled = rssRepo.toggleFeedShowInAllLists(feed)
            emitMessage(
                if (enabled) "${feed.title} enabled in Lists"
                else "${feed.title} hidden from Lists"
            )
        }
    }

    fun toggleFeedSyncEnabled(feed: FeedEntity) {
        viewModelScope.launch {
            val enabled = rssRepo.toggleFeedSyncEnabled(feed)
            emitMessage(
                if (enabled) "Auto-refresh enabled for ${feed.title}"
                else "Auto-refresh disabled for ${feed.title}"
            )
        }
    }

    fun toggleFeedPinned(feed: FeedEntity) {
        viewModelScope.launch {
            val pinned = rssRepo.toggleFeedPinned(feed)
            emitMessage(
                if (pinned) "Pinned ${feed.title} next to All"
                else "Unpinned ${feed.title}"
            )
        }
    }

    fun toggleFeedNotify(feed: FeedEntity) {
        viewModelScope.launch {
            val next = !feed.notifyEnabled
            rssRepo.toggleFeedNotify(feed.id, next)
            emitMessage(
                if (next) "In-app notifications enabled for ${feed.title}"
                else "In-app notifications disabled for ${feed.title}"
            )
        }
    }

    fun markAllNotifiedArticlesRead() {
        viewModelScope.launch {
            rssRepo.markAllNotifiedArticlesRead()
            emitMessage("Marked all priority notifications as read")
        }
    }

    fun observeFiltersForFeed(feedId: Long): Flow<List<FeedFilterEntity>> =
        rssRepo.observeFiltersForFeed(feedId)

    fun addFeedFilter(feedId: Long, pattern: String, isRegex: Boolean, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = rssRepo.addFilter(feedId, pattern, isRegex)
            onDone(count)
            emitMessage("Filter added (cleaned $count posts)")
        }
    }

    fun removeFeedFilter(filterId: Long) {
        viewModelScope.launch {
            rssRepo.removeFilter(filterId)
            emitMessage("Filter removed")
        }
    }

    fun reorderFeeds(orderedIds: List<Long>) {
        viewModelScope.launch {
            rssRepo.reorderFeeds(orderedIds)
        }
    }

    fun deleteFeed(feed: FeedEntity) {
        viewModelScope.launch {
            val wasSelected = _selectedFeedId.value == feed.id
            if (wasSelected) {
                _selectedFeedId.value = null
                settingsRepo.saveSelectedFilters(null, _selectedListName.value)
            }
            val backup = rssRepo.deleteFeedWithBackup(feed.id)
            if (backup != null) {
                lastDeletedFeedBackup = backup
                wasDeletedFeedSelected = wasSelected
                _snackbarEvents.tryEmit(
                    AppSnackbarEvent(
                        message = "Removed ${feed.title}",
                        actionLabel = "Undo",
                        timeoutMs = 5_000L,
                        undoAction = SnackbarUndoAction.DELETE_FEED
                    )
                )
            } else {
                emitMessage("Removed ${feed.title}")
            }
        }
    }

    fun undoDeleteFeed() {
        val backup = lastDeletedFeedBackup ?: return
        val restoreSelection = wasDeletedFeedSelected
        lastDeletedFeedBackup = null
        wasDeletedFeedSelected = false
        viewModelScope.launch {
            rssRepo.restoreDeletedFeed(backup)
            if (restoreSelection && _selectedFeedId.value == null) {
                _selectedFeedId.value = backup.feed.id
                settingsRepo.saveSelectedFilters(backup.feed.id, _selectedListName.value)
            }
            emitMessage("Restored ${backup.feed.title}")
        }
    }

    fun markFeedAllRead(feed: FeedEntity) {
        viewModelScope.launch {
            rssRepo.markFeedAllRead(feed.id)
            emitMessage("Marked ${feed.title} as read")
        }
    }

    fun markAllArticlesRead() {
        viewModelScope.launch {
            rssRepo.markAllRead()
            emitMessage("Marked all as read")
        }
    }

    fun clearReadHistory() {
        viewModelScope.launch {
            val cleared = rssRepo.clearReadHistory()
            if (cleared.isNotEmpty()) {
                lastClearedReadBackup = cleared
                settingsRepo.saveScrollPosition(SessionResumeState.TAB_READ, 0, 0)
                _snackbarEvents.tryEmit(
                    AppSnackbarEvent(
                        message = "Cleared ${cleared.size} read posts",
                        actionLabel = "Undo",
                        timeoutMs = 5_000L,
                        undoAction = SnackbarUndoAction.CLEAR_READ
                    )
                )
            } else {
                emitMessage("No unsaved read posts to clear")
            }
        }
    }

    fun undoClearReadHistory() {
        val backup = lastClearedReadBackup
        if (backup.isEmpty()) return
        lastClearedReadBackup = emptyList()
        viewModelScope.launch {
            rssRepo.restoreClearedArticles(backup)
            emitMessage("Restored ${backup.size} read posts")
        }
    }

    fun toggleArticleRead(articleId: Long, markRead: Boolean) {
        viewModelScope.launch {
            rssRepo.setArticleRead(articleId, markRead)
        }
    }

    fun toggleArticleSaved(articleId: Long, save: Boolean) {
        viewModelScope.launch {
            rssRepo.setArticleSaved(articleId, save)
        }
    }

    fun translateCardHeadline(item: ArticleWithFeed) {
        val id = item.article.id
        if (id in _translatingCardIds.value) return
        viewModelScope.launch {
            _translatingCardIds.update { it + id }
            val res = rssRepo.translateOrToggleCardTitle(item.article, settingsState.value)
            _translatingCardIds.update { it - id }
            res.onFailure { err ->
                emitMessage(err.message ?: "Headline translation failed")
            }
        }
    }

    // --- Reader Flow (Gemini vs DeepL for Translation; Gemini single-paragraph for Summarization) ---

    fun openArticleInReader(articleId: Long) {
        viewModelScope.launch {
            rssRepo.setArticleRead(articleId, true)
        }
        runReaderMode(articleId = articleId, explicitMode = null, forceRefresh = false)
    }

    fun switchReaderAiMode(articleId: Long, targetMode: ReaderAiMode, forceRefresh: Boolean = false) {
        runReaderMode(articleId = articleId, explicitMode = targetMode, forceRefresh = forceRefresh)
    }

    private fun resolveEngineBadge(mode: ReaderAiMode, settings: AppSettings): String = when (mode) {
        ReaderAiMode.NONE -> ""
        ReaderAiMode.TRANSLATE -> when (settings.titleProvider) {
            TitleTranslationProvider.GEMINI -> settings.selectedModel
            TitleTranslationProvider.OLLAMA -> settings.selectedOllamaModel
            else -> settings.titleProvider.label
        }
        ReaderAiMode.SUMMARIZE -> {
            if (settings.summaryProvider == SummaryProvider.OLLAMA) {
                settings.selectedOllamaModel
            } else {
                settings.selectedModel
            }
        }
    }

    private fun resolveCachedAiText(
        article: ArticleEntity,
        mode: ReaderAiMode,
        settings: AppSettings
    ): String? {
        val targetIso = settings.targetLanguage.isoCode
        if (article.aiProcessedLang != targetIso) return null
        return when (mode) {
            ReaderAiMode.NONE -> null
            ReaderAiMode.TRANSLATE -> {
                val chosen = settings.titleProvider
                val customPrompt = settings.activeTranslatePromptOverride
                val key = if ((chosen == TitleTranslationProvider.GEMINI || chosen == TitleTranslationProvider.OLLAMA) && customPrompt != null) {
                    "${chosen.name}:cp_${customPrompt.hashCode()}"
                } else {
                    chosen.name
                }
                article.aiTranslatedBody?.takeIf { it.isNotBlank() && article.aiTranslatedEngine == key }
            }
            ReaderAiMode.SUMMARIZE -> {
                val chosen = settings.summaryProvider
                val customPrompt = settings.activeSummarizePromptOverride
                val key = if (customPrompt != null) {
                    "${chosen.name}:cp_${customPrompt.hashCode()}"
                } else {
                    chosen.name
                }
                val storedEngine = article.aiSummarizedEngine ?: SummaryProvider.GEMINI.name
                article.aiSummarizedBody?.takeIf { it.isNotBlank() && storedEngine == key }
            }
        }
    }

    private fun runReaderMode(
        articleId: Long,
        explicitMode: ReaderAiMode?,
        forceRefresh: Boolean
    ) {
        readerAiJob?.cancel()
        val currentSettings = settingsState.value
        val cachedItem = getCachedArticle(articleId)

        if (cachedItem != null) {
            val cachedFeedMode = runCatching {
                ReaderAiMode.valueOf(cachedItem.feedDefaultReaderMode)
            }.getOrDefault(ReaderAiMode.NONE)
            val resolvedMode = when {
                explicitMode != null -> explicitMode
                cachedFeedMode == ReaderAiMode.SUMMARIZE && !currentSettings.canSummarizeWithAi -> ReaderAiMode.NONE
                else -> cachedFeedMode
            }
            val badge = resolveEngineBadge(resolvedMode, currentSettings)

            if (!forceRefresh) {
                if (resolvedMode == ReaderAiMode.NONE) {
                    val existingRaw = cachedItem.article.rawContent
                    val needsWebUpgrade = existingRaw.length < 480 && cachedItem.article.link.startsWith("http")
                    if (existingRaw.isNotBlank() || !needsWebUpgrade) {
                        // Display prefetched article synchronously on Frame 0 with zero loading state!
                        _readerState.value = ReaderUiState(
                            articleId = articleId,
                            activeMode = ReaderAiMode.NONE,
                            isLoading = false,
                            displayedText = existingRaw.ifBlank { cachedItem.article.title },
                            errorMessage = null,
                            activeModelUsed = ""
                        )
                        if (!needsWebUpgrade) {
                            return
                        }
                        // Upgrade silently in background if full webpage has more content than snippet
                        readerAiJob = viewModelScope.launch {
                            rssRepo.processArticleInReader(
                                articleId = articleId,
                                mode = ReaderAiMode.NONE,
                                settings = currentSettings,
                                forceRefresh = false
                            ).onSuccess { output ->
                                if (_readerState.value.articleId == articleId &&
                                    _readerState.value.activeMode == ReaderAiMode.NONE &&
                                    output.cleanBody.length > existingRaw.length
                                ) {
                                    _readerState.value = ReaderUiState(
                                        articleId = articleId,
                                        activeMode = ReaderAiMode.NONE,
                                        isLoading = false,
                                        displayedText = output.cleanBody,
                                        errorMessage = null,
                                        activeModelUsed = ""
                                    )
                                }
                            }
                        }
                        return
                    }
                } else {
                    val cachedAiOutput = resolveCachedAiText(cachedItem.article, resolvedMode, currentSettings)
                    if (cachedAiOutput != null) {
                        _readerState.value = ReaderUiState(
                            articleId = articleId,
                            activeMode = resolvedMode,
                            isLoading = false,
                            displayedText = cachedAiOutput,
                            errorMessage = null,
                            activeModelUsed = badge
                        )
                        return
                    }
                }
            }

            // Set synchronous loading state for AI generation or uncached empty article
            _readerState.value = ReaderUiState(
                articleId = articleId,
                activeMode = resolvedMode,
                isLoading = true,
                displayedText = "",
                errorMessage = null,
                activeModelUsed = badge
            )
        }

        readerAiJob = viewModelScope.launch {
            val feedMode = if (cachedItem != null) {
                runCatching { ReaderAiMode.valueOf(cachedItem.feedDefaultReaderMode) }.getOrDefault(ReaderAiMode.NONE)
            } else {
                rssRepo.getFeedReaderModeForArticle(
                    articleId = articleId,
                    fallback = ReaderAiMode.NONE
                )
            }
            val mode = when {
                explicitMode != null -> explicitMode
                feedMode == ReaderAiMode.SUMMARIZE && !currentSettings.canSummarizeWithAi -> ReaderAiMode.NONE
                else -> feedMode
            }
            val initialEngineLabel = resolveEngineBadge(mode, currentSettings)

            if (cachedItem == null) {
                _readerState.value = ReaderUiState(
                    articleId = articleId,
                    activeMode = mode,
                    isLoading = true,
                    displayedText = "",
                    errorMessage = null,
                    activeModelUsed = initialEngineLabel
                )
            }

            val result = rssRepo.processArticleInReader(
                articleId = articleId,
                mode = mode,
                settings = currentSettings,
                forceRefresh = forceRefresh
            )
            result.onSuccess { output ->
                _readerState.value = ReaderUiState(
                    articleId = articleId,
                    activeMode = mode,
                    isLoading = false,
                    displayedText = output.cleanBody,
                    errorMessage = null,
                    activeModelUsed = output.usedModel
                )
            }.onFailure { err ->
                _readerState.value = ReaderUiState(
                    articleId = articleId,
                    activeMode = mode,
                    isLoading = false,
                    displayedText = "",
                    errorMessage = err.message ?: "Article processing failed",
                    activeModelUsed = initialEngineLabel
                )
            }
        }
    }

    // --- Telegram Channel Publishing Flow ---

    fun openTelegramPreview(articleId: Long, hasImage: Boolean) {
        _telegramPreview.value = TelegramPreviewState(
            isVisible = true,
            isDrafting = true,
            isSending = false,
            draftText = "",
            includePhoto = hasImage,
            errorMessage = null
        )
        draftTelegramSummary(articleId)
    }

    fun regenerateTelegramDraft(articleId: Long) {
        _telegramPreview.update { it.copy(isDrafting = true, errorMessage = null) }
        draftTelegramSummary(articleId)
    }

    private fun draftTelegramSummary(articleId: Long) {
        viewModelScope.launch {
            val res = rssRepo.draftTelegramSummary(articleId, settingsState.value)
            res.onSuccess { text ->
                _telegramPreview.update {
                    it.copy(isDrafting = false, draftText = text, errorMessage = null)
                }
            }.onFailure { err ->
                _telegramPreview.update {
                    it.copy(isDrafting = false, errorMessage = err.message ?: "Failed to draft summary")
                }
            }
        }
    }

    fun updateTelegramDraftText(newText: String) {
        _telegramPreview.update { it.copy(draftText = newText) }
    }

    fun toggleTelegramIncludePhoto(include: Boolean) {
        _telegramPreview.update { it.copy(includePhoto = include) }
    }

    fun dismissTelegramPreview() {
        _telegramPreview.value = TelegramPreviewState()
    }

    fun sendTelegramPost(imageUrl: String?) {
        val preview = _telegramPreview.value
        if (preview.isSending || preview.draftText.isBlank()) return
        viewModelScope.launch {
            _telegramPreview.update { it.copy(isSending = true, errorMessage = null) }
            val photoToUse = if (preview.includePhoto) imageUrl else null
            val res = rssRepo.publishToTelegram(
                settings = settingsState.value,
                summaryText = preview.draftText,
                imageUrl = photoToUse
            )
            res.onSuccess {
                _telegramPreview.value = TelegramPreviewState()
                emitMessage("Published to Telegram channel")
            }.onFailure { err ->
                _telegramPreview.update {
                    it.copy(isSending = false, errorMessage = err.message ?: "Telegram send failed")
                }
            }
        }
    }

    // --- Settings Updates ---

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setLayoutMode(mode: FeedLayoutMode) {
        viewModelScope.launch { settingsRepo.setLayoutMode(mode) }
    }

    fun cycleLayoutMode() {
        val modes = FeedLayoutMode.entries
        val next = modes[(modes.indexOf(settingsState.value.layoutMode) + 1) % modes.size]
        setLayoutMode(next)
    }

    fun setTitleProvider(provider: TitleTranslationProvider) {
        viewModelScope.launch {
            settingsRepo.setTitleProvider(provider)
            emitMessage("Translator: ${provider.label}")
        }
    }

    fun setArticleTranslationEngine(engine: ArticleTranslationEngine) {
        viewModelScope.launch {
            settingsRepo.setArticleTranslationEngine(engine)
            emitMessage("Article translator: ${engine.label}")
        }
    }

    fun setTargetLanguage(lang: TargetLanguage) {
        viewModelScope.launch {
            settingsRepo.setTargetLanguage(lang)
            val updatedSettings = settingsState.value.copy(targetLanguage = lang)
            feedsState.value.filter { it.feed.autoTranslate }.forEach { item ->
                rssRepo.autoTranslateFeedPosts(item.feed.id, updatedSettings)
            }
        }
    }

    fun setSummaryProvider(provider: SummaryProvider) {
        viewModelScope.launch {
            settingsRepo.setSummaryProvider(provider)
            emitMessage(provider.label)
            if (provider == SummaryProvider.OLLAMA && settingsState.value.lastOllamaModelsSyncMs == 0L) {
                rssRepo.refreshFreeOllamaModels()
            }
        }
    }

    fun selectGeminiModel(model: String) {
        viewModelScope.launch {
            settingsRepo.setSelectedModel(model)
            emitMessage(model)
        }
    }

    fun selectOllamaModel(model: String) {
        viewModelScope.launch {
            settingsRepo.setSelectedOllamaModel(model, userInitiated = true)
            emitMessage(model)
        }
    }

    fun setUseCustomTranslatePrompt(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setUseCustomTranslatePrompt(enabled)
        }
    }

    fun setCustomTranslatePrompt(prompt: String) {
        viewModelScope.launch {
            settingsRepo.setCustomTranslatePrompt(prompt)
        }
    }

    fun setUseCustomSummarizePrompt(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setUseCustomSummarizePrompt(enabled)
        }
    }

    fun setCustomSummarizePrompt(prompt: String) {
        viewModelScope.launch {
            settingsRepo.setCustomSummarizePrompt(prompt)
        }
    }

    fun setTelegramEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setTelegramEnabled(enabled)
        }
    }

    fun refreshFreeModelsManually() {
        if (_isRefreshingModels.value) return
        viewModelScope.launch {
            _isRefreshingModels.value = true
            val res = rssRepo.refreshFreeModels()
            _isRefreshingModels.value = false
            res.onSuccess { list ->
                emitMessage("${list.size} models")
            }.onFailure { err ->
                emitMessage(err.message ?: "Failed to refresh models list")
            }
        }
    }

    fun refreshFreeOllamaModelsManually() {
        if (_isRefreshingOllamaModels.value) return
        viewModelScope.launch {
            _isRefreshingOllamaModels.value = true
            val res = rssRepo.refreshFreeOllamaModels()
            _isRefreshingOllamaModels.value = false
            res.onSuccess { list ->
                emitMessage("${list.size} models")
            }.onFailure { err ->
                emitMessage(err.message ?: "Failed to refresh Ollama models list")
            }
        }
    }

    fun verifyGeminiKey(rawInput: String) {
        val candidate = rawInput.trim().ifEmpty { settingsState.value.effectiveGeminiKey }
        if (candidate.isEmpty()) {
            val msg = "Please enter a Gemini API key first"
            _geminiKeyStatus.value = KeyVerificationState.Invalid(msg)
            emitMessage(msg)
            return
        }
        viewModelScope.launch {
            _geminiKeyStatus.value = KeyVerificationState.Verifying
            val res = rssRepo.verifyGeminiApiKey(candidate)
            res.onSuccess { count ->
                val msg = "Valid Gemini key ($count models available)"
                _geminiKeyStatus.value = KeyVerificationState.Valid(msg)
                emitMessage(msg)
            }.onFailure { err ->
                val msg = err.message ?: "Invalid Gemini API key — please provide a valid key"
                _geminiKeyStatus.value = KeyVerificationState.Invalid(msg)
                emitMessage(msg)
            }
        }
    }

    fun verifyOllamaKey(rawInput: String) {
        val candidate = rawInput.trim().ifEmpty { settingsState.value.effectiveOllamaKey }
        if (candidate.isEmpty()) {
            val msg = "Please enter an Ollama API token first"
            _ollamaKeyStatus.value = KeyVerificationState.Invalid(msg)
            emitMessage(msg)
            return
        }
        viewModelScope.launch {
            _ollamaKeyStatus.value = KeyVerificationState.Verifying
            val res = rssRepo.verifyOllamaApiKey(candidate)
            res.onSuccess { count ->
                val msg = "Valid Ollama token ($count models available)"
                _ollamaKeyStatus.value = KeyVerificationState.Valid(msg)
                emitMessage(msg)
            }.onFailure { err ->
                val msg = err.message ?: "Invalid Ollama token — please provide a valid token"
                _ollamaKeyStatus.value = KeyVerificationState.Invalid(msg)
                emitMessage(msg)
            }
        }
    }

    fun verifyDeeplKey(rawInput: String) {
        val candidate = rawInput.trim().ifEmpty { settingsState.value.effectiveDeeplKey }
        if (candidate.isEmpty()) {
            val msg = "Please enter a DeepL API key first"
            _deeplKeyStatus.value = KeyVerificationState.Invalid(msg)
            emitMessage(msg)
            return
        }
        viewModelScope.launch {
            _deeplKeyStatus.value = KeyVerificationState.Verifying
            val res = rssRepo.verifyDeeplApiKey(candidate)
            res.onSuccess { msg ->
                _deeplKeyStatus.value = KeyVerificationState.Valid(msg)
                emitMessage(msg)
            }.onFailure { err ->
                val msg = err.message ?: "Invalid DeepL API key — please provide a valid key"
                _deeplKeyStatus.value = KeyVerificationState.Invalid(msg)
                emitMessage(msg)
            }
        }
    }

    fun updateSingleApiKey(providerKey: String, newValue: String) {
        val clean = newValue.trim()
        val current = settingsState.value
        viewModelScope.launch {
            when (providerKey) {
                "GEMINI" -> {
                    settingsRepo.updateSecrets(
                        geminiKey = clean,
                        ollamaKey = current.ollamaApiKeyOverride,
                        deeplKey = current.deeplApiKeyOverride,
                        telegramToken = current.telegramBotTokenOverride,
                        telegramChannel = current.telegramChannelIdOverride
                    )
                    if (clean.isNotEmpty()) verifyGeminiKey(clean) else {
                        _geminiKeyStatus.value = KeyVerificationState.Idle
                        if (current.titleProvider == TitleTranslationProvider.GEMINI &&
                            settingsRepo.settingsFlow.first().effectiveGeminiKey.isBlank()
                        ) {
                            settingsRepo.setTitleProvider(TitleTranslationProvider.DEEPL_WEB)
                        }
                        emitMessage("Gemini API key cleared")
                    }
                }
                "OLLAMA" -> {
                    settingsRepo.updateSecrets(
                        geminiKey = current.geminiApiKeyOverride,
                        ollamaKey = clean,
                        deeplKey = current.deeplApiKeyOverride,
                        telegramToken = current.telegramBotTokenOverride,
                        telegramChannel = current.telegramChannelIdOverride
                    )
                    if (clean.isNotEmpty()) verifyOllamaKey(clean) else {
                        _ollamaKeyStatus.value = KeyVerificationState.Idle
                        if (current.titleProvider == TitleTranslationProvider.OLLAMA &&
                            settingsRepo.settingsFlow.first().effectiveOllamaKey.isBlank()
                        ) {
                            settingsRepo.setTitleProvider(TitleTranslationProvider.DEEPL_WEB)
                        }
                        emitMessage("Ollama token cleared")
                    }
                }
                "DEEPL" -> {
                    settingsRepo.updateSecrets(
                        geminiKey = current.geminiApiKeyOverride,
                        ollamaKey = current.ollamaApiKeyOverride,
                        deeplKey = clean,
                        telegramToken = current.telegramBotTokenOverride,
                        telegramChannel = current.telegramChannelIdOverride
                    )
                    if (clean.isNotEmpty()) verifyDeeplKey(clean) else {
                        _deeplKeyStatus.value = KeyVerificationState.Idle
                        emitMessage("DeepL API key cleared")
                    }
                }
            }
        }
    }

    fun deleteSingleApiKey(providerKey: String) {
        updateSingleApiKey(providerKey, "")
    }

    fun saveApiAndTelegramConfig(
        geminiKey: String,
        ollamaKey: String,
        deeplKey: String,
        telegramToken: String,
        telegramChannel: String
    ) {
        viewModelScope.launch {
            settingsRepo.updateSecrets(
                geminiKey = geminiKey,
                ollamaKey = ollamaKey,
                deeplKey = deeplKey,
                telegramToken = telegramToken,
                telegramChannel = telegramChannel
            )
            emitMessage("Configuration saved")
        }
    }

    fun testTelegramBotConnection(tokenInput: String) {
        val tokenToTest = tokenInput.trim().ifEmpty { settingsState.value.effectiveTelegramToken }
        viewModelScope.launch {
            val res = rssRepo.verifyTelegramBot(tokenToTest)
            res.onSuccess { botHandle ->
                emitMessage("Verified: $botHandle")
            }.onFailure { err ->
                emitMessage(err.message ?: "Bot verification failed")
            }
        }
    }

    fun emitMessage(msg: String) {
        _snackbarEvents.tryEmit(AppSnackbarEvent(message = msg))
    }
}
