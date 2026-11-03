package com.example.data.backup

import com.example.data.local.FeedFilterEntity
import com.example.data.local.ArticleEntity
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.model.BackupSelection
import com.example.data.model.ImportCandidateFeed
import com.example.data.model.PendingImportPreview
import com.example.data.opml.OpmlManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupListItem(
    val name: String,
    val showInAllLists: Boolean = true,
    val syncEnabled: Boolean = true,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class BackupFeedItem(
    val url: String,
    val siteUrl: String = "",
    val title: String,
    val iconUrl: String = "",
    val groupName: String = "",
    val autoTranslate: Boolean = false,
    val defaultReaderMode: String = "NONE",
    val showInAllFeeds: Boolean = true,
    val showInAllLists: Boolean = true,
    val syncEnabled: Boolean = true,
    val isPinned: Boolean = false,
    val notifyEnabled: Boolean = false,
    val sortOrder: Int = 0
)

@Serializable
data class BackupFeedFilterItem(
    val feedUrl: String,
    val pattern: String,
    val isRegex: Boolean = true
)

@Serializable
data class BackupArticleItem(
    val feedUrl: String,
    val feedTitle: String,
    val feedSiteUrl: String = "",
    val feedIconUrl: String = "",
    val feedGroupName: String = "",
    val guid: String,
    val link: String,
    val title: String,
    val rawContent: String,
    val imageUrl: String? = null,
    val publishedAt: Long,
    val isRead: Boolean = false,
    val isSaved: Boolean = false,
    val translatedTitle: String? = null,
    val translatedTitleLang: String? = null,
    val showTranslatedTitle: Boolean = false,
    val aiTranslatedBody: String? = null,
    val aiTranslatedEngine: String? = null,
    val aiSummarizedBody: String? = null,
    val aiSummarizedEngine: String? = null,
    val aiProcessedLang: String? = null
)

@Serializable
data class CrimsonFeedBackupBundle(
    val format: String = "crimsonfeed-backup",
    val version: Int = 1,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val lists: List<BackupListItem> = emptyList(),
    val feeds: List<BackupFeedItem> = emptyList(),
    val filters: List<BackupFeedFilterItem> = emptyList(),
    val savedPosts: List<BackupArticleItem> = emptyList(),
    val readPosts: List<BackupArticleItem> = emptyList()
)

data class BackupImportSummary(
    val listsCount: Int,
    val feedsCount: Int,
    val savedCount: Int,
    val readCount: Int,
    val filtersCount: Int = 0
) {
    fun toUserMessage(): String {
        val parts = buildList {
            if (listsCount > 0) add("$listsCount lists")
            if (feedsCount > 0) add("$feedsCount feeds")
            if (filtersCount > 0) add("$filtersCount filters")
            if (savedCount > 0) add("$savedCount saved posts")
            if (readCount > 0) add("$readCount read posts")
        }
        return if (parts.isEmpty()) {
            "Backup imported (no new matching items)"
        } else {
            "Restored " + parts.joinToString(", ")
        }
    }
}

object BackupManager {

    private val backupJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
        explicitNulls = false
    }

    fun exportToJson(
        selection: BackupSelection,
        feeds: List<FeedEntity>,
        customLists: List<CustomListEntity>,
        savedArticles: List<ArticleEntity>,
        readArticles: List<ArticleEntity>,
        filters: List<FeedFilterEntity> = emptyList()
    ): String {
        val feedsById = feeds.associateBy { it.id }

        val exportedLists = if (selection.includeLists) {
            customLists.map { list ->
                BackupListItem(
                    name = list.name,
                    showInAllLists = list.showInAllLists,
                    syncEnabled = list.syncEnabled,
                    isPinned = list.isPinned,
                    sortOrder = list.sortOrder
                )
            }
        } else {
            emptyList()
        }

        val exportedFeeds = if (selection.includeFeeds) {
            feeds.map { feed ->
                BackupFeedItem(
                    url = feed.url,
                    siteUrl = feed.siteUrl,
                    title = feed.title,
                    iconUrl = feed.iconUrl,
                    groupName = if (selection.includeLists) feed.groupName else feed.groupName,
                    autoTranslate = feed.autoTranslate,
                    defaultReaderMode = feed.defaultReaderMode,
                    showInAllFeeds = feed.showInAllFeeds,
                    showInAllLists = feed.showInAllLists,
                    syncEnabled = feed.syncEnabled,
                    isPinned = feed.isPinned,
                    notifyEnabled = feed.notifyEnabled,
                    sortOrder = feed.sortOrder
                )
            }
        } else {
            emptyList()
        }

        val exportedFilters = if (selection.includeFilters) {
            filters.mapNotNull { filter ->
                val parentUrl = feedsById[filter.feedId]?.url ?: return@mapNotNull null
                BackupFeedFilterItem(
                    feedUrl = parentUrl,
                    pattern = filter.pattern,
                    isRegex = filter.isRegex
                )
            }
        } else {
            emptyList()
        }

        fun ArticleEntity.toBackupItem(): BackupArticleItem? {
            val parent = feedsById[feedId] ?: return null
            return BackupArticleItem(
                feedUrl = parent.url,
                feedTitle = parent.title,
                feedSiteUrl = parent.siteUrl,
                feedIconUrl = parent.iconUrl,
                feedGroupName = parent.groupName,
                guid = guid,
                link = link,
                title = title,
                rawContent = rawContent,
                imageUrl = imageUrl,
                publishedAt = publishedAt,
                isRead = isRead,
                isSaved = isSaved,
                translatedTitle = translatedTitle,
                translatedTitleLang = translatedTitleLang,
                showTranslatedTitle = showTranslatedTitle,
                aiTranslatedBody = aiTranslatedBody,
                aiTranslatedEngine = aiTranslatedEngine,
                aiSummarizedBody = aiSummarizedBody,
                aiSummarizedEngine = aiSummarizedEngine,
                aiProcessedLang = aiProcessedLang
            )
        }

        val exportedSaved = if (selection.includeSaved) {
            savedArticles.mapNotNull { it.toBackupItem() }
        } else {
            emptyList()
        }

        val exportedRead = if (selection.includeRead) {
            readArticles.mapNotNull { it.toBackupItem() }
        } else {
            emptyList()
        }

        val bundle = CrimsonFeedBackupBundle(
            lists = exportedLists,
            feeds = exportedFeeds,
            filters = exportedFilters,
            savedPosts = exportedSaved,
            readPosts = exportedRead
        )
        return backupJson.encodeToString(CrimsonFeedBackupBundle.serializer(), bundle)
    }

    fun looksLikeJsonBackup(rawContent: String): Boolean {
        val trimmed = rawContent.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return trimmed.startsWith("{")
    }

    fun isAppFormatBackup(rawContent: String): Boolean {
        if (!looksLikeJsonBackup(rawContent)) return false
        val parsed = runCatching { parseFromJson(rawContent) }.getOrNull() ?: return false
        val trimmed = rawContent.trim()
        return parsed.format.equals("crimsonfeed-backup", ignoreCase = true) &&
            (trimmed.contains("\"crimsonfeed-backup\"") ||
                parsed.lists.isNotEmpty() ||
                parsed.feeds.isNotEmpty() ||
                parsed.filters.isNotEmpty() ||
                parsed.savedPosts.isNotEmpty() ||
                parsed.readPosts.isNotEmpty())
    }

    fun parseFromJson(rawJson: String): CrimsonFeedBackupBundle {
        val clean = rawJson.trim().removePrefix("\uFEFF")
        return backupJson.decodeFromString(CrimsonFeedBackupBundle.serializer(), clean)
    }

    fun inspectImportPreview(
        rawContent: String,
        normalizeUrl: (String) -> String = { it.trim() }
    ): PendingImportPreview {
        if (isAppFormatBackup(rawContent)) {
            val bundle = parseFromJson(rawContent)
            val candidateFeeds = bundle.feeds
                .filter { it.url.isNotBlank() }
                .map { item ->
                    val norm = normalizeUrl(item.url)
                    ImportCandidateFeed(
                        url = item.url.trim(),
                        normalizedUrl = norm,
                        title = item.title.trim().ifBlank { item.url.trim() },
                        groupName = item.groupName.trim(),
                        iconUrl = item.iconUrl.trim()
                    )
                }
                .distinctBy { it.normalizedUrl }

            return PendingImportPreview(
                rawContent = rawContent,
                isAppFormatBackup = true,
                feeds = candidateFeeds,
                listsCount = bundle.lists.size,
                savedCount = bundle.savedPosts.size,
                readCount = bundle.readPosts.size,
                filtersCount = bundle.filters.size
            )
        }

        val parsedOpml = OpmlManager.parseOpmlXml(rawContent)
        if (parsedOpml.feeds.isEmpty() && parsedOpml.listNames.isEmpty()) {
            throw IllegalArgumentException("No valid backup or OPML data found in file")
        }

        val opmlCandidateFeeds = parsedOpml.feeds
            .filter { it.xmlUrl.isNotBlank() }
            .map { entry ->
                val norm = normalizeUrl(entry.xmlUrl)
                ImportCandidateFeed(
                    url = entry.xmlUrl.trim(),
                    normalizedUrl = norm,
                    title = entry.title.trim().ifBlank { entry.xmlUrl.trim() },
                    groupName = entry.listName.trim()
                )
            }
            .distinctBy { it.normalizedUrl }

        return PendingImportPreview(
            rawContent = rawContent,
            isAppFormatBackup = false,
            feeds = opmlCandidateFeeds,
            listsCount = parsedOpml.listNames.size,
            savedCount = 0,
            readCount = 0
        )
    }
}
