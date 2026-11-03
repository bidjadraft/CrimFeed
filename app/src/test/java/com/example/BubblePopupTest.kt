package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedWithCount
import com.example.data.model.FeedLayoutMode
import com.example.data.model.ReaderAiMode
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.CrimsonFeedTheme
import com.example.ui.viewmodel.FeedSyncProgress
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BubblePopupTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `clicking feed options 3 dots opens bubble popup smoothly without crash`() {
        val feed = FeedEntity(
            id = 42L,
            url = "https://example.com/rss",
            title = "Tech World",
            showInAllFeeds = true,
            syncEnabled = true,
            isPinned = false
        )
        val customList = CustomListEntity(
            id = 101L,
            name = "Science",
            showInAllLists = true,
            syncEnabled = true
        )

        val feedSyncToggled = AtomicBoolean(false)

        composeTestRule.setContent {
            CrimsonFeedTheme {
                HomeScreen(
                    feeds = listOf(FeedWithCount(feed, 0)),
                    customLists = listOf(customList),
                    selectedFeedId = null,
                    selectedListName = null,
                    articles = emptyList(),
                    layoutMode = FeedLayoutMode.LIST,
                    initialScrollIndex = 0,
                    initialScrollOffset = 0,
                    isIconSyncing = false,
                    isPullSyncing = false,
                    syncingFeedIds = emptySet(),
                    syncProgress = FeedSyncProgress(),
                    isAddingFeed = false,
                    isImportingOpml = false,
                    translatingIds = emptySet(),
                    canSummarize = false,
                    showTranslateButton = true,
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
                    onToggleFeedSyncEnabled = { feedSyncToggled.set(true) },
                    onToggleFeedPinned = {},
                    onToggleFeedShowInAllFeeds = {},
                    onToggleFeedShowInAllLists = {},
                    onReorderFeeds = {},
                    onToggleFeedAutoTranslate = {},
                    onSetFeedReaderMode = { _, _ -> },
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

        // Tap the 3-dots button on the feed pill
        composeTestRule.onNodeWithTag("btn_feed_options_Tech World")
            .assertIsDisplayed()
            .performClick()

        // Verify the bubble popup opened and close button is visible
        composeTestRule.onNodeWithTag("btn_close_feed_bubble")
            .assertIsDisplayed()
            .performClick()

        // Tap the 3-dots button on the custom list pill
        composeTestRule.onNodeWithTag("btn_list_options_Science")
            .assertIsDisplayed()
            .performClick()

        // Verify the list bubble popup opened and close button is visible
        composeTestRule.onNodeWithTag("btn_close_list_bubble")
            .assertIsDisplayed()
            .performClick()
    }
}
