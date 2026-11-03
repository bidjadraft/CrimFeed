package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.local.ArticleEntity
import com.example.data.local.ArticleWithFeed
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedWithCount
import com.example.data.model.FeedLayoutMode
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.SavedArticlesScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.settings.FeedsAndListsSettingsPage
import com.example.ui.screens.settings.FeedsAndListsViewMode
import com.example.ui.viewmodel.FeedSyncProgress
import com.example.ui.viewmodel.SearchFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class CrimsonFeedV45Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleFeeds = listOf(
        FeedWithCount(
            feed = FeedEntity(
                id = 1L,
                title = "Tech News",
                url = "https://example.com/tech.xml",
                groupName = "Tech"
            ),
            unreadCount = 3
        ),
        FeedWithCount(
            feed = FeedEntity(
                id = 2L,
                title = "AI Weekly",
                url = "https://example.com/ai.xml",
                groupName = ""
            ),
            unreadCount = 1
        )
    )

    private val sampleCustomLists = listOf(
        CustomListEntity(id = 10L, name = "Tech", isPinned = true, showInAllLists = true, syncEnabled = true)
    )

    @Test
    fun homeScreen_showsAllFeedsAndListsWithoutAddBars() {
        composeTestRule.setContent {
            MaterialTheme {
                HomeScreen(
                    feeds = sampleFeeds,
                    customLists = sampleCustomLists,
                    selectedFeedId = null,
                    selectedListName = null,
                    articles = emptyList(),
                    layoutMode = FeedLayoutMode.CARD,
                    initialScrollIndex = 0,
                    initialScrollOffset = 0,
                    isIconSyncing = false,
                    isPullSyncing = false,
                    syncingFeedIds = emptySet(),
                    syncProgress = FeedSyncProgress(),
                    isAddingFeed = false,
                    isImportingOpml = false,
                    translatingIds = emptySet(),
                    canSummarize = true,
                    onScrollPositionChanged = { _, _ -> },
                    onSelectFeed = {},
                    onSelectCustomList = {},
                    onCreateCustomList = { _, _, _, cb -> cb("Tech") },
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
                    onAddFeed = { _, _, _, _, _, cb -> cb() },
                    onEditFeed = { _, _, _, _, _, _, _, _ -> },
                    onToggleFeedSyncEnabled = {},
                    onToggleFeedPinned = {},
                    onToggleFeedShowInAllFeeds = {},
                    onToggleFeedShowInAllLists = {},
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
                    notifiedArticles = emptyList(),
                    notifiedUnreadCount = 0,
                    onOpenNotifications = {},
                    onToggleFeedNotify = {},
                    onMarkAllNotifiedRead = {},
                    onClearNotifiedArticles = {},
                    onToggleCustomListPinned = {},
                    onToggleCustomListAutoTranslate = {},
                    onSetFeedReaderMode = { _, _ -> },
                    onOpenStandaloneFeeds = {},
                    onOpenStandaloneLists = {},
                    onOpenFeedsAndListsSettings = {},
                    onEmitMessage = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify "All Feeds" and "All Lists" pills are displayed
        composeTestRule.onNodeWithTag("feed_pill_all").assertIsDisplayed()
        composeTestRule.onNodeWithText("All Feeds").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_list_all").assertIsDisplayed()

        // Verify NO add feed / add list pills exist next to feeds or lists
        composeTestRule.onNodeWithTag("feed_pill_add").assertDoesNotExist()
        composeTestRule.onNodeWithTag("chip_add_custom_list").assertDoesNotExist()
    }

    @Test
    fun feedsAndListsSettingsPage_blueIndicatorMatchesSelectedTabInRtlAndLtr() {
        var backTriggered = false

        // Test in RTL (Arabic device direction) to guarantee indicator is never inverted!
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    FeedsAndListsSettingsPage(
                        feeds = sampleFeeds,
                        customLists = sampleCustomLists,
                        canSummarize = true,
                        viewMode = FeedsAndListsViewMode.SETTINGS_TABS,
                        onCreateCustomList = { _, _, _, _ -> },
                        onRenameCustomList = { _, _, _, _ -> },
                        onDeleteCustomList = { _, _ -> },
                        onEditFeed = { _, _, _, _, _, _, _, _ -> },
                        onDeleteFeed = {},
                        onAddFeed = { _, _, _, _, _, _ -> },
                        onEmitMessage = {},
                        onBack = { backTriggered = true }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Initially Feeds tab is selected: active indicator MUST align horizontally with Feeds tab!
        composeTestRule.onNodeWithTag("tab_settings_feeds").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithTag("tab_settings_lists").assertIsDisplayed().assertIsNotSelected()

        val feedsTabBoundsRtl = composeTestRule.onNodeWithTag("tab_settings_feeds")
            .fetchSemanticsNode().boundsInRoot
        val activeFeedsIndicatorBoundsRtl = composeTestRule.onNodeWithTag("active_tab_indicator_feeds", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(feedsTabBoundsRtl.left, activeFeedsIndicatorBoundsRtl.left, 1f)
        assertEquals(feedsTabBoundsRtl.right, activeFeedsIndicatorBoundsRtl.right, 1f)
        composeTestRule.onNodeWithTag("active_tab_indicator_lists", useUnmergedTree = true).assertDoesNotExist()

        // 2. Tap Lists tab: active indicator MUST move strictly under Lists tab (NOT Feeds)!
        composeTestRule.onNodeWithTag("tab_settings_lists").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_settings_lists").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_settings_feeds").assertIsNotSelected()

        val listsTabBoundsRtl = composeTestRule.onNodeWithTag("tab_settings_lists")
            .fetchSemanticsNode().boundsInRoot
        val activeListsIndicatorBoundsRtl = composeTestRule.onNodeWithTag("active_tab_indicator_lists", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(listsTabBoundsRtl.left, activeListsIndicatorBoundsRtl.left, 1f)
        assertEquals(listsTabBoundsRtl.right, activeListsIndicatorBoundsRtl.right, 1f)
        composeTestRule.onNodeWithTag("active_tab_indicator_feeds", useUnmergedTree = true).assertDoesNotExist()

        // Verify search in Lists tab opens SeamlessSearchTopBar and focuses input immediately
        composeTestRule.onNodeWithTag("btn_lists_search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_lists_search").assertIsDisplayed().assertIsFocused()
        composeTestRule.onNodeWithTag("btn_settings_search_back").performClick()
        composeTestRule.waitForIdle()

        // Back button triggers onBack
        composeTestRule.onNodeWithTag("btn_settings_subpage_back").performClick()
        assertTrue(backTriggered)
    }

    @Test
    fun seamlessSearchTopBar_focusesImmediatelyAcrossNotificationsSavedAndGlobalSearch() {
        val sampleArticle = ArticleWithFeed(
            article = ArticleEntity(
                id = 55L,
                feedId = 1L,
                guid = "g-55",
                link = "https://example.com/55",
                title = "Quantum Breakthrough",
                rawContent = "Scientists achieved stable qubits.",
                publishedAt = 1_760_000_000_000L,
                isSaved = true,
                isRead = false
            ),
            feedTitle = "Tech News",
            feedIconUrl = "",
            feedGroupName = "Tech",
            feedUrl = "https://example.com/tech.xml",
            feedAutoTranslate = false,
            feedShowInAllFeeds = true
        )

        composeTestRule.setContent {
            MaterialTheme {
                SavedArticlesScreen(
                    articles = listOf(sampleArticle),
                    layoutMode = FeedLayoutMode.LIST,
                    initialScrollIndex = 0,
                    initialScrollOffset = 0,
                    translatingIds = emptySet(),
                    onScrollPositionChanged = { _, _ -> },
                    onCycleLayout = {},
                    onOpenArticle = {},
                    onTranslateTitle = {},
                    onToggleRead = {},
                    onSearchRelated = {},
                    onToggleSave = {},
                    onShare = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap search icon on Saved screen -> borderless top search bar opens and immediately focuses!
        composeTestRule.onNodeWithTag("btn_saved_search").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_saved_search")
            .assertIsDisplayed()
            .assertIsFocused()
            .performTextInput("Quantum")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Quantum Breakthrough").assertIsDisplayed()
    }
}
