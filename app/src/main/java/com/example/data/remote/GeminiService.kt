package com.example.data.remote

import com.example.data.model.AppSettings
import com.example.data.model.ReaderAiMode
import com.example.data.model.TargetLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

data class AiArticleOutput(
    val cleanBody: String,
    val usedModel: String
)

class GeminiService(
    private val httpClient: OkHttpClient = NetworkClient.aiHttpClient,
    private val modelsEndpointBase: String = GEMINI_BASE
) {

    companion object {
        const val FREE_MODELS_URL =
            "https://raw.githubusercontent.com/bidjadraft/Free-Models/refs/heads/main/free_pdf_models.txt"
        const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private val PROHIBITED_LEGACY_MODELS = setOf(
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-pro",
            "gemini-2.0-flash",
            "gemini-2.0-pro",
            "gemini-2.0-flash-thinking"
        )
        private val THINK_BLOCK_REGEX = Regex("""(?is)<think>.*?</think>""")

        fun buildArticleAiPrompt(
            articleTitle: String,
            articleBody: String,
            mode: ReaderAiMode,
            targetLanguage: TargetLanguage,
            customPrompt: String? = null
        ): String {
            val safeContent = articleBody.take(18_000).ifBlank { articleTitle }
            val trimmedCustom = customPrompt?.trim()?.takeIf { it.isNotEmpty() }
            val rawTemplate = trimmedCustom ?: when (mode) {
                ReaderAiMode.NONE -> ""
                ReaderAiMode.TRANSLATE -> AppSettings.DEFAULT_TRANSLATE_PROMPT
                ReaderAiMode.SUMMARIZE -> AppSettings.DEFAULT_SUMMARIZE_PROMPT
            }
            val taskInstruction = when {
                rawTemplate.isEmpty() -> ""
                rawTemplate.contains("{language}", ignoreCase = true) ->
                    rawTemplate.replace("{language}", targetLanguage.englishName, ignoreCase = true)
                trimmedCustom != null ->
                    "$trimmedCustom\nTarget language: ${targetLanguage.englishName}"
                else -> rawTemplate
            }

            return buildString {
                appendLine(taskInstruction)
                appendLine()
                appendLine("Reference context (do NOT output this title): $articleTitle")
                appendLine("Article source text:")
                appendLine(safeContent)
            }
        }

        fun buildTelegramDraftPrompt(
            articleTitle: String,
            articleBody: String
        ): String {
            val safeContent = articleBody.take(14_000).ifBlank { articleTitle }
            return """
                لخّص هذا الخبر أو المقال لنشره مباشرة في قناة تيليجرام بأسلوب بشري طبيعي جداً، وبألفاظ عربية فصحى بسيطة وسهلة الفهم وسلسة.
                شروط صارمة جداً:
                - اكتب بأسلوب محرر صحفي بشري يشارك المعلومة مباشرة وببساطة، وتجنّب تماماً أي عبارات آلية أو مبتذلة توحي بالذكاء الاصطناعي (مثل: "في تطور لافت"، "يشهد العالم"، "الجدير بالذكر"، "في الختام"، "يسلط المقال الضوء").
                - لا تضع أي مقدمات أو تعليقات جانبية أو رموز ماركداون مثل النجوم (** أو *).
                - اجعل النص مختصراً ومرتباً في فقرتين أو ثلاث قصيرة ومريحة للقراءة (بحد أقصى 750 حرفاً ليتناسب مع صورة تيليجرام).
                
                عنوان الخبر للمعلومية: $articleTitle
                نص المقال:
                $safeContent
            """.trimIndent()
        }

        fun stripThinkingBlocks(rawText: String): String {
            return rawText
                .replace(THINK_BLOCK_REGEX, "")
                .replace("</think>", "", ignoreCase = true)
                .trim()
        }

        fun parseCleanArticleOutput(
            rawOutput: String,
            originalTitle: String,
            usedModel: String,
            singleParagraph: Boolean = false
        ): AiArticleOutput {
            val withoutThinking = stripThinkingBlocks(rawOutput)
            val cleanedLines = withoutThinking
                .replace("**", "")
                .replace("__", "")
                .lines()
                .map { line ->
                    line.trim()
                        .removePrefix("### ")
                        .removePrefix("## ")
                        .removePrefix("# ")
                        .removePrefix("- ")
                        .removePrefix("* ")
                        .removePrefix("• ")
                        .trim()
                }
                .dropWhile { it.isBlank() || it.equals(originalTitle.trim(), ignoreCase = true) }

            val cleanedBody = if (singleParagraph) {
                cleanedLines
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .replace(Regex("""\s{2,}"""), " ")
                    .trim()
            } else {
                cleanedLines
                    .joinToString("\n")
                    .replace(Regex("""\n{3,}"""), "\n\n")
                    .trim()
            }

            return AiArticleOutput(
                cleanBody = cleanedBody.ifBlank { withoutThinking.ifBlank { rawOutput.trim() } },
                usedModel = usedModel
            )
        }

        fun buildHeadlineTranslatePrompt(
            headline: String,
            targetLanguage: TargetLanguage
        ): String {
            return """
                Translate the following news headline naturally and accurately into ${targetLanguage.englishName}.
                STRICT OUTPUT RULES:
                - Output ONLY the translated headline on a single line.
                - Do NOT add quotes, prefixes, explanations, or markdown symbols.
                
                Headline: ${headline.trim()}
            """.trimIndent()
        }

        fun cleanHeadlineOutput(rawText: String): String {
            return stripThinkingBlocks(rawText)
                .replace("**", "")
                .replace("__", "")
                .lines()
                .map { it.trim().trim('"', '“', '”', '\'', '`') }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }

        fun cleanTelegramDraft(rawText: String): String {
            return stripThinkingBlocks(rawText)
                .replace("**", "")
                .replace("*", "")
                .replace(Regex("""^#+\s*"""), "")
                .trim()
                .take(980)
        }
    }

    /**
     * Fetches the daily updated free models list from the GitHub raw .txt file.
     */
    suspend fun fetchFreeModelsList(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(FREE_MODELS_URL)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Failed to fetch models list (HTTP ${resp.code})")
                }
                val text = resp.body?.string().orEmpty()
                val parsed = parseModelsTxt(text)
                if (parsed.isEmpty()) {
                    throw IllegalStateException("Models file contained no valid model names")
                }
                parsed
            }
        }
    }

    /**
     * Verifies a Gemini API key by requesting the official models endpoint (`/v1beta/models?key=...`).
     * Valid if and only if HTTP 200 is returned with a non-empty `models` JSON array.
     */
    suspend fun verifyApiKey(apiKey: String): Result<Int> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Please enter a valid Gemini API key")
            )
        }
        runCatching {
            val req = Request.Builder()
                .url("$modelsEndpointBase?key=$cleanKey")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Invalid Gemini API key (HTTP ${resp.code}). Please enter a valid key.")
                }
                val jsonObj = runCatching {
                    NetworkClient.json.parseToJsonElement(rawBody).jsonObject
                }.getOrElse {
                    throw IllegalStateException("Invalid Gemini API key response. Please enter a valid key.")
                }
                if (jsonObj["error"] != null) {
                    throw IllegalStateException("Invalid Gemini API key. Please enter a valid key.")
                }
                val modelsArr = jsonObj["models"]?.jsonArray
                if (modelsArr == null || modelsArr.isEmpty()) {
                    throw IllegalStateException("Invalid Gemini API key — no models returned.")
                }
                modelsArr.size
            }
        }
    }

    fun parseModelsTxt(rawText: String): List<String> {
        val result = linkedSetOf<String>()
        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEach

            // Strip numbering, bullets, markdown formatting, or trailing comments/pipes
            val cleaned = line
                .replace(Regex("""^[-*•\d.)\s`"]+"""), "")
                .substringBefore('|')
                .substringBefore('#')
                .substringBefore(',')
                .substringBefore('\t')
                .substringBefore(' ')
                .trim('`', '"', '\'', ' ', '/')
                .removePrefix("models/")
                .removePrefix("google/")

            if (cleaned.length >= 4 &&
                cleaned.matches(Regex("""^[a-zA-Z0-9._-]+$""")) &&
                cleaned.lowercase() !in PROHIBITED_LEGACY_MODELS
            ) {
                result.add(cleaned)
            }
        }
        return result.toList()
    }

    /**
     * Executes a Gemini prompt starting with [selectedModel], automatically failing over
     * sequentially to the next model in [availableModels] on quota/overload/error.
     */
    suspend fun generateWithFailover(
        apiKey: String,
        prompt: String,
        availableModels: List<String>,
        selectedModel: String,
        temperature: Float = 0.3f,
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is missing. Add GEMINI_API_KEY in AI Studio Secrets or Settings.")
            )
        }

        val modelsPool = availableModels
            .filter { it.isNotBlank() && it.lowercase() !in PROHIBITED_LEGACY_MODELS }
            .ifEmpty { AppSettings.DEFAULT_FALLBACK_MODELS }

        val startIndex = modelsPool.indexOf(selectedModel).let { if (it >= 0) it else 0 }
        val orderedCandidates = buildList {
            for (offset in modelsPool.indices) {
                add(modelsPool[(startIndex + offset) % modelsPool.size])
            }
            // Append safe fallback models at the tail if none of the dynamic models matched
            AppSettings.DEFAULT_FALLBACK_MODELS.forEach { fallback ->
                if (fallback !in this) add(fallback)
            }
        }

        var lastError: Throwable? = null
        for ((attemptIndex, modelName) in orderedCandidates.withIndex()) {
            val attempt = runCatching {
                callSingleModel(apiKey, modelName, prompt, temperature)
            }
            if (attempt.isSuccess) {
                val text = attempt.getOrThrow().trim()
                if (text.isNotEmpty()) {
                    if (attemptIndex > 0 && modelName != selectedModel) {
                        onModelSwitched?.invoke(modelName)
                    }
                    return@withContext Result.success(text to modelName)
                }
            } else {
                lastError = attempt.exceptionOrNull()
            }
        }

        Result.failure(lastError ?: IllegalStateException("All Gemini models failed or hit quota limits."))
    }

    private fun callSingleModel(
        apiKey: String,
        modelName: String,
        prompt: String,
        temperature: Float
    ): String {
        val cleanModel = modelName.trim().removePrefix("models/")
        val endpoint = "$modelsEndpointBase/$cleanModel:generateContent?key=$apiKey"
        val payload = GeminiGenerateRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(temperature = temperature, topP = 0.92f)
        )
        val bodyStr = NetworkClient.json.encodeToString(GeminiGenerateRequest.serializer(), payload)
        val req = Request.Builder()
            .url(endpoint)
            .post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { response ->
            val rawResp = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Model $cleanModel HTTP ${response.code}: ${rawResp.take(180)}")
            }
            val parsed = NetworkClient.json.decodeFromString(GeminiGenerateResponse.serializer(), rawResp)
            return parsed.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.joinToString("") { it.text }
                .orEmpty()
        }
    }

    /**
     * Translates or summarizes an article cleanly without titles, intros, or symbols.
     */
    suspend fun processArticle(
        apiKey: String,
        articleTitle: String,
        articleBody: String,
        mode: ReaderAiMode,
        targetLanguage: TargetLanguage,
        availableModels: List<String>,
        selectedModel: String,
        customPrompt: String? = null,
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<AiArticleOutput> {
        val fullPrompt = buildArticleAiPrompt(
            articleTitle = articleTitle,
            articleBody = articleBody,
            mode = mode,
            targetLanguage = targetLanguage,
            customPrompt = customPrompt
        )

        return generateWithFailover(
            apiKey = apiKey,
            prompt = fullPrompt,
            availableModels = availableModels,
            selectedModel = selectedModel,
            temperature = 0.25f,
            onModelSwitched = onModelSwitched
        ).map { (rawOutput, usedModel) ->
            parseCleanBody(
                rawOutput = rawOutput,
                originalTitle = articleTitle,
                usedModel = usedModel,
                singleParagraph = (mode == ReaderAiMode.SUMMARIZE && customPrompt.isNullOrBlank())
            )
        }
    }

    /**
     * Translates a single feed card headline cleanly using Gemini.
     */
    suspend fun translateHeadline(
        apiKey: String,
        text: String,
        targetLanguage: TargetLanguage,
        availableModels: List<String>,
        selectedModel: String,
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return Result.success(text)
        val prompt = buildHeadlineTranslatePrompt(clean, targetLanguage)
        return generateWithFailover(
            apiKey = apiKey,
            prompt = prompt,
            availableModels = availableModels,
            selectedModel = selectedModel,
            temperature = 0.2f,
            onModelSwitched = onModelSwitched
        ).mapCatching { (rawOutput, _) ->
            cleanHeadlineOutput(rawOutput).ifBlank {
                throw IllegalStateException("Gemini returned an empty headline translation")
            }
        }
    }

    internal fun parseCleanBody(
        rawOutput: String,
        originalTitle: String,
        usedModel: String,
        singleParagraph: Boolean = false
    ): AiArticleOutput = parseCleanArticleOutput(
        rawOutput = rawOutput,
        originalTitle = originalTitle,
        usedModel = usedModel,
        singleParagraph = singleParagraph
    )

    /**
     * Drafts a natural, human-sounding simple Modern Standard Arabic post ready for Telegram.
     */
    suspend fun draftTelegramPost(
        apiKey: String,
        articleTitle: String,
        articleBody: String,
        availableModels: List<String>,
        selectedModel: String,
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<String> {
        val prompt = buildTelegramDraftPrompt(
            articleTitle = articleTitle,
            articleBody = articleBody
        )

        return generateWithFailover(
            apiKey = apiKey,
            prompt = prompt,
            availableModels = availableModels,
            selectedModel = selectedModel,
            temperature = 0.45f,
            onModelSwitched = onModelSwitched
        ).map { (text, _) ->
            cleanTelegramDraft(text)
        }
    }

    /**
     * Smart Gemini RSS fallback when a feed URL has non-standard XML/HTML that standard parsers cannot decode.
     */
    suspend fun discoverFeedViaGemini(
        apiKey: String,
        feedUrl: String,
        rawDocument: String,
        availableModels: List<String>,
        selectedModel: String,
        smartRssParser: SmartRssParser,
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<ParsedRssFeed> {
        val snippet = rawDocument.take(28_000)
        val prompt = """
            Extract the feed metadata and articles from the following raw RSS/XML/HTML document from URL: $feedUrl
            Return ONLY valid raw JSON (no markdown code fences, no commentary) with this exact structure:
            {
              "feedTitle": "Name of website or feed",
              "siteUrl": "https://...",
              "iconUrl": "https://... or empty string",
              "items": [
                {
                  "title": "Article headline",
                  "link": "https://...",
                  "content": "Plain text summary or article content",
                  "imageUrl": "https://... direct article image URL if present, else empty string"
                }
              ]
            }
            
            Raw document content:
            $snippet
        """.trimIndent()

        return generateWithFailover(
            apiKey = apiKey,
            prompt = prompt,
            availableModels = availableModels,
            selectedModel = selectedModel,
            temperature = 0.1f,
            onModelSwitched = onModelSwitched
        ).mapCatching { (rawJsonText, _) ->
            val cleanedJson = rawJsonText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val startBrace = cleanedJson.indexOf('{')
            val endBrace = cleanedJson.lastIndexOf('}')
            if (startBrace == -1 || endBrace <= startBrace) {
                throw IllegalStateException("Gemini did not return valid feed JSON")
            }
            val jsonObj: JsonObject = NetworkClient.json
                .parseToJsonElement(cleanedJson.substring(startBrace, endBrace + 1))
                .jsonObject

            val title = jsonObj["feedTitle"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .ifBlank { smartRssParser.extractHost(feedUrl) }
            val siteUrl = jsonObj["siteUrl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .ifBlank { smartRssParser.extractOriginUrl(feedUrl) }
            val rawIcon = jsonObj["iconUrl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val itemsJson: JsonArray = jsonObj["items"]?.jsonArray ?: JsonArray(emptyList())

            val now = System.currentTimeMillis()
            val items = itemsJson.mapIndexedNotNull { idx, elem ->
                val obj = elem.jsonObject
                val itemTitle = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val itemLink = obj["link"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val itemContent = obj["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val itemImg = obj["imageUrl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (itemTitle.isBlank()) return@mapIndexedNotNull null
                val resolvedLink = smartRssParser.resolveUrl(feedUrl, itemLink) ?: itemLink
                ParsedRssItem(
                    guid = resolvedLink.ifBlank { "$feedUrl#$itemTitle" },
                    link = resolvedLink,
                    title = itemTitle,
                    content = itemContent.ifBlank { itemTitle },
                    imageUrl = smartRssParser.resolveUrl(resolvedLink.ifBlank { feedUrl }, itemImg),
                    publishedAt = now - (idx * 1000L)
                )
            }
            if (items.isEmpty()) {
                throw IllegalStateException("No feed posts could be discovered from this URL")
            }
            ParsedRssFeed(
                feedUrl = feedUrl,
                siteUrl = siteUrl,
                title = title,
                iconUrl = smartRssParser.resolveFeedIcon(rawIcon, siteUrl.ifBlank { feedUrl }, rawDocument),
                items = items
            )
        }
    }
}
