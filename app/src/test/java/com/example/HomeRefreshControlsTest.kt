package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.local.ArticleWithFeed
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedWithCount
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.FeedLayoutMode
import com.example.data.preferences.SettingsRepository
import com.example.data.remote.SmartRssParser
import com.example.data.repository.RssRepository
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.CrimsonFeedTheme
import com.example.ui.viewmodel.FeedSyncProgress
import com.example.ui.viewmodel.filterAndSortHomeArticles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeRefreshControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `refresh controls bottom sync progress bar all vs feed read filtering pin and long press actions work`() {
        val iconSyncCalls = AtomicInteger(0)
        val pullSyncCalls = AtomicInteger(0)
        val singleFeedRefreshedIds = CopyOnWriteArrayList<Long>()
        val customListRefreshedIds = CopyOnWriteArrayList<Long>()
        val feedSyncToggledIds = CopyOnWriteArrayList<Long>()
        val listSyncToggledIds = CopyOnWriteArrayList<Long>()
        val pinnedToggledIds = CopyOnWriteArrayList<Long>()
        val allFeedsVisibilityToggledIds = CopyOnWriteArrayList<Long>()
        val feedListsVisibilityToggledIds = CopyOnWriteArrayList<Long>()
        val allListsVisibilityToggledIds = CopyOnWriteArrayList<Long>()
        val reorderedFeedOrders = CopyOnWriteArrayList<List<Long>>()
        val reorderedListOrders = CopyOnWriteArrayList<List<Long>>()

        var isIconSyncingState by mutableStateOf(false)
        var isPullSyncingState by mutableStateOf(false)
        var syncProgressState by mutableStateOf(FeedSyncProgress())
        var openedFeedsAndListsSettingsWithAllFeeds by mutableStateOf<Boolean?>(null)
        var activeLayout by mutableStateOf(FeedLayoutMode.LIST)
        var selectedFeedIdState by mutableStateOf<Long?>(null)
        var selectedListNameState by mutableStateOf<String?>(null)

        val customLists = listOf(
            CustomListEntity(id = 10L, name = "Tech", showInAllLists = true, syncEnabled = true, sortOrder = 0),
            CustomListEntity(id = 20L, name = "World", showInAllLists = true, syncEnabled = true, sortOrder = 1)
        )

        val feedOne = FeedEntity(
            id = 1L,
            url = "https://example.com/rss-1.xml",
            siteUrl = "https://example.com",
            title = "Example Tech",
            groupName = "Tech",
            showInAllFeeds = true,
            syncEnabled = true,
            isPinned = true,
            sortOrder = 0
        )
        val feedTwo = FeedEntity(
            id = 2L,
            url = "https://world.example.com/rss-2.xml",
            siteUrl = "https://world.example.com",
            title = "World Wire",
            groupName = "World",
            showInAllFeeds = true,
            syncEnabled = true,
            isPinned = true,
            sortOrder = 1
        )
        val extraFeeds = (3L..8L).map { fid ->
            FeedWithCount(
                feed = FeedEntity(
                    id = fid,
                    url = "https://extra-$fid.example.com/rss.xml",
                    siteUrl = "https://extra-$fid.example.com",
                    title = "Extra Channel Number $fid",
                    groupName = "World",
                    showInAllFeeds = true,
                    syncEnabled = true,
                    isPinned = false,
                    sortOrder = fid.toInt()
                ),
                unreadCount = if (fid == 3L) 4 else 0
            )
        }
        val feeds = listOf(
            FeedWithCount(feed = feedOne, unreadCount = 2),
            FeedWithCount(feed = feedTwo, unreadCount = 3)
        ) + extraFeeds

        val unreadArticle = ArticleWithFeed(
            article = ArticleEntity(
                id = 101L,
                feedId = 1L,
                guid = "post-101",
                link = "https://example.com/post-101",
                title = "Unread Top Headline",
                rawContent = "Unread body content",
                publishedAt = 1_760_500_000_000L,
                isRead = false
            ),
            feedTitle = "Example Tech",
            feedIconUrl = "",
            feedGroupName = "Tech",
            feedUrl = "https://example.com/rss-1.xml",
            feedAutoTranslate = false,
            feedShowInAllFeeds = true
        )
        val readArticle = ArticleWithFeed(
            article = ArticleEntity(
                id = 102L,
                feedId = 1L,
                guid = "post-102",
                link = "https://example.com/post-102",
                title = "Already Read Faded Headline",
                rawContent = "Read body content",
                publishedAt = 1_760_600_000_000L,
                isRead = true
            ),
            feedTitle = "Example Tech",
            feedIconUrl = "",
            feedGroupName = "Tech",
            feedUrl = "https://example.com/rss-1.xml",
            feedAutoTranslate = false,
            feedShowInAllFeeds = true
        )
        val allArticles = listOf(readArticle, unreadArticle)

        composeTestRule.setContent {
            CrimsonFeedTheme(themeMode = AppThemeMode.DARK) {
                val filteredArticles = filterAndSortHomeArticles(
                    all = allArticles,
                    customLists = customLists,
                    selectedFeedId = selectedFeedIdState,
                    selectedListName = selectedListNameState
                )
                HomeScreen(
                    feeds = feeds,
                    customLists = customLists,
                    selectedFeedId = selectedFeedIdState,
                    selectedListName = selectedListNameState,
                    articles = filteredArticles,
                    layoutMode = activeLayout,
                    initialScrollIndex = 0,
                    initialScrollOffset = 0,
                    isIconSyncing = isIconSyncingState,
                    isPullSyncing = isPullSyncingState,
                    syncingFeedIds = emptySet(),
                    syncProgress = syncProgressState,
                    isAddingFeed = false,
                    isImportingOpml = false,
                    translatingIds = emptySet(),
                    onScrollPositionChanged = { _, _ -> },
                    onSelectFeed = { selectedFeedIdState = it },
                    onSelectCustomList = { selectedListNameState = it },
                    onCreateCustomList = { _, _, _, _ -> },
                    onRenameCustomList = { _, _, _, _ -> },
                    onRefreshCustomList = { customListRefreshedIds.add(it.id) },
                    onToggleCustomListSyncEnabled = { listSyncToggledIds.add(it.id) },
                    onToggleCustomListShowInAllLists = { allListsVisibilityToggledIds.add(it.id) },
                    onReorderCustomLists = { reorderedListOrders.add(it) },
                    onDeleteCustomList = { _, _ -> },
                    onImportOpmlClick = {},
                    onCycleLayout = { activeLayout = FeedLayoutMode.CARD },
                    onOpenSearch = {},
                    onSyncFromIcon = {
                        iconSyncCalls.incrementAndGet()
                        isIconSyncingState = true
                    },
                    onSyncFromPull = {
                        pullSyncCalls.incrementAndGet()
                        isPullSyncingState = true
                    },
                    onRefreshSingleFeed = { targetFeed ->
                        singleFeedRefreshedIds.add(targetFeed.id)
                    },
                    onAddFeed = { _, _, _, _, _, _ -> },
                    onEditFeed = { _, _, _, _, _, _, _, _ -> },
                    onToggleFeedSyncEnabled = { feedSyncToggledIds.add(it.id) },
                    onToggleFeedPinned = { pinnedToggledIds.add(it.id) },
                    onToggleFeedShowInAllFeeds = { allFeedsVisibilityToggledIds.add(it.id) },
                    onToggleFeedShowInAllLists = { feedListsVisibilityToggledIds.add(it.id) },
                    onReorderFeeds = { reorderedFeedOrders.add(it) },
                    onToggleFeedAutoTranslate = {},
                    onMarkFeedAllRead = {},
                    onMarkAllReadGlobal = {},
                    onDeleteFeed = {},
                    onOpenArticle = {},
                    onTranslateTitle = {},
                    onToggleRead = {},
                    onToggleSave = {},
                    onShare = {},
                    onOpenFeedsAndListsSettings = { openedFeedsAndListsSettingsWithAllFeeds = it },
                    onEmitMessage = {}
                )
            }
        }

        // 0. Custom Lists bar shows exact post counts on "All Lists" (2 + 3 + 4 = 9) and each list (Tech = 2, World = 7)
        composeTestRule.onNodeWithTag("chip_list_count_all", useUnmergedTree = true).assertTextEquals("9")
        composeTestRule.onNodeWithTag("chip_list_count_Tech", useUnmergedTree = true).assertTextEquals("2")
        composeTestRule.onNodeWithTag("chip_list_count_World", useUnmergedTree = true).assertTextEquals("7")

        // 1. In "All" (selectedFeedId == null), read articles MUST be hidden while unread articles show
        composeTestRule.onNodeWithTag("article_list_101").assertIsDisplayed()
        composeTestRule.onNodeWithTag("article_list_102").assertDoesNotExist()

        // 2. Selecting Feed 1 shows unread articles at top AND read articles dimmed below them
        composeTestRule.onNodeWithTag("feed_pill_1").performClick()
        composeTestRule.waitForIdle()
        val unreadBounds = composeTestRule.onNodeWithTag("article_list_101").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val readBounds = composeTestRule.onNodeWithTag("article_list_102").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Unread posts must appear above read posts inside individual feeds (${unreadBounds.top} < ${readBounds.top})",
            unreadBounds.top < readBounds.top
        )

        // Return to "All" and verify read article disappears again
        composeTestRule.onNodeWithTag("feed_pill_all").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("article_list_102").assertDoesNotExist()

        // 3. Startup/pull sync + bottom progress bar (8/10 -> 10/10) shows counter
        isPullSyncingState = true
        syncProgressState = FeedSyncProgress(isVisible = true, checkedCount = 8, totalCount = 10)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_sync_progress_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sync_progress_counter", useUnmergedTree = true).assertTextEquals("8/10")

        syncProgressState = FeedSyncProgress(isVisible = true, checkedCount = 10, totalCount = 10)
        isPullSyncingState = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sync_progress_counter", useUnmergedTree = true).assertTextEquals("10/10")

        syncProgressState = FeedSyncProgress(isVisible = false, checkedCount = 0, totalCount = 0)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_sync_progress_bar").assertDoesNotExist()

        // 4. Mark all read icon is in top bar in place of refresh icon
        composeTestRule.onNodeWithTag("btn_mark_all_read_top").assertIsDisplayed()

        // 5. Pull-to-refresh invokes onSyncFromPull
        composeTestRule.onNodeWithTag("home_pull_to_refresh")
            .performTouchInput { swipeDown(startY = top + 40f, endY = bottom - 40f, durationMillis = 300L) }
        composeTestRule.waitForIdle()
        assertEquals(1, pullSyncCalls.get())
        isPullSyncingState = false
        composeTestRule.waitForIdle()

        // 6. Long-pressing a Feed pill offers Edit, Refresh Feed, Delete (and NO pin/unpin or disable rows)
        composeTestRule.onNodeWithTag("feed_pill_1").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sheet_toggle_pin_feed").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sheet_toggle_feed_sync").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sheet_toggle_show_in_all_feeds").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sheet_toggle_feed_show_in_lists").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sheet_refresh_single_feed").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(1L), singleFeedRefreshedIds.toList())

        // 7. Long-pressing a Custom List pill offers Refresh List, Pin/Unpin, Rename, Delete (and NO disable/visibility rows)
        composeTestRule.onNodeWithTag("chip_list_Tech").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sheet_refresh_custom_list").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(10L), customListRefreshedIds.toList())

        composeTestRule.onNodeWithTag("sheet_toggle_list_sync").assertDoesNotExist()
        composeTestRule.onNodeWithTag("sheet_toggle_show_in_all_lists").assertDoesNotExist()

        // 8. Long-pressing "All Lists" has been disabled per user request (no sheet or navigation)
        composeTestRule.onNodeWithTag("chip_list_all").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sheet_all_lists_container").assertDoesNotExist()

        // 9. Long-pressing "All Feeds" has been disabled per user request (no sheet or navigation)
        composeTestRule.onNodeWithTag("feed_pill_all").performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sheet_all_feeds_container").assertDoesNotExist()
    }

    @Test
    fun `floating new posts pill appears when scrolled down and scrolls back to newest posts on click`() {
        val feed = FeedEntity(
            id = 1L,
            url = "https://example.com/rss.xml",
            siteUrl = "https://example.com",
            title = "Breaking Wire"
        )
        val initialPosts = (1..18).map { idx ->
            ArticleWithFeed(
                article = ArticleEntity(
                    id = idx.toLong(),
                    feedId = 1L,
                    guid = "post-$idx",
                    link = "https://example.com/post-$idx",
                    title = "Existing Story Number $idx",
                    rawContent = "Story content $idx",
                    publishedAt = 1_760_500_000_000L - (idx * 60_000L),
                    isRead = false
                ),
                feedTitle = "Breaking Wire",
                feedIconUrl = "",
                feedGroupName = "",
                feedUrl = "https://example.com/rss.xml",
                feedAutoTranslate = false,
                feedShowInAllFeeds = true
            )
        }

        var articlesState by mutableStateOf(initialPosts)

        composeTestRule.setContent {
            CrimsonFeedTheme(themeMode = AppThemeMode.DARK) {
                HomeScreen(
                    feeds = listOf(FeedWithCount(feed = feed, unreadCount = articlesState.size)),
                    customLists = emptyList(),
                    selectedFeedId = null,
                    selectedListName = null,
                    articles = articlesState,
                    layoutMode = FeedLayoutMode.CARD,
                    initialScrollIndex = 6,
                    initialScrollOffset = 0,
                    isIconSyncing = false,
                    isPullSyncing = false,
                    syncingFeedIds = emptySet(),
                    syncProgress = FeedSyncProgress(),
                    isAddingFeed = false,
                    isImportingOpml = false,
                    translatingIds = emptySet(),
                    onScrollPositionChanged = { _, _ -> },
                    onSelectFeed = {},
                    onSelectCustomList = {},
                    onCreateCustomList = { _, _, _, _ -> },
                    onRenameCustomList = { _, _, _, _ -> },
                    onRefreshCustomList = {},
                    onToggleCustomListSyncEnabled = {},
                    onToggleCustomListShowInAllLists = {},
                    onReorderCustomLists = {},
                    onDeleteCustomList = { _, _ -> },
                    onImportOpmlClick = {},
                    onCycleLayout = {},
                    onOpenSearch = {},
                    onSyncFromIcon = {},
                    onSyncFromPull = {},
                    onRefreshSingleFeed = {},
                    onAddFeed = { _, _, _, _, _, _ -> },
                    onEditFeed = { _, _, _, _, _, _, _, _ -> },
                    onToggleFeedSyncEnabled = {},
                    onToggleFeedPinned = {},
                    onToggleFeedShowInAllFeeds = {},
                    onReorderFeeds = {},
                    onToggleFeedAutoTranslate = {},
                    onMarkFeedAllRead = {},
                    onMarkAllReadGlobal = {},
                    onDeleteFeed = {},
                    onOpenArticle = {},
                    onTranslateTitle = {},
                    onToggleRead = {},
                    onToggleSave = {},
                    onShare = {},
                    onEmitMessage = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Before new posts arrive, pill must NOT exist
        composeTestRule.onNodeWithTag("pill_new_posts").assertDoesNotExist()

        // Prepend 2 brand-new unread posts at the top while user is scrolled down at index 6+
        val brandNewPosts = listOf(901L, 902L).map { newId ->
            ArticleWithFeed(
                article = ArticleEntity(
                    id = newId,
                    feedId = 1L,
                    guid = "fresh-$newId",
                    link = "https://example.com/fresh-$newId",
                    title = "Fresh Breaking Alert $newId",
                    rawContent = "Breaking body $newId",
                    publishedAt = 1_760_900_000_000L + newId,
                    isRead = false
                ),
                feedTitle = "Breaking Wire",
                feedIconUrl = "",
                feedGroupName = "",
                feedUrl = "https://example.com/rss.xml",
                feedAutoTranslate = false,
                feedShowInAllFeeds = true
            )
        }
        articlesState = brandNewPosts + initialPosts
        composeTestRule.waitForIdle()

        // Floating pill appears with count and scrolls to top on click
        composeTestRule.onNodeWithTag("pill_new_posts")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        // Topmost new article is now visible and the pill disappears
        composeTestRule.onNodeWithTag("article_card_901").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pill_new_posts").assertDoesNotExist()
    }

    @Test
    fun `filterAndSortHomeArticles enforces independent Show in Feeds and Show in Lists flags and removes read from All`() {
        val lists = listOf(
            CustomListEntity(id = 1L, name = "EnabledList", showInAllLists = true),
            CustomListEntity(id = 2L, name = "MutedList", showInAllLists = false)
        )
        val postNormal = ArticleWithFeed(
            article = ArticleEntity(id = 1L, feedId = 10L, guid = "g1", link = "l1", title = "Normal", rawContent = "", publishedAt = 300L, isRead = false),
            feedTitle = "F1", feedIconUrl = "", feedGroupName = "EnabledList", feedUrl = "u1", feedAutoTranslate = false, feedShowInAllFeeds = true, feedShowInAllLists = true
        )
        val postFeedsOffListsOn = ArticleWithFeed(
            article = ArticleEntity(id = 2L, feedId = 20L, guid = "g2", link = "l2", title = "HiddenInFeedsVisibleInLists", rawContent = "", publishedAt = 250L, isRead = false),
            feedTitle = "F2", feedIconUrl = "", feedGroupName = "EnabledList", feedUrl = "u2", feedAutoTranslate = false, feedShowInAllFeeds = false, feedShowInAllLists = true
        )
        val postFeedsOnListsOff = ArticleWithFeed(
            article = ArticleEntity(id = 5L, feedId = 50L, guid = "g5", link = "l5", title = "VisibleInFeedsHiddenInLists", rawContent = "", publishedAt = 220L, isRead = false),
            feedTitle = "F5", feedIconUrl = "", feedGroupName = "EnabledList", feedUrl = "u5", feedAutoTranslate = false, feedShowInAllFeeds = true, feedShowInAllLists = false
        )
        val postDisabledList = ArticleWithFeed(
            article = ArticleEntity(id = 3L, feedId = 30L, guid = "g3", link = "l3", title = "MutedListPost", rawContent = "", publishedAt = 200L, isRead = false),
            feedTitle = "F3", feedIconUrl = "", feedGroupName = "MutedList", feedUrl = "u3", feedAutoTranslate = false, feedShowInAllFeeds = true, feedShowInAllLists = true
        )
        val postReadInNormalFeed = ArticleWithFeed(
            article = ArticleEntity(id = 4L, feedId = 10L, guid = "g4", link = "l4", title = "ReadPost", rawContent = "", publishedAt = 400L, isRead = true),
            feedTitle = "F1", feedIconUrl = "", feedGroupName = "EnabledList", feedUrl = "u1", feedAutoTranslate = false, feedShowInAllFeeds = true, feedShowInAllLists = true
        )
        val all = listOf(postReadInNormalFeed, postNormal, postFeedsOffListsOn, postFeedsOnListsOff, postDisabledList)

        // 1. Global All Feeds: excludes read, excludes feedShowInAllFeeds == false (id=2), INCLUDES feedShowInAllFeeds == true even if feedShowInAllLists == false (id=5), excludes disabled list (id=3)
        val inGlobalAll = filterAndSortHomeArticles(all, lists, selectedFeedId = null, selectedListName = null)
        assertEquals(listOf(1L, 5L), inGlobalAll.map { it.article.id })

        // 2. Selecting EnabledList directly: includes feedShowInAllLists == true (id=1, id=2) and EXCLUDES feedShowInAllLists == false (id=5)
        val inEnabledList = filterAndSortHomeArticles(all, lists, selectedFeedId = null, selectedListName = "EnabledList")
        assertEquals(listOf(1L, 2L), inEnabledList.map { it.article.id })

        // 3. Selecting MutedList directly: shows unread posts from feeds with feedShowInAllLists == true in MutedList
        val inMutedList = filterAndSortHomeArticles(all, lists, selectedFeedId = null, selectedListName = "MutedList")
        assertEquals(listOf(3L), inMutedList.map { it.article.id })

        // 4. Selecting DisabledFeed (feedId = 20L) directly: shows its posts!
        val inDisabledFeed = filterAndSortHomeArticles(all, lists, selectedFeedId = 20L, selectedListName = null)
        assertEquals(listOf(2L), inDisabledFeed.map { it.article.id })

        // 5. Selecting Feed 10 directly: shows unread first (id=1), then read below (id=4)
        val inFeed10 = filterAndSortHomeArticles(all, lists, selectedFeedId = 10L, selectedListName = null)
        assertEquals(listOf(1L, 4L), inFeed10.map { it.article.id })
    }

    @Test
    fun `repository dynamic unread unpinned feed ordering and per-feed per-list sync disable with manual refresh work end to end`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val requestedHosts = CopyOnWriteArrayList<String>()
        val pubDateByHost = mapOf(
            "one.example.com" to "Wed, 28 Oct 2026 08:00:00 GMT",
            "two.example.com" to "Wed, 28 Oct 2026 11:00:00 GMT",
            "three.example.com" to "Wed, 28 Oct 2026 09:00:00 GMT",
            "four.example.com" to "Wed, 28 Oct 2026 12:00:00 GMT"
        )

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host
                requestedHosts.add(host)
                val pubDate = pubDateByHost[host] ?: "Wed, 28 Oct 2026 10:00:00 GMT"
                val xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <rss version="2.0">
                        <channel>
                            <title>Feed $host</title>
                            <link>https://$host</link>
                            <item>
                                <guid>$host-1</guid>
                                <title>Headline from $host</title>
                                <link>https://$host/p1</link>
                                <description>Summary</description>
                                <pubDate>$pubDate</pubDate>
                            </item>
                        </channel>
                    </rss>
                """.trimIndent()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(xml.toResponseBody("application/rss+xml".toMediaType()))
                    .build()
            }
            .build()

        try {
            val dao = db.rssDao()
            val repo = RssRepository(
                dao = dao,
                settingsRepository = SettingsRepository(context),
                rssParser = SmartRssParser(httpClient = mockClient)
            )

            // Custom Lists default to showInAllLists = true, syncEnabled = true
            repo.createCustomList("Alpha").getOrThrow()
            repo.createCustomList("Beta").getOrThrow()
            repo.createCustomList("Gamma").getOrThrow()
            val initialLists = repo.customListsFlow.first()
            val listA = initialLists.first { it.name == "Alpha" }
            val listB = initialLists.first { it.name == "Beta" }
            val listC = initialLists.first { it.name == "Gamma" }
            assertTrue(listA.showInAllLists && listA.syncEnabled && listB.syncEnabled && listC.syncEnabled)

            repo.reorderCustomLists(listOf(listC.id, listA.id, listB.id))
            assertEquals(listOf("Gamma", "Alpha", "Beta"), repo.customListsFlow.first().map { it.name })

            assertFalse(repo.toggleCustomListShowInAllLists(listA))

            // Insert 4 feeds:
            // - Feed 3 is pinned (stays first even with 0 unread)
            // - Feed 1 (unpinned, Alpha), Feed 2 (unpinned, Beta), Feed 4 (unpinned, no list)
            val id1 = dao.insertFeed(FeedEntity(url = "https://one.example.com/rss.xml", title = "One", groupName = "Alpha", sortOrder = 10))
            val id2 = dao.insertFeed(FeedEntity(url = "https://two.example.com/rss.xml", title = "Two", groupName = "Beta", sortOrder = 20))
            val id3 = dao.insertFeed(FeedEntity(url = "https://three.example.com/rss.xml", title = "Three", sortOrder = 30))
            val id4 = dao.insertFeed(FeedEntity(url = "https://four.example.com/rss.xml", title = "Four", sortOrder = 40))

            // Pin feed 3 -> jumps to very start (right next to All)
            assertTrue(repo.toggleFeedPinned(dao.getFeedByIdOnce(id3)!!))
            assertEquals(listOf(id3, id1, id2, id4), repo.feedsWithCountsFlow.first().map { it.feed.id })

            // Disable auto-refresh on Feed 4 individually AND disable auto-refresh on Custom List "Beta" (Feed 2)
            assertFalse(repo.toggleFeedSyncEnabled(dao.getFeedByIdOnce(id4)!!))
            assertFalse(repo.toggleCustomListSyncEnabled(listB))

            // Global syncAllFeeds MUST skip Feed 4 (feed sync disabled) AND Feed 2 (list "Beta" sync disabled),
            // syncing ONLY Feed 3 and Feed 1 (0/2 -> 2/2)!
            val progressSnapshots = CopyOnWriteArrayList<Pair<Int, Int>>()
            val addedInGlobalSync = repo.syncAllFeeds(
                settings = AppSettings(),
                onProgress = { checked, total, _ -> progressSnapshots.add(checked to total) }
            ).getOrThrow()

            assertEquals(2, addedInGlobalSync)
            assertEquals(0 to 2, progressSnapshots.first())
            assertEquals(2 to 2, progressSnapshots.last())
            assertFalse("Feed 4 must not be contacted during auto/global sync", "four.example.com" in requestedHosts)
            assertFalse("Feed 2 in sync-disabled list Beta must not be contacted during auto/global sync", "two.example.com" in requestedHosts)

            // Check dynamic unpinned feed order right now:
            // Pinned: [id3]
            // Unpinned with unread > 0: [id1 (unread = 1)]
            // Unpinned empty (unread == 0): [id2, id4]
            assertEquals(listOf(id3, id1, id2, id4), repo.feedsWithCountsFlow.first().map { it.feed.id })

            // Now manually refresh Custom List "Beta" -> MUST fetch Feed 2 (published 11:00 > Feed 1's 08:00)!
            val addedFromManualList = repo.syncCustomListFeeds("Beta", AppSettings()).getOrThrow()
            assertEquals(1, addedFromManualList)

            // Unpinned Feed 2 now has a newer unread post (11:00) than Feed 1 (08:00), so Feed 2 automatically
            // moves right next to Pinned Feed 3, ahead of Feed 1, while empty Feed 4 stays pushed to the end!
            assertEquals(listOf(id3, id2, id1, id4), repo.feedsWithCountsFlow.first().map { it.feed.id })

            // Manually refresh single sync-disabled Feed 4 -> MUST fetch Feed 4 (published 12:00, newest of all)!
            val addedFromManualFeed = repo.syncSingleFeed(dao.getFeedByIdOnce(id4)!!, AppSettings()).getOrThrow()
            assertEquals(1, addedFromManualFeed)

            // Feed 4 now has unread > 0 with newest timestamp (12:00), so it jumps immediately next to Pinned Feed 3!
            assertEquals(listOf(id3, id4, id2, id1), repo.feedsWithCountsFlow.first().map { it.feed.id })

            // Mark all posts in Feed 4 as read -> Feed 4 is now empty (unread == 0) so it automatically
            // slides all the way to the far end!
            repo.markFeedAllRead(id4)
            assertEquals(listOf(id3, id2, id1, id4), repo.feedsWithCountsFlow.first().map { it.feed.id })
        } finally {
            db.close()
        }
    }
}
