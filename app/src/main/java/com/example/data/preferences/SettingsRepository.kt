package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.AppThemeMode
import com.example.data.model.ArticleTranslationEngine
import com.example.data.model.FeedLayoutMode
import com.example.data.model.ReaderAiMode
import com.example.data.model.SessionResumeState
import com.example.data.model.SummaryProvider
import com.example.data.model.TargetLanguage
import com.example.data.model.TitleTranslationProvider
import com.example.data.remote.OllamaService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "crimson_feed_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LAYOUT = stringPreferencesKey("layout_mode")
        val TITLE_PROVIDER = stringPreferencesKey("title_provider")
        val ARTICLE_ENGINE = stringPreferencesKey("article_translation_engine")
        val SUMMARY_PROVIDER = stringPreferencesKey("summary_provider")
        val READER_MODE = stringPreferencesKey("reader_ai_mode")
        val TARGET_LANG = stringPreferencesKey("target_language")
        val MODELS_CSV = stringPreferencesKey("available_models_csv")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val OLLAMA_MODELS_CSV = stringPreferencesKey("available_ollama_models_csv")
        val SELECTED_OLLAMA_MODEL = stringPreferencesKey("selected_ollama_model")
        val OLLAMA_USER_SELECTED = booleanPreferencesKey("ollama_user_selected_model")
        val LAST_MODELS_SYNC = longPreferencesKey("last_models_sync_ms")
        val LAST_OLLAMA_MODELS_SYNC = longPreferencesKey("last_ollama_models_sync_ms")
        val TG_ENABLED = booleanPreferencesKey("telegram_enabled")
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key_override")
        val OLLAMA_KEY = stringPreferencesKey("ollama_api_key_override")
        val DEEPL_KEY = stringPreferencesKey("deepl_api_key_override")
        val TG_TOKEN = stringPreferencesKey("telegram_bot_token")
        val TG_CHANNEL = stringPreferencesKey("telegram_channel_id")
        val USE_CUSTOM_TRANSLATE_PROMPT = booleanPreferencesKey("use_custom_translate_prompt")
        val CUSTOM_TRANSLATE_PROMPT = stringPreferencesKey("custom_translate_prompt")
        val USE_CUSTOM_SUMMARIZE_PROMPT = booleanPreferencesKey("use_custom_summarize_prompt")
        val CUSTOM_SUMMARIZE_PROMPT = stringPreferencesKey("custom_summarize_prompt")

        // Session resume state (page, selected filters, and scroll position)
        val LAST_TAB = stringPreferencesKey("session_last_tab")
        val LAST_FEED_ID = longPreferencesKey("session_selected_feed_id")
        val LAST_LIST_NAME = stringPreferencesKey("session_selected_list_name")
        val HOME_SCROLL_IDX = intPreferencesKey("session_home_scroll_idx")
        val HOME_SCROLL_OFF = intPreferencesKey("session_home_scroll_off")
        val SAVED_SCROLL_IDX = intPreferencesKey("session_saved_scroll_idx")
        val SAVED_SCROLL_OFF = intPreferencesKey("session_saved_scroll_off")
        val READ_SCROLL_IDX = intPreferencesKey("session_read_scroll_idx")
        val READ_SCROLL_OFF = intPreferencesKey("session_read_scroll_off")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val parsedModels = prefs[Keys.MODELS_CSV]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: AppSettings.DEFAULT_FALLBACK_MODELS

        val savedSelected = prefs[Keys.SELECTED_MODEL]?.trim().orEmpty()
        val activeModel = if (savedSelected in parsedModels) savedSelected else parsedModels.first()

        val ollamaModels = prefs[Keys.OLLAMA_MODELS_CSV]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?: AppSettings.DEFAULT_OLLAMA_MODELS

        val preferredOllamaDefault = OllamaService.resolveDefaultModel(ollamaModels)
        val savedOllama = prefs[Keys.SELECTED_OLLAMA_MODEL]?.trim().orEmpty()
        val activeOllamaModel = if (savedOllama in ollamaModels) savedOllama else preferredOllamaDefault

        val tgToken = prefs[Keys.TG_TOKEN].orEmpty()
        val tgChannel = prefs[Keys.TG_CHANNEL].orEmpty()
        val tgEnabled = prefs[Keys.TG_ENABLED] ?: (tgToken.isNotBlank() || tgChannel.isNotBlank())

        val geminiOverride = prefs[Keys.GEMINI_KEY].orEmpty()
        val ollamaOverride = prefs[Keys.OLLAMA_KEY].orEmpty()
        val deeplOverride = prefs[Keys.DEEPL_KEY].orEmpty()

        val rawTitleProvider = runCatching { TitleTranslationProvider.valueOf(prefs[Keys.TITLE_PROVIDER] ?: "") }
            .getOrDefault(TitleTranslationProvider.DEEPL_WEB)

        val probeSettings = AppSettings(
            geminiApiKeyOverride = geminiOverride,
            ollamaApiKeyOverride = ollamaOverride,
            deeplApiKeyOverride = deeplOverride
        )
        val validatedTitleProvider = when (rawTitleProvider) {
            TitleTranslationProvider.GEMINI ->
                if (probeSettings.effectiveGeminiKey.isNotBlank()) rawTitleProvider else TitleTranslationProvider.YANDEX_WEB
            TitleTranslationProvider.OLLAMA ->
                if (probeSettings.effectiveOllamaKey.isNotBlank()) rawTitleProvider else TitleTranslationProvider.YANDEX_WEB
            else -> rawTitleProvider
        }

        AppSettings(
            themeMode = runCatching { AppThemeMode.valueOf(prefs[Keys.THEME] ?: "") }
                .getOrDefault(AppThemeMode.SYSTEM),
            layoutMode = runCatching { FeedLayoutMode.valueOf(prefs[Keys.LAYOUT] ?: "") }
                .getOrDefault(AppLayoutDefault),
            titleProvider = validatedTitleProvider,
            articleTranslationEngine = runCatching { ArticleTranslationEngine.valueOf(prefs[Keys.ARTICLE_ENGINE] ?: "") }
                .getOrDefault(ArticleTranslationEngine.GEMINI),
            summaryProvider = runCatching { SummaryProvider.valueOf(prefs[Keys.SUMMARY_PROVIDER] ?: "") }
                .getOrDefault(SummaryProvider.GEMINI),
            readerAiMode = runCatching { ReaderAiMode.valueOf(prefs[Keys.READER_MODE] ?: "") }
                .getOrDefault(ReaderAiMode.NONE),
            targetLanguage = runCatching { TargetLanguage.valueOf(prefs[Keys.TARGET_LANG] ?: "") }
                .getOrDefault(TargetLanguage.ARABIC),
            availableModels = parsedModels,
            selectedModel = activeModel,
            availableOllamaModels = ollamaModels,
            selectedOllamaModel = activeOllamaModel,
            lastModelsSyncMs = prefs[Keys.LAST_MODELS_SYNC] ?: 0L,
            lastOllamaModelsSyncMs = prefs[Keys.LAST_OLLAMA_MODELS_SYNC] ?: 0L,
            telegramEnabled = tgEnabled,
            geminiApiKeyOverride = geminiOverride,
            ollamaApiKeyOverride = ollamaOverride,
            deeplApiKeyOverride = deeplOverride,
            telegramBotTokenOverride = tgToken,
            telegramChannelIdOverride = tgChannel,
            useCustomTranslatePrompt = prefs[Keys.USE_CUSTOM_TRANSLATE_PROMPT] ?: false,
            customTranslatePrompt = if (prefs[Keys.USE_CUSTOM_TRANSLATE_PROMPT] == true) {
                prefs[Keys.CUSTOM_TRANSLATE_PROMPT]?.takeIf { it.isNotEmpty() }
                    ?: AppSettings.DEFAULT_TRANSLATE_PROMPT
            } else {
                AppSettings.DEFAULT_TRANSLATE_PROMPT
            },
            useCustomSummarizePrompt = prefs[Keys.USE_CUSTOM_SUMMARIZE_PROMPT] ?: false,
            customSummarizePrompt = if (prefs[Keys.USE_CUSTOM_SUMMARIZE_PROMPT] == true) {
                prefs[Keys.CUSTOM_SUMMARIZE_PROMPT]?.takeIf { it.isNotEmpty() }
                    ?: AppSettings.DEFAULT_SUMMARIZE_PROMPT
            } else {
                AppSettings.DEFAULT_SUMMARIZE_PROMPT
            }
        )
    }

    val sessionResumeFlow: Flow<SessionResumeState> = context.settingsDataStore.data.map { prefs ->
        val savedFeedId = prefs[Keys.LAST_FEED_ID]?.takeIf { it > 0L }
        val savedListName = prefs[Keys.LAST_LIST_NAME]?.takeIf { it.isNotBlank() }
        SessionResumeState(
            lastTab = prefs[Keys.LAST_TAB] ?: SessionResumeState.TAB_FEEDS,
            selectedFeedId = savedFeedId,
            selectedListName = savedListName,
            homeScrollIndex = (prefs[Keys.HOME_SCROLL_IDX] ?: 0).coerceAtLeast(0),
            homeScrollOffset = (prefs[Keys.HOME_SCROLL_OFF] ?: 0).coerceAtLeast(0),
            savedScrollIndex = (prefs[Keys.SAVED_SCROLL_IDX] ?: 0).coerceAtLeast(0),
            savedScrollOffset = (prefs[Keys.SAVED_SCROLL_OFF] ?: 0).coerceAtLeast(0),
            readScrollIndex = (prefs[Keys.READ_SCROLL_IDX] ?: 0).coerceAtLeast(0),
            readScrollOffset = (prefs[Keys.READ_SCROLL_OFF] ?: 0).coerceAtLeast(0)
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setLayoutMode(mode: FeedLayoutMode) {
        context.settingsDataStore.edit { it[Keys.LAYOUT] = mode.name }
    }

    suspend fun setTitleProvider(provider: TitleTranslationProvider) {
        context.settingsDataStore.edit { it[Keys.TITLE_PROVIDER] = provider.name }
    }

    suspend fun setArticleTranslationEngine(engine: ArticleTranslationEngine) {
        context.settingsDataStore.edit { it[Keys.ARTICLE_ENGINE] = engine.name }
    }

    suspend fun setSummaryProvider(provider: SummaryProvider) {
        context.settingsDataStore.edit { it[Keys.SUMMARY_PROVIDER] = provider.name }
    }

    suspend fun setReaderAiMode(mode: ReaderAiMode) {
        context.settingsDataStore.edit { it[Keys.READER_MODE] = mode.name }
    }

    suspend fun setTargetLanguage(lang: TargetLanguage) {
        context.settingsDataStore.edit { it[Keys.TARGET_LANG] = lang.name }
    }

    suspend fun setSelectedModel(model: String) {
        context.settingsDataStore.edit { it[Keys.SELECTED_MODEL] = model.trim() }
    }

    suspend fun setSelectedOllamaModel(model: String, userInitiated: Boolean = true) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SELECTED_OLLAMA_MODEL] = model.trim()
            if (userInitiated) {
                prefs[Keys.OLLAMA_USER_SELECTED] = true
            }
        }
    }

    suspend fun setTelegramEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.TG_ENABLED] = enabled }
    }

    suspend fun setUseCustomTranslatePrompt(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.USE_CUSTOM_TRANSLATE_PROMPT] = enabled
            if (!enabled) {
                prefs[Keys.CUSTOM_TRANSLATE_PROMPT] = AppSettings.DEFAULT_TRANSLATE_PROMPT
            } else if (prefs[Keys.CUSTOM_TRANSLATE_PROMPT].isNullOrEmpty()) {
                prefs[Keys.CUSTOM_TRANSLATE_PROMPT] = AppSettings.DEFAULT_TRANSLATE_PROMPT
            }
        }
    }

    suspend fun setCustomTranslatePrompt(prompt: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_TRANSLATE_PROMPT] = prompt
        }
    }

    suspend fun setUseCustomSummarizePrompt(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.USE_CUSTOM_SUMMARIZE_PROMPT] = enabled
            if (!enabled) {
                prefs[Keys.CUSTOM_SUMMARIZE_PROMPT] = AppSettings.DEFAULT_SUMMARIZE_PROMPT
            } else if (prefs[Keys.CUSTOM_SUMMARIZE_PROMPT].isNullOrEmpty()) {
                prefs[Keys.CUSTOM_SUMMARIZE_PROMPT] = AppSettings.DEFAULT_SUMMARIZE_PROMPT
            }
        }
    }

    suspend fun setCustomSummarizePrompt(prompt: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_SUMMARIZE_PROMPT] = prompt
        }
    }

    suspend fun saveLastTab(tabKey: String) {
        context.settingsDataStore.edit { it[Keys.LAST_TAB] = tabKey }
    }

    suspend fun saveSelectedFilters(feedId: Long?, listName: String?) {
        context.settingsDataStore.edit { prefs ->
            if (feedId != null && feedId > 0L) {
                prefs[Keys.LAST_FEED_ID] = feedId
            } else {
                prefs.remove(Keys.LAST_FEED_ID)
            }
            if (!listName.isNullOrBlank()) {
                prefs[Keys.LAST_LIST_NAME] = listName
            } else {
                prefs.remove(Keys.LAST_LIST_NAME)
            }
        }
    }

    suspend fun saveScrollPosition(tabKey: String, index: Int, offset: Int) {
        val safeIdx = index.coerceAtLeast(0)
        val safeOff = offset.coerceAtLeast(0)
        context.settingsDataStore.edit { prefs ->
            when (tabKey) {
                SessionResumeState.TAB_FEEDS -> {
                    prefs[Keys.HOME_SCROLL_IDX] = safeIdx
                    prefs[Keys.HOME_SCROLL_OFF] = safeOff
                }
                SessionResumeState.TAB_SAVED -> {
                    prefs[Keys.SAVED_SCROLL_IDX] = safeIdx
                    prefs[Keys.SAVED_SCROLL_OFF] = safeOff
                }
                SessionResumeState.TAB_READ -> {
                    prefs[Keys.READ_SCROLL_IDX] = safeIdx
                    prefs[Keys.READ_SCROLL_OFF] = safeOff
                }
            }
        }
    }

    suspend fun updateFetchedModels(models: List<String>) {
        if (models.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.MODELS_CSV] = models.joinToString("\n")
            prefs[Keys.LAST_MODELS_SYNC] = System.currentTimeMillis()
            val current = prefs[Keys.SELECTED_MODEL]
            if (current.isNullOrBlank() || current !in models) {
                prefs[Keys.SELECTED_MODEL] = models.first()
            }
        }
    }

    suspend fun updateFetchedOllamaModels(models: List<String>) {
        val cleanModels = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanModels.isEmpty()) return
        val preferredDefault = OllamaService.resolveDefaultModel(cleanModels)
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.OLLAMA_MODELS_CSV] = cleanModels.joinToString("\n")
            prefs[Keys.LAST_OLLAMA_MODELS_SYNC] = System.currentTimeMillis()
            val userSelected = prefs[Keys.OLLAMA_USER_SELECTED] ?: false
            val current = prefs[Keys.SELECTED_OLLAMA_MODEL]?.trim()
            if (!userSelected || current.isNullOrBlank() || current !in cleanModels) {
                prefs[Keys.SELECTED_OLLAMA_MODEL] = preferredDefault
                if (current != null && current !in cleanModels) {
                    prefs[Keys.OLLAMA_USER_SELECTED] = false
                }
            }
        }
    }

    suspend fun updateSecrets(
        geminiKey: String? = null,
        ollamaKey: String? = null,
        deeplKey: String? = null,
        telegramToken: String? = null,
        telegramChannel: String? = null
    ) {
        context.settingsDataStore.edit { prefs ->
            geminiKey?.let { prefs[Keys.GEMINI_KEY] = it.trim() }
            ollamaKey?.let { prefs[Keys.OLLAMA_KEY] = it.trim() }
            deeplKey?.let { prefs[Keys.DEEPL_KEY] = it.trim() }
            telegramToken?.let { prefs[Keys.TG_TOKEN] = it.trim() }
            telegramChannel?.let { prefs[Keys.TG_CHANNEL] = it.trim() }
        }
    }

    companion object {
        private val AppLayoutDefault = FeedLayoutMode.CARD
    }
}
