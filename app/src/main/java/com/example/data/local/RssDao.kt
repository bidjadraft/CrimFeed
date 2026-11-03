package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {

    // --- Custom Lists ---

    @Query("SELECT * FROM custom_lists ORDER BY isPinned DESC, sortOrder ASC, createdAt ASC, id ASC")
    fun observeCustomLists(): Flow<List<CustomListEntity>>

    @Query("SELECT * FROM custom_lists ORDER BY isPinned DESC, sortOrder ASC, createdAt ASC, id ASC")
    suspend fun getAllCustomListsOnce(): List<CustomListEntity>

    @Query("SELECT MAX(sortOrder) FROM custom_lists")
    suspend fun getMaxCustomListSortOrder(): Int?

    @Query("SELECT * FROM custom_lists WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getCustomListByNameOnce(name: String): CustomListEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomList(list: CustomListEntity): Long

    @Query("UPDATE custom_lists SET name = :newName WHERE id = :listId")
    suspend fun renameCustomListById(listId: Long, newName: String)

    @Query("UPDATE custom_lists SET showInAllLists = :showInAllLists WHERE id = :listId")
    suspend fun setCustomListShowInAllLists(listId: Long, showInAllLists: Boolean)

    @Query("UPDATE custom_lists SET syncEnabled = :syncEnabled WHERE id = :listId")
    suspend fun setCustomListSyncEnabled(listId: Long, syncEnabled: Boolean)

    @Query("UPDATE custom_lists SET isPinned = :isPinned WHERE id = :listId")
    suspend fun setCustomListPinned(listId: Long, isPinned: Boolean)

    @Query("UPDATE custom_lists SET sortOrder = :sortOrder WHERE id = :listId")
    suspend fun updateCustomListSortOrder(listId: Long, sortOrder: Int)

    @Transaction
    suspend fun updateCustomListsOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, listId ->
            updateCustomListSortOrder(listId, (index + 1) * 10)
        }
    }

    @Query("UPDATE feeds SET groupName = :newName WHERE LOWER(groupName) = LOWER(:oldName)")
    suspend fun updateFeedsListName(oldName: String, newName: String)

    @Query("DELETE FROM custom_lists WHERE id = :listId")
    suspend fun deleteCustomListById(listId: Long)

    @Query("UPDATE feeds SET groupName = '' WHERE LOWER(groupName) = LOWER(:listName)")
    suspend fun clearFeedsListName(listName: String)

    @Query("DELETE FROM feeds WHERE LOWER(TRIM(groupName)) = LOWER(TRIM(:listName))")
    suspend fun deleteFeedsByListName(listName: String)

    // --- Feeds ---

    @Query(
        """
        SELECT f.*, 
               (SELECT COUNT(*) FROM articles a WHERE a.feedId = f.id AND a.isRead = 0) AS unreadCount
        FROM feeds f
        ORDER BY f.isPinned DESC,
                 CASE WHEN f.isPinned = 1 THEN f.sortOrder ELSE 0 END ASC,
                 CASE WHEN (SELECT COUNT(*) FROM articles a WHERE a.feedId = f.id AND a.isRead = 0) > 0 THEN 1 ELSE 0 END DESC,
                 (SELECT COALESCE(MAX(a2.publishedAt), 0) FROM articles a2 WHERE a2.feedId = f.id AND a2.isRead = 0) DESC,
                 f.sortOrder ASC,
                 f.title COLLATE NOCASE ASC,
                 f.id ASC
        """
    )
    fun observeFeedsWithUnreadCount(): Flow<List<FeedWithCount>>

    @Query("SELECT * FROM feeds ORDER BY isPinned DESC, sortOrder ASC, title COLLATE NOCASE ASC, id ASC")
    suspend fun getAllFeedsOnce(): List<FeedEntity>

    @Query("SELECT MAX(sortOrder) FROM feeds")
    suspend fun getMaxFeedSortOrder(): Int?

    @Query("SELECT MIN(sortOrder) FROM feeds WHERE isPinned = 1")
    suspend fun getMinPinnedFeedSortOrder(): Int?

    @Query("SELECT * FROM feeds WHERE id = :feedId LIMIT 1")
    suspend fun getFeedByIdOnce(feedId: Long): FeedEntity?

    @Query("SELECT * FROM feeds WHERE url = :url LIMIT 1")
    suspend fun getFeedByUrl(url: String): FeedEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeed(feed: FeedEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFeed(feed: FeedEntity)

    @Update
    suspend fun updateFeed(feed: FeedEntity)

    @Query("UPDATE feeds SET showInAllFeeds = :showInAllFeeds WHERE id = :feedId")
    suspend fun setFeedShowInAllFeeds(feedId: Long, showInAllFeeds: Boolean)

    @Query("UPDATE feeds SET showInAllLists = :showInAllLists WHERE id = :feedId")
    suspend fun setFeedShowInAllLists(feedId: Long, showInAllLists: Boolean)

    @Query("UPDATE feeds SET syncEnabled = :syncEnabled WHERE id = :feedId")
    suspend fun setFeedSyncEnabled(feedId: Long, syncEnabled: Boolean)

    @Query("UPDATE feeds SET notifyEnabled = :notifyEnabled WHERE id = :feedId")
    suspend fun setFeedNotifyEnabled(feedId: Long, notifyEnabled: Boolean)

    @Query("UPDATE feeds SET isPinned = :isPinned, sortOrder = :sortOrder WHERE id = :feedId")
    suspend fun setFeedPinnedAndSortOrder(feedId: Long, isPinned: Boolean, sortOrder: Int)

    @Query("UPDATE feeds SET sortOrder = :sortOrder WHERE id = :feedId")
    suspend fun updateFeedSortOrder(feedId: Long, sortOrder: Int)

    @Transaction
    suspend fun updateFeedsOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, feedId ->
            updateFeedSortOrder(feedId, (index + 1) * 10)
        }
    }

    @Query(
        """
        UPDATE feeds 
        SET lastSyncedPubAt = :lastSyncedPubAt, 
            lastSyncedGuid = :lastSyncedGuid,
            httpEtag = :httpEtag,
            httpLastModified = :httpLastModified,
            lastBodyDigest = :lastBodyDigest
        WHERE id = :feedId
        """
    )
    suspend fun updateFeedSyncWatermarkAndHttpState(
        feedId: Long,
        lastSyncedPubAt: Long,
        lastSyncedGuid: String,
        httpEtag: String,
        httpLastModified: String,
        lastBodyDigest: String
    )

    @Query(
        """
        UPDATE feeds 
        SET httpEtag = :httpEtag,
            httpLastModified = :httpLastModified,
            lastBodyDigest = :lastBodyDigest
        WHERE id = :feedId
        """
    )
    suspend fun updateFeedHttpSyncState(
        feedId: Long,
        httpEtag: String,
        httpLastModified: String,
        lastBodyDigest: String
    )

    @Query("DELETE FROM feeds WHERE id = :feedId")
    suspend fun deleteFeedById(feedId: Long)

    // --- Seen Articles (Permanent Tombstones so Read/Cleared Posts Never Reappear) ---

    @Query("SELECT dedupeKey FROM seen_articles WHERE feedId = :feedId")
    suspend fun getSeenKeysForFeed(feedId: Long): List<String>

    @Query("SELECT * FROM seen_articles WHERE feedId = :feedId")
    suspend fun getSeenArticlesForFeedOnce(feedId: Long): List<SeenArticleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeenArticles(entries: List<SeenArticleEntity>)

    @Query("SELECT * FROM seen_articles")
    suspend fun getAllSeenArticlesOnce(): List<SeenArticleEntity>

    // --- Articles ---

    @Query(
        """
        SELECT a.*, 
               f.title AS feedTitle, 
               f.iconUrl AS feedIconUrl, 
               f.groupName AS feedGroupName,
               f.url AS feedUrl,
               f.autoTranslate AS feedAutoTranslate,
               f.defaultReaderMode AS feedDefaultReaderMode,
               f.showInAllFeeds AS feedShowInAllFeeds,
               f.showInAllLists AS feedShowInAllLists,
               f.notifyEnabled AS feedNotifyEnabled
        FROM articles a
        INNER JOIN feeds f ON a.feedId = f.id
        ORDER BY a.publishedAt DESC, a.id DESC
        """
    )
    fun observeAllArticlesWithFeed(): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT a.*, 
               f.title AS feedTitle, 
               f.iconUrl AS feedIconUrl, 
               f.groupName AS feedGroupName,
               f.url AS feedUrl,
               f.autoTranslate AS feedAutoTranslate,
               f.defaultReaderMode AS feedDefaultReaderMode,
               f.showInAllFeeds AS feedShowInAllFeeds,
               f.showInAllLists AS feedShowInAllLists,
               f.notifyEnabled AS feedNotifyEnabled
        FROM articles a
        INNER JOIN feeds f ON a.feedId = f.id
        WHERE a.id = :articleId
        LIMIT 1
        """
    )
    fun observeArticleById(articleId: Long): Flow<ArticleWithFeed?>

    @Query("SELECT * FROM articles WHERE id = :articleId LIMIT 1")
    suspend fun getArticleByIdOnce(articleId: Long): ArticleEntity?

    @Query("SELECT * FROM articles WHERE feedId = :feedId ORDER BY publishedAt DESC")
    suspend fun getArticlesByFeedIdOnce(feedId: Long): List<ArticleEntity>

    @Query(
        """
        SELECT * FROM articles 
        WHERE feedId = :feedId 
          AND (translatedTitle IS NULL OR TRIM(translatedTitle) = '' OR translatedTitleLang != :lang)
        ORDER BY publishedAt DESC
        """
    )
    suspend fun getUntranslatedArticlesByFeedIdOnce(feedId: Long, lang: String): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE isRead = 1 AND isSaved = 0")
    suspend fun getReadUnsavedArticlesOnce(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE isSaved = 1 ORDER BY publishedAt DESC")
    suspend fun getSavedArticlesOnce(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE isRead = 1 ORDER BY publishedAt DESC")
    suspend fun getReadArticlesOnce(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE feedId = :feedId AND guid = :guid LIMIT 1")
    suspend fun getArticleByFeedAndGuidOnce(feedId: Long, guid: String): ArticleEntity?

    @Query("SELECT guid FROM articles WHERE feedId = :feedId")
    suspend fun getExistingGuidsForFeed(feedId: Long): List<String>

    @Query("SELECT guid, publishedAt FROM articles WHERE feedId = :feedId")
    suspend fun getExistingGuidTimestampsForFeed(feedId: Long): List<ArticleGuidTimestamp>

    @Query("SELECT COUNT(*) FROM articles WHERE feedId = :feedId")
    suspend fun getArticleCountForFeed(feedId: Long): Int

    @Query("SELECT guid FROM articles WHERE feedId = :feedId AND (imageUrl IS NULL OR TRIM(imageUrl) = '')")
    suspend fun getGuidsMissingImageForFeed(feedId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET imageUrl = :imageUrl WHERE feedId = :feedId AND guid = :guid AND (imageUrl IS NULL OR imageUrl = '')")
    suspend fun backfillArticleImage(feedId: Long, guid: String, imageUrl: String)

    @Query("UPDATE articles SET publishedAt = :publishedAt WHERE feedId = :feedId AND guid = :guid")
    suspend fun updateArticlePublishedAt(feedId: Long, guid: String, publishedAt: Long)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :articleId")
    suspend fun setArticleRead(articleId: Long, isRead: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE feedId = :feedId")
    suspend fun markAllFeedArticlesRead(feedId: Long)

    @Query("UPDATE articles SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllArticlesRead()

    @Query("DELETE FROM articles WHERE isRead = 1 AND isSaved = 0")
    suspend fun deleteReadUnsavedArticles()

    @Query("UPDATE articles SET isSaved = :isSaved WHERE id = :articleId")
    suspend fun setArticleSaved(articleId: Long, isSaved: Boolean)

    @Query(
        """
        UPDATE articles 
        SET translatedTitle = :translatedTitle, 
            translatedTitleLang = :lang, 
            showTranslatedTitle = :showTranslated
        WHERE id = :articleId
        """
    )
    suspend fun updateTitleTranslation(
        articleId: Long,
        translatedTitle: String,
        lang: String,
        showTranslated: Boolean
    )

    @Query("UPDATE articles SET showTranslatedTitle = :showTranslated WHERE id = :articleId")
    suspend fun toggleShowTranslatedTitle(articleId: Long, showTranslated: Boolean)

    @Query(
        """
        UPDATE articles 
        SET aiTranslatedBody = :translatedBody,
            aiTranslatedEngine = :engineName,
            aiProcessedLang = :lang
        WHERE id = :articleId
        """
    )
    suspend fun updateAiTranslation(
        articleId: Long,
        translatedBody: String,
        engineName: String,
        lang: String
    )

    @Query(
        """
        UPDATE articles 
        SET aiSummarizedBody = :summarizedBody,
            aiSummarizedEngine = :engineName,
            aiProcessedLang = :lang
        WHERE id = :articleId
        """
    )
    suspend fun updateAiSummary(
        articleId: Long,
        summarizedBody: String,
        engineName: String,
        lang: String
    )

    @Query("UPDATE articles SET rawContent = :fullContent WHERE id = :articleId")
    suspend fun updateRawContent(articleId: Long, fullContent: String)

    @Query(
        """
        UPDATE articles
        SET rawContent = :fullContent
        WHERE feedId = :feedId AND guid = :guid AND LENGTH(rawContent) < LENGTH(:fullContent)
        """
    )
    suspend fun backfillArticleRawContentByGuid(feedId: Long, guid: String, fullContent: String)

    @Query(
        """
        UPDATE articles
        SET rawContent = :fullContent
        WHERE id = :articleId AND LENGTH(rawContent) < LENGTH(:fullContent)
        """
    )
    suspend fun upgradeArticleRawContent(articleId: Long, fullContent: String)

    @Query(
        """
        SELECT * FROM articles
        WHERE isRead = 0
          AND LENGTH(rawContent) < 480
          AND (link LIKE 'http://%' OR link LIKE 'https://%')
        ORDER BY publishedAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getShortUnreadArticlesOnce(limit: Int = 40): List<ArticleEntity>

    // --- In-App Feed Notifications ---

    @Query(
        """
        SELECT a.*, 
               f.title AS feedTitle, 
               f.iconUrl AS feedIconUrl, 
               f.groupName AS feedGroupName,
               f.url AS feedUrl,
               f.autoTranslate AS feedAutoTranslate,
               f.defaultReaderMode AS feedDefaultReaderMode,
               f.showInAllFeeds AS feedShowInAllFeeds,
               f.showInAllLists AS feedShowInAllLists,
               f.notifyEnabled AS feedNotifyEnabled
        FROM articles a
        INNER JOIN feeds f ON a.feedId = f.id
        WHERE f.notifyEnabled = 1 AND a.isRead = 0
        ORDER BY a.publishedAt DESC
        """
    )
    fun observeNotifiedArticlesWithFeed(): Flow<List<ArticleWithFeed>>

    @Query(
        """
        SELECT COUNT(*) 
        FROM articles a
        INNER JOIN feeds f ON a.feedId = f.id
        WHERE f.notifyEnabled = 1 AND a.isRead = 0
        """
    )
    fun observeNotifiedUnreadCount(): Flow<Int>

    @Query(
        """
        UPDATE articles 
        SET isRead = 1 
        WHERE isRead = 0 AND feedId IN (SELECT id FROM feeds WHERE notifyEnabled = 1)
        """
    )
    suspend fun markAllNotifiedArticlesRead()

    // --- Feed Content & Regex Filters ---

    @Query("SELECT * FROM feed_filters WHERE feedId = :feedId ORDER BY id ASC")
    fun observeFiltersForFeed(feedId: Long): Flow<List<FeedFilterEntity>>

    @Query("SELECT * FROM feed_filters WHERE feedId = :feedId ORDER BY id ASC")
    suspend fun getFiltersForFeedOnce(feedId: Long): List<FeedFilterEntity>

    @Query("SELECT * FROM feed_filters ORDER BY id ASC")
    suspend fun getAllFiltersOnce(): List<FeedFilterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFilter(filter: FeedFilterEntity): Long

    @Query("DELETE FROM feed_filters WHERE id = :filterId")
    suspend fun deleteFilterById(filterId: Long)

    @Query("DELETE FROM feed_filters WHERE feedId = :feedId")
    suspend fun deleteFiltersForFeed(feedId: Long)
}
