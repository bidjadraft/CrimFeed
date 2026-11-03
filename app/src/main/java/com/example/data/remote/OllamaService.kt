package com.example.data.remote

import com.example.data.model.AppSettings
import com.example.data.model.ReaderAiMode
import com.example.data.model.TargetLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean
)

@Serializable
data class OllamaChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatChoice(
    val message: OllamaResponseMessage? = null
)

@Serializable
data class OllamaResponseMessage(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    val message: OllamaResponseMessage? = null,
    val response: String? = null,
    val choices: List<OllamaChatChoice>? = null
)

class OllamaUncaughtAuthException(message: String) : IllegalStateException(message)

class OllamaService(
    private val httpClient: OkHttpClient = NetworkClient.aiHttpClient,
    private val endpointUrl: String = OLLAMA_CHAT_URL,
    private val modelsListUrl: String = FREE_OLLAMA_MODELS_URL,
    private val tagsEndpointUrl: String = OLLAMA_TAGS_URL
) {

    companion object {
        const val OLLAMA_CHAT_URL = "https://ollama.com/api/chat"
        const val OLLAMA_TAGS_URL = "https://ollama.com/api/tags"
        const val FREE_OLLAMA_MODELS_URL =
            "https://raw.githubusercontent.com/bidjadraft/Free-Models/refs/heads/main/ollama.txt"

        /**
         * Resolves the preferred default Ollama model from [models]:
         * If any model containing "gemma" (case-insensitive) is available, selects it as default;
         * otherwise falls back to the first model in the list.
         */
        fun resolveDefaultModel(models: List<String>): String {
            val clean = models.map { it.trim() }.filter { it.isNotEmpty() }
            if (clean.isEmpty()) return AppSettings.DEFAULT_OLLAMA_MODELS.first()
            return clean.firstOrNull { it.contains("gemma", ignoreCase = true) } ?: clean.first()
        }
    }

    /**
     * Fetches the dynamically updated free Ollama models list from the GitHub raw ollama.txt file,
     * working identically to GeminiService.fetchFreeModelsList().
     */
    suspend fun fetchFreeModelsList(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(modelsListUrl)
                .header("Cache-Control", "no-cache")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Failed to fetch Ollama models list (HTTP ${resp.code})")
                }
                val text = resp.body?.string().orEmpty()
                val parsed = parseModelsTxt(text)
                if (parsed.isEmpty()) {
                    throw IllegalStateException("Ollama models file contained no valid model names")
                }
                parsed
            }
        }
    }

    /**
     * Verifies an Ollama token via `GET https://ollama.com/api/tags` with `Authorization: Bearer <TOKEN>`.
     * Valid if HTTP 200 is returned with a non-empty `models` array and no error field; otherwise prompts for a valid token.
     */
    suspend fun verifyApiKey(apiKey: String): Result<Int> = withContext(Dispatchers.IO) {
        val cleanToken = normalizeBearerToken(apiKey)
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Please enter a valid Ollama API token")
            )
        }
        runCatching {
            val req = Request.Builder()
                .url(tagsEndpointUrl)
                .header("Authorization", "Bearer $cleanToken")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val rawBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Invalid Ollama token (HTTP ${resp.code}). Please enter a valid token.")
                }
                val jsonObj = runCatching {
                    NetworkClient.json.parseToJsonElement(rawBody).jsonObject
                }.getOrElse {
                    throw IllegalStateException("Invalid Ollama token response. Please enter a valid token.")
                }
                val errMsg = jsonObj["error"]?.jsonPrimitive?.contentOrNull
                if (!errMsg.isNullOrBlank()) {
                    throw IllegalStateException("Invalid Ollama token ($errMsg). Please enter a valid token.")
                }
                val modelsArr = jsonObj["models"]?.jsonArray
                if (modelsArr == null || modelsArr.isEmpty()) {
                    throw IllegalStateException("Invalid Ollama token — no models found. Please enter a valid token.")
                }
                modelsArr.size
            }
        }
    }

    fun parseModelsTxt(rawText: String): List<String> {
        val result = linkedSetOf<String>()
        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return@forEach

            // Strip optional bullets/numbering, markdown quotes, or trailing comments/pipes
            val cleaned = line
                .replace(Regex("""^(?:[-*•]+|\d+[.)])\s*"""), "")
                .trimStart('`', '"', '\'', ' ')
                .substringBefore('|')
                .substringBefore('#')
                .substringBefore(',')
                .substringBefore('\t')
                .substringBefore(' ')
                .trim('`', '"', '\'', ' ', '/')
                .removePrefix("ollama/")
                .removePrefix("models/")

            if (cleaned.length >= 2 &&
                !cleaned.startsWith("http:", ignoreCase = true) &&
                !cleaned.startsWith("https:", ignoreCase = true) &&
                cleaned.matches(Regex("""^[a-zA-Z0-9][a-zA-Z0-9._:/-]*$"""))
            ) {
                result.add(cleaned)
            }
        }
        return result.toList()
    }

    /**
     * Executes an Ollama Cloud chat request starting with [selectedModel],
     * automatically failing over sequentially through [availableModels].
     */
    suspend fun generateWithFailover(
        apiKey: String,
        prompt: String,
        availableModels: List<String> = AppSettings.DEFAULT_OLLAMA_MODELS,
        selectedModel: String = AppSettings.DEFAULT_OLLAMA_MODELS.first(),
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val cleanToken = normalizeBearerToken(apiKey)
        if (cleanToken.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Ollama API key is missing. Add OLLAMA_API_KEY in AI Studio Secrets or Settings.")
            )
        }

        val modelsPool = availableModels
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { AppSettings.DEFAULT_OLLAMA_MODELS }

        val startIndex = modelsPool.indexOf(selectedModel.trim()).let { if (it >= 0) it else 0 }
        val orderedCandidates = buildList {
            for (offset in modelsPool.indices) {
                add(modelsPool[(startIndex + offset) % modelsPool.size])
            }
            AppSettings.DEFAULT_OLLAMA_MODELS.forEach { fallback ->
                if (fallback !in this) add(fallback)
            }
        }

        var lastError: Throwable? = null
        for ((attemptIndex, modelName) in orderedCandidates.withIndex()) {
            val attempt = runCatching {
                callSingleModel(
                    bearerToken = cleanToken,
                    modelName = modelName,
                    prompt = prompt
                )
            }
            if (attempt.isSuccess) {
                val text = GeminiService.stripThinkingBlocks(attempt.getOrThrow()).trim()
                if (text.isNotEmpty()) {
                    if (attemptIndex > 0 && modelName != selectedModel) {
                        onModelSwitched?.invoke(modelName)
                    }
                    return@withContext Result.success(text to modelName)
                } else {
                    lastError = IllegalStateException("Ollama model $modelName returned an empty response")
                }
            } else {
                val err = attempt.exceptionOrNull()
                lastError = err
                if (err is OllamaUncaughtAuthException) {
                    return@withContext Result.failure(err)
                }
            }
        }

        Result.failure(lastError ?: IllegalStateException("All Ollama models failed or hit quota limits."))
    }

    private fun callSingleModel(
        bearerToken: String,
        modelName: String,
        prompt: String
    ): String {
        val cleanModel = modelName.trim()
        val payload = OllamaChatRequest(
            model = cleanModel,
            messages = listOf(
                OllamaChatMessage(
                    role = "user",
                    content = prompt
                )
            ),
            stream = false
        )
        val bodyStr = NetworkClient.json.encodeToString(OllamaChatRequest.serializer(), payload)
        val req = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $bearerToken")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(bodyStr.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { response ->
            val rawResp = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw OllamaUncaughtAuthException(
                    "Ollama authorization failed (HTTP ${response.code}). Please verify your Ollama API key in Settings."
                )
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("Ollama model $cleanModel HTTP ${response.code}: ${rawResp.take(180)}")
            }
            val parsed = NetworkClient.json.decodeFromString(OllamaChatResponse.serializer(), rawResp)
            return parsed.message?.content
                ?.takeIf { it.isNotBlank() }
                ?: parsed.choices?.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: parsed.response.orEmpty()
        }
    }

    /**
     * Summarizes an article using Ollama with the EXACT same prompt and single-paragraph output rules as Gemini.
     */
    suspend fun summarizeArticle(
        apiKey: String,
        articleTitle: String,
        articleBody: String,
        targetLanguage: TargetLanguage,
        availableModels: List<String> = AppSettings.DEFAULT_OLLAMA_MODELS,
        selectedModel: String = AppSettings.DEFAULT_OLLAMA_MODELS.first(),
        customPrompt: String? = null,
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<AiArticleOutput> {
        val exactSamePrompt = GeminiService.buildArticleAiPrompt(
            articleTitle = articleTitle,
            articleBody = articleBody,
            mode = ReaderAiMode.SUMMARIZE,
            targetLanguage = targetLanguage,
            customPrompt = customPrompt
        )

        return generateWithFailover(
            apiKey = apiKey,
            prompt = exactSamePrompt,
            availableModels = availableModels,
            selectedModel = selectedModel,
            onModelSwitched = onModelSwitched
        ).map { (rawOutput, usedModel) ->
            GeminiService.parseCleanArticleOutput(
                rawOutput = rawOutput,
                originalTitle = articleTitle,
                usedModel = usedModel,
                singleParagraph = customPrompt.isNullOrBlank()
            )
        }
    }

    /**
     * Translates a full article using Ollama with the exact same article translation prompt as Gemini.
     */
    suspend fun translateArticle(
        apiKey: String,
        articleTitle: String,
        articleBody: String,
        targetLanguage: TargetLanguage,
        availableModels: List<String> = AppSettings.DEFAULT_OLLAMA_MODELS,
        selectedModel: String = AppSettings.DEFAULT_OLLAMA_MODELS.first(),
        customPrompt: String? = null,
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<AiArticleOutput> {
        val exactSamePrompt = GeminiService.buildArticleAiPrompt(
            articleTitle = articleTitle,
            articleBody = articleBody,
            mode = ReaderAiMode.TRANSLATE,
            targetLanguage = targetLanguage,
            customPrompt = customPrompt
        )

        return generateWithFailover(
            apiKey = apiKey,
            prompt = exactSamePrompt,
            availableModels = availableModels,
            selectedModel = selectedModel,
            onModelSwitched = onModelSwitched
        ).map { (rawOutput, usedModel) ->
            GeminiService.parseCleanArticleOutput(
                rawOutput = rawOutput,
                originalTitle = articleTitle,
                usedModel = usedModel,
                singleParagraph = false
            )
        }
    }

    /**
     * Translates a single feed card headline cleanly using Ollama.
     */
    suspend fun translateHeadline(
        apiKey: String,
        text: String,
        targetLanguage: TargetLanguage,
        availableModels: List<String> = AppSettings.DEFAULT_OLLAMA_MODELS,
        selectedModel: String = AppSettings.DEFAULT_OLLAMA_MODELS.first(),
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return Result.success(text)
        val prompt = GeminiService.buildHeadlineTranslatePrompt(clean, targetLanguage)
        return generateWithFailover(
            apiKey = apiKey,
            prompt = prompt,
            availableModels = availableModels,
            selectedModel = selectedModel,
            onModelSwitched = onModelSwitched
        ).mapCatching { (rawOutput, _) ->
            GeminiService.cleanHeadlineOutput(rawOutput).ifBlank {
                throw IllegalStateException("Ollama returned an empty headline translation")
            }
        }
    }

    /**
     * Drafts a Telegram channel summary post using Ollama with the exact same prompt as Gemini.
     */
    suspend fun draftTelegramPost(
        apiKey: String,
        articleTitle: String,
        articleBody: String,
        availableModels: List<String> = AppSettings.DEFAULT_OLLAMA_MODELS,
        selectedModel: String = AppSettings.DEFAULT_OLLAMA_MODELS.first(),
        onModelSwitched: (suspend (String) -> Unit)? = null
    ): Result<String> {
        val exactSamePrompt = GeminiService.buildTelegramDraftPrompt(
            articleTitle = articleTitle,
            articleBody = articleBody
        )

        return generateWithFailover(
            apiKey = apiKey,
            prompt = exactSamePrompt,
            availableModels = availableModels,
            selectedModel = selectedModel,
            onModelSwitched = onModelSwitched
        ).map { (text, _) ->
            GeminiService.cleanTelegramDraft(text)
        }
    }

    private fun normalizeBearerToken(rawKey: String): String {
        val trimmed = rawKey.trim()
        return if (trimmed.startsWith("Bearer ", ignoreCase = true)) {
            trimmed.substring(7).trim()
        } else {
            trimmed
        }
    }
}
