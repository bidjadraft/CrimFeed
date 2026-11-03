package com.example.data.remote

import com.example.data.model.ArticleTranslationEngine
import com.example.data.model.TargetLanguage
import com.example.data.model.TitleTranslationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import kotlin.random.Random

/**
 * Supports:
 * 1) External post headline translation via DeepL Web, DeepL Official API, Google Web, and Bing Web.
 * 2) Full multi-paragraph article translation in Reader via any selected TitleTranslationProvider.
 * 3) Official DeepL API key verification via `/v2/usage`.
 */
class HeadlineTranslationService(
    private val aiClient: OkHttpClient = NetworkClient.aiHttpClient,
    private val webClient: OkHttpClient = NetworkClient.rssHttpClient
) {

    suspend fun translateHeadline(
        text: String,
        targetLanguage: TargetLanguage,
        provider: TitleTranslationProvider,
        deeplApiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val clean = text.trim()
        if (clean.isEmpty()) return@withContext Result.success(text)

        val fallbackPlan = when (provider) {
            TitleTranslationProvider.DEEPL_WEB -> listOfNotNull(
                TitleTranslationProvider.DEEPL_WEB,
                TitleTranslationProvider.YANDEX_WEB,
                if (deeplApiKey.isNotBlank()) TitleTranslationProvider.DEEPL_API else null
            )
            TitleTranslationProvider.YANDEX_WEB -> listOfNotNull(
                TitleTranslationProvider.YANDEX_WEB,
                TitleTranslationProvider.DEEPL_WEB,
                if (deeplApiKey.isNotBlank()) TitleTranslationProvider.DEEPL_API else null
            )
            TitleTranslationProvider.DEEPL_API -> listOfNotNull(
                TitleTranslationProvider.DEEPL_API,
                TitleTranslationProvider.DEEPL_WEB,
                TitleTranslationProvider.YANDEX_WEB
            )
            TitleTranslationProvider.GEMINI,
            TitleTranslationProvider.OLLAMA -> throw IllegalArgumentException("Use AI service for ${provider.label}")
        }

        var lastError: Throwable? = null
        for (current in fallbackPlan) {
            val attempt = runCatching {
                when (current) {
                    TitleTranslationProvider.YANDEX_WEB -> translateViaYandexWeb(clean, targetLanguage.isoCode)
                    TitleTranslationProvider.DEEPL_WEB -> translateViaDeeplWebParagraphs(listOf(clean), targetLanguage.deeplCode).first()
                    TitleTranslationProvider.DEEPL_API -> {
                        if (deeplApiKey.isBlank()) throw IllegalStateException("DeepL API key required")
                        translateViaDeeplOfficialApiParagraphs(listOf(clean), targetLanguage.deeplCode, deeplApiKey).first()
                    }
                    else -> throw IllegalStateException("Unsupported provider: ${current.label}")
                }
            }
            if (attempt.isSuccess && !attempt.getOrNull().isNullOrBlank()) {
                return@withContext Result.success(attempt.getOrThrow())
            }
            lastError = attempt.exceptionOrNull()
        }

        // Final resilient web fallback via Google Web
        val googleAttempt = runCatching {
            translateViaGoogleWebParagraphs(listOf(clean), targetLanguage.isoCode).first()
        }
        if (googleAttempt.isSuccess && !googleAttempt.getOrNull().isNullOrBlank()) {
            return@withContext Result.success(googleAttempt.getOrThrow())
        }

        Result.failure(lastError ?: googleAttempt.exceptionOrNull() ?: IllegalStateException("All translation providers failed"))
    }

    /**
     * Translates a full multi-paragraph article body cleanly using DeepL API or DeepL Web,
     * with automated failover to alternate web providers (Yandex Web, DeepL Web) if web requests hit rate-limits (HTTP 419, etc.).
     */
    suspend fun translateArticleViaDeepl(
        articleBody: String,
        targetLanguage: TargetLanguage,
        engine: ArticleTranslationEngine,
        deeplApiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val targetProvider = when (engine) {
            ArticleTranslationEngine.DEEPL_API -> TitleTranslationProvider.DEEPL_API
            ArticleTranslationEngine.DEEPL_WEB -> TitleTranslationProvider.DEEPL_WEB
            ArticleTranslationEngine.GEMINI -> throw IllegalArgumentException("Use GeminiService for GEMINI engine")
        }
        translateFullArticle(articleBody, targetLanguage, targetProvider, deeplApiKey)
    }

    /**
     * Translates a full multi-paragraph article body cleanly using whichever Translator is selected
     * (`DeepL Web`, `Yandex Web`, or `DeepL API`), automatically falling back across
     * alternate web providers if an HTTP 419, rate limit, or network error occurs.
     */
    suspend fun translateFullArticle(
        articleBody: String,
        targetLanguage: TargetLanguage,
        provider: TitleTranslationProvider,
        deeplApiKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val paragraphs = articleBody
            .take(24_000)
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (paragraphs.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Article content is empty"))
        }

        val fallbackPlan = when (provider) {
            TitleTranslationProvider.DEEPL_WEB -> listOfNotNull(
                TitleTranslationProvider.DEEPL_WEB,
                TitleTranslationProvider.YANDEX_WEB,
                if (deeplApiKey.isNotBlank()) TitleTranslationProvider.DEEPL_API else null
            )
            TitleTranslationProvider.YANDEX_WEB -> listOfNotNull(
                TitleTranslationProvider.YANDEX_WEB,
                TitleTranslationProvider.DEEPL_WEB,
                if (deeplApiKey.isNotBlank()) TitleTranslationProvider.DEEPL_API else null
            )
            TitleTranslationProvider.DEEPL_API -> listOfNotNull(
                TitleTranslationProvider.DEEPL_API,
                TitleTranslationProvider.DEEPL_WEB,
                TitleTranslationProvider.YANDEX_WEB
            )
            TitleTranslationProvider.GEMINI,
            TitleTranslationProvider.OLLAMA -> throw IllegalArgumentException("Use AI service for ${provider.label}")
        }

        var lastError: Throwable? = null
        for (current in fallbackPlan) {
            val attempt = runCatching {
                val translatedParagraphs: List<String> = when (current) {
                    TitleTranslationProvider.DEEPL_API -> {
                        if (deeplApiKey.isBlank()) throw IllegalStateException("DeepL API key required")
                        paragraphs.chunked(35).flatMap { chunk ->
                            translateViaDeeplOfficialApiParagraphs(chunk, targetLanguage.deeplCode, deeplApiKey)
                        }
                    }
                    TitleTranslationProvider.DEEPL_WEB -> {
                        paragraphs.chunked(12).flatMap { chunk ->
                            translateViaDeeplWebParagraphs(chunk, targetLanguage.deeplCode)
                        }
                    }
                    TitleTranslationProvider.YANDEX_WEB -> {
                        paragraphs.chunked(12).flatMap { chunk ->
                            translateViaYandexWebParagraphs(chunk, targetLanguage.isoCode)
                        }
                    }
                    TitleTranslationProvider.GEMINI,
                    TitleTranslationProvider.OLLAMA -> {
                        throw IllegalArgumentException("Use AI service for ${current.label}")
                    }
                }
                translatedParagraphs
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n\n")
                    .ifBlank { throw IllegalStateException("${current.label} returned empty article translation") }
            }
            if (attempt.isSuccess && !attempt.getOrNull().isNullOrBlank()) {
                return@withContext Result.success(attempt.getOrThrow())
            }
            lastError = attempt.exceptionOrNull()
        }

        // Resilient fallback via Google Web
        val googleAttempt = runCatching {
            paragraphs.chunked(10).flatMap { chunk ->
                translateViaGoogleWebParagraphs(chunk, targetLanguage.isoCode)
            }.joinToString("\n\n")
        }
        if (googleAttempt.isSuccess && !googleAttempt.getOrNull().isNullOrBlank()) {
            return@withContext Result.success(googleAttempt.getOrThrow())
        }

        Result.failure(lastError ?: googleAttempt.exceptionOrNull() ?: IllegalStateException("All translation providers failed"))
    }

    /**
     * Verifies a DeepL API key via `/v2/usage`.
     */
    suspend fun verifyDeeplApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Please enter a valid DeepL API key")
            )
        }
        val baseHost = if (cleanKey.endsWith(":fx")) {
            "https://api-free.deepl.com"
        } else {
            "https://api.deepl.com"
        }
        runCatching {
            val req = Request.Builder()
                .url("$baseHost/v2/usage")
                .header("Authorization", "DeepL-Auth-Key $cleanKey")
                .header("Accept", "application/json")
                .get()
                .build()

            aiClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Invalid DeepL API key (HTTP ${resp.code}). Please enter a valid key.")
                }
                val raw = resp.body?.string().orEmpty()
                val root = NetworkClient.json.parseToJsonElement(raw).jsonObject
                val used = root["character_count"]?.jsonPrimitive?.contentOrNull ?: "0"
                val limit = root["character_limit"]?.jsonPrimitive?.contentOrNull ?: "∞"
                "Valid DeepL key ($used / $limit chars)"
            }
        }
    }

    private fun translateViaYandexWeb(text: String, targetIso: String): String {
        return translateViaYandexWebParagraphs(listOf(text), targetIso).first()
    }

    private fun translateViaYandexWebParagraphs(paragraphs: List<String>, targetIso: String): List<String> {
        val yandexLang = when (targetIso) {
            "zh" -> "zh"
            else -> targetIso
        }
        val ucid = java.util.UUID.randomUUID().toString().replace("-", "")
        val formBuilder = FormBody.Builder()
            .add("lang", yandexLang)
        paragraphs.forEach { para ->
            formBuilder.add("text", para)
        }

        val req = Request.Builder()
            .url("https://translate.yandex.net/api/v1/tr.json/translate?srv=android&format=text&sid=$ucid-0-0")
            .header("User-Agent", "ru.yandex.translate/22.11.8.22364114 (samsung SM-A505GM; Android 12)")
            .post(formBuilder.build())
            .build()

        webClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("Yandex Web Translate HTTP ${resp.code}: ${raw.take(120)}")
            }
            val root = NetworkClient.json.parseToJsonElement(raw).jsonObject
            val arr = root["text"]?.jsonArray
                ?: throw IllegalStateException("Empty Yandex Web translation")
            val results = arr.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }.filter { it.isNotEmpty() }
            if (results.isEmpty()) throw IllegalStateException("No text returned by Yandex Web")
            return results
        }
    }

    private fun mapDeeplTargetLang(code: String): String {
        return when (code.trim().uppercase()) {
            "AR" -> "ar"
            "EN", "EN-US" -> "en-US"
            "EN-GB" -> "en-GB"
            "ZH", "ZH-HANS" -> "zh-Hans"
            "ZH-HANT" -> "zh-Hant"
            "PT", "PT-BR" -> "pt-BR"
            "PT-PT" -> "pt-PT"
            else -> code.trim().lowercase()
        }
    }

    private fun translateViaDeeplWebParagraphs(paragraphs: List<String>, deeplTargetLang: String): List<String> {
        val nonBlankParas = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        if (nonBlankParas.isEmpty()) return emptyList()

        // 1. Primary Modern Path: DeepL oneshot-free endpoint
        val oneshotResult = runCatching {
            val targetCode = mapDeeplTargetLang(deeplTargetLang)
            val instanceId = java.util.UUID.randomUUID().toString()
            val sessionId = java.util.UUID.randomUUID().toString()

            val bodyObj = buildJsonObject {
                putJsonArray("text") {
                    nonBlankParas.forEach { add(JsonPrimitive(it)) }
                }
                put("target_lang", targetCode)
                put("usage_type", "translate")
                putJsonObject("app_information") {
                    put("os", "iOS")
                    put("os_version", "26.0")
                    put("app_version", "26.52")
                    put("app_build", "5443737")
                    put("instance_id", instanceId)
                }
            }

            val req = Request.Builder()
                .url("https://oneshot-free.www.deepl.com/v1/translate")
                .header("Content-Type", "application/json")
                .header("Authorization", "None")
                .header("User-Agent", "DeepL/5443737 CFNetwork/3826.600.41 Darwin/25.0.0")
                .header("x-app-os-version", "26.0")
                .header("x-app-instance-id", instanceId)
                .header("x-app-session-id", sessionId)
                .post(bodyObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            aiClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("DeepL oneshot HTTP ${resp.code}: ${body.take(120)}")
                }
                val root = NetworkClient.json.parseToJsonElement(body).jsonObject
                val transArr = root["translations"]?.jsonArray
                    ?: throw IllegalStateException("Empty translations in oneshot")
                val results = transArr.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.trim() }
                    .filter { it.isNotEmpty() }
                if (results.isEmpty()) throw IllegalStateException("No text returned by DeepL oneshot")
                results
            }
        }
        if (oneshotResult.isSuccess && oneshotResult.getOrNull()?.isNotEmpty() == true) {
            return oneshotResult.getOrThrow()
        }

        // 2. Fallback Path: JSON-RPC
        val id = Random.nextLong(10_000_000L, 99_999_999L) * 1000L
        val totalICount = nonBlankParas.sumOf { p -> p.count { it == 'i' || it == 'I' } }
        val nowMs = System.currentTimeMillis()
        val adjustedTimestamp = if (totalICount > 0) {
            val countPlusOne = totalICount + 1
            nowMs - (nowMs % countPlusOne) + countPlusOne
        } else {
            nowMs
        }

        val rpcObject = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "LMT_handle_texts")
            put("id", id)
            putJsonObject("params") {
                putJsonArray("texts") {
                    nonBlankParas.forEach { para ->
                        add(buildJsonObject {
                            put("text", para)
                            put("requestAlternatives", 1)
                        })
                    }
                }
                put("splitting", "newlines")
                putJsonObject("lang") {
                    put("source_lang_user_selected", "auto")
                    put("target_lang", deeplTargetLang.uppercase())
                }
                put("timestamp", adjustedTimestamp)
                putJsonObject("commonJobParams") {
                    put("wasSpoken", false)
                    put("transcribe_as", "")
                }
            }
        }

        val rawJson = rpcObject.toString().let { serialized ->
            if ((id + 5) % 29 == 0L || (id + 3) % 13 == 0L) {
                serialized.replace("\"method\":\"", "\"method\" : \"")
            } else {
                serialized.replace("\"method\":\"", "\"method\": \"")
            }
        }

        val req = Request.Builder()
            .url("https://www2.deepl.com/jsonrpc?method=LMT_handle_texts")
            .header("Content-Type", "application/json")
            .header("Accept", "*/*")
            .header("Origin", "https://www.deepl.com")
            .header("Referer", "https://www.deepl.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36")
            .post(rawJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        aiClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("DeepL Web HTTP ${resp.code}: ${body.take(120)}")
            }
            val root = NetworkClient.json.parseToJsonElement(body).jsonObject
            val resObj = root["result"]?.jsonObject

            val isOutdatedNotice: (String) -> Boolean = { s ->
                s.contains("older version of DeepL", ignoreCase = true) ||
                s.contains("update to the latest version", ignoreCase = true)
            }

            val textsArr = resObj?.get("texts")?.jsonArray
            if (textsArr != null) {
                val output = textsArr.mapNotNull { textElem ->
                    textElem.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.trim()
                }.filter { it.isNotEmpty() }
                if (output.any(isOutdatedNotice)) {
                    throw IllegalStateException("DeepL Web client deprecated; falling back to alternative provider")
                }
                if (output.isNotEmpty()) return output
            }

            val translationsArr = resObj?.get("translations")?.jsonArray
            if (translationsArr != null) {
                val output = translationsArr.map { transElem ->
                    transElem.jsonObject["beams"]?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("sentences")?.jsonArray
                        ?.joinToString("") {
                            (it.jsonObject["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                        }
                        ?.trim()
                        .orEmpty()
                }.filter { it.isNotEmpty() }
                if (output.any(isOutdatedNotice)) {
                    throw IllegalStateException("DeepL Web client deprecated; falling back to alternative provider")
                }
                if (output.isNotEmpty()) return output
            }

            val errMsg = root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            if (errMsg != null && isOutdatedNotice(errMsg)) {
                throw IllegalStateException("DeepL Web outdated notice; falling back to alternative provider")
            }
            throw IllegalStateException(errMsg ?: "DeepL Web returned empty translation")
        }
    }

    private fun translateViaGoogleWebParagraphs(paragraphs: List<String>, targetIso: String): List<String> {
        return paragraphs.map { para ->
            val clean = para.trim()
            if (clean.isEmpty()) return@map ""
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetIso&dt=t&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkClient.BROWSER_USER_AGENT)
                .get()
                .build()
            webClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw IllegalStateException("Google Web HTTP ${resp.code}")
                val rootArr = NetworkClient.json.parseToJsonElement(raw).jsonArray
                val sentences = rootArr.firstOrNull()?.jsonArray ?: throw IllegalStateException("Empty Google response")
                sentences.mapNotNull { it.jsonArray.firstOrNull()?.jsonPrimitive?.contentOrNull }.joinToString("").trim()
            }
        }.filter { it.isNotEmpty() }
    }

    private fun translateViaDeeplOfficialApiParagraphs(
        paragraphs: List<String>,
        deeplTargetLang: String,
        apiKey: String
    ): List<String> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            throw IllegalStateException("DeepL API key is missing. Add DEEPL_API_KEY in Settings or AI Studio Secrets.")
        }
        val baseHost = if (cleanKey.endsWith(":fx")) {
            "https://api-free.deepl.com"
        } else {
            "https://api.deepl.com"
        }

        val formBuilder = FormBody.Builder()
            .add("target_lang", deeplTargetLang)
        paragraphs.forEach { para ->
            formBuilder.add("text", para)
        }

        val req = Request.Builder()
            .url("$baseHost/v2/translate")
            .header("Authorization", "DeepL-Auth-Key $cleanKey")
            .post(formBuilder.build())
            .build()

        aiClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("DeepL API HTTP ${resp.code}: ${raw.take(140)}")
            }
            val root = NetworkClient.json.parseToJsonElement(raw).jsonObject
            val arr = root["translations"]?.jsonArray
                ?: throw IllegalStateException("DeepL API returned no translations")
            val results = arr.mapNotNull { elem ->
                elem.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.trim()
            }.filter { it.isNotEmpty() }
            if (results.isEmpty()) throw IllegalStateException("DeepL API returned empty translation")
            return results
        }
    }
}
