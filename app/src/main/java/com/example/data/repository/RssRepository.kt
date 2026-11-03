package com.example.data.repository

import com.example.data.local.ArticleEntity
import com.example.data.local.ArticleWithFeed
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedFilterEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedWithCount
import com.example.data.local.RssDao
import com.example.data.backup.BackupArticleItem
import com.example.data.backup.BackupFeedFilterItem
import com.example.data.backup.BackupImportSummary
import com.example.data.backup.BackupManager
import com.example.data.local.SeenArticleEntity
import com.example.data.model.AppSettings
import com.example.data.model.ArticleTranslationEngine
import com.example.data.model.BackupSelection
import com.example.data.model.PendingImportPreview
import com.example.data.model.ReaderAiMode
import com.example.data.model.SummaryProvider
import com.example.data.model.TitleTranslationProvider
import com.example.data.opml.OpmlManager
import com.example.data.preferences.SettingsRepository
import com.example.data.remote.AiArticleOutput
import com.example.data.remote.FeedRefreshOutcome
import com.example.data.remote.GeminiService
import com.example.data.remote.HeadlineTranslationService
import com.example.data.remote.OllamaService
import com.example.data.remote.ParsedRssFeed
import com.example.data.remote.ParsedRssItem
import com.example.data.remote.SmartRssParser
import com.example.data.remote.TelegramService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

data class DeletedFeedBackup(
    val feed: FeedEntity,
    val articles: List<ArticleEntity>,
    val seenArticles: List<SeenArticleEntity>
)

