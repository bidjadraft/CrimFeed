package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.ReaderAiMode

@Entity(
    tableName = "custom_lists",
    indices = [Index(value = ["name"], unique = true)]
)
data class CustomListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val showInAllLists: Boolean = true,
    val syncEnabled: Boolean = true,
    val isPinned: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "feeds",
    indices = [Index(value = ["url"], unique = true)]
)
data class FeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val siteUrl: String = "",
    val title: String,
    val iconUrl: String = "",
    val groupName: String = "",
    val autoTranslate: Boolean = false,
    val defaultReaderMode: String = ReaderAiMode.NONE.name,
    val showInAllFeeds: Boolean = true,
    val showInAllLists: Boolean = true,
    val syncEnabled: Boolean = true,
    val isPinned: Boolean = false,
    val notifyEnabled: Boolean = false,
    val sortOrder: Int = 0,
    val lastSyncedPubAt: Long = 0L,
    val lastSyncedGuid: String = "",
    val httpEtag: String = "",
    val httpLastModified: String = "",
    val lastBodyDigest: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    val resolvedReaderMode: ReaderAiMode
        get() = runCatching { ReaderAiMode.valueOf(defaultReaderMode) }.getOrDefault(ReaderAiMode.NONE)
}

@Entity(
    tableName = "seen_articles",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feedId", "dedupeKey"], unique = true),
        Index(value = ["feedId"])
    ]
)
data class SeenArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val dedupeKey: String,
    val seenAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feedId", "guid"], unique = true),
        Index(value = ["feedId"]),
        Index(value = ["publishedAt"]),
        Index(value = ["isRead"]),
        Index(value = ["isSaved"])
    ]
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val link: String,
    val title: String,
    val rawContent: String,
    val imageUrl: String? = null,
    val publishedAt: Long = System.currentTimeMillis(),
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

data class ArticleWithFeed(
    @Embedded val article: ArticleEntity,
    @ColumnInfo(name = "feedTitle") val feedTitle: String,
    @ColumnInfo(name = "feedIconUrl") val feedIconUrl: String,
    @ColumnInfo(name = "feedGroupName") val feedGroupName: String,
    @ColumnInfo(name = "feedUrl") val feedUrl: String,
    @ColumnInfo(name = "feedAutoTranslate") val feedAutoTranslate: Boolean,
    @ColumnInfo(name = "feedDefaultReaderMode") val feedDefaultReaderMode: String = ReaderAiMode.NONE.name,
    @ColumnInfo(name = "feedShowInAllFeeds") val feedShowInAllFeeds: Boolean = true,
    @ColumnInfo(name = "feedShowInAllLists") val feedShowInAllLists: Boolean = true,
    @ColumnInfo(name = "feedNotifyEnabled") val feedNotifyEnabled: Boolean = false
)

@Entity(
    tableName = "feed_filters",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["feedId"])
    ]
)
data class FeedFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedId: Long,
    val pattern: String,
    val isRegex: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class FeedWithCount(
    @Embedded val feed: FeedEntity,
    @ColumnInfo(name = "unreadCount") val unreadCount: Int
)

data class ArticleGuidTimestamp(
    @ColumnInfo(name = "guid") val guid: String,
    @ColumnInfo(name = "publishedAt") val publishedAt: Long
)
