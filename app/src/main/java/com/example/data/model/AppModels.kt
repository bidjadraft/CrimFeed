package com.example.data.model

import com.example.BuildConfig

enum class AppThemeMode(val label: String, val isLight: Boolean) {
    SYSTEM("System", false),
    CARBON("Dark", false),
    LIGHT_CARBON("Light", true),
    CRIMSON_NIGHT("Crimson Night", false),
    DARK("Midnight Dark", false),
    OCEAN_DARK("Ocean Abyss", false),
    FOREST_DARK("Emerald Night", false),
    AMETHYST_DARK("Royal Amethyst", false),
    AMBER_DARK("Obsidian Gold", false),
    LIGHT("Warm Editorial", true),
    NORDIC_LIGHT("Nordic Daylight", true),
    SAKURA_LIGHT("Rose Paper", true),
    SAGE_LIGHT("Mint Sage", true),
    AMBER_LIGHT("Solar Sand", true),
    LAVENDER_LIGHT("Lavender Mist", true)
}

enum class FeedLayoutMode(val label: String) {
    CARD("Cards"),
    LIST("List"),
    THUMBNAIL_GRID("Thumbnails"),
    MAGAZINE("Magazine")
}

enum class TitleTranslationProvider(val label: String, val requiresApiKey: Boolean = false) {
    DEEPL_WEB("DeepL Web", false),
    YANDEX_WEB("Yandex Web", false),
    DEEPL_API("DeepL API", true),
    GEMINI("Gemini", true),
    OLLAMA("Ollama", true)
}

enum class ArticleTranslationEngine(val label: String, val badge: String) {
    GEMINI("Gemini AI", "AI"),
    DEEPL_API("DeepL API", "Official"),
    DEEPL_WEB("DeepL Web", "Free")
}

enum class ReaderAiMode(val label: String) {
    NONE("None"),
    TRANSLATE("Translate"),
    SUMMARIZE("Summarize")
}

enum class SummaryProvider(val label: String, val shortLabel: String) {
    GEMINI("Gemini", "Gemini"),
    OLLAMA("Ollama", "Ollama")
}

enum class TargetLanguage(
    val isoCode: String,
    val deeplCode: String,
    val englishName: String,
    val displayLabel: String,
    val isRtl: Boolean
) {
    ARABIC("ar", "AR", "Arabic", "العربية (Arabic)", true),
    ENGLISH("en", "EN", "English", "English", false),
    FRENCH("fr", "FR", "French", "Français (French)", false),
    SPANISH("es", "ES", "Spanish", "Español (Spanish)", false),
    TURKISH("tr", "TR", "Turkish", "Türkçe (Turkish)", false),
    GERMAN("de", "DE", "German", "Deutsch (German)", false),
    RUSSIAN("ru", "RU", "Russian", "Русский (Russian)", false),
    CHINESE("zh", "ZH", "Chinese", "中文 (Chinese)", false)
}

sealed class KeyVerificationState {
    data object Idle : KeyVerificationState()
    data object Verifying : KeyVerificationState()
    data class Valid(val message: String) : KeyVerificationState()
    data class Invalid(val message: String) : KeyVerificationState()
}

data class BackupSelection(
    val includeFeeds: Boolean = true,
    val includeLists: Boolean = true,
    val includeSaved: Boolean = false,
    val includeRead: Boolean = false,
    val includeFilters: Boolean = true
) {
    val hasAnySelection: Boolean
        get() = includeFeeds || includeLists || includeSaved || includeRead || includeFilters
}

data class ImportCandidateFeed(
    val url: String,
    val normalizedUrl: String,
    val title: String,
    val groupName: String = "",
    val iconUrl: String = ""
)

data class PendingImportPreview(
    val rawContent: String,
    val isAppFormatBackup: Boolean,
    val feeds: List<ImportCandidateFeed> = emptyList(),
    val listsCount: Int = 0,
    val savedCount: Int = 0,
    val readCount: Int = 0,
    val filtersCount: Int = 0
)

