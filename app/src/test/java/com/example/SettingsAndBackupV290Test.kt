package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupManager
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.local.FeedWithCount
import com.example.data.local.SeenArticleEntity
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.BackupSelection
import com.example.data.model.KeyVerificationState
import com.example.data.model.ReaderAiMode
import com.example.data.model.SummaryProvider
import com.example.data.model.TargetLanguage
import com.example.data.model.TitleTranslationProvider
import com.example.data.preferences.SettingsRepository
import com.example.data.remote.GeminiService
import com.example.data.remote.OllamaService
import com.example.data.remote.SmartRssParser
import com.example.data.repository.RssRepository
import com.example.ui.components.AddFeedDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SettingsSubPage
import com.example.ui.screens.settings.SmartImportSelectionDialog
import com.example.ui.theme.CrimsonFeedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class SettingsAndBackupV290Test {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `safe deleteCustomList keeps feeds when unchecked and deletes feeds when checked`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.rssDao()
            val repo = RssRepository(dao = dao, settingsRepository = SettingsRepository(context))

            repo.createCustomList("Tech").getOrThrow()
            repo.createCustomList("World").getOrThrow()

            val f1 = dao.insertFeed(FeedEntity(url = "https://tech1.example.com/rss", title = "Tech One", groupName = "Tech"))
            val f2 = dao.insertFeed(FeedEntity(url = "https://tech2.example.com/rss", title = "Tech Two", groupName = "Tech"))
            val f3 = dao.insertFeed(FeedEntity(url = "https://world1.example.com/rss", title = "World One", groupName = "World"))

            val techList = repo.customListsFlow.first().first { it.name == "Tech" }
            val worldList = repo.customListsFlow.first().first { it.name == "World" }

            // 1. Delete "Tech" with deleteFeedsInside = false -> keeps Tech One & Tech Two (ungrouped)
            repo.deleteCustomList(techList, deleteFeedsInside = false)
            assertEquals(listOf("World"), repo.customListsFlow.first().map { it.name })
            val kept1 = dao.getFeedByIdOnce(f1)!!
            val kept2 = dao.getFeedByIdOnce(f2)!!
            assertEquals("", kept1.groupName)
            assertEquals("", kept2.groupName)

            // 2. Delete "World" with deleteFeedsInside = true -> deletes both list AND World One feed!
            repo.deleteCustomList(worldList, deleteFeedsInside = true)
            assertTrue(repo.customListsFlow.first().isEmpty())
            assertNull(dao.getFeedByIdOnce(f3))
            assertEquals(2, dao.getAllFeedsOnce().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `GeminiService and OllamaService verifyApiKey validate live models endpoints and headers`() = runBlocking {
        val lastAuthHeader = AtomicReference<String?>()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                lastAuthHeader.set(req.header("Authorization"))
                val (code, body) = when {
                    url.contains("generativelanguage.googleapis.com/v1beta/models?key=VALID_GEMINI") -> {
                        200 to """{"models":[{"name":"models/gemini-2.5-flash"},{"name":"models/gemini-2.5-pro"}]}"""
                    }
                    url.contains("generativelanguage.googleapis.com/v1beta/models?key=EMPTY_GEMINI") -> {
                        200 to """{"models":[]}"""
                    }
                    url.contains("generativelanguage.googleapis.com") -> {
                        400 to """{"error":{"message":"API key not valid. Please pass a valid API key."}}"""
                    }
                    url == "https://ollama.com/api/tags" && req.header("Authorization") == "Bearer VALID_OLLAMA" -> {
                        200 to """{"models":[{"name":"gemma4:31b"},{"name":"gpt-oss:120b"}]}"""
                    }
                    url == "https://ollama.com/api/tags" -> {
                        401 to """{"error":"unauthorized: invalid token"}"""
                    }
                    else -> 404 to "{}"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Mock")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val geminiService = GeminiService(httpClient = mockClient)
        assertEquals(2, geminiService.verifyApiKey("VALID_GEMINI").getOrThrow())
        assertTrue(geminiService.verifyApiKey("BAD_GEMINI").isFailure)
        assertTrue(geminiService.verifyApiKey("EMPTY_GEMINI").isFailure)

        val ollamaService = OllamaService(httpClient = mockClient)
        assertEquals(2, ollamaService.verifyApiKey("VALID_OLLAMA").getOrThrow())
        assertEquals("Bearer VALID_OLLAMA", lastAuthHeader.get())
        assertTrue(ollamaService.verifyApiKey("BAD_OLLAMA").isFailure)
    }

    @Test
    fun `selective Backup and Restore exports and imports exact chosen categories`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val sourceDao = sourceDb.rssDao()
            val sourceRepo = RssRepository(dao = sourceDao, settingsRepository = SettingsRepository(context))

            sourceRepo.createCustomList("Science").getOrThrow()
            val feedId = sourceDao.insertFeed(
                FeedEntity(
                    url = "https://science.example.com/feed.xml",
                    title = "Science Daily",
                    groupName = "Science",
                    isPinned = true,
                    defaultReaderMode = ReaderAiMode.SUMMARIZE.name
                )
            )
            sourceDao.insertArticles(
                listOf(
                    ArticleEntity(
                        feedId = feedId,
                        guid = "saved-1",
                        link = "https://science.example.com/1",
                        title = "Saved Discovery",
                        rawContent = "Saved body",
                        publishedAt = 1000L,
                        isSaved = true,
                        isRead = false
                    ),
                    ArticleEntity(
                        feedId = feedId,
                        guid = "read-1",
                        link = "https://science.example.com/2",
                        title = "Read Discovery",
                        rawContent = "Read body",
                        publishedAt = 900L,
                        isSaved = false,
                        isRead = true
                    )
                )
            )

            // Full export of all 4 categories
            val fullJson = sourceRepo.exportBackupJson(
                BackupSelection(includeFeeds = true, includeLists = true, includeSaved = true, includeRead = true)
            )

            val targetDao = targetDb.rssDao()
            val targetRepo = RssRepository(dao = targetDao, settingsRepository = SettingsRepository(context))

            // 1. Import ONLY Feeds + Saved Posts (Lists = false, Read = false)
            val summary1 = targetRepo.importBackupContent(
                rawContent = fullJson,
                selection = BackupSelection(
                    includeFeeds = true,
                    includeLists = false,
                    includeSaved = true,
                    includeRead = false
                ),
                settings = AppSettings(),
                syncAfterFeedImport = false
            ).getOrThrow()

            assertEquals(1, summary1.feedsCount)
            assertEquals(0, summary1.listsCount)
            assertEquals(1, summary1.savedCount)
            assertEquals(0, summary1.readCount)
            assertTrue(targetRepo.customListsFlow.first().isEmpty())
            val importedSaved = targetDao.getSavedArticlesOnce()
            assertEquals(1, importedSaved.size)
            assertEquals(0, targetDao.getReadArticlesOnce().size)
            // Verify per-feed defaultReaderMode (SUMMARIZE) survived backup/restore and resolves for articles
            assertEquals(
                ReaderAiMode.SUMMARIZE,
                targetRepo.getFeedReaderModeForArticle(importedSaved.first().id)
            )

            // 2. Now import Lists + Read Posts into targetDb
            val summary2 = targetRepo.importBackupContent(
                rawContent = fullJson,
                selection = BackupSelection(
                    includeFeeds = false,
                    includeLists = true,
                    includeSaved = false,
                    includeRead = true
                ),
                settings = AppSettings(),
                syncAfterFeedImport = false
            ).getOrThrow()

            assertEquals(1, summary2.listsCount)
            assertEquals(1, summary2.readCount)
            assertEquals(listOf("Science"), targetRepo.customListsFlow.first().map { it.name })
            assertEquals(1, targetDao.getReadArticlesOnce().size)
        } finally {
            sourceDb.close()
            targetDb.close()
        }
    }

    @Test
    fun `ReaderAiMode NONE returns feed article directly without AI or translation calls`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.rssDao()
            val repo = RssRepository(dao = dao, settingsRepository = SettingsRepository(context))

            val feedId = dao.insertFeed(
                FeedEntity(
                    url = "https://direct.example.com/rss",
                    title = "Direct Feed",
                    defaultReaderMode = ReaderAiMode.NONE.name
                )
            )
            val longBody = "Original feed article content paragraph. ".repeat(16).trim()
            dao.insertArticles(
                listOf(
                    ArticleEntity(
                        feedId = feedId,
                        guid = "direct-1",
                        link = "https://direct.example.com/post-1",
                        title = "Direct Post",
                        rawContent = longBody,
                        publishedAt = 2000L,
                        isSaved = true
                    )
                )
            )
            val savedArticle = dao.getSavedArticlesOnce().first()
            assertEquals(ReaderAiMode.NONE, repo.getFeedReaderModeForArticle(savedArticle.id))

            val output = repo.processArticleInReader(
                articleId = savedArticle.id,
                mode = ReaderAiMode.NONE,
                settings = AppSettings()
            ).getOrThrow()

            assertEquals(longBody, output.cleanBody)
            assertEquals("", output.usedModel)
        } finally {
            db.close()
        }
    }

    @Test
    fun `multi-page SettingsScreen hub navigates cleanly to subpages and enforces v292 UI rules`() {
        // Verify default translator is DeepL Web
        assertEquals(TitleTranslationProvider.DEEPL_WEB, AppSettings().titleProvider)

        var settingsState by mutableStateOf(
            AppSettings(
                themeMode = AppThemeMode.CRIMSON_NIGHT,
                summaryProvider = SummaryProvider.GEMINI
            )
        )
        var backupSelection by mutableStateOf(BackupSelection())
        var hasPendingImportBackup by mutableStateOf(false)
        var activeSubPage by mutableStateOf(SettingsSubPage.HUB)
        var resetSignal by mutableStateOf(0)
        var exportTriggeredCount = 0
        var importFilePickerCount = 0
        var confirmedPendingImportCount = 0
        val deleteListRecords = mutableListOf<Pair<String, Boolean>>()
        val editedFeedModes = mutableListOf<ReaderAiMode>()

        val sampleList = CustomListEntity(id = 7L, name = "AI News")
        val secondList = CustomListEntity(id = 8L, name = "Global Economics")
        val sampleFeeds = listOf(
            FeedWithCount(
                feed = FeedEntity(
                    id = 11L,
                    url = "https://ai.example.com/rss",
                    title = "AI Weekly",
                    groupName = "AI News",
                    defaultReaderMode = ReaderAiMode.TRANSLATE.name
                ),
                unreadCount = 3
            ),
            FeedWithCount(
                feed = FeedEntity(
                    id = 12L,
                    url = "https://ml.example.com/rss",
                    title = "ML Digest",
                    groupName = "AI News",
                    defaultReaderMode = ReaderAiMode.SUMMARIZE.name
                ),
                unreadCount = 1
            ),
            FeedWithCount(
                feed = FeedEntity(
                    id = 13L,
                    url = "https://solo.example.com/rss",
                    title = "Independent Blog",
                    groupName = "",
                    defaultReaderMode = ReaderAiMode.NONE.name
                ),
                unreadCount = 2
            )
        )

        composeTestRule.setContent {
            CrimsonFeedTheme(themeMode = settingsState.themeMode) {
                SettingsScreen(
                    settings = settingsState,
                    feeds = sampleFeeds,
                    customLists = listOf(sampleList, secondList),
                    backupSelection = backupSelection,
                    ollamaKeyStatus = KeyVerificationState.Valid("Valid Ollama token (7 models available)"),
                    geminiKeyStatus = KeyVerificationState.Invalid("Invalid Gemini API key"),
                    deeplKeyStatus = KeyVerificationState.Idle,
                    isRefreshingModels = false,
                    isRefreshingOllamaModels = false,
                    isImportingBackup = false,
                    hasPendingImportBackup = hasPendingImportBackup,
                    externalActivePage = activeSubPage,
                    onExternalActivePageChange = { activeSubPage = it },
                    resetToHubSignal = resetSignal,
                    onUpdateBackupSelection = { backupSelection = it },
                    onExportBackupClick = { exportTriggeredCount++ },
                    onImportBackupClick = { importFilePickerCount++ },
                    onConfirmPendingImport = { selection ->
                        backupSelection = selection
                        confirmedPendingImportCount++
                        hasPendingImportBackup = false
                    },
                    onCancelPendingImport = { hasPendingImportBackup = false },
                    onExportOpmlClick = {},
                    onCreateCustomList = { _, _, _, _ -> },
                    onRenameCustomList = { _, _, _, _ -> },
                    onDeleteCustomList = { list, deleteFeeds -> deleteListRecords.add(list.name to deleteFeeds) },
                    onEditFeed = { _, _, _, _, mode, _, _, _ -> editedFeedModes.add(mode) },
                    onDeleteFeed = {},
                    onSelectTheme = { settingsState = settingsState.copy(themeMode = it) },
                    onSelectLanguage = { settingsState = settingsState.copy(targetLanguage = it) },
                    onSelectTitleProvider = { settingsState = settingsState.copy(titleProvider = it) },
                    onSelectSummaryProvider = { settingsState = settingsState.copy(summaryProvider = it) },
                    onSelectModel = {},
                    onSelectOllamaModel = {},
                    onRefreshModels = {},
                    onRefreshOllamaModels = {},
                    onToggleCustomTranslatePrompt = { enabled ->
                        settingsState = if (enabled) {
                            settingsState.copy(useCustomTranslatePrompt = true)
                        } else {
                            settingsState.copy(
                                useCustomTranslatePrompt = false,
                                customTranslatePrompt = AppSettings.DEFAULT_TRANSLATE_PROMPT
                            )
                        }
                    },
                    onUpdateCustomTranslatePrompt = { prompt ->
                        settingsState = settingsState.copy(customTranslatePrompt = prompt)
                    },
                    onToggleCustomSummarizePrompt = { enabled ->
                        settingsState = if (enabled) {
                            settingsState.copy(useCustomSummarizePrompt = true)
                        } else {
                            settingsState.copy(
                                useCustomSummarizePrompt = false,
                                customSummarizePrompt = AppSettings.DEFAULT_SUMMARIZE_PROMPT
                            )
                        }
                    },
                    onUpdateCustomSummarizePrompt = { prompt ->
                        settingsState = settingsState.copy(customSummarizePrompt = prompt)
                    },
                    onVerifyOllamaKey = {},
                    onVerifyGeminiKey = {},
                    onVerifyDeeplKey = {},
                    onSaveOrReplaceSingleKey = { _, _ -> },
                    onDeleteSingleKey = {},
                    onToggleTelegramEnabled = {},
                    onSaveTelegramConfig = { _, _ -> },
                    onTestTelegramBot = {},
                    onEmitMessage = {}
                )
            }
        }

        // 1. Hub shows merged "Translate & Summarize" and does NOT show standalone "Summarize"
        composeTestRule.onNodeWithTag("settings_hub_appearance").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_feeds_lists").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_api_keys").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_translate").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Translate & Summarize").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hub_summarize").assertDoesNotExist()

        // 2. Appearance sub-page: clicking System expands Dark & Light; 6 dark themes & 6 light themes below
        composeTestRule.onNodeWithTag("settings_hub_appearance").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("dropdown_system_theme_toggle").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_swatch_CARBON").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dropdown_system_theme_toggle").performClick()
        composeTestRule.onNodeWithTag("theme_swatch_SYSTEM").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_swatch_CARBON").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_swatch_LIGHT_CARBON").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(AppThemeMode.LIGHT_CARBON, settingsState.themeMode)
        // Verify 6 Dark Themes & 6 Light Themes exist below
        listOf(
            AppThemeMode.CRIMSON_NIGHT,
            AppThemeMode.DARK,
            AppThemeMode.OCEAN_DARK,
            AppThemeMode.FOREST_DARK,
            AppThemeMode.AMETHYST_DARK,
            AppThemeMode.AMBER_DARK,
            AppThemeMode.LIGHT,
            AppThemeMode.NORDIC_LIGHT,
            AppThemeMode.SAKURA_LIGHT,
            AppThemeMode.SAGE_LIGHT,
            AppThemeMode.AMBER_LIGHT,
            AppThemeMode.LAVENDER_LIGHT
        ).forEach { mode ->
            composeTestRule.onNodeWithTag("theme_swatch_${mode.name}").performScrollTo().assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag("btn_settings_subpage_back").performClick()

        // 3. Feeds & Lists sub-page: Defaults to Feeds tab, shows feeds and add feed button
        composeTestRule.onNodeWithTag("settings_hub_feeds_lists").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("tab_settings_feeds").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_add_feed_in_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI Weekly").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("ML Digest").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Independent Blog").performScrollTo().assertIsDisplayed()

        // Switch to Lists tab: shows Create list button and custom list cards
        composeTestRule.onNodeWithTag("tab_settings_lists").performClick()
        composeTestRule.onNodeWithTag("btn_settings_create_list").assertIsDisplayed()

        // Open custom list "AI News" -> Edit feed -> verify 3 Article AI Modes (None, Translate, Summarize) + horizontal custom lists
        composeTestRule.onNodeWithTag("card_settings_list_7").performScrollTo().performClick()
        composeTestRule.onNodeWithText("AI Weekly").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_feed_more_11").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("menu_item_edit_feed_11").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("row_custom_lists_horizontal").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_edit_feed_mode_NONE").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("chip_edit_feed_mode_TRANSLATE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_edit_feed_mode_SUMMARIZE").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("btn_confirm_edit_feed").performScrollTo().performClick()
        assertEquals(listOf(ReaderAiMode.NONE), editedFeedModes)
        composeTestRule.onNodeWithTag("btn_detail_delete_list").performClick()
        composeTestRule.onNodeWithTag("checkbox_delete_list_feeds").assertIsOff()
        composeTestRule.onNodeWithTag("btn_confirm_delete_custom_list").performClick()
        assertEquals(listOf("AI News" to false), deleteListRecords)
        composeTestRule.onNodeWithTag("btn_settings_subpage_back").performClick()
        composeTestRule.onNodeWithTag("btn_settings_subpage_back").performClick()

        // 4. API Keys sub-page: Ollama, Gemini, DeepL + inline Eye & Portal buttons + bottom Settings re-tap returns directly to Hub!
        composeTestRule.onNodeWithTag("settings_hub_api_keys").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_toggle_visibility_ollama").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_portal_ollama").assertIsDisplayed()
        composeTestRule.onNodeWithTag("status_valid_ollama").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_toggle_visibility_gemini").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_portal_gemini").assertIsDisplayed()
        composeTestRule.onNodeWithTag("status_invalid_gemini").performScrollTo().assertIsDisplayed()
        // Simulate tapping Settings on the bottom navigation bar while inside API Keys -> returns to Settings Hub!
        activeSubPage = SettingsSubPage.HUB
        resetSignal++
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_hub_translate").performScrollTo().assertIsDisplayed()

        // 5. Translate & Summarize merged sub-page: Language + Translator + Custom Prompts + Summarize (Gemini & Ollama + models)
        composeTestRule.onNodeWithTag("settings_hub_translate").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dropdown_language_toggle").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("dropdown_translator_toggle").performScrollTo().assertIsDisplayed()
        // While DeepL Web is selected, AI Translate prompt box is NOT shown
        composeTestRule.onNodeWithTag("switch_custom_translate_prompt").assertDoesNotExist()
        composeTestRule.onNodeWithTag("translator_option_GOOGLE_WEB").assertDoesNotExist()
        composeTestRule.onNodeWithTag("dropdown_translator_toggle").performClick()
        TitleTranslationProvider.entries.forEach { provider ->
            composeTestRule.onNodeWithTag("translator_option_${provider.name}").performScrollTo().assertIsDisplayed()
        }
        if (settingsState.effectiveGeminiKey.isBlank()) {
            composeTestRule.onNodeWithTag("translator_option_GEMINI").assertIsNotEnabled()
        }
        if (settingsState.effectiveOllamaKey.isBlank()) {
            composeTestRule.onNodeWithTag("translator_option_OLLAMA").assertIsNotEnabled()
        }
        settingsState = settingsState.copy(
            geminiApiKeyOverride = "AIzaTestValidKey",
            ollamaApiKeyOverride = "ollamaTestValidToken"
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("translator_option_GEMINI").performScrollTo().assertIsEnabled()
        composeTestRule.onNodeWithTag("translator_option_OLLAMA").performScrollTo().assertIsEnabled().performClick()
        composeTestRule.waitForIdle()
        assertEquals(TitleTranslationProvider.OLLAMA, settingsState.titleProvider)

        // Now that Ollama (AI Translator) is selected, Translate prompt box appears:
        // OFF by default, non-editable showing default prompt; ON makes editable; OFF resets to default!
        composeTestRule.onNodeWithTag("switch_custom_translate_prompt").performScrollTo().assertIsDisplayed().assertIsOff()
        composeTestRule.onNodeWithTag("input_translate_prompt").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("switch_custom_translate_prompt").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_translate_prompt").assertIsEnabled().performTextReplacement("Translate strictly into formal {language}.")
        assertEquals("Translate strictly into formal {language}.", settingsState.customTranslatePrompt)
        composeTestRule.onNodeWithTag("switch_custom_translate_prompt").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_translate_prompt").assertIsNotEnabled()
        assertEquals(AppSettings.DEFAULT_TRANSLATE_PROMPT, settingsState.customTranslatePrompt)

        // Summarize section directly inside Translate & Summarize at the bottom + Summarize Custom Prompt
        composeTestRule.onNodeWithTag("chip_summary_provider_GEMINI").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_models_count").performScrollTo().assertIsDisplayed().assertTextEquals("4 models")
        composeTestRule.onNodeWithTag("dropdown_models_toggle").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("chip_summary_provider_OLLAMA").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("text_models_count").performScrollTo().assertIsDisplayed().assertTextEquals("7 models")
        composeTestRule.onNodeWithTag("dropdown_ollama_models_toggle").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_custom_summarize_prompt").performScrollTo().assertIsDisplayed().assertIsOff()
        composeTestRule.onNodeWithTag("input_summarize_prompt").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("switch_custom_summarize_prompt").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_summarize_prompt").assertIsEnabled().performTextReplacement("Summarize in 3 bullet points in {language}.")
        assertEquals("Summarize in 3 bullet points in {language}.", settingsState.customSummarizePrompt)
        composeTestRule.onNodeWithTag("switch_custom_summarize_prompt").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_summarize_prompt").assertIsNotEnabled()
        assertEquals(AppSettings.DEFAULT_SUMMARIZE_PROMPT, settingsState.customSummarizePrompt)
        composeTestRule.onNodeWithTag("btn_settings_subpage_back").performClick()
        composeTestRule.waitForIdle()

        // 6. Backup & Restore:
        //    - Tapping Import IMMEDIATELY triggers file picker WITHOUT showing modal first!
        //    - When an app-compatible backup file is selected (hasPendingImportBackup = true), modal opens AFTER selection with Feeds & Lists ON, Reads & Saved OFF.
        //    - Tapping Export opens modal directly as before with Feeds & Lists ON, Reads & Saved OFF.
        composeTestRule.onNodeWithTag("settings_hub_backup").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("checkbox_backup_feeds").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_export_opml_settings").assertDoesNotExist()
        composeTestRule.onNodeWithTag("btn_import_backup").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, importFilePickerCount)
        composeTestRule.onNodeWithTag("dialog_backup_options").assertDoesNotExist()

        // Simulate selecting an app-format backup file -> modal opens AFTER file choice with Feeds & Lists ON
        hasPendingImportBackup = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_backup_options").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkbox_backup_feeds").assertIsOn()
        composeTestRule.onNodeWithTag("checkbox_backup_lists").assertIsOn()
        composeTestRule.onNodeWithTag("checkbox_backup_read").assertIsOff()
        composeTestRule.onNodeWithTag("checkbox_backup_saved").assertIsOff()
        composeTestRule.onNodeWithTag("checkbox_backup_saved").performClick()
        composeTestRule.onNodeWithTag("btn_confirm_backup_dialog").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, confirmedPendingImportCount)
        assertTrue(backupSelection.includeFeeds && backupSelection.includeLists && backupSelection.includeSaved && !backupSelection.includeRead)

        // Clicking Export opens modal directly with fresh defaults (Feeds & Lists ON, Reads & Saved OFF)
        composeTestRule.onNodeWithTag("btn_export_backup").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("checkbox_backup_feeds").assertIsOn()
        composeTestRule.onNodeWithTag("checkbox_backup_lists").assertIsOn()
        composeTestRule.onNodeWithTag("checkbox_backup_read").assertIsOff()
        composeTestRule.onNodeWithTag("checkbox_backup_saved").assertIsOff()
        composeTestRule.onNodeWithTag("btn_confirm_backup_dialog").performClick()
        assertEquals(1, exportTriggeredCount)
        composeTestRule.onNodeWithTag("btn_settings_subpage_back").performClick()
        composeTestRule.waitForIdle()

        // 7. About: creator links & Version 2.95
        composeTestRule.onNodeWithTag("settings_hub_about").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("about_link_youtube").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_link_telegram").assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_link_website").assertIsDisplayed()
        composeTestRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `deleteFeedWithBackup and restoreDeletedFeed restore feed articles and seen tombstones for Undo`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.rssDao()
            val repo = RssRepository(dao = dao, settingsRepository = SettingsRepository(context))

            // Verify new Custom List options persist properly too
            repo.createCustomList(rawName = "QuietList", showInAllLists = false, syncEnabled = false).getOrThrow()
            val quietList = repo.customListsFlow.first().first { it.name == "QuietList" }
            assertFalse(quietList.showInAllLists)
            assertFalse(quietList.syncEnabled)

            val feedId = dao.insertFeed(
                FeedEntity(
                    url = "https://undo.example.com/rss",
                    title = "Undoable Tech",
                    groupName = "QuietList",
                    showInAllFeeds = false,
                    syncEnabled = false
                )
            )
            assertEquals(ReaderAiMode.NONE, dao.getFeedByIdOnce(feedId)!!.resolvedReaderMode)

            dao.insertArticles(
                listOf(
                    ArticleEntity(
                        feedId = feedId,
                        guid = "u-1",
                        link = "https://undo.example.com/1",
                        title = "Unread Undo Post",
                        rawContent = "Body 1",
                        publishedAt = 5000L,
                        isRead = false,
                        isSaved = true
                    ),
                    ArticleEntity(
                        feedId = feedId,
                        guid = "u-2",
                        link = "https://undo.example.com/2",
                        title = "Read Undo Post",
                        rawContent = "Body 2",
                        publishedAt = 4000L,
                        isRead = true,
                        isSaved = false
                    )
                )
            )
            dao.insertSeenArticles(
                listOf(SeenArticleEntity(feedId = feedId, dedupeKey = "guid:u-old", seenAt = 3000L))
            )

            // Delete feed with backup (as invoked by MainViewModel.deleteFeed from Home & Settings)
            val backup = repo.deleteFeedWithBackup(feedId)
            assertTrue(backup != null)
            assertNull(dao.getFeedByIdOnce(feedId))
            assertTrue(dao.getArticlesByFeedIdOnce(feedId).isEmpty())
            assertTrue(dao.getSeenKeysForFeed(feedId).isEmpty())

            // Tap Undo -> restores exact feed ID, properties, articles, and seen tombstones
            repo.restoreDeletedFeed(backup!!)
            val restoredFeed = dao.getFeedByIdOnce(feedId)!!
            assertEquals("Undoable Tech", restoredFeed.title)
            assertFalse(restoredFeed.showInAllFeeds)
            assertFalse(restoredFeed.syncEnabled)
            assertEquals(2, dao.getArticlesByFeedIdOnce(feedId).size)
            assertEquals(listOf("guid:u-old"), dao.getSeenKeysForFeed(feedId))
        } finally {
            db.close()
        }
    }

    @Test
    fun `AddFeedDialog and inline CustomListDialog enforce v294 separate ShowInFeeds and ShowInLists switches and Auto Manual sync`() {
        var canSummarizeState by mutableStateOf(false)
        var capturedFeedArgs: List<Any>? = null
        var capturedListArgs: Triple<String, Boolean, Boolean>? = null

        composeTestRule.setContent {
            CrimsonFeedTheme(themeMode = AppThemeMode.DARK) {
                AddFeedDialog(
                    customLists = emptyList(),
                    isAdding = false,
                    canSummarize = canSummarizeState,
                    onDismiss = {},
                    onAddFeed = { url, listName, autoTrans, mode, notifyEnabled ->
                        capturedFeedArgs = listOf(url, listName, autoTrans, mode, notifyEnabled)
                    },
                    onCreateCustomList = { name, showInAllLists, syncEnabled, onCreated ->
                        capturedListArgs = Triple(name, showInAllLists, syncEnabled)
                        onCreated(name)
                    }
                )
            }
        }

        // 1. Auto-Translate Feed switch MUST appear strictly ABOVE Article AI Mode
        val autoTransTop = composeTestRule.onNodeWithTag("switch_add_feed_auto_translate")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsOff()
            .fetchSemanticsNode()
            .boundsInRoot.top
        val modeNoneTop = composeTestRule.onNodeWithTag("chip_add_feed_mode_NONE")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsSelected()
            .fetchSemanticsNode()
            .boundsInRoot.top
        assertTrue(
            "Auto-Translate Feed ($autoTransTop) must be positioned above Article AI Mode ($modeNoneTop)",
            autoTransTop < modeNoneTop
        )

        // 2. Article AI Mode defaults to None; Summarize is disabled without Ollama/Gemini key
        composeTestRule.onNodeWithTag("chip_add_feed_mode_TRANSLATE").assertIsNotSelected()
        composeTestRule.onNodeWithTag("chip_add_feed_mode_SUMMARIZE").assertIsNotEnabled()

        // Enabling API key state immediately unlocks Summarize chip
        canSummarizeState = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chip_add_feed_mode_SUMMARIZE").assertIsEnabled()


        // 4. Open inline New Custom List dialog -> verify Show in All Lists ON & Automatic by default, then toggle to Manual + hidden
        composeTestRule.onNodeWithTag("chip_create_custom_list_inline").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("switch_custom_list_show_in_all").assertIsOn().performClick()
        composeTestRule.onNodeWithTag("chip_custom_list_sync_auto").assertIsSelected()
        composeTestRule.onNodeWithTag("chip_custom_list_sync_manual").performClick()
        composeTestRule.onNodeWithTag("input_custom_list_name").performTextReplacement("Research")
        composeTestRule.onNodeWithTag("btn_confirm_custom_list").performClick()
        assertEquals(Triple("Research", false, false), capturedListArgs)

        // 5. Enter URL and submit Add Feed
        composeTestRule.onNodeWithTag("input_feed_url").performScrollTo().performTextReplacement("https://research.example.com/rss")
        composeTestRule.onNodeWithTag("confirm_add_feed_button").performScrollTo().performClick()

        assertEquals(
            listOf("https://research.example.com/rss", "Research", false, ReaderAiMode.NONE, false),
            capturedFeedArgs
        )
    }

    @Test
    fun `BackupManager distinguishes app format backup from standard OPML and GeminiService applies custom prompts`() {
        val standardOpml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <body>
                <outline text="Tech">
                  <outline type="rss" text="Example" xmlUrl="https://example.com/rss.xml" />
                </outline>
              </body>
            </opml>
        """.trimIndent()
        assertFalse(BackupManager.isAppFormatBackup(standardOpml))

        val appJson = BackupManager.exportToJson(
            selection = BackupSelection(includeFeeds = true, includeLists = true, includeSaved = true, includeRead = true),
            feeds = listOf(FeedEntity(url = "https://example.com/rss.xml", title = "Example", showInAllLists = false)),
            customLists = listOf(CustomListEntity(name = "Tech")),
            savedArticles = emptyList(),
            readArticles = emptyList()
        )
        assertTrue(BackupManager.isAppFormatBackup(appJson))
        val parsed = BackupManager.parseFromJson(appJson)
        assertFalse(parsed.feeds.first().showInAllLists)

        val defaultSummaryPrompt = GeminiService.buildArticleAiPrompt(
            articleTitle = "Test Title",
            articleBody = "Test Body",
            mode = ReaderAiMode.SUMMARIZE,
            targetLanguage = TargetLanguage.ARABIC,
            customPrompt = null
        )
        assertTrue(defaultSummaryPrompt.contains("Summarize the following article clearly, comprehensively, and engagingly in Arabic"))

        val customSummaryPrompt = GeminiService.buildArticleAiPrompt(
            articleTitle = "Test Title",
            articleBody = "Test Body",
            mode = ReaderAiMode.SUMMARIZE,
            targetLanguage = TargetLanguage.ARABIC,
            customPrompt = "Give 2 key takeaways in {language} only."
        )
        assertTrue(customSummaryPrompt.contains("Give 2 key takeaways in Arabic only."))
    }

    @Test
    fun `v295 System Carbon is default theme and SmartImportSelectionDialog filters standard OPML and app backup feeds`(): Unit = runBlocking {
        // 1. Default theme is SYSTEM (Dark Carbon / Light Carbon)
        assertEquals(AppThemeMode.SYSTEM, AppSettings().themeMode)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.rssDao()
            val repo = RssRepository(dao = dao, settingsRepository = SettingsRepository(context))

            val standardOpml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <opml version="2.0">
                  <body>
                    <outline text="Tech">
                      <outline type="rss" text="Alpha Feed" xmlUrl="https://alpha.example.com/rss.xml" />
                    </outline>
                    <outline text="Sports">
                      <outline type="rss" text="Beta Feed" xmlUrl="https://beta.example.com/rss.xml" />
                    </outline>
                  </body>
                </opml>
            """.trimIndent()

            val opmlPreview = repo.inspectImportContent(standardOpml).getOrThrow()
            assertFalse(opmlPreview.isAppFormatBackup)
            assertEquals(2, opmlPreview.feeds.size)

            var activePreview by mutableStateOf(opmlPreview)
            var confirmedSelection: BackupSelection? = null
            var confirmedFeeds: Set<String>? = null

            composeTestRule.setContent {
                CrimsonFeedTheme {
                    SmartImportSelectionDialog(
                        preview = activePreview,
                        onDismiss = {},
                        onConfirm = { sel, urls ->
                            confirmedSelection = sel
                            confirmedFeeds = urls
                        }
                    )
                }
            }

            // Standard OPML hides category checkboxes and shows ONLY feed checklist with all feeds ON by default
            composeTestRule.onNodeWithTag("checkbox_backup_feeds").assertDoesNotExist()
            composeTestRule.onNodeWithTag("checkbox_backup_lists").assertDoesNotExist()
            composeTestRule.onNodeWithTag("checkbox_import_feed_0").assertIsOn()
            composeTestRule.onNodeWithTag("checkbox_import_feed_1").assertIsOn()

            // Uncheck Beta Feed (index 1) and confirm
            composeTestRule.onNodeWithTag("checkbox_import_feed_1").performClick()
            composeTestRule.onNodeWithTag("btn_confirm_backup_dialog").performClick()
            composeTestRule.waitForIdle()

            assertEquals(setOf("https://alpha.example.com/rss.xml"), confirmedFeeds)
            val importSummary = repo.importBackupContent(
                rawContent = standardOpml,
                selection = confirmedSelection!!,
                settings = AppSettings(),
                allowedFeedUrls = confirmedFeeds,
                syncAfterFeedImport = false
            ).getOrThrow()
            assertEquals(1, importSummary.feedsCount)
            assertEquals(1, importSummary.listsCount)
            assertEquals(listOf("Tech"), dao.getAllCustomListsOnce().map { it.name })
            assertEquals(listOf("Alpha Feed"), dao.getAllFeedsOnce().map { it.title })

            // App-format JSON backup shows BOTH the 4 category checkboxes AND the per-feed checklist
            val appJson = BackupManager.exportToJson(
                selection = BackupSelection(includeFeeds = true, includeLists = true, includeSaved = true, includeRead = true),
                feeds = listOf(
                    FeedEntity(url = "https://one.example.com/rss", title = "One Feed"),
                    FeedEntity(url = "https://two.example.com/rss", title = "Two Feed")
                ),
                customLists = listOf(CustomListEntity(name = "MainList")),
                savedArticles = emptyList(),
                readArticles = emptyList()
            )
            activePreview = repo.inspectImportContent(appJson).getOrThrow()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("checkbox_backup_feeds").assertIsDisplayed().assertIsOn()
            composeTestRule.onNodeWithTag("checkbox_backup_lists").assertIsDisplayed().assertIsOn()
            composeTestRule.onNodeWithTag("checkbox_backup_read").assertIsDisplayed().assertIsOff()
            composeTestRule.onNodeWithTag("checkbox_backup_saved").assertIsDisplayed().assertIsOff()
            composeTestRule.onNodeWithTag("checkbox_import_feed_0").assertIsOn()
            composeTestRule.onNodeWithTag("checkbox_import_feed_1").assertIsOn()
            Unit
        } finally {
            db.close()
        }
    }

    @Test
    fun `v295 prefetchShortUnreadArticles upgrades short RSS snippets to full readable articles and og image in single pass`(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val requestCount = java.util.concurrent.atomic.AtomicInteger(0)
            val fullParagraph1 = "Artificial intelligence researchers unveiled a fast new architecture today that dramatically reduces memory latency across mobile edge devices worldwide."
            val fullParagraph2 = "By prefetching structured article content alongside feed metadata during background synchronization, readers can open full stories instantaneously with zero spinner delay."
            val fullParagraph3 = "Independent benchmarks confirmed immediate rendering in standard reading mode as well as significantly faster startup for translation and summarization pipelines."
            val fakeHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta property="og:image" content="https://news.example.com/images/hero-fast.jpg" />
                </head>
                <body>
                  <nav><p>Navigation menu item that should be ignored completely by extractor</p></nav>
                  <article>
                    <p>$fullParagraph1</p>
                    <p>$fullParagraph2</p>
                    <p>$fullParagraph3</p>
                  </article>
                </body>
                </html>
            """.trimIndent()

            val mockClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    requestCount.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "text/html; charset=utf-8")
                        .body(fakeHtml.toResponseBody("text/html".toMediaType()))
                        .build()
                }
                .build()

            val parser = SmartRssParser(httpClient = mockClient, ogClient = mockClient)
            val dao = db.rssDao()
            val repo = RssRepository(
                dao = dao,
                settingsRepository = SettingsRepository(context),
                rssParser = parser
            )

            val feedId = dao.insertFeed(
                FeedEntity(url = "https://news.example.com/rss", title = "Fast News")
            )
            val insertedIds = dao.insertArticles(
                listOf(
                    ArticleEntity(
                        feedId = feedId,
                        guid = "post-fast-1",
                        link = "https://news.example.com/articles/fast-1",
                        title = "Instant Reader Launch",
                        rawContent = "Short teaser snippet only.",
                        imageUrl = null,
                        publishedAt = 10_000L,
                        isRead = false
                    )
                )
            )
            val articleId = insertedIds.first()

            val upgraded = repo.prefetchShortUnreadArticles(limit = 10)
            assertEquals(1, upgraded)
            // Single HTTP pass extracted BOTH readable full body AND og:image
            assertEquals(1, requestCount.get())

            val stored = dao.getArticleByIdOnce(articleId)!!
            assertTrue(stored.rawContent.contains(fullParagraph1))
            assertTrue(stored.rawContent.contains(fullParagraph2))
            assertTrue(stored.rawContent.contains(fullParagraph3))
            assertFalse(stored.rawContent.contains("Navigation menu item"))
            assertEquals("https://news.example.com/images/hero-fast.jpg", stored.imageUrl)
        } finally {
            db.close()
        }
    }
}
