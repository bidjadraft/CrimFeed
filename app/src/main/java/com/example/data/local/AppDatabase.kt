package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CustomListEntity::class,
        FeedEntity::class,
        SeenArticleEntity::class,
        ArticleEntity::class,
        FeedFilterEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rssDao(): RssDao

    companion object {
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN autoTranslate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE articles ADD COLUMN aiTranslatedEngine TEXT")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_lists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_lists_name` ON `custom_lists` (`name`)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `custom_lists` (`name`, `createdAt`)
                    SELECT DISTINCT TRIM(`groupName`), strftime('%s','now') * 1000
                    FROM `feeds`
                    WHERE TRIM(`groupName`) != ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `articles_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `feedId` INTEGER NOT NULL,
                        `guid` TEXT NOT NULL,
                        `link` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `rawContent` TEXT NOT NULL,
                        `imageUrl` TEXT,
                        `publishedAt` INTEGER NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        `isSaved` INTEGER NOT NULL,
                        `translatedTitle` TEXT,
                        `translatedTitleLang` TEXT,
                        `showTranslatedTitle` INTEGER NOT NULL,
                        `aiTranslatedBody` TEXT,
                        `aiTranslatedEngine` TEXT,
                        `aiSummarizedBody` TEXT,
                        `aiProcessedLang` TEXT,
                        FOREIGN KEY(`feedId`) REFERENCES `feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `articles_new` (
                        `id`, `feedId`, `guid`, `link`, `title`, `rawContent`, `imageUrl`,
                        `publishedAt`, `isRead`, `isSaved`, `translatedTitle`, `translatedTitleLang`,
                        `showTranslatedTitle`, `aiTranslatedBody`, `aiTranslatedEngine`,
                        `aiSummarizedBody`, `aiProcessedLang`
                    )
                    SELECT
                        `id`, `feedId`, `guid`, `link`, `title`, `rawContent`, `imageUrl`,
                        `publishedAt`, `isRead`, `isSaved`, `translatedTitle`, `translatedTitleLang`,
                        `showTranslatedTitle`, `aiTranslatedBody`, `aiTranslatedEngine`,
                        `aiSummarizedBody`, `aiProcessedLang`
                    FROM `articles`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `articles`")
                db.execSQL("ALTER TABLE `articles_new` RENAME TO `articles`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_articles_feedId_guid` ON `articles` (`feedId`, `guid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_feedId` ON `articles` (`feedId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_publishedAt` ON `articles` (`publishedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_isRead` ON `articles` (`isRead`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_isSaved` ON `articles` (`isSaved`)")
            }
        }

        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `lastSyncedPubAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `lastSyncedGuid` TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `seen_articles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `feedId` INTEGER NOT NULL,
                        `dedupeKey` TEXT NOT NULL,
                        `seenAt` INTEGER NOT NULL,
                        FOREIGN KEY(`feedId`) REFERENCES `feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_seen_articles_feedId_dedupeKey` ON `seen_articles` (`feedId`, `dedupeKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_seen_articles_feedId` ON `seen_articles` (`feedId`)")

                // Backfill seen_articles from all currently stored articles
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `seen_articles` (`feedId`, `dedupeKey`, `seenAt`)
                    SELECT `feedId`, 'guid:' || TRIM(`guid`), `publishedAt`
                    FROM `articles`
                    WHERE TRIM(`guid`) != ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `seen_articles` (`feedId`, `dedupeKey`, `seenAt`)
                    SELECT `feedId`, 'rawlink:' || TRIM(`link`), `publishedAt`
                    FROM `articles`
                    WHERE TRIM(`link`) != ''
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `seen_articles` (`feedId`, `dedupeKey`, `seenAt`)
                    SELECT `feedId`, 'title:' || LOWER(TRIM(`title`)), `publishedAt`
                    FROM `articles`
                    WHERE TRIM(`title`) != ''
                    """.trimIndent()
                )

                // Backfill watermark timestamp on feeds from existing articles
                db.execSQL(
                    """
                    UPDATE `feeds`
                    SET `lastSyncedPubAt` = COALESCE(
                        (SELECT MAX(`publishedAt`) FROM `articles` WHERE `articles`.`feedId` = `feeds`.`id`),
                        0
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `httpEtag` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `httpLastModified` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `lastBodyDigest` TEXT NOT NULL DEFAULT ''")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Clear cached HTTP ETag / Last-Modified / digest ONCE upon upgrading to v2.1 so every
                // subscribed feed re-parses cleanly (picking up any previously missed posts and repairing
                // publication timestamps while keeping `seen_articles` tombstones 100% intact).
                db.execSQL(
                    """
                    UPDATE `feeds`
                    SET `httpEtag` = '',
                        `httpLastModified` = '',
                        `lastBodyDigest` = ''
                    """.trimIndent()
                )
                // Repair any future-skewed `lastSyncedPubAt` watermark so no feed can ever be blocked
                db.execSQL(
                    """
                    UPDATE `feeds`
                    SET `lastSyncedPubAt` = COALESCE(
                        (
                            SELECT MAX(`publishedAt`)
                            FROM `articles`
                            WHERE `articles`.`feedId` = `feeds`.`id`
                              AND `publishedAt` <= (strftime('%s','now') * 1000 + 300000)
                        ),
                        0
                    )
                    WHERE `lastSyncedPubAt` > (strftime('%s','now') * 1000 + 300000)
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `articles` ADD COLUMN `aiSummarizedEngine` TEXT")
            }
        }

        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `custom_lists` ADD COLUMN `showInAllLists` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `custom_lists` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE `custom_lists`
                    SET `sortOrder` = (
                        SELECT COUNT(*)
                        FROM `custom_lists` AS c2
                        WHERE c2.`createdAt` < `custom_lists`.`createdAt`
                           OR (c2.`createdAt` = `custom_lists`.`createdAt` AND c2.`id` <= `custom_lists`.`id`)
                    ) * 10
                    """.trimIndent()
                )

                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `showInAllFeeds` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE `feeds`
                    SET `sortOrder` = (
                        SELECT COUNT(*)
                        FROM `feeds` AS f2
                        WHERE LOWER(f2.`title`) < LOWER(`feeds`.`title`)
                           OR (LOWER(f2.`title`) = LOWER(`feeds`.`title`) AND f2.`id` <= `feeds`.`id`)
                    ) * 10
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `custom_lists` ADD COLUMN `syncEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `syncEnabled` INTEGER NOT NULL DEFAULT 1")
            }
        }

        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `defaultReaderMode` TEXT NOT NULL DEFAULT 'TRANSLATE'")
            }
        }

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `showInAllLists` INTEGER NOT NULL DEFAULT 1")
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `feeds` ADD COLUMN `notifyEnabled` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `feed_filters` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `feedId` INTEGER NOT NULL,
                        `pattern` TEXT NOT NULL,
                        `isRegex` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`feedId`) REFERENCES `feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_filters_feedId` ON `feed_filters` (`feedId`)")
            }
        }

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `custom_lists` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crimson_feed_rss.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