data class SessionResumeState(
    val lastTab: String = TAB_FEEDS,
    val selectedFeedId: Long? = null,
    val selectedListName: String? = null,
    val homeScrollIndex: Int = 0,
    val homeScrollOffset: Int = 0,
    val savedScrollIndex: Int = 0,
    val savedScrollOffset: Int = 0,
    val readScrollIndex: Int = 0,
    val readScrollOffset: Int = 0
) {
    companion object {
        const val TAB_FEEDS = "feeds"
        const val TAB_SAVED = "saved"
        const val TAB_READ = "read"
        const val TAB_SETTINGS = "settings"
    }
}

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val layoutMode: FeedLayoutMode = FeedLayoutMode.CARD,
    val titleProvider: TitleTranslationProvider = TitleTranslationProvider.DEEPL_WEB,
    val articleTranslationEngine: ArticleTranslationEngine = ArticleTranslationEngine.GEMINI,
    val summaryProvider: SummaryProvider = SummaryProvider.GEMINI,
    val readerAiMode: ReaderAiMode = ReaderAiMode.NONE,
    val targetLanguage: TargetLanguage = TargetLanguage.ARABIC,
    val availableModels: List<String> = DEFAULT_FALLBACK_MODELS,
    val selectedModel: String = DEFAULT_FALLBACK_MODELS.first(),
    val availableOllamaModels: List<String> = DEFAULT_OLLAMA_MODELS,
    val selectedOllamaModel: String = DEFAULT_OLLAMA_MODELS.first(),
    val lastModelsSyncMs: Long = 0L,
    val lastOllamaModelsSyncMs: Long = 0L,
    val telegramEnabled: Boolean = false,
    val geminiApiKeyOverride: String = "",
    val ollamaApiKeyOverride: String = "",
    val deeplApiKeyOverride: String = "",
    val telegramBotTokenOverride: String = "",
    val telegramChannelIdOverride: String = "",
    val useCustomTranslatePrompt: Boolean = false,
    val customTranslatePrompt: String = DEFAULT_TRANSLATE_PROMPT,
    val useCustomSummarizePrompt: Boolean = false,
    val customSummarizePrompt: String = DEFAULT_SUMMARIZE_PROMPT
) {
    val effectiveGeminiKey: String
        get() = geminiApiKeyOverride.trim()

    val effectiveOllamaKey: String
        get() = resolveSecret(ollamaApiKeyOverride, BuildConfig.OLLAMA_API_KEY, "MY_OLLAMA_API_KEY")

    val canSummarizeWithAi: Boolean
        get() = effectiveGeminiKey.isNotBlank() || effectiveOllamaKey.isNotBlank()

    val activeTranslatePromptOverride: String?
        get() = customTranslatePrompt.trim().takeIf { useCustomTranslatePrompt && it.isNotEmpty() }

    val activeSummarizePromptOverride: String?
        get() = customSummarizePrompt.trim().takeIf { useCustomSummarizePrompt && it.isNotEmpty() }

    val effectiveDeeplKey: String
        get() = resolveSecret(deeplApiKeyOverride, BuildConfig.DEEPL_API_KEY, "MY_DEEPL_API_KEY")

    val effectiveTelegramToken: String
        get() = resolveSecret(telegramBotTokenOverride, BuildConfig.TELEGRAM_BOT_TOKEN, "MY_TELEGRAM_BOT_TOKEN")

    val effectiveTelegramChannel: String
        get() = resolveSecret(telegramChannelIdOverride, BuildConfig.TELEGRAM_CHANNEL_ID, "MY_TELEGRAM_CHANNEL_ID")

    private fun resolveSecret(overrideVal: String, buildConfigVal: String, placeholder: String): String {
        val cleanOverride = overrideVal.trim()
        if (cleanOverride.isNotEmpty()) return cleanOverride
        val cleanBuild = buildConfigVal.trim()
        return if (cleanBuild.isNotEmpty() && cleanBuild != placeholder) cleanBuild else ""
    }

    companion object {
        val DEFAULT_TRANSLATE_PROMPT = """
            Translate the following article faithfully and naturally into {language}.
            STRICT OUTPUT RULES:
            1. Output ONLY the translated article paragraphs directly.
            2. Do NOT include the article title or any headline at the top.
            3. Do NOT write any introduction, greeting, explanation, or closing note (no "Here is the translation", etc.).
            4. Do NOT use Markdown formatting symbols (*, **, #, ##, ```, >) or hashtags. Write clean, plain readable paragraphs separated by blank lines.
        """.trimIndent()

        val DEFAULT_SUMMARIZE_PROMPT = """
            Summarize the following article clearly, comprehensively, and engagingly in {language} in STRICTLY ONE SINGLE PARAGRAPH that captures the entire story and all essential facts.
            STRICT OUTPUT RULES:
            1. Output ONLY ONE SINGLE PARAGRAPH (no line breaks, no multiple paragraphs, no bullet points, no numbered lists).
            2. Do NOT include the article title or any headline at the top.
            3. Do NOT write any introduction, preface, or meta commentary (no "Summary:", "This article discusses...", etc.).
            4. Do NOT use Markdown formatting symbols (*, **, #, ##, ```, >) or hashtags.
        """.trimIndent()

        val DEFAULT_FALLBACK_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.1-flash-lite-preview",
            "gemini-3.1-pro-preview"
        )

        val DEFAULT_OLLAMA_MODELS = listOf(
            "gpt-oss:120b",
            "gpt-oss:20b",
            "gemma4:31b",
            "beam",
            "nemotron-3-nano:30b",
            "nemotron-3-super",
            "nemotron-3-ultra"
        )
    }
}

typealias FeedWithUnreadCount = com.example.data.local.FeedWithCount