class RssRepository(
    private val dao: RssDao,
    private val settingsRepository: SettingsRepository,
    private val rssParser: SmartRssParser = SmartRssParser(),
    private val geminiService: GeminiService = GeminiService(),
    private val ollamaService: OllamaService = OllamaService(),
    private val headlineTranslator: HeadlineTranslationService = HeadlineTranslationService(),
    private val telegramService: TelegramService = TelegramService()
) {
    val feedsWithCountsFlow: Flow<List<FeedWithCount>> = dao.observeFeedsWithUnreadCount()
    val allArticlesFlow: Flow<List<ArticleWithFeed>> = dao.observeAllArticlesWithFeed()
    val customListsFlow: Flow<List<CustomListEntity>> = dao.observeCustomLists()

    fun observeArticle(articleId: Long): Flow<ArticleWithFeed?> = dao.observeArticleById(articleId)

    // --- Custom Lists Management ---

    private suspend fun nextCustomListSortOrder(): Int =
        (dao.getMaxCustomListSortOrder() ?: 0) + 10

    private suspend fun nextFeedSortOrder(): Int =
        (dao.getMaxFeedSortOrder() ?: 0) + 10

    suspend fun createCustomList(
        rawName: String,
        showInAllLists: Boolean = true,
        syncEnabled: Boolean = true
    ): Result<String> {
        val clean = rawName.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("List name cannot be empty"))
        }
        val insertedId = dao.insertCustomList(
            CustomListEntity(
                name = clean,
                showInAllLists = showInAllLists,
                syncEnabled = syncEnabled,
                sortOrder = nextCustomListSortOrder()
            )
        )
        if (insertedId <= 0L) {
            val existing = dao.getCustomListByNameOnce(clean)
            if (existing != null) {
                dao.setCustomListShowInAllLists(existing.id, showInAllLists)
                dao.setCustomListSyncEnabled(existing.id, syncEnabled)
            }
        }
        return Result.success(clean)
    }

    suspend fun renameCustomList(
        list: CustomListEntity,
        newRawName: String,
        showInAllLists: Boolean = list.showInAllLists,
        syncEnabled: Boolean = list.syncEnabled
    ): Result<String> {
        val clean = newRawName.trim()
        if (clean.isEmpty()) {
            return Result.failure(IllegalArgumentException("List name cannot be empty"))
        }
        if (!list.name.equals(clean, ignoreCase = false)) {
            dao.renameCustomListById(list.id, clean)
            dao.updateFeedsListName(list.name, clean)
        }
        if (list.showInAllLists != showInAllLists) {
            dao.setCustomListShowInAllLists(list.id, showInAllLists)
        }
        if (list.syncEnabled != syncEnabled) {
            dao.setCustomListSyncEnabled(list.id, syncEnabled)
        }
        return Result.success(clean)
    }

    suspend fun toggleCustomListShowInAllLists(list: CustomListEntity): Boolean {
        val next = !list.showInAllLists
        dao.setCustomListShowInAllLists(list.id, next)
        return next
    }

    suspend fun toggleCustomListSyncEnabled(list: CustomListEntity): Boolean {
        val fresh = dao.getAllCustomListsOnce().find { it.id == list.id } ?: list
        val next = !fresh.syncEnabled
        dao.setCustomListSyncEnabled(fresh.id, next)
        return next
    }

    suspend fun toggleCustomListPinned(list: CustomListEntity): Boolean {
        val fresh = dao.getAllCustomListsOnce().find { it.id == list.id } ?: list
        val next = !fresh.isPinned
        dao.setCustomListPinned(fresh.id, next)
        return next
    }

    suspend fun reorderCustomLists(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        dao.updateCustomListsOrder(orderedIds)
    }

    suspend fun deleteCustomList(list: CustomListEntity, deleteFeedsInside: Boolean = false) {
        if (deleteFeedsInside) {
            dao.deleteFeedsByListName(list.name)
            dao.deleteCustomListById(list.id)
        } else {
            dao.deleteCustomListById(list.id)
            dao.clearFeedsListName(list.name)
        }
    }

    // --- In-App Feed Notifications ---

    fun observeNotifiedArticles(): Flow<List<ArticleWithFeed>> =
        dao.observeNotifiedArticlesWithFeed()

    fun observeNotifiedUnreadCount(): Flow<Int> =
        dao.observeNotifiedUnreadCount()

    suspend fun toggleFeedNotify(feedId: Long, notifyEnabled: Boolean) {
        dao.setFeedNotifyEnabled(feedId, notifyEnabled)
    }

    suspend fun markAllNotifiedArticlesRead() {
        dao.markAllNotifiedArticlesRead()
    }

    // --- Feed Content & Regex Filters ---

    fun cleanContentWithFilters(content: String, filters: List<FeedFilterEntity>): String {
        if (filters.isEmpty() || content.isBlank()) return content
        var result = content
        for (filter in filters) {
            val pattern = filter.pattern.trim()
            if (pattern.isEmpty()) continue
            result = if (filter.isRegex) {
                runCatching {
                    val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
                    result.replace(regex, "")
                }.getOrDefault(result.replace(pattern, "", ignoreCase = true))
            } else {
                result.replace(pattern, "", ignoreCase = true)
            }
        }
        return result.replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    fun observeFiltersForFeed(feedId: Long): Flow<List<FeedFilterEntity>> =
        dao.observeFiltersForFeed(feedId)

    suspend fun getFiltersForFeedOnce(feedId: Long): List<FeedFilterEntity> =
        dao.getFiltersForFeedOnce(feedId)

    suspend fun getAllFiltersOnce(): List<FeedFilterEntity> =
        dao.getAllFiltersOnce()

    suspend fun addFilter(feedId: Long, rawPattern: String, isRegex: Boolean): Int {
        val pattern = rawPattern.trim()
        if (pattern.isEmpty()) return 0
        val filterId = dao.insertFilter(
            FeedFilterEntity(
                feedId = feedId,
                pattern = pattern,
                isRegex = isRegex
            )
        )
        if (filterId <= 0L) return 0

        // Apply immediately to all existing articles of this feed
        val articles = dao.getArticlesByFeedIdOnce(feedId)
        val allFilters = dao.getFiltersForFeedOnce(feedId)
        var affected = 0
        for (art in articles) {
            val cleanedRaw = cleanContentWithFilters(art.rawContent, allFilters)
            var changed = false
            if (cleanedRaw != art.rawContent) {
                dao.updateRawContent(art.id, cleanedRaw)
                changed = true
            }
            if (!art.aiTranslatedBody.isNullOrBlank()) {
                val cleanedTrans = cleanContentWithFilters(art.aiTranslatedBody, allFilters)
                if (cleanedTrans != art.aiTranslatedBody) {
                    dao.updateAiTranslation(
                        art.id,
                        cleanedTrans,
                        art.aiTranslatedEngine.orEmpty(),
                        art.aiProcessedLang.orEmpty()
                    )
                    changed = true
                }
            }
            if (!art.aiSummarizedBody.isNullOrBlank()) {
                val cleanedSumm = cleanContentWithFilters(art.aiSummarizedBody, allFilters)
                if (cleanedSumm != art.aiSummarizedBody) {
                    dao.updateAiSummary(
                        art.id,
                        cleanedSumm,
                        art.aiSummarizedEngine.orEmpty(),
                        art.aiProcessedLang.orEmpty()
                    )
                    changed = true
                }
            }
            if (changed) affected++
        }
        return affected
    }

    suspend fun removeFilter(filterId: Long) {
        dao.deleteFilterById(filterId)
    }

    // --- Backup & Restore (JSON + OPML) ---

    suspend fun exportOpmlXml(): String {
        val feeds = dao.getAllFeedsOnce()
        val lists = dao.getAllCustomListsOnce()
        return OpmlManager.exportToOpmlXml(feeds = feeds, customLists = lists)
    }

    suspend fun exportBackupJson(selection: BackupSelection): String {
        val feeds = dao.getAllFeedsOnce()
        val lists = dao.getAllCustomListsOnce()
        val savedArticles = if (selection.includeSaved) dao.getSavedArticlesOnce() else emptyList()
        val readArticles = if (selection.includeRead) dao.getReadArticlesOnce() else emptyList()
        val filters = if (selection.includeFilters) dao.getAllFiltersOnce() else emptyList()
        return BackupManager.exportToJson(
            selection = selection,
            feeds = feeds,
            customLists = lists,
            savedArticles = savedArticles,
            readArticles = readArticles,
            filters = filters
        )
    }

    fun inspectImportContent(rawContent: String): Result<PendingImportPreview> {
        return runCatching {
            BackupManager.inspectImportPreview(rawContent) { url ->
                rssParser.normalizeInputUrl(url)
            }
        }
    }

    suspend fun importBackupContent(
        rawContent: String,
        selection: BackupSelection,
        settings: AppSettings,
        allowedFeedUrls: Set<String>? = null,
        syncAfterFeedImport: Boolean = true,
        onProgress: ((checked: Int, total: Int) -> Unit)? = null
    ): Result<BackupImportSummary> = coroutineScope {
        runCatching {
            if (!selection.hasAnySelection) {
                throw IllegalArgumentException("Please select at least one option to import")
            }
            val normalizedAllowedUrls: Set<String>? = allowedFeedUrls
                ?.map { rssParser.normalizeInputUrl(it) }
                ?.toHashSet()

            if (BackupManager.looksLikeJsonBackup(rawContent)) {
                val bundle = BackupManager.parseFromJson(rawContent)
                var importedListsCount = 0
                var importedFeedsCount = 0
                var importedSavedCount = 0
                var importedReadCount = 0

                val existingListNamesLower = dao.getAllCustomListsOnce()
                    .map { it.name.trim().lowercase(Locale.ROOT) }
                    .toMutableSet()
                var listOrderCursor = dao.getMaxCustomListSortOrder() ?: 0

                if (selection.includeLists) {
                    for (listItem in bundle.lists) {
                        val clean = listItem.name.trim()
                        if (clean.isNotEmpty() && clean.lowercase(Locale.ROOT) !in existingListNamesLower) {
                            listOrderCursor += 10
                            val id = dao.insertCustomList(
                                CustomListEntity(
                                    name = clean,
                                    showInAllLists = listItem.showInAllLists,
                                    syncEnabled = listItem.syncEnabled,
                                    isPinned = listItem.isPinned,
                                    sortOrder = if (listItem.sortOrder > 0) listItem.sortOrder else listOrderCursor
                                )
                            )
                            if (id > 0L) {
                                existingListNamesLower.add(clean.lowercase(Locale.ROOT))
                                importedListsCount++
                            }
                        }
                    }
                }

                var feedOrderCursor = dao.getMaxFeedSortOrder() ?: 0
                suspend fun ensureFeedForBackup(
                    rawUrl: String,
                    title: String,
                    siteUrl: String,
                    iconUrl: String,
                    groupName: String,
                    autoTranslate: Boolean = false,
                    showInAllFeeds: Boolean = true,
                    showInAllLists: Boolean = true,
                    syncEnabled: Boolean = true,
                    isPinned: Boolean = false,
                    notifyEnabled: Boolean = false,
                    sortOrder: Int = 0,
                    defaultReaderMode: String = ReaderAiMode.NONE.name,
                    updateExistingMetadata: Boolean = false
                ): Pair<Long, Boolean> {
                    val normalizedUrl = rssParser.normalizeInputUrl(rawUrl)
                    val resolvedGroup = groupName.trim().let { candidate ->
                        when {
                            candidate.isEmpty() -> ""
                            selection.includeLists -> {
                                if (candidate.lowercase(Locale.ROOT) !in existingListNamesLower) {
                                    listOrderCursor += 10
                                    val insertedListId = dao.insertCustomList(
                                        CustomListEntity(
                                            name = candidate,
                                            showInAllLists = true,
                                            sortOrder = listOrderCursor
                                        )
                                    )
                                    if (insertedListId > 0L) {
                                        existingListNamesLower.add(candidate.lowercase(Locale.ROOT))
                                        importedListsCount++
                                    }
                                }
                                candidate
                            }
                            candidate.lowercase(Locale.ROOT) in existingListNamesLower -> candidate
                            else -> ""
                        }
                    }

                    val normalizedReaderMode = runCatching {
                        ReaderAiMode.valueOf(defaultReaderMode)
                    }.getOrDefault(ReaderAiMode.NONE).name

                    val existing = dao.getFeedByUrl(normalizedUrl) ?: dao.getFeedByUrl(rawUrl)
                    if (existing != null) {
                        if (updateExistingMetadata) {
                            val updated = existing.copy(
                                title = existing.title.ifBlank { title },
                                siteUrl = existing.siteUrl.ifBlank { siteUrl },
                                iconUrl = existing.iconUrl.ifBlank { iconUrl },
                                groupName = if (resolvedGroup.isNotEmpty()) resolvedGroup else existing.groupName,
                                autoTranslate = existing.autoTranslate || autoTranslate,
                                showInAllFeeds = showInAllFeeds,
                                showInAllLists = showInAllLists,
                                notifyEnabled = existing.notifyEnabled || notifyEnabled,
                                defaultReaderMode = normalizedReaderMode
                            )
                            dao.updateFeed(updated)
                        }
                        return existing.id to false
                    }

                    val fallbackSite = siteUrl.ifBlank { rssParser.extractOriginUrl(normalizedUrl) }
                    val fallbackTitle = title.ifBlank {
                        rssParser.extractHost(normalizedUrl).ifBlank { normalizedUrl }
                    }
                    val fallbackHost = rssParser.extractHost(fallbackSite.ifBlank { normalizedUrl })
                    val fallbackIcon = iconUrl.ifBlank {
                        if (fallbackHost.isNotBlank()) {
                            "https://www.google.com/s2/favicons?domain=$fallbackHost&sz=128"
                        } else {
                            ""
                        }
                    }
                    feedOrderCursor += 10
                    val entity = FeedEntity(
                        url = normalizedUrl,
                        siteUrl = fallbackSite,
                        title = fallbackTitle,
                        iconUrl = fallbackIcon,
                        groupName = resolvedGroup,
                        autoTranslate = autoTranslate,
                        defaultReaderMode = normalizedReaderMode,
                        showInAllFeeds = showInAllFeeds,
                        showInAllLists = showInAllLists,
                        syncEnabled = syncEnabled,
                        isPinned = isPinned,
                        notifyEnabled = notifyEnabled,
                        sortOrder = if (sortOrder > 0) sortOrder else feedOrderCursor
                    )
                    val insertedId = dao.insertFeed(entity)
                    val realId = if (insertedId > 0L) insertedId else dao.getFeedByUrl(normalizedUrl)?.id ?: 0L
                    return realId to (realId > 0L)
                }

                if (selection.includeFeeds) {
                    val feedsToImport = if (normalizedAllowedUrls != null) {
                        bundle.feeds.filter { feedItem ->
                            feedItem.url.isNotBlank() &&
                                rssParser.normalizeInputUrl(feedItem.url) in normalizedAllowedUrls
                        }
                    } else {
                        bundle.feeds
                    }
                    for (feedItem in feedsToImport) {
                        if (feedItem.url.isBlank()) continue
                        val (feedId, _) = ensureFeedForBackup(
                            rawUrl = feedItem.url,
                            title = feedItem.title,
                            siteUrl = feedItem.siteUrl,
                            iconUrl = feedItem.iconUrl,
                            groupName = feedItem.groupName,
                            autoTranslate = feedItem.autoTranslate,
                            showInAllFeeds = feedItem.showInAllFeeds,
                            showInAllLists = feedItem.showInAllLists,
                            syncEnabled = feedItem.syncEnabled,
                            isPinned = feedItem.isPinned,
                            notifyEnabled = feedItem.notifyEnabled,
                            sortOrder = feedItem.sortOrder,
                            defaultReaderMode = feedItem.defaultReaderMode,
                            updateExistingMetadata = true
                        )
                        if (feedId > 0L) importedFeedsCount++
                    }
                }

                val mergedArticlesByKey = LinkedHashMap<String, Pair<BackupArticleItem, Pair<Boolean, Boolean>>>()
                if (selection.includeSaved) {
                    for (art in bundle.savedPosts) {
                        val key = "${rssParser.normalizeInputUrl(art.feedUrl)}::${art.guid.ifBlank { art.link }}"
                        val prev = mergedArticlesByKey[key]
                        mergedArticlesByKey[key] = art to (true to (prev?.second?.second == true))
                    }
                }
                if (selection.includeRead) {
                    for (art in bundle.readPosts) {
                        val key = "${rssParser.normalizeInputUrl(art.feedUrl)}::${art.guid.ifBlank { art.link }}"
                        val prev = mergedArticlesByKey[key]
                        mergedArticlesByKey[key] = (prev?.first ?: art) to ((prev?.second?.first == true) to true)
                    }
                }

                if (mergedArticlesByKey.isNotEmpty()) {
                    val nowMs = System.currentTimeMillis()
                    val tombstones = mutableListOf<SeenArticleEntity>()
                    for ((artItem, flags) in mergedArticlesByKey.values) {
                        val (restoreSaved, restoreRead) = flags
                        if (artItem.feedUrl.isBlank()) continue
                        val (parentFeedId, createdImplicitFeed) = ensureFeedForBackup(
                            rawUrl = artItem.feedUrl,
                            title = artItem.feedTitle,
                            siteUrl = artItem.feedSiteUrl,
                            iconUrl = artItem.feedIconUrl,
                            groupName = artItem.feedGroupName
                        )
                        if (parentFeedId <= 0L) continue
                        if (createdImplicitFeed && selection.includeFeeds) {
                            importedFeedsCount++
                        }

                        val cleanGuid = artItem.guid.trim().ifBlank {
                            artItem.link.trim().ifBlank { artItem.title.trim() }
                        }
                        val existingArt = dao.getArticleByFeedAndGuidOnce(parentFeedId, cleanGuid)
                        val targetSaved = (existingArt?.isSaved == true) || restoreSaved
                        val targetRead = (existingArt?.isRead == true) || restoreRead

                        val entityToSave = ArticleEntity(
                            id = existingArt?.id ?: 0L,
                            feedId = parentFeedId,
                            guid = cleanGuid,
                            link = artItem.link,
                            title = artItem.title,
                            rawContent = existingArt?.rawContent?.takeIf { it.length > artItem.rawContent.length }
                                ?: artItem.rawContent,
                            imageUrl = existingArt?.imageUrl ?: artItem.imageUrl,
                            publishedAt = artItem.publishedAt,
                            isRead = targetRead,
                            isSaved = targetSaved,
                            translatedTitle = existingArt?.translatedTitle ?: artItem.translatedTitle,
                            translatedTitleLang = existingArt?.translatedTitleLang ?: artItem.translatedTitleLang,
                            showTranslatedTitle = existingArt?.showTranslatedTitle ?: artItem.showTranslatedTitle,
                            aiTranslatedBody = existingArt?.aiTranslatedBody ?: artItem.aiTranslatedBody,
                            aiTranslatedEngine = existingArt?.aiTranslatedEngine ?: artItem.aiTranslatedEngine,
                            aiSummarizedBody = existingArt?.aiSummarizedBody ?: artItem.aiSummarizedBody,
                            aiSummarizedEngine = existingArt?.aiSummarizedEngine ?: artItem.aiSummarizedEngine,
                            aiProcessedLang = existingArt?.aiProcessedLang ?: artItem.aiProcessedLang
                        )
                        dao.upsertArticles(listOf(entityToSave))
                        buildDedupeKeys(cleanGuid, artItem.link, artItem.title).forEach { dKey ->
                            tombstones.add(SeenArticleEntity(feedId = parentFeedId, dedupeKey = dKey, seenAt = nowMs))
                        }
                        if (restoreSaved) importedSavedCount++
                        if (restoreRead) importedReadCount++
                    }
                    if (tombstones.isNotEmpty()) {
                        dao.insertSeenArticles(tombstones.distinctBy { "${it.feedId}_${it.dedupeKey}" })
                    }
                }

                var importedFiltersCount = 0
                if (selection.includeFilters && bundle.filters.isNotEmpty()) {
                    for (fItem in bundle.filters) {
                        if (fItem.pattern.isBlank()) continue
                        val norm = rssParser.normalizeInputUrl(fItem.feedUrl)
                        val targetFeed = dao.getFeedByUrl(norm) ?: dao.getFeedByUrl(fItem.feedUrl)
                        if (targetFeed != null) {
                            val existingFilters = dao.getFiltersForFeedOnce(targetFeed.id)
                            if (existingFilters.none { it.pattern.equals(fItem.pattern.trim(), ignoreCase = true) }) {
                                dao.insertFilter(
                                    FeedFilterEntity(
                                        feedId = targetFeed.id,
                                        pattern = fItem.pattern.trim(),
                                        isRegex = fItem.isRegex
                                    )
                                )
                                importedFiltersCount++
                            }
                        }
                    }
                }

                if (syncAfterFeedImport && selection.includeFeeds && importedFeedsCount > 0) {
                    runCatching { syncAllFeeds(settings = settings, onProgress = { c, t, _ -> onProgress?.invoke(c, t) }) }
                }

                BackupImportSummary(
                    listsCount = importedListsCount,
                    feedsCount = importedFeedsCount,
                    savedCount = importedSavedCount,
                    readCount = importedReadCount,
                    filtersCount = importedFiltersCount
                )
            } else {
                // OPML 1.0 / 2.0 XML Import honoring selected checkboxes & per-feed checklist
                val parsedOpml = OpmlManager.parseOpmlXml(rawContent)
                if (parsedOpml.feeds.isEmpty() && parsedOpml.listNames.isEmpty()) {
                    throw IllegalArgumentException("No valid backup or OPML data found in file")
                }

                val selectedOpmlFeeds = if (normalizedAllowedUrls != null) {
                    parsedOpml.feeds.filter { entry ->
                        rssParser.normalizeInputUrl(entry.xmlUrl) in normalizedAllowedUrls
                    }
                } else {
                    parsedOpml.feeds
                }

                if (normalizedAllowedUrls != null && parsedOpml.feeds.isNotEmpty() && selectedOpmlFeeds.isEmpty()) {
                    throw IllegalArgumentException("Please select at least one feed to import")
                }

                val allFeedFolderNamesLower = parsedOpml.feeds
                    .mapNotNull { it.listName.trim().takeIf { n -> n.isNotEmpty() }?.lowercase(Locale.ROOT) }
                    .toHashSet()
                val selectedFeedFolderNamesLower = selectedOpmlFeeds
                    .mapNotNull { it.listName.trim().takeIf { n -> n.isNotEmpty() }?.lowercase(Locale.ROOT) }
                    .toHashSet()

                var importedListsCount = 0
                var importedFeedsCount = 0
                var listOrderCursor = dao.getMaxCustomListSortOrder() ?: 0
                val existingListNamesLower = dao.getAllCustomListsOnce()
                    .map { it.name.trim().lowercase(Locale.ROOT) }
                    .toMutableSet()

                if (selection.includeLists) {
                    parsedOpml.listNames.forEach { listName ->
                        val clean = listName.trim()
                        val cleanLower = clean.lowercase(Locale.ROOT)
                        val shouldKeepFolder = normalizedAllowedUrls == null ||
                            cleanLower in selectedFeedFolderNamesLower ||
                            cleanLower !in allFeedFolderNamesLower
                        if (clean.isNotEmpty() && shouldKeepFolder && cleanLower !in existingListNamesLower) {
                            listOrderCursor += 10
                            val id = dao.insertCustomList(
                                CustomListEntity(
                                    name = clean,
                                    showInAllLists = true,
                                    sortOrder = listOrderCursor
                                )
                            )
                            if (id > 0L) {
                                existingListNamesLower.add(cleanLower)
                                importedListsCount++
                            }
                        }
                    }
                }

                if (selection.includeFeeds) {
                    var feedOrderCursor = dao.getMaxFeedSortOrder() ?: 0
                    for (entry in selectedOpmlFeeds) {
                        val cleanList = entry.listName.trim().let { candidate ->
                            when {
                                candidate.isEmpty() -> ""
                                selection.includeLists -> {
                                    if (candidate.lowercase(Locale.ROOT) !in existingListNamesLower) {
                                        listOrderCursor += 10
                                        val id = dao.insertCustomList(
                                            CustomListEntity(
                                                name = candidate,
                                                showInAllLists = true,
                                                sortOrder = listOrderCursor
                                            )
                                        )
                                        if (id > 0L) {
                                            existingListNamesLower.add(candidate.lowercase(Locale.ROOT))
                                            importedListsCount++
                                        }
                                    }
                                    candidate
                                }
                                candidate.lowercase(Locale.ROOT) in existingListNamesLower -> candidate
                                else -> ""
                            }
                        }

                        val normalizedXmlUrl = rssParser.normalizeInputUrl(entry.xmlUrl)
                        val existing = dao.getFeedByUrl(normalizedXmlUrl) ?: dao.getFeedByUrl(entry.xmlUrl)
                        if (existing != null) {
                            val updated = existing.copy(
                                title = existing.title.ifBlank { entry.title },
                                groupName = if (cleanList.isNotEmpty()) cleanList else existing.groupName,
                                autoTranslate = existing.autoTranslate || entry.autoTranslate
                            )
                            dao.updateFeed(updated)
                            importedFeedsCount++
                        } else {
                            val fallbackSite = entry.htmlUrl.ifBlank { rssParser.extractOriginUrl(normalizedXmlUrl) }
                            val fallbackTitle = entry.title.ifBlank {
                                rssParser.extractHost(normalizedXmlUrl).ifBlank { normalizedXmlUrl }
                            }
                            val fallbackHost = rssParser.extractHost(fallbackSite.ifBlank { normalizedXmlUrl })
                            val fallbackIcon = if (fallbackHost.isNotBlank()) {
                                "https://www.google.com/s2/favicons?domain=$fallbackHost&sz=128"
                            } else {
                                ""
                            }
                            feedOrderCursor += 10
                            val entity = FeedEntity(
                                url = normalizedXmlUrl,
                                siteUrl = fallbackSite,
                                title = fallbackTitle,
                                iconUrl = fallbackIcon,
                                groupName = cleanList,
                                autoTranslate = entry.autoTranslate,
                                showInAllFeeds = true,
                                sortOrder = feedOrderCursor
                            )
                            val insertedId = dao.insertFeed(entity)
                            val realId = if (insertedId > 0) insertedId else dao.getFeedByUrl(normalizedXmlUrl)?.id ?: 0L
                            if (realId > 0L) importedFeedsCount++
                        }
                    }

                    if (syncAfterFeedImport && importedFeedsCount > 0) {
                        runCatching {
                            syncAllFeeds(
                                settings = settings,
                                onProgress = onProgress?.let { p -> { c, t, _ -> p(c, t) } }
                            )
                        }
                    }
                }

                BackupImportSummary(
                    listsCount = importedListsCount,
                    feedsCount = importedFeedsCount,
                    savedCount = 0,
                    readCount = 0
                )
            }
        }
    }

    suspend fun importOpmlXml(
        rawXml: String,
        settings: AppSettings,
        onProgress: ((checked: Int, total: Int) -> Unit)? = null
    ): Result<Int> = coroutineScope {
        runCatching {
            val parsedOpml = OpmlManager.parseOpmlXml(rawXml)
            if (parsedOpml.feeds.isEmpty() && parsedOpml.listNames.isEmpty()) {
                throw IllegalArgumentException("No valid RSS feeds or lists found in OPML file")
            }

            var listOrderCursor = dao.getMaxCustomListSortOrder() ?: 0
            parsedOpml.listNames.forEach { listName ->
                val clean = listName.trim()
                if (clean.isNotEmpty()) {
                    listOrderCursor += 10
                    dao.insertCustomList(
                        CustomListEntity(
                            name = clean,
                            showInAllLists = true,
                            sortOrder = listOrderCursor
                        )
                    )
                }
            }

            var feedOrderCursor = dao.getMaxFeedSortOrder() ?: 0
            val importedFeeds = mutableListOf<FeedEntity>()
            for (entry in parsedOpml.feeds) {
                val cleanList = entry.listName.trim()
                if (cleanList.isNotEmpty()) {
                    listOrderCursor += 10
                    dao.insertCustomList(
                        CustomListEntity(
                            name = cleanList,
                            showInAllLists = true,
                            sortOrder = listOrderCursor
                        )
                    )
                }

                val normalizedXmlUrl = rssParser.normalizeInputUrl(entry.xmlUrl)
                val existing = dao.getFeedByUrl(normalizedXmlUrl) ?: dao.getFeedByUrl(entry.xmlUrl)
                if (existing != null) {
                    val updated = existing.copy(
                        title = existing.title.ifBlank { entry.title },
                        groupName = if (cleanList.isNotEmpty()) cleanList else existing.groupName,
                        autoTranslate = existing.autoTranslate || entry.autoTranslate
                    )
                    dao.updateFeed(updated)
                    importedFeeds.add(updated)
                } else {
                    val fallbackSite = entry.htmlUrl.ifBlank { rssParser.extractOriginUrl(normalizedXmlUrl) }
                    val fallbackTitle = entry.title.ifBlank {
                        rssParser.extractHost(normalizedXmlUrl).ifBlank { normalizedXmlUrl }
                    }
                    val fallbackHost = rssParser.extractHost(fallbackSite.ifBlank { normalizedXmlUrl })
                    val fallbackIcon = if (fallbackHost.isNotBlank()) {
                        "https://www.google.com/s2/favicons?domain=$fallbackHost&sz=128"
                    } else {
                        ""
                    }

                    feedOrderCursor += 10
                    val entity = FeedEntity(
                        url = normalizedXmlUrl,
                        siteUrl = fallbackSite,
                        title = fallbackTitle,
                        iconUrl = fallbackIcon,
                        groupName = cleanList,
                        autoTranslate = entry.autoTranslate,
                        showInAllFeeds = true,
                        sortOrder = feedOrderCursor
                    )
                    val insertedId = dao.insertFeed(entity)
                    val realId = if (insertedId > 0) insertedId else dao.getFeedByUrl(normalizedXmlUrl)?.id ?: 0L
                    if (realId > 0L) {
                        importedFeeds.add(entity.copy(id = realId))
                    }
                }
            }

            syncAllFeeds(
                settings = settings,
                onProgress = onProgress?.let { p -> { c, t, _ -> p(c, t) } }
            )

            importedFeeds.size
        }
    }

    suspend fun refreshFreeModels(): Result<List<String>> {
        val res = geminiService.fetchFreeModelsList()
        res.onSuccess { models ->
            settingsRepository.updateFetchedModels(models)
        }
        return res
    }

    suspend fun refreshFreeOllamaModels(): Result<List<String>> {
        val res = ollamaService.fetchFreeModelsList()
        res.onSuccess { models ->
            settingsRepository.updateFetchedOllamaModels(models)
        }
        return res
    }

    suspend fun addFeed(
        rawUrl: String,
        customGroup: String,
        autoTranslate: Boolean,
        settings: AppSettings,
        defaultReaderMode: ReaderAiMode = ReaderAiMode.NONE,
        showInAllFeeds: Boolean = true,
        showInAllLists: Boolean = true,
        syncEnabled: Boolean = true,
        notifyEnabled: Boolean = false
    ): Result<FeedEntity> {
        val parsedAttempt = rssParser.fetchAndParseFeed(rawUrl)
        val initialFeed = parsedAttempt.getOrElse { err ->
            return Result.failure(err)
        }

        val finalParsed: ParsedRssFeed = if (initialFeed.items.isNotEmpty()) {
            initialFeed
        } else {
            if (initialFeed.rawFallbackBody.isBlank()) {
                return Result.failure(IllegalStateException("Empty response from feed URL"))
            }
            geminiService.discoverFeedViaGemini(
                apiKey = settings.effectiveGeminiKey,
                feedUrl = initialFeed.feedUrl,
                rawDocument = initialFeed.rawFallbackBody,
                availableModels = settings.availableModels,
                selectedModel = settings.selectedModel,
                smartRssParser = rssParser,
                onModelSwitched = { switched -> settingsRepository.setSelectedModel(switched) }
            ).getOrElse { geminiErr ->
                return Result.failure(
                    IllegalStateException("Could not parse feed directly or via Gemini: ${geminiErr.message}")
                )
            }
        }

        val cleanGroup = customGroup.trim()
        if (cleanGroup.isNotEmpty()) {
            dao.insertCustomList(
                CustomListEntity(
                    name = cleanGroup,
                    showInAllLists = true,
                    sortOrder = nextCustomListSortOrder()
                )
            )
        }

        val existing = dao.getFeedByUrl(finalParsed.feedUrl)
        val savedFeed: FeedEntity
        if (existing != null) {
            savedFeed = existing.copy(
                title = existing.title.ifBlank { finalParsed.title },
                iconUrl = finalParsed.iconUrl.ifBlank { existing.iconUrl },
                groupName = if (cleanGroup.isNotEmpty()) cleanGroup else existing.groupName,
                autoTranslate = autoTranslate,
                defaultReaderMode = defaultReaderMode.name,
                showInAllFeeds = showInAllFeeds,
                showInAllLists = showInAllLists,
                syncEnabled = syncEnabled,
                notifyEnabled = notifyEnabled,
                httpEtag = finalParsed.httpEtag.ifBlank { existing.httpEtag },
                httpLastModified = finalParsed.httpLastModified.ifBlank { existing.httpLastModified },
                lastBodyDigest = finalParsed.bodyDigest.ifBlank { existing.lastBodyDigest }
            )
            dao.updateFeed(savedFeed)
        } else {
            val entity = FeedEntity(
                url = finalParsed.feedUrl,
                siteUrl = finalParsed.siteUrl,
                title = finalParsed.title,
                iconUrl = finalParsed.iconUrl,
                groupName = cleanGroup,
                autoTranslate = autoTranslate,
                defaultReaderMode = defaultReaderMode.name,
                showInAllFeeds = showInAllFeeds,
                showInAllLists = showInAllLists,
                syncEnabled = syncEnabled,
                notifyEnabled = notifyEnabled,
                sortOrder = nextFeedSortOrder(),
                httpEtag = finalParsed.httpEtag,
                httpLastModified = finalParsed.httpLastModified,
                lastBodyDigest = finalParsed.bodyDigest
            )
            val insertedId = dao.insertFeed(entity)
            val realId = if (insertedId > 0) insertedId else dao.getFeedByUrl(finalParsed.feedUrl)?.id ?: 0L
            savedFeed = entity.copy(id = realId)
        }

        if (savedFeed.id > 0L) {
            persistParsedArticles(
                feed = savedFeed,
                parsed = finalParsed,
                httpEtag = finalParsed.httpEtag.ifBlank { savedFeed.httpEtag },
                httpLastModified = finalParsed.httpLastModified.ifBlank { savedFeed.httpLastModified },
                bodyDigest = finalParsed.bodyDigest.ifBlank { savedFeed.lastBodyDigest },
                enrichMissingNewItemImages = false // already enriched up to 5 items during addFeed
            )
            if (savedFeed.autoTranslate) {
                autoTranslateFeedPosts(savedFeed.id, settings)
            }
        }
        return Result.success(savedFeed)
    }

    private suspend fun translateSingleHeadline(
        text: String,
        settings: AppSettings
    ): Result<String> {
        return when (settings.titleProvider) {
            TitleTranslationProvider.GEMINI -> geminiService.translateHeadline(
                apiKey = settings.effectiveGeminiKey,
                text = text,
                targetLanguage = settings.targetLanguage,
                availableModels = settings.availableModels,
                selectedModel = settings.selectedModel,
                onModelSwitched = { newModel -> settingsRepository.setSelectedModel(newModel) }
            )
            TitleTranslationProvider.OLLAMA -> ollamaService.translateHeadline(
                apiKey = settings.effectiveOllamaKey,
                text = text,
                targetLanguage = settings.targetLanguage,
                availableModels = settings.availableOllamaModels,
                selectedModel = settings.selectedOllamaModel,
                onModelSwitched = { newModel ->
                    settingsRepository.setSelectedOllamaModel(newModel, userInitiated = false)
                }
            )
            else -> headlineTranslator.translateHeadline(
                text = text,
                targetLanguage = settings.targetLanguage,
                provider = settings.titleProvider,
                deeplApiKey = settings.effectiveDeeplKey
            )
        }
    }

    /**
     * Automatically translates ALL old & new posts strictly for a feed that has autoTranslate = true
     * (used when enabling Auto-Translate on a feed or switching target language).
     */
    suspend fun autoTranslateFeedPosts(feedId: Long, settings: AppSettings) = coroutineScope {
        val feed = dao.getFeedByIdOnce(feedId) ?: return@coroutineScope
        if (!feed.autoTranslate) return@coroutineScope

        val langIso = settings.targetLanguage.isoCode
        val articles = dao.getArticlesByFeedIdOnce(feedId)
        val semaphore = Semaphore(4)

        articles.map { article ->
            async {
                semaphore.withPermit {
                    if (!article.translatedTitle.isNullOrBlank() && article.translatedTitleLang == langIso) {
                        if (!article.showTranslatedTitle) {
                            dao.toggleShowTranslatedTitle(article.id, true)
                        }
                    } else {
                        translateSingleHeadline(
                            text = article.title,
                            settings = settings
                        ).onSuccess { translated ->
                            dao.updateTitleTranslation(
                                articleId = article.id,
                                translatedTitle = translated,
                                lang = langIso,
                                showTranslated = true
                            )
                        }
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Translates ONLY newly inserted untranslated posts during routine feed sync.
     */
    private suspend fun autoTranslateNewUntranslatedPosts(feedId: Long, settings: AppSettings) = coroutineScope {
        val langIso = settings.targetLanguage.isoCode
        val untranslated = dao.getUntranslatedArticlesByFeedIdOnce(feedId, langIso)
        if (untranslated.isEmpty()) return@coroutineScope

        val semaphore = Semaphore(4)
        untranslated.map { article ->
            async {
                semaphore.withPermit {
                    translateSingleHeadline(
                        text = article.title,
                        settings = settings
                    ).onSuccess { translated ->
                        dao.updateTitleTranslation(
                            articleId = article.id,
                            translatedTitle = translated,
                            lang = langIso,
                            showTranslated = true
                        )
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Interleaves feeds round-robin across distinct hosts so multiple feeds belonging to the
     * same domain (e.g. YouTube, Reddit, RSSHub, FeedBurner, WordPress, news sections) are spread
     * evenly across the batch instead of bursting against one server simultaneously.
     */
    private fun interleaveFeedsByHost(feeds: List<FeedEntity>): List<FeedEntity> {
        if (feeds.size <= 2) return feeds
        val buckets = LinkedHashMap<String, ArrayDeque<FeedEntity>>()
        for (feed in feeds) {
            val host = rssParser.extractHost(feed.url).lowercase(Locale.ROOT).ifBlank { feed.url }
            buckets.getOrPut(host) { ArrayDeque() }.addLast(feed)
        }
        val interleaved = ArrayList<FeedEntity>(feeds.size)
        var addedInRound: Boolean
        do {
            addedInRound = false
            for (queue in buckets.values) {
                if (queue.isNotEmpty()) {
                    interleaved.add(queue.removeFirst())
                    addedInRound = true
                }
            }
        } while (addedInRound)
        return interleaved
    }

    private suspend fun getActiveSyncableFeedsOnce(): List<FeedEntity> {
        val allFeeds = dao.getAllFeedsOnce()
        if (allFeeds.isEmpty()) return emptyList()
        val disabledListNamesLower = dao.getAllCustomListsOnce()
            .asSequence()
            .filter { !it.syncEnabled }
            .map { it.name.trim().lowercase(Locale.ROOT) }
            .toHashSet()
        return allFeeds.filter { feed ->
            val listSyncEnabled = feed.groupName.isBlank() ||
                feed.groupName.trim().lowercase(Locale.ROOT) !in disabledListNamesLower
            feed.syncEnabled && listSyncEnabled
        }
    }

    /**
     * Refreshes all active sync-enabled feeds (skipping feeds whose auto-refresh is disabled or
     * whose parent Custom List has auto-refresh disabled):
     * 1. Interleaves feeds round-robin by host + enforces global (`Semaphore(12)`) and per-host (`Semaphore(2)`)
     *    concurrency so shared domains never drop requests under burst rate-limiting.
     * 2. Persists parsed articles into Room IMMEDIATELY before optional OpenGraph `<head>` probes so slow
     *    article webpages can never cancel or roll back any feed's articles.
     * 3. Automatically runs a gentler Wave 2 recovery pass (`Semaphore(4)`, per-host `Semaphore(1)`) on any
     *    feed that experienced a transient error in Wave 1.
     * 4. Re-checks active syncable feeds before finishing so any feed added while sync was running is also synced.
     */
    suspend fun syncAllFeeds(
        settings: AppSettings,
        onProgress: ((checked: Int, total: Int, currentFeed: FeedEntity?) -> Unit)? = null
    ): Result<Int> = supervisorScope {
        val initialFeeds = getActiveSyncableFeedsOnce()
        if (initialFeeds.isEmpty()) return@supervisorScope Result.success(0)

        val completedResults = LinkedHashMap<Long, Pair<FeedEntity, Result<Int>>>()
        val processedFeedIds = HashSet<Long>()
        val checkedCounter = AtomicInteger(0)
        val totalFeedsCounter = AtomicInteger(0)

        while (true) {
            val currentSyncableFeeds = getActiveSyncableFeedsOnce()
            val pendingFeeds = interleaveFeedsByHost(
                currentSyncableFeeds.filter { it.id !in processedFeedIds }
            )
            if (pendingFeeds.isEmpty()) break

            pendingFeeds.forEach { processedFeedIds.add(it.id) }
            val updatedTotal = totalFeedsCounter.addAndGet(pendingFeeds.size)
            onProgress?.invoke(checkedCounter.get(), updatedTotal, pendingFeeds.firstOrNull())

            // Wave 1: Primary concurrent refresh across all pending feeds
            val wave1Gate = Semaphore(12)
            val wave1Results: List<Pair<FeedEntity, Result<Int>>> = pendingFeeds.map { feed ->
                async {
                    onProgress?.invoke(checkedCounter.get(), totalFeedsCounter.get(), feed)
                    val res = wave1Gate.withPermit {
                        syncSingleFeedCore(feed = feed, settings = settings, gentleMode = false)
                    }
                    val needsWave2Retry = res.isFailure && !rssParser.isPermanentFeedError(res.exceptionOrNull())
                    if (!needsWave2Retry) {
                        val done = checkedCounter.incrementAndGet()
                        onProgress?.invoke(done, totalFeedsCounter.get(), feed)
                    }
                    feed to res
                }
            }.awaitAll()

            val failedInWave1 = mutableListOf<FeedEntity>()
            for ((feed, res) in wave1Results) {
                completedResults[feed.id] = feed to res
                if (res.isFailure && !rssParser.isPermanentFeedError(res.exceptionOrNull())) {
                    failedInWave1.add(feed)
                }
            }

            // Wave 2: Automatic gentler recovery pass for feeds with transient errors in Wave 1
            if (failedInWave1.isNotEmpty()) {
                delay(650L)
                val wave2Gate = Semaphore(4)
                val wave2Results = interleaveFeedsByHost(failedInWave1).map { feed ->
                    async {
                        onProgress?.invoke(checkedCounter.get(), totalFeedsCounter.get(), feed)
                        val res = wave2Gate.withPermit {
                            syncSingleFeedCore(feed = feed, settings = settings, gentleMode = true)
                        }
                        val done = checkedCounter.incrementAndGet()
                        onProgress?.invoke(done, totalFeedsCounter.get(), feed)
                        feed to res
                    }
                }.awaitAll()

                for ((feed, res) in wave2Results) {
                    completedResults[feed.id] = feed to res
                }
            }
        }

        val finalTotal = totalFeedsCounter.get()
        if (finalTotal > 0) {
            onProgress?.invoke(finalTotal, finalTotal, null)
        }

        // Translate headlines for feeds with autoTranslate enabled AFTER all feeds have been synced & persisted
        val feedsNeedingAutoTranslate = completedResults.values.filter { (feed, res) ->
            res.getOrDefault(0) > 0 && feed.autoTranslate
        }
        if (feedsNeedingAutoTranslate.isNotEmpty()) {
            val translateGate = Semaphore(4)
            feedsNeedingAutoTranslate.map { (feed, _) ->
                async {
                    translateGate.withPermit {
                        runCatching {
                            withTimeoutOrNull(10_000L) {
                                autoTranslateNewUntranslatedPosts(feed.id, settings)
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        val results = completedResults.values.map { it.second }
        val newCount = results.sumOf { it.getOrDefault(0) }
        val firstFailure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        if (results.isNotEmpty() && results.all { it.isFailure } && firstFailure != null) {
            Result.failure(firstFailure)
        } else {
            Result.success(newCount)
        }
    }

    /**
     * Manually refreshes feeds belonging to the specified Custom List (even when the Custom List's
     * auto-refresh is disabled).
     */
    suspend fun syncCustomListFeeds(
        listName: String,
        settings: AppSettings,
        onProgress: ((checked: Int, total: Int) -> Unit)? = null
    ): Result<Int> = supervisorScope {
        val cleanName = listName.trim()
        if (cleanName.isEmpty()) return@supervisorScope Result.success(0)

        val listFeeds = dao.getAllFeedsOnce().filter {
            it.groupName.equals(cleanName, ignoreCase = true)
        }
        if (listFeeds.isEmpty()) return@supervisorScope Result.success(0)

        val targetFeeds = interleaveFeedsByHost(
            listFeeds.filter { it.syncEnabled }.ifEmpty { listFeeds }
        )
        val total = targetFeeds.size
        val checkedCounter = AtomicInteger(0)
        onProgress?.invoke(0, total)

        val completedResults = LinkedHashMap<Long, Pair<FeedEntity, Result<Int>>>()
        val wave1Gate = Semaphore(12)
        val wave1Results: List<Pair<FeedEntity, Result<Int>>> = targetFeeds.map { feed ->
            async {
                val res = wave1Gate.withPermit {
                    syncSingleFeedCore(feed = feed, settings = settings, gentleMode = false)
                }
                val needsWave2Retry = res.isFailure && !rssParser.isPermanentFeedError(res.exceptionOrNull())
                if (!needsWave2Retry) {
                    val done = checkedCounter.incrementAndGet()
                    onProgress?.invoke(done, total)
                }
                feed to res
            }
        }.awaitAll()

        val failedInWave1 = mutableListOf<FeedEntity>()
        for ((feed, res) in wave1Results) {
            completedResults[feed.id] = feed to res
            if (res.isFailure && !rssParser.isPermanentFeedError(res.exceptionOrNull())) {
                failedInWave1.add(feed)
            }
        }

        if (failedInWave1.isNotEmpty()) {
            delay(650L)
            val wave2Gate = Semaphore(4)
            val wave2Results = interleaveFeedsByHost(failedInWave1).map { feed ->
                async {
                    val res = wave2Gate.withPermit {
                        syncSingleFeedCore(feed = feed, settings = settings, gentleMode = true)
                    }
                    val done = checkedCounter.incrementAndGet()
                    onProgress?.invoke(done, total)
                    feed to res
                }
            }.awaitAll()

            for ((feed, res) in wave2Results) {
                completedResults[feed.id] = feed to res
            }
        }

        onProgress?.invoke(total, total)

        val feedsNeedingAutoTranslate = completedResults.values.filter { (feed, res) ->
            res.getOrDefault(0) > 0 && feed.autoTranslate
        }
        if (feedsNeedingAutoTranslate.isNotEmpty()) {
            val translateGate = Semaphore(4)
            feedsNeedingAutoTranslate.map { (feed, _) ->
                async {
                    translateGate.withPermit {
                        runCatching {
                            withTimeoutOrNull(10_000L) {
                                autoTranslateNewUntranslatedPosts(feed.id, settings)
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        val results = completedResults.values.map { it.second }
        val newCount = results.sumOf { it.getOrDefault(0) }
        val firstFailure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        if (results.isNotEmpty() && results.all { it.isFailure } && firstFailure != null) {
            Result.failure(firstFailure)
        } else {
            Result.success(newCount)
        }
    }

    /**
     * Refreshes ONLY the single specified RSS feed (with automatic gentle retry on transient failure).
     */
    suspend fun syncSingleFeed(feed: FeedEntity, settings: AppSettings): Result<Int> {
        var result = syncSingleFeedCore(feed = feed, settings = settings, gentleMode = false)
        if (result.isFailure && !rssParser.isPermanentFeedError(result.exceptionOrNull())) {
            delay(500L)
            result = syncSingleFeedCore(feed = feed, settings = settings, gentleMode = true)
        }
        return result.onSuccess { addedCount ->
            val freshFeed = dao.getFeedByIdOnce(feed.id) ?: feed
            if (addedCount > 0 && freshFeed.autoTranslate) {
                runCatching {
                    withTimeoutOrNull(10_000L) {
                        autoTranslateNewUntranslatedPosts(freshFeed.id, settings)
                    }
                }
            }
        }
    }

    private suspend fun syncSingleFeedCore(
        feed: FeedEntity,
        settings: AppSettings? = null,
        gentleMode: Boolean = false
    ): Result<Int> {
        return runCatching {
            val freshFeed = dao.getFeedByIdOnce(feed.id) ?: feed

            val outcome = rssParser.checkFeedForUpdates(
                feedUrl = freshFeed.url,
                previousEtag = freshFeed.httpEtag,
                previousLastModified = freshFeed.httpLastModified,
                previousBodyDigest = freshFeed.lastBodyDigest,
                gentleMode = gentleMode
            ).getOrThrow()

            when (outcome) {
                is FeedRefreshOutcome.NotModified -> {
                    if (outcome.httpEtag != freshFeed.httpEtag ||
                        outcome.httpLastModified != freshFeed.httpLastModified ||
                        outcome.bodyDigest != freshFeed.lastBodyDigest
                    ) {
                        dao.updateFeedHttpSyncState(
                            feedId = freshFeed.id,
                            httpEtag = outcome.httpEtag,
                            httpLastModified = outcome.httpLastModified,
                            lastBodyDigest = outcome.bodyDigest
                        )
                    }
                    0
                }

                is FeedRefreshOutcome.Updated -> {
                    var resolved = outcome.parsedFeed
                    // If this feed is a non-standard HTML page that required Gemini discovery on addFeed,
                    // recover new items via Gemini ONLY when its document digest actually changed
                    if (!gentleMode &&
                        resolved.items.isEmpty() &&
                        resolved.rawFallbackBody.isNotBlank() &&
                        settings != null &&
                        settings.effectiveGeminiKey.isNotBlank() &&
                        (resolved.bodyDigest.isBlank() || resolved.bodyDigest != freshFeed.lastBodyDigest)
                    ) {
                        val geminiDiscovered = withTimeoutOrNull(8_000L) {
                            geminiService.discoverFeedViaGemini(
                                apiKey = settings.effectiveGeminiKey,
                                feedUrl = resolved.feedUrl.ifBlank { freshFeed.url },
                                rawDocument = resolved.rawFallbackBody,
                                availableModels = settings.availableModels,
                                selectedModel = settings.selectedModel,
                                smartRssParser = rssParser,
                                onModelSwitched = { switched -> settingsRepository.setSelectedModel(switched) }
                            ).getOrNull()
                        }
                        if (geminiDiscovered != null && geminiDiscovered.items.isNotEmpty()) {
                            resolved = geminiDiscovered.copy(bodyDigest = resolved.bodyDigest)
                        }
                    }

                    var activeFeed = freshFeed
                    val discoveredFeedUrl = resolved.feedUrl.trim()
                    val canUpdateCanonicalUrl = discoveredFeedUrl.isNotBlank() &&
                        discoveredFeedUrl != freshFeed.url &&
                        resolved.items.isNotEmpty() &&
                        dao.getFeedByUrl(discoveredFeedUrl) == null
                    val needsMetadataUpdate = canUpdateCanonicalUrl ||
                        ((freshFeed.iconUrl.isBlank() || freshFeed.siteUrl.isBlank()) && resolved.iconUrl.isNotBlank())

                    if (needsMetadataUpdate) {
                        activeFeed = freshFeed.copy(
                            url = if (canUpdateCanonicalUrl) discoveredFeedUrl else freshFeed.url,
                            iconUrl = resolved.iconUrl.ifBlank { freshFeed.iconUrl },
                            siteUrl = resolved.siteUrl.ifBlank { freshFeed.siteUrl }
                        )
                        dao.updateFeed(activeFeed)
                    }

                    persistParsedArticles(
                        feed = activeFeed,
                        parsed = resolved,
                        httpEtag = outcome.httpEtag.ifBlank { resolved.httpEtag },
                        httpLastModified = outcome.httpLastModified.ifBlank { resolved.httpLastModified },
                        bodyDigest = outcome.bodyDigest.ifBlank { resolved.bodyDigest },
                        enrichMissingNewItemImages = true
                    )
                }
            }
        }
    }

    /**
     * Detects whether a dated feed lists items in strictly oldest-first ascending order,
     * without being fooled by a single old pinned/sticky post at slot #0.
     */
    private fun isFeedChronologicallyAscending(datedItems: List<ParsedRssItem>): Boolean {
        if (datedItems.size < 2) return false
        if (datedItems.size == 2) {
            return datedItems[0].publishedAt < datedItems[1].publishedAt
        }
        var ascendingPairs = 0
        var descendingPairs = 0
        for (i in 0 until datedItems.lastIndex) {
            val a = datedItems[i].publishedAt
            val b = datedItems[i + 1].publishedAt
            if (a < b) ascendingPairs++ else if (a > b) descendingPairs++
        }
        return ascendingPairs > 0 && ascendingPairs > descendingPairs * 2
    }

    /**
     * Persists genuinely new articles IMMEDIATELY to Room while keeping cleared/read/archive posts
     * permanently suppressed and maintaining exact feed publication timestamps (`publishedAt`).
     * Optional `<head>` OpenGraph image probes run AFTER Room persistence so slow article pages
     * can never block, timeout, or lose synced feed posts.
     */
    suspend fun persistParsedArticles(
        feed: FeedEntity,
        parsed: ParsedRssFeed,
        httpEtag: String = parsed.httpEtag.ifBlank { feed.httpEtag },
        httpLastModified: String = parsed.httpLastModified.ifBlank { feed.httpLastModified },
        bodyDigest: String = parsed.bodyDigest.ifBlank { feed.lastBodyDigest },
        enrichMissingNewItemImages: Boolean = false
    ): Int {
        val feedId = feed.id
        if (parsed.items.isEmpty()) {
            if (httpEtag != feed.httpEtag ||
                httpLastModified != feed.httpLastModified ||
                bodyDigest != feed.lastBodyDigest
            ) {
                dao.updateFeedHttpSyncState(
                    feedId = feedId,
                    httpEtag = httpEtag,
                    httpLastModified = httpLastModified,
                    lastBodyDigest = bodyDigest
                )
            }
            return 0
        }

        val existingGuidTimestamps = dao.getExistingGuidTimestampsForFeed(feedId)
        val existingPubAtByGuid = HashMap<String, Long>(existingGuidTimestamps.size)
        val existingGuids = HashSet<String>(existingGuidTimestamps.size)
        for (entry in existingGuidTimestamps) {
            existingGuids.add(entry.guid)
            existingPubAtByGuid[entry.guid] = entry.publishedAt
        }

        val seenKeys = dao.getSeenKeysForFeed(feedId).toHashSet()

        val hasSyncedBefore = feed.lastSyncedPubAt > 0L ||
            feed.lastSyncedGuid.isNotBlank() ||
            seenKeys.isNotEmpty() ||
            existingGuids.isNotEmpty()

        val datedItems = parsed.items.filter { it.hasExplicitDate }
        val isOldestFirst = isFeedChronologicallyAscending(datedItems)

        val nowMs = System.currentTimeMillis()
        val maxAllowedFutureMs = nowMs + 5 * 60_000L
        val effectiveWatermark = if (feed.lastSyncedPubAt > 0L) {
            minOf(feed.lastSyncedPubAt, maxAllowedFutureMs)
        } else {
            0L
        }
        val maxArchiveAgeFromWatermarkMs = 7L * 24 * 3_600_000L

        // Pre-compute first/last positions of already-known items in this feed snapshot,
        // ignoring an isolated older sticky/pinned post at slot #0 so brand-new posts below a
        // pinned post are never misclassified as historical archive items when older posts rotate out.
        var firstSeenIndexInFeed = -1
        var nonStickyLastSeenIndex = -1
        if (hasSyncedBefore) {
            for ((idx, candidate) in parsed.items.withIndex()) {
                val cGuid = candidate.guid.trim().ifBlank { candidate.link.trim().ifBlank { candidate.title.trim() } }
                val cKeys = buildDedupeKeys(guid = cGuid, link = candidate.link, title = candidate.title)
                val known = cKeys.any { it in seenKeys } ||
                    cGuid in existingGuids ||
                    (feed.lastSyncedGuid.isNotBlank() && cGuid == feed.lastSyncedGuid)
                if (known) {
                    if (firstSeenIndexInFeed == -1) firstSeenIndexInFeed = idx
                    val isLikelyOldStickyAtTop = idx == 0 &&
                        parsed.items.size > 1 &&
                        ((effectiveWatermark > 0L && candidate.hasExplicitDate && candidate.publishedAt < effectiveWatermark - 60_000L) ||
                            (feed.lastSyncedGuid.isNotBlank() && cGuid != feed.lastSyncedGuid))
                    if (!isLikelyOldStickyAtTop) {
                        nonStickyLastSeenIndex = idx
                    }
                }
            }
        }

        val newCandidates = mutableListOf<Pair<String, ParsedRssItem>>()
        val candidateImageBackfills = mutableListOf<Pair<String, String>>()
        val seenRecordsToSave = mutableListOf<SeenArticleEntity>()

        for ((index, item) in parsed.items.withIndex()) {
            val guid = item.guid.trim().ifBlank { item.link.trim().ifBlank { item.title.trim() } }
            val itemKeys = buildDedupeKeys(guid = guid, link = item.link, title = item.title)

            // Record only genuinely unseen keys into seen_articles
            itemKeys.forEach { key ->
                if (key !in seenKeys) {
                    seenRecordsToSave.add(SeenArticleEntity(feedId = feedId, dedupeKey = key, seenAt = nowMs))
                }
            }

            val isAlreadySeen = itemKeys.any { it in seenKeys } ||
                guid in existingGuids ||
                (feed.lastSyncedGuid.isNotBlank() && guid == feed.lastSyncedGuid)

            if (isAlreadySeen) {
                if (guid in existingGuids) {
                    if (!item.imageUrl.isNullOrBlank()) {
                        candidateImageBackfills.add(guid to item.imageUrl)
                    }
                    // Repair existing article timestamp if it was previously stored with a fallback or imprecise timestamp
                    if (item.hasExplicitDate) {
                        val storedPubAt = existingPubAtByGuid[guid]
                        if (storedPubAt != null && abs(storedPubAt - item.publishedAt) > 1_000L) {
                            dao.updateArticlePublishedAt(feedId, guid, item.publishedAt)
                            existingPubAtByGuid[guid] = item.publishedAt
                        }
                    }
                }
                itemKeys.forEach { seenKeys.add(it) }
                continue
            }

            if (hasSyncedBefore) {
                val isStrictlyNewerThanWatermark = item.hasExplicitDate &&
                    effectiveWatermark > 0L &&
                    item.publishedAt > effectiveWatermark

                val isAncientArchiveItem = effectiveWatermark > 0L &&
                    item.hasExplicitDate &&
                    item.publishedAt < (effectiveWatermark - maxArchiveAgeFromWatermarkMs)

                if (!isOldestFirst) {
                    val isInHistoricalTailBelowAllSeen = nonStickyLastSeenIndex >= 0 && index > nonStickyLastSeenIndex
                    if (isInHistoricalTailBelowAllSeen && !isStrictlyNewerThanWatermark) {
                        itemKeys.forEach { seenKeys.add(it) }
                        continue
                    }
                    if (isAncientArchiveItem) {
                        itemKeys.forEach { seenKeys.add(it) }
                        continue
                    }
                } else {
                    val isInHistoricalHeadBeforeAllSeen = firstSeenIndexInFeed >= 0 && index < firstSeenIndexInFeed
                    if (isInHistoricalHeadBeforeAllSeen && !isStrictlyNewerThanWatermark) {
                        itemKeys.forEach { seenKeys.add(it) }
                        continue
                    }
                    if (isAncientArchiveItem) {
                        itemKeys.forEach { seenKeys.add(it) }
                        continue
                    }
                }
            }

            itemKeys.forEach { seenKeys.add(it) }
            newCandidates.add(guid to item)
        }

        // Only execute SQL UPDATE on existing articles if an article in Room genuinely lacks an image
        if (candidateImageBackfills.isNotEmpty()) {
            val guidsMissingImage = dao.getGuidsMissingImageForFeed(feedId).toHashSet()
            if (guidsMissingImage.isNotEmpty()) {
                for ((guid, imgUrl) in candidateImageBackfills) {
                    if (guid in guidsMissingImage) {
                        dao.backfillArticleImage(feedId, guid, imgUrl)
                    }
                }
            }
        }

        // 1. Insert new articles & tombstones into Room IMMEDIATELY so feed posts are never delayed or lost!
        val activeFilters = dao.getFiltersForFeedOnce(feedId)
        val toInsert = newCandidates
            .sortedBy { (_, item) -> item.publishedAt }
            .map { (guid, item) ->
                val cleanedBody = cleanContentWithFilters(item.content.take(80_000), activeFilters)
                ArticleEntity(
                    feedId = feedId,
                    guid = guid,
                    link = item.link,
                    title = item.title,
                    rawContent = cleanedBody,
                    imageUrl = item.imageUrl,
                    publishedAt = item.publishedAt
                )
            }

        if (seenRecordsToSave.isNotEmpty()) {
            dao.insertSeenArticles(seenRecordsToSave.distinctBy { it.dedupeKey })
        }
        if (toInsert.isNotEmpty()) {
            dao.insertArticles(toInsert)
        }

        val maxExplicitPubAt = datedItems
            .map { it.publishedAt }
            .filter { it <= maxAllowedFutureMs }
            .maxOrNull() ?: 0L
        val nextWatermarkPubAt = maxOf(effectiveWatermark, maxExplicitPubAt)
        val newestItem = if (isOldestFirst) {
            parsed.items.lastOrNull()
        } else {
            // Skip an older pinned item at slot #0 when recording lastSyncedGuid
            if (parsed.items.size > 1 &&
                parsed.items[0].hasExplicitDate &&
                parsed.items[1].hasExplicitDate &&
                parsed.items[0].publishedAt < parsed.items[1].publishedAt - 60_000L
            ) {
                parsed.items[1]
            } else {
                parsed.items.firstOrNull()
            }
        }
        val nextWatermarkGuid = newestItem
            ?.let { it.guid.trim().ifBlank { it.link.trim().ifBlank { it.title.trim() } } }
            .orEmpty()
            .ifBlank { feed.lastSyncedGuid }

        if (nextWatermarkPubAt != feed.lastSyncedPubAt ||
            nextWatermarkGuid != feed.lastSyncedGuid ||
            httpEtag != feed.httpEtag ||
            httpLastModified != feed.httpLastModified ||
            bodyDigest != feed.lastBodyDigest
        ) {
            dao.updateFeedSyncWatermarkAndHttpState(
                feedId = feedId,
                lastSyncedPubAt = nextWatermarkPubAt,
                lastSyncedGuid = nextWatermarkGuid,
                httpEtag = httpEtag,
                httpLastModified = httpLastModified,
                lastBodyDigest = bodyDigest
            )
        }

        // 2. Post-save bounded full-article + OpenGraph image enrichment for genuinely brand-new posts
        //    so opening any post in None / Translate / Summarize displays immediately with zero scrape delay!
        if (newCandidates.isNotEmpty() && enrichMissingNewItemImages) {
            runCatching {
                withTimeoutOrNull(6_000L) {
                    val enrichedList = rssParser.enrichNewItemsWithFullArticlesAndImages(
                        items = newCandidates.map { it.second },
                        maxFullFetches = 10,
                        maxHeadImageProbes = 4
                    )
                    for (idx in newCandidates.indices) {
                        val guid = newCandidates[idx].first
                        val beforeItem = newCandidates[idx].second
                        val afterItem = enrichedList[idx]
                        if (afterItem.content.length > beforeItem.content.length) {
                            dao.backfillArticleRawContentByGuid(
                                feedId = feedId,
                                guid = guid,
                                fullContent = afterItem.content.take(80_000)
                            )
                        }
                        if (beforeItem.imageUrl.isNullOrBlank() && !afterItem.imageUrl.isNullOrBlank()) {
                            dao.backfillArticleImage(feedId, guid, afterItem.imageUrl)
                        }
                    }
                }
            }
        }

        return toInsert.size
    }

    /**
     * Background catch-up prefetcher: upgrades short unread RSS snippets (`< 480` chars) in Room
     * with their full readable article body (and any missing OpenGraph hero image) so opening
     * an article in None, Translate, or Summarize is instantaneous.
     */
    suspend fun prefetchShortUnreadArticles(limit: Int = 36): Int = supervisorScope {
        val candidates = dao.getShortUnreadArticlesOnce(limit = limit)
        if (candidates.isEmpty()) return@supervisorScope 0

        val gate = Semaphore(6)
        val upgradedCounter = AtomicInteger(0)
        candidates.map { article ->
            async {
                gate.withPermit {
                    runCatching {
                        withTimeoutOrNull(5_500L) {
                            val page = rssParser.fetchFullArticlePage(article.link)
                            val fullBody = page.readableBody
                            if (!fullBody.isNullOrBlank() && fullBody.length > article.rawContent.length) {
                                dao.upgradeArticleRawContent(article.id, fullBody)
                                upgradedCounter.incrementAndGet()
                            }
                            if (article.imageUrl.isNullOrBlank() && !page.metaImageUrl.isNullOrBlank()) {
                                dao.backfillArticleImage(article.feedId, article.guid, page.metaImageUrl)
                            }
                        }
                    }
                }
            }
        }.awaitAll()
        upgradedCounter.get()
    }

    private fun buildDedupeKeys(guid: String, link: String, title: String): List<String> {
        val keys = ArrayList<String>(4)
        val cleanGuid = guid.trim()
        if (cleanGuid.isNotEmpty()) {
            keys.add("guid:$cleanGuid")
            if (cleanGuid.startsWith("http://", true) || cleanGuid.startsWith("https://", true)) {
                val canonGuid = rssParser.canonicalizeUrl(cleanGuid)
                if (canonGuid.isNotEmpty()) keys.add("canon:$canonGuid")
            }
        }
        val cleanLink = link.trim()
        if (cleanLink.isNotEmpty()) {
            keys.add("rawlink:$cleanLink")
            val canonLink = rssParser.canonicalizeUrl(cleanLink)
            if (canonLink.isNotEmpty()) keys.add("canon:$canonLink")
        }
        val cleanTitle = title.trim().lowercase(Locale.ROOT)
        if (cleanTitle.isNotEmpty()) {
            keys.add("title:$cleanTitle")
        }
        return keys.distinct()
    }

    suspend fun updateFeedDetails(
        feed: FeedEntity,
        newTitle: String,
        newGroup: String,
        autoTranslate: Boolean,
        settings: AppSettings,
        defaultReaderMode: ReaderAiMode = feed.resolvedReaderMode,
        showInAllFeeds: Boolean = feed.showInAllFeeds,
        showInAllLists: Boolean = feed.showInAllLists,
        syncEnabled: Boolean = feed.syncEnabled
    ) {
        val cleanGroup = newGroup.trim()
        if (cleanGroup.isNotEmpty()) {
            dao.insertCustomList(
                CustomListEntity(
                    name = cleanGroup,
                    showInAllLists = true,
                    sortOrder = nextCustomListSortOrder()
                )
            )
        }
        val updated = feed.copy(
            title = newTitle.trim().ifBlank { feed.title },
            groupName = cleanGroup,
            autoTranslate = autoTranslate,
            defaultReaderMode = defaultReaderMode.name,
            showInAllFeeds = showInAllFeeds,
            showInAllLists = showInAllLists,
            syncEnabled = syncEnabled
        )
        dao.updateFeed(updated)
        if (autoTranslate) {
            autoTranslateFeedPosts(updated.id, settings)
        }
    }

    suspend fun updateFeed(feed: FeedEntity) {
        dao.updateFeed(feed)
    }

    suspend fun getFeedReaderModeForArticle(
        articleId: Long,
        fallback: ReaderAiMode = ReaderAiMode.NONE
    ): ReaderAiMode {
        val article = dao.getArticleByIdOnce(articleId) ?: return fallback
        val feed = dao.getFeedByIdOnce(article.feedId) ?: return fallback
        return feed.resolvedReaderMode
    }

    suspend fun toggleFeedShowInAllFeeds(feed: FeedEntity): Boolean {
        val fresh = dao.getFeedByIdOnce(feed.id) ?: feed
        val next = !fresh.showInAllFeeds
        dao.setFeedShowInAllFeeds(fresh.id, next)
        return next
    }

    suspend fun toggleFeedShowInAllLists(feed: FeedEntity): Boolean {
        val fresh = dao.getFeedByIdOnce(feed.id) ?: feed
        val next = !fresh.showInAllLists
        dao.setFeedShowInAllLists(fresh.id, next)
        return next
    }

    suspend fun toggleFeedSyncEnabled(feed: FeedEntity): Boolean {
        val fresh = dao.getFeedByIdOnce(feed.id) ?: feed
        val next = !fresh.syncEnabled
        dao.setFeedSyncEnabled(fresh.id, next)
        return next
    }

    suspend fun toggleFeedPinned(feed: FeedEntity): Boolean {
        val fresh = dao.getFeedByIdOnce(feed.id) ?: feed
        val nextPinned = !fresh.isPinned
        val nextSortOrder = if (nextPinned) {
            val minPinned = dao.getMinPinnedFeedSortOrder()
            if (minPinned != null) minPinned - 10 else 0
        } else {
            nextFeedSortOrder()
        }
        dao.setFeedPinnedAndSortOrder(fresh.id, nextPinned, nextSortOrder)
        return nextPinned
    }

    suspend fun reorderFeeds(orderedIds: List<Long>) {
        if (orderedIds.isEmpty()) return
        dao.updateFeedsOrder(orderedIds)
    }

    suspend fun deleteFeed(feedId: Long) = dao.deleteFeedById(feedId)

    suspend fun deleteFeedWithBackup(feedId: Long): DeletedFeedBackup? {
        val feed = dao.getFeedByIdOnce(feedId) ?: return null
        val articles = dao.getArticlesByFeedIdOnce(feedId)
        val seenArticles = dao.getSeenArticlesForFeedOnce(feedId)
        dao.deleteFeedById(feedId)
        return DeletedFeedBackup(
            feed = feed,
            articles = articles,
            seenArticles = seenArticles
        )
    }

    suspend fun restoreDeletedFeed(backup: DeletedFeedBackup) {
        dao.upsertFeed(backup.feed)
        if (backup.articles.isNotEmpty()) {
            dao.upsertArticles(backup.articles)
        }
        if (backup.seenArticles.isNotEmpty()) {
            dao.insertSeenArticles(backup.seenArticles)
        }
    }

    suspend fun markFeedAllRead(feedId: Long) = dao.markAllFeedArticlesRead(feedId)

    suspend fun markAllRead() = dao.markAllArticlesRead()

    /**
     * Records tombstones in [seen_articles] for all read unsaved posts before deleting them
     * from [articles], guaranteeing they can never reappear on subsequent feed refreshes.
     * Returns the deleted [ArticleEntity] list so the user can Undo within 5 seconds.
     */
    suspend fun clearReadHistory(): List<ArticleEntity> {
        val readArticles = dao.getReadUnsavedArticlesOnce()
        if (readArticles.isNotEmpty()) {
            val nowMs = System.currentTimeMillis()
            val tombstones = readArticles.flatMap { article ->
                buildDedupeKeys(
                    guid = article.guid,
                    link = article.link,
                    title = article.title
                ).map { key ->
                    SeenArticleEntity(
                        feedId = article.feedId,
                        dedupeKey = key,
                        seenAt = nowMs
                    )
                }
            }
            dao.insertSeenArticles(tombstones)
            dao.deleteReadUnsavedArticles()
        }
        return readArticles
    }

    suspend fun restoreClearedArticles(articles: List<ArticleEntity>) {
        if (articles.isEmpty()) return
        val existingFeedIds = dao.getAllFeedsOnce().map { it.id }.toHashSet()
        val validArticles = articles.filter { it.feedId in existingFeedIds }
        if (validArticles.isNotEmpty()) {
            dao.upsertArticles(validArticles)
        }
    }

    suspend fun setArticleRead(articleId: Long, isRead: Boolean) = dao.setArticleRead(articleId, isRead)

    suspend fun setArticleSaved(articleId: Long, isSaved: Boolean) = dao.setArticleSaved(articleId, isSaved)

    suspend fun translateOrToggleCardTitle(
        article: ArticleEntity,
        settings: AppSettings
    ): Result<String> {
        val langIso = settings.targetLanguage.isoCode
        if (!article.translatedTitle.isNullOrBlank() && article.translatedTitleLang == langIso) {
            val nextShow = !article.showTranslatedTitle
            dao.toggleShowTranslatedTitle(article.id, nextShow)
            return Result.success(article.translatedTitle)
        }

        val res = translateSingleHeadline(
            text = article.title,
            settings = settings
        )
        res.onSuccess { translated ->
            dao.updateTitleTranslation(
                articleId = article.id,
                translatedTitle = translated,
                lang = langIso,
                showTranslated = true
            )
        }
        return res
    }

    /**
     * Processes an article inside the Reader:
     * - SUMMARIZE mode: Uses either Gemini API or Ollama API based on [settings.summaryProvider].
     * - TRANSLATE mode: Uses the active Translator selected in Translate & Languages (`DeepL Web`, `DeepL API`, `Google Web`, `Bing Web`, `Gemini`, or `Ollama`).
     */
    suspend fun processArticleInReader(
        articleId: Long,
        mode: ReaderAiMode,
        settings: AppSettings,
        forceRefresh: Boolean = false
    ): Result<AiArticleOutput> {
        val current = dao.getArticleByIdOnce(articleId)
            ?: return Result.failure(IllegalArgumentException("Article not found"))

        if (mode == ReaderAiMode.NONE) {
            var fullSourceBody = current.rawContent
            if (fullSourceBody.length < 480 && current.link.startsWith("http")) {
                val page = rssParser.fetchFullArticlePage(current.link)
                val scraped = page.readableBody
                if (!scraped.isNullOrBlank() && scraped.length > fullSourceBody.length) {
                    fullSourceBody = scraped
                    dao.updateRawContent(articleId, fullSourceBody)
                }
                if (current.imageUrl.isNullOrBlank() && !page.metaImageUrl.isNullOrBlank()) {
                    dao.backfillArticleImage(current.feedId, current.guid, page.metaImageUrl)
                }
            }
            return Result.success(
                AiArticleOutput(
                    cleanBody = fullSourceBody.ifBlank { current.title },
                    usedModel = ""
                )
            )
        }

        val targetIso = settings.targetLanguage.isoCode
        val chosenTitleProvider = settings.titleProvider
        val chosenSummaryProvider = settings.summaryProvider
        val activeTranslateCustomPrompt = settings.activeTranslatePromptOverride
        val activeSummarizeCustomPrompt = settings.activeSummarizePromptOverride

        val translateEngineKey = if (
            (chosenTitleProvider == TitleTranslationProvider.GEMINI || chosenTitleProvider == TitleTranslationProvider.OLLAMA) &&
            activeTranslateCustomPrompt != null
        ) {
            "${chosenTitleProvider.name}:cp_${activeTranslateCustomPrompt.hashCode()}"
        } else {
            chosenTitleProvider.name
        }

        val summaryEngineKey = if (activeSummarizeCustomPrompt != null) {
            "${chosenSummaryProvider.name}:cp_${activeSummarizeCustomPrompt.hashCode()}"
        } else {
            chosenSummaryProvider.name
        }

        if (!forceRefresh && current.aiProcessedLang == targetIso) {
            when (mode) {
                ReaderAiMode.NONE -> Unit
                ReaderAiMode.SUMMARIZE -> {
                    val cachedSummaryEngine = current.aiSummarizedEngine ?: SummaryProvider.GEMINI.name
                    if (!current.aiSummarizedBody.isNullOrBlank() &&
                        cachedSummaryEngine == summaryEngineKey
                    ) {
                        val summaryModelBadge = if (chosenSummaryProvider == SummaryProvider.OLLAMA) {
                            settings.selectedOllamaModel
                        } else {
                            settings.selectedModel
                        }
                        return Result.success(
                            AiArticleOutput(
                                cleanBody = current.aiSummarizedBody,
                                usedModel = summaryModelBadge
                            )
                        )
                    }
                }
                ReaderAiMode.TRANSLATE -> {
                    if (!current.aiTranslatedBody.isNullOrBlank() &&
                        current.aiTranslatedEngine == translateEngineKey
                    ) {
                        val transBadge = when (chosenTitleProvider) {
                            TitleTranslationProvider.GEMINI -> settings.selectedModel
                            TitleTranslationProvider.OLLAMA -> settings.selectedOllamaModel
                            else -> chosenTitleProvider.label
                        }
                        return Result.success(
                            AiArticleOutput(
                                cleanBody = current.aiTranslatedBody,
                                usedModel = transBadge
                            )
                        )
                    }
                }
            }
        }

        var fullSourceBody = current.rawContent
        if (fullSourceBody.length < 480 && current.link.startsWith("http")) {
            val page = rssParser.fetchFullArticlePage(current.link)
            val scraped = page.readableBody
            if (!scraped.isNullOrBlank() && scraped.length > fullSourceBody.length) {
                fullSourceBody = scraped
                dao.updateRawContent(articleId, fullSourceBody)
            }
            if (current.imageUrl.isNullOrBlank() && !page.metaImageUrl.isNullOrBlank()) {
                dao.backfillArticleImage(current.feedId, current.guid, page.metaImageUrl)
            }
        }

        if (mode == ReaderAiMode.TRANSLATE) {
            val transRes: Result<AiArticleOutput> = when (chosenTitleProvider) {
                TitleTranslationProvider.GEMINI -> {
                    geminiService.processArticle(
                        apiKey = settings.effectiveGeminiKey,
                        articleTitle = current.title,
                        articleBody = fullSourceBody.ifBlank { current.title },
                        mode = ReaderAiMode.TRANSLATE,
                        targetLanguage = settings.targetLanguage,
                        availableModels = settings.availableModels,
                        selectedModel = settings.selectedModel,
                        onModelSwitched = { newModel -> settingsRepository.setSelectedModel(newModel) },
                        customPrompt = activeTranslateCustomPrompt
                    )
                }
                TitleTranslationProvider.OLLAMA -> {
                    ollamaService.translateArticle(
                        apiKey = settings.effectiveOllamaKey,
                        articleTitle = current.title,
                        articleBody = fullSourceBody.ifBlank { current.title },
                        targetLanguage = settings.targetLanguage,
                        availableModels = settings.availableOllamaModels,
                        selectedModel = settings.selectedOllamaModel,
                        onModelSwitched = { newModel ->
                            settingsRepository.setSelectedOllamaModel(newModel, userInitiated = false)
                        },
                        customPrompt = activeTranslateCustomPrompt
                    )
                }
                else -> {
                    headlineTranslator.translateFullArticle(
                        articleBody = fullSourceBody.ifBlank { current.title },
                        targetLanguage = settings.targetLanguage,
                        provider = chosenTitleProvider,
                        deeplApiKey = settings.effectiveDeeplKey
                    ).map { translatedText ->
                        AiArticleOutput(
                            cleanBody = translatedText,
                            usedModel = chosenTitleProvider.label
                        )
                    }
                }
            }
            transRes.onSuccess { output ->
                dao.updateAiTranslation(
                    articleId = articleId,
                    translatedBody = output.cleanBody,
                    engineName = translateEngineKey,
                    lang = targetIso
                )
            }
            return transRes
        }

        if (chosenSummaryProvider == SummaryProvider.OLLAMA) {
            val ollamaRes = ollamaService.summarizeArticle(
                apiKey = settings.effectiveOllamaKey,
                articleTitle = current.title,
                articleBody = fullSourceBody,
                targetLanguage = settings.targetLanguage,
                availableModels = settings.availableOllamaModels,
                selectedModel = settings.selectedOllamaModel,
                onModelSwitched = { newModel ->
                    settingsRepository.setSelectedOllamaModel(newModel, userInitiated = false)
                },
                customPrompt = activeSummarizeCustomPrompt
            )
            ollamaRes.onSuccess { output ->
                dao.updateAiSummary(
                    articleId = articleId,
                    summarizedBody = output.cleanBody,
                    engineName = summaryEngineKey,
                    lang = targetIso
                )
            }
            return ollamaRes
        }

        val aiRes = geminiService.processArticle(
            apiKey = settings.effectiveGeminiKey,
            articleTitle = current.title,
            articleBody = fullSourceBody,
            mode = ReaderAiMode.SUMMARIZE,
            targetLanguage = settings.targetLanguage,
            availableModels = settings.availableModels,
            selectedModel = settings.selectedModel,
            onModelSwitched = { newModel -> settingsRepository.setSelectedModel(newModel) },
            customPrompt = activeSummarizeCustomPrompt
        )

        aiRes.onSuccess { output ->
            dao.updateAiSummary(
                articleId = articleId,
                summarizedBody = output.cleanBody,
                engineName = summaryEngineKey,
                lang = targetIso
            )
        }
        return aiRes
    }

    suspend fun draftTelegramSummary(
        articleId: Long,
        settings: AppSettings
    ): Result<String> {
        val current = dao.getArticleByIdOnce(articleId)
            ?: return Result.failure(IllegalArgumentException("Article not found"))

        var sourceText = current.rawContent
        if (sourceText.length < 450 && current.link.startsWith("http")) {
            val scraped = rssParser.fetchFullReadableArticle(current.link)
            if (!scraped.isNullOrBlank() && scraped.length > sourceText.length) {
                sourceText = scraped
                dao.updateRawContent(articleId, sourceText)
            }
        }

        return if (settings.summaryProvider == SummaryProvider.OLLAMA) {
            ollamaService.draftTelegramPost(
                apiKey = settings.effectiveOllamaKey,
                articleTitle = current.title,
                articleBody = sourceText,
                availableModels = settings.availableOllamaModels,
                selectedModel = settings.selectedOllamaModel,
                onModelSwitched = { newModel ->
                    settingsRepository.setSelectedOllamaModel(newModel, userInitiated = false)
                }
            )
        } else {
            geminiService.draftTelegramPost(
                apiKey = settings.effectiveGeminiKey,
                articleTitle = current.title,
                articleBody = sourceText,
                availableModels = settings.availableModels,
                selectedModel = settings.selectedModel,
                onModelSwitched = { newModel -> settingsRepository.setSelectedModel(newModel) }
            )
        }
    }

    suspend fun verifyGeminiApiKey(apiKey: String): Result<Int> = geminiService.verifyApiKey(apiKey)

    suspend fun verifyOllamaApiKey(apiKey: String): Result<Int> = ollamaService.verifyApiKey(apiKey)

    suspend fun verifyDeeplApiKey(apiKey: String): Result<String> = headlineTranslator.verifyDeeplApiKey(apiKey)

    suspend fun verifyTelegramBot(token: String): Result<String> = telegramService.verifyBot(token)

    suspend fun publishToTelegram(
        settings: AppSettings,
        summaryText: String,
        imageUrl: String?
    ): Result<Unit> = telegramService.publishPost(
        botToken = settings.effectiveTelegramToken,
        channelId = settings.effectiveTelegramChannel,
        summaryText = summaryText,
        imageUrl = imageUrl
    )
}
