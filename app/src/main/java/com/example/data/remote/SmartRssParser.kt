package com.example.data.remote

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class ParsedRssItem(
    val guid: String,
    val link: String,
    val title: String,
    val content: String,
    val imageUrl: String?,
    val publishedAt: Long,
    val hasExplicitDate: Boolean = false
)

data class ParsedRssFeed(
    val feedUrl: String,
    val siteUrl: String,
    val title: String,
    val iconUrl: String,
    val items: List<ParsedRssItem>,
    val rawFallbackBody: String = "",
    val httpEtag: String = "",
    val httpLastModified: String = "",
    val bodyDigest: String = ""
)

sealed interface FeedRefreshOutcome {
    val httpEtag: String
    val httpLastModified: String
    val bodyDigest: String

    data class NotModified(
        override val httpEtag: String,
        override val httpLastModified: String,
        override val bodyDigest: String
    ) : FeedRefreshOutcome

    data class Updated(
        val parsedFeed: ParsedRssFeed,
        override val httpEtag: String,
        override val httpLastModified: String,
        override val bodyDigest: String
    ) : FeedRefreshOutcome
}

data class FetchedArticlePage(
    val readableBody: String?,
    val metaImageUrl: String?
)

class SmartRssParser(
    private val httpClient: OkHttpClient = NetworkClient.rssHttpClient,
    private val ogClient: OkHttpClient? = null
) {
    private val perHostSemaphores = java.util.concurrent.ConcurrentHashMap<String, Semaphore>()
    private val gentleHostSemaphores = java.util.concurrent.ConcurrentHashMap<String, Semaphore>()
    private val inFlightArticlePages = java.util.concurrent.ConcurrentHashMap<String, CompletableDeferred<FetchedArticlePage>>()

    private fun hostGateFor(url: String, gentleMode: Boolean): Semaphore {
        val hostKey = extractHost(url).lowercase(Locale.ROOT).ifBlank { "default" }
        return if (gentleMode) {
            gentleHostSemaphores.computeIfAbsent(hostKey) { Semaphore(1) }
        } else {
            perHostSemaphores.computeIfAbsent(hostKey) { Semaphore(2) }
        }
    }

    /**
     * Reliable live RSS update check for every registered feed:
     * 1. Enforces per-host concurrency pacing (`Semaphore(2)` in primary wave, `Semaphore(1)` in recovery wave)
     *    so multiple feeds on the same host (YouTube, Reddit, RSSHub, WordPress, Cloudflare, news portals)
     *    never trigger burst HTTP 429 / 403 / 503 blocks.
     * 2. Always fetches the live gzipped RSS/Atom/JSON feed with `Cache-Control: no-cache, max-age=0`
     *    (never sending `If-None-Match` or `If-Modified-Since`, eliminating false 304 Not Modified responses
     *    from timezone-skewed `Last-Modified` headers, static ETags, and CDN `stale-while-revalidate` caches).
     * 3. Retries up to 3 times with backoff and User-Agent rotation on transient network / 403 / 429 / 5xx errors.
     * 4. Parses ONLY the feed XML/JSON in memory — NEVER downloads images or article HTML pages here!
     */
    suspend fun checkFeedForUpdates(
        feedUrl: String,
        previousEtag: String = "",
        previousLastModified: String = "",
        previousBodyDigest: String = "",
        gentleMode: Boolean = false
    ): Result<FeedRefreshOutcome> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeInputUrl(feedUrl)
        val hostSemaphore = hostGateFor(normalizedUrl, gentleMode)
        var lastError: Throwable? = null
        var retryDelayMs = if (gentleMode) 900L else 650L

        for (attempt in 0..2) {
            if (attempt > 0) {
                delay(retryDelayMs)
            }
            val attemptResult = hostSemaphore.withPermit {
                runCatching {
                    val userAgent = if (attempt == 1) {
                        NetworkClient.RSS_READER_USER_AGENT
                    } else {
                        NetworkClient.BROWSER_USER_AGENT
                    }
                    val req = Request.Builder()
                        .url(normalizedUrl)
                        .header("Cache-Control", "no-cache, max-age=0")
                        .header("Pragma", "no-cache")
                        .header("User-Agent", userAgent)
                        .get()
                        .build()

                    httpClient.newCall(req).execute().use { response ->
                        val respEtag = response.header("ETag")?.trim()?.takeIf { it.isNotEmpty() } ?: previousEtag
                        val respLastMod = response.header("Last-Modified")?.trim()?.takeIf { it.isNotEmpty() } ?: previousLastModified

                        if (response.code == 304) {
                            // Even if an upstream proxy forces 304 despite unconditional GET, retry once with alternate UA
                            if (attempt == 0) {
                                throw HttpFeedStatusException(503, "Unexpected 304 without conditional request for $normalizedUrl")
                            }
                            return@use FeedRefreshOutcome.NotModified(
                                httpEtag = respEtag,
                                httpLastModified = respLastMod,
                                bodyDigest = previousBodyDigest
                            )
                        }

                        if (!response.isSuccessful) {
                            val retryAfterSec = response.header("Retry-After")?.trim()?.toLongOrNull()
                            throw HttpFeedStatusException(
                                code = response.code,
                                message = "HTTP ${response.code} for $normalizedUrl",
                                retryAfterMs = retryAfterSec?.coerceIn(1L, 4L)?.times(1000L)
                            )
                        }

                        val finalUrl = response.request.url.toString()
                        val rawBody = readCappedText(response, maxChars = 2_500_000)
                        val digest = computeSha256Hex(rawBody)

                        val parsed = tryParseFeedDocument(finalUrl, rawBody)
                        if (parsed != null && parsed.items.isNotEmpty()) {
                            val resolvedIcon = resolveFeedIcon(
                                existingIcon = parsed.iconUrl,
                                siteOrFeedUrl = parsed.siteUrl.ifBlank { finalUrl }
                            )
                            return@use FeedRefreshOutcome.Updated(
                                parsedFeed = parsed.copy(
                                    iconUrl = resolvedIcon,
                                    httpEtag = respEtag,
                                    httpLastModified = respLastMod,
                                    bodyDigest = digest
                                ),
                                httpEtag = respEtag,
                                httpLastModified = respLastMod,
                                bodyDigest = digest
                            )
                        }

                        // Auto-recover if feed URL redirected to or is an HTML page with <link rel="alternate"> or standard RSS paths
                        val alternateUrls = discoverCandidateFeedUrls(finalUrl, rawBody)
                        for (altUrl in alternateUrls) {
                            val altFetch = runCatching { downloadText(altUrl, userAgent = userAgent) }.getOrNull() ?: continue
                            val altParsed = tryParseFeedDocument(altFetch.finalUrl, altFetch.body)
                            if (altParsed != null && altParsed.items.isNotEmpty()) {
                                val altDigest = computeSha256Hex(altFetch.body)
                                val resolvedIcon = resolveFeedIcon(
                                    existingIcon = altParsed.iconUrl,
                                    siteOrFeedUrl = altParsed.siteUrl.ifBlank { altFetch.finalUrl },
                                    htmlSnippet = rawBody
                                )
                                return@use FeedRefreshOutcome.Updated(
                                    parsedFeed = altParsed.copy(
                                        iconUrl = resolvedIcon,
                                        httpEtag = altFetch.etag,
                                        httpLastModified = altFetch.lastModified,
                                        bodyDigest = altDigest
                                    ),
                                    httpEtag = altFetch.etag,
                                    httpLastModified = altFetch.lastModified,
                                    bodyDigest = altDigest
                                )
                            }
                        }

                        // If an anti-bot / CDN challenge returned transient HTML with 0 items on attempt 0, retry with alternate UA
                        if (attempt == 0 && rawBody.isNotBlank() && isLikelyTransientChallengeHtml(rawBody)) {
                            throw HttpFeedStatusException(503, "Transient challenge response for $normalizedUrl")
                        }

                        // Preserve rawFallbackBody so RssRepository can optionally invoke Gemini discovery on non-standard HTML feeds
                        FeedRefreshOutcome.Updated(
                            parsedFeed = ParsedRssFeed(
                                feedUrl = finalUrl,
                                siteUrl = extractOriginUrl(finalUrl),
                                title = extractHtmlTitle(rawBody).ifBlank { extractHost(finalUrl) },
                                iconUrl = resolveFeedIcon("", finalUrl, rawBody),
                                items = emptyList(),
                                rawFallbackBody = rawBody,
                                httpEtag = "",
                                httpLastModified = "",
                                bodyDigest = digest
                            ),
                            httpEtag = "",
                            httpLastModified = "",
                            bodyDigest = ""
                        )
                    }
                }
            }

            if (attemptResult.isSuccess) {
                return@withContext attemptResult
            }
            val err = attemptResult.exceptionOrNull()
            lastError = err
            if (isPermanentFeedError(err)) {
                return@withContext attemptResult
            }
            val httpErr = err as? HttpFeedStatusException
            val isRetryableStatus = httpErr != null && (
                httpErr.code in setOf(403, 406, 408, 425, 429, 500, 502, 503, 504) ||
                    httpErr.code in 520..526
                )
            val isTransient = (err is IOException && httpErr == null) || isRetryableStatus
            if (!isTransient) {
                return@withContext attemptResult
            }
            retryDelayMs = httpErr?.retryAfterMs ?: if (attempt == 0) 700L else 1_400L
        }
        Result.failure(lastError ?: IllegalStateException("Feed refresh failed"))
    }

    fun isPermanentFeedError(err: Throwable?): Boolean {
        if (err == null) return false
        if (err is java.net.UnknownHostException) return true
        val httpErr = err as? HttpFeedStatusException
        return httpErr != null && httpErr.code in setOf(404, 410)
    }

    private fun isLikelyTransientChallengeHtml(body: String): Boolean {
        val sample = body.take(4096).lowercase(Locale.ROOT)
        return sample.contains("just a moment...") ||
            sample.contains("cf-browser-verification") ||
            sample.contains("checking your browser") ||
            sample.contains("too many requests") ||
            sample.contains("rate limit")
    }

    private class HttpFeedStatusException(
        val code: Int,
        message: String,
        val retryAfterMs: Long? = null
    ) : IOException(message)

    /**
     * Used ONLY when adding a brand-new feed URL (supports HTML `<link rel="alternate">` discovery
     * and Gemini fallback if the user pasted a homepage URL instead of an XML feed URL).
     */
    suspend fun fetchAndParseFeed(inputUrl: String): Result<ParsedRssFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = normalizeInputUrl(inputUrl)
            val primaryFetch = downloadText(normalizedUrl)
            val directParsed = tryParseFeedDocument(primaryFetch.finalUrl, primaryFetch.body)
            if (directParsed != null && directParsed.items.isNotEmpty()) {
                val icon = resolveFeedIcon(
                    directParsed.iconUrl,
                    directParsed.siteUrl.ifBlank { primaryFetch.finalUrl },
                    primaryFetch.body
                )
                val enrichedItems = enrichNewItemsWithFullArticlesAndImages(
                    items = directParsed.items,
                    maxFullFetches = 8,
                    maxHeadImageProbes = 5
                )
                return@runCatching directParsed.copy(
                    iconUrl = icon,
                    items = enrichedItems,
                    httpEtag = primaryFetch.etag,
                    httpLastModified = primaryFetch.lastModified,
                    bodyDigest = computeSha256Hex(primaryFetch.body)
                )
            }

            // Try HTML <link rel="alternate"> auto-discovery and common RSS endpoint paths
            val discoveredUrls = discoverCandidateFeedUrls(primaryFetch.finalUrl, primaryFetch.body)
            for (candidateUrl in discoveredUrls) {
                val candidateFetch = runCatching { downloadText(candidateUrl) }.getOrNull() ?: continue
                val parsed = tryParseFeedDocument(candidateFetch.finalUrl, candidateFetch.body)
                if (parsed != null && parsed.items.isNotEmpty()) {
                    val icon = resolveFeedIcon(
                        parsed.iconUrl,
                        parsed.siteUrl.ifBlank { candidateFetch.finalUrl },
                        primaryFetch.body
                    )
                    val enrichedItems = enrichNewItemsWithFullArticlesAndImages(
                        items = parsed.items,
                        maxFullFetches = 8,
                        maxHeadImageProbes = 5
                    )
                    return@runCatching parsed.copy(
                        iconUrl = icon,
                        items = enrichedItems,
                        httpEtag = candidateFetch.etag,
                        httpLastModified = candidateFetch.lastModified,
                        bodyDigest = computeSha256Hex(candidateFetch.body)
                    )
                }
            }

            // Return empty items with raw body so Repository can invoke Gemini Feed Discovery fallback!
            ParsedRssFeed(
                feedUrl = primaryFetch.finalUrl,
                siteUrl = extractOriginUrl(primaryFetch.finalUrl),
                title = extractHtmlTitle(primaryFetch.body).ifBlank { extractHost(primaryFetch.finalUrl) },
                iconUrl = resolveFeedIcon("", primaryFetch.finalUrl, primaryFetch.body),
                items = emptyList(),
                rawFallbackBody = primaryFetch.body,
                httpEtag = primaryFetch.etag,
                httpLastModified = primaryFetch.lastModified,
                bodyDigest = computeSha256Hex(primaryFetch.body)
            )
        }
    }

    /**
     * Probes `<head>` OpenGraph / Twitter image metadata STRICTLY for genuinely new items
     * that did not already include an image in the RSS/Atom XML payload.
     * - If all new items already have images (or there are 0 new items), performs ZERO network calls.
     * - Aborts connection immediately via `call.cancel()` right after `</head>` or 24 KB.
     */
    suspend fun enrichMissingImagesForNewItemsOnly(
        items: List<ParsedRssItem>,
        maxProbes: Int = 4
    ): List<ParsedRssItem> = withContext(Dispatchers.IO) {
        if (items.isEmpty() || maxProbes <= 0) return@withContext items
        val needsProbeIndices = items.indices
            .filter { idx -> items[idx].imageUrl.isNullOrBlank() && items[idx].link.startsWith("http") }
            .take(maxProbes)
            .toHashSet()

        if (needsProbeIndices.isEmpty()) return@withContext items

        coroutineScope {
            val gate = Semaphore(3)
            items.mapIndexed { idx, item ->
                if (idx in needsProbeIndices) {
                    async {
                        gate.withPermit {
                            val ogImg = fetchOpenGraphImageHeadOnly(item.link)
                            if (!ogImg.isNullOrBlank()) item.copy(imageUrl = ogImg) else item
                        }
                    }
                } else {
                    async { item }
                }
            }.awaitAll()
        }
    }

    /**
     * Concurrently prefetches full readable article bodies (plus missing hero images in the same HTTP pass)
     * for new items whose RSS snippet is short (`< 480` chars), and probes `<head>` OpenGraph images
     * for remaining imageless items that already had full RSS text.
     */
    suspend fun enrichNewItemsWithFullArticlesAndImages(
        items: List<ParsedRssItem>,
        maxFullFetches: Int = 10,
        maxHeadImageProbes: Int = 4
    ): List<ParsedRssItem> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext items

        val fullFetchIndices = items.indices
            .filter { idx -> items[idx].content.length < 480 && items[idx].link.startsWith("http") }
            .take(maxFullFetches)
            .toHashSet()

        val headProbeIndices = items.indices
            .filter { idx ->
                idx !in fullFetchIndices &&
                    items[idx].imageUrl.isNullOrBlank() &&
                    items[idx].link.startsWith("http")
            }
            .take(maxHeadImageProbes)
            .toHashSet()

        if (fullFetchIndices.isEmpty() && headProbeIndices.isEmpty()) return@withContext items

        coroutineScope {
            val gate = Semaphore(6)
            items.mapIndexed { idx, item ->
                when {
                    idx in fullFetchIndices -> async {
                        gate.withPermit {
                            val page = withTimeoutOrNull(4_500L) {
                                fetchFullArticlePage(item.link)
                            }
                            val nextBody = page?.readableBody
                                ?.takeIf { it.length > item.content.length }
                                ?: item.content
                            val nextImg = if (item.imageUrl.isNullOrBlank() && !page?.metaImageUrl.isNullOrBlank()) {
                                page?.metaImageUrl
                            } else {
                                item.imageUrl
                            }
                            item.copy(content = nextBody, imageUrl = nextImg)
                        }
                    }
                    idx in headProbeIndices -> async {
                        gate.withPermit {
                            val ogImg = withTimeoutOrNull(3_500L) {
                                fetchOpenGraphImageHeadOnly(item.link)
                            }
                            if (!ogImg.isNullOrBlank()) item.copy(imageUrl = ogImg) else item
                        }
                    }
                    else -> async { item }
                }
            }.awaitAll()
        }
    }

    /**
     * Fetches an article webpage once (with in-flight deduplication per canonical URL) and extracts
     * both the clean readable full article body and any OpenGraph / hero image.
     */
    suspend fun fetchFullArticlePage(articleUrl: String): FetchedArticlePage = withContext(Dispatchers.IO) {
        val cleanUrl = articleUrl.trim()
        if (cleanUrl.isEmpty() || (!cleanUrl.startsWith("http://", true) && !cleanUrl.startsWith("https://", true))) {
            return@withContext FetchedArticlePage(null, null)
        }
        val key = canonicalizeUrl(cleanUrl).ifBlank { cleanUrl }
        val ownDeferred = CompletableDeferred<FetchedArticlePage>()
        val inFlight = inFlightArticlePages.putIfAbsent(key, ownDeferred)
        if (inFlight != null) {
            return@withContext inFlight.await()
        }
        try {
            val page = runCatching {
                val fetched = downloadText(cleanUrl, maxChars = 180_000)
                val body = extractReadableHtmlBody(fetched.body)
                    .take(80_000)
                    .takeIf { it.length > 220 }
                val metaImg = extractMetaImageFromHead(fetched.body, fetched.finalUrl)
                    ?: extractBestImageFromHtml(fetched.body, fetched.finalUrl)
                FetchedArticlePage(readableBody = body, metaImageUrl = metaImg)
            }.getOrDefault(FetchedArticlePage(null, null))
            ownDeferred.complete(page)
            page
        } finally {
            inFlightArticlePages.remove(key, ownDeferred)
        }
    }

    suspend fun fetchFullReadableArticle(articleUrl: String): String? =
        fetchFullArticlePage(articleUrl).readableBody

    private data class FetchedDoc(
        val finalUrl: String,
        val body: String,
        val etag: String = "",
        val lastModified: String = ""
    )

    private fun downloadText(
        url: String,
        maxChars: Int = 2_500_000,
        userAgent: String = NetworkClient.BROWSER_USER_AGENT
    ): FetchedDoc {
        val req = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache, max-age=0")
            .header("User-Agent", userAgent)
            .get()
            .build()
        httpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            val finalUrl = response.request.url.toString()
            val etag = response.header("ETag")?.trim().orEmpty()
            val lastMod = response.header("Last-Modified")?.trim().orEmpty()
            val text = readCappedText(response, maxChars = maxChars)
            return FetchedDoc(finalUrl, text, etag, lastMod)
        }
    }

    private fun readCappedText(response: okhttp3.Response, maxChars: Int): String {
        return response.body?.charStream()?.use { reader ->
            val buffer = CharArray(8192)
            val sb = StringBuilder()
            var read: Int
            while (reader.read(buffer).also { read = it } != -1 && sb.length < maxChars) {
                sb.append(buffer, 0, read)
            }
            sb.toString()
        }.orEmpty()
    }

    internal fun computeSha256Hex(input: String): String {
        if (input.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val hexChars = CharArray(digest.size * 2)
        val digits = "0123456789abcdef"
        for (i in digest.indices) {
            val v = digest[i].toInt() and 0xFF
            hexChars[i * 2] = digits[v ushr 4]
            hexChars[i * 2 + 1] = digits[v and 0x0F]
        }
        return String(hexChars)
    }

    internal fun tryParseFeedDocument(feedUrl: String, rawBody: String): ParsedRssFeed? {
        val trimmed = rawBody.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (trimmed.isEmpty()) return null

        // 1. JSON Feed 1.0 / 1.1 support
        if (trimmed.startsWith("{") && trimmed.contains("\"items\"")) {
            val jsonParsed = runCatching { parseJsonFeed(feedUrl, trimmed) }.getOrNull()
            if (jsonParsed != null && jsonParsed.items.isNotEmpty()) return jsonParsed
        }

        // 2. XML RSS 2.0 / RSS 1.0 (RDF) / Atom support
        val sanitizedXml = sanitizeXml(trimmed)
        val xmlParsed = runCatching { parseXmlFeed(feedUrl, sanitizedXml) }.getOrNull()
        if (xmlParsed != null && xmlParsed.items.isNotEmpty()) return xmlParsed

        // 3. Regex XML emergency fallback if strict XmlPullParser hit malformed entities mid-stream
        val regexParsed = runCatching { parseMalformedXmlViaRegex(feedUrl, sanitizedXml) }.getOrNull()
        if (regexParsed != null && regexParsed.items.isNotEmpty()) return regexParsed

        return null
    }

    private fun parseJsonFeed(feedUrl: String, jsonText: String): ParsedRssFeed? {
        val root = NetworkClient.json.parseToJsonElement(jsonText).jsonObject
        val feedTitle = root["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { extractHost(feedUrl) }
        val homePageUrl = root["home_page_url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { extractOriginUrl(feedUrl) }
        val icon = root["icon"]?.jsonPrimitive?.contentOrNull
            ?: root["favicon"]?.jsonPrimitive?.contentOrNull
            ?: ""

        val itemsArray: JsonArray = root["items"]?.jsonArray ?: return null
        val fallbackBaseMs = System.currentTimeMillis()
        val rawItems = itemsArray.mapIndexedNotNull { idx, elem ->
            val obj: JsonObject = elem.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val url = obj["url"]?.jsonPrimitive?.contentOrNull
                ?: obj["external_url"]?.jsonPrimitive?.contentOrNull
                ?: ""
            val rawTitle = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val contentHtml = obj["content_html"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val contentText = obj["content_text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val summary = obj["summary"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val directImage = obj["image"]?.jsonPrimitive?.contentOrNull
                ?: obj["banner_image"]?.jsonPrimitive?.contentOrNull
                ?: extractFirstAttachmentImage(obj)

            val bestHtmlOrText = contentHtml.ifBlank { contentText.ifBlank { summary } }
            val resolvedImg = resolveUrl(
                baseUrl = url.ifBlank { homePageUrl.ifBlank { feedUrl } },
                candidate = directImage ?: extractBestImageFromHtml(contentHtml, url.ifBlank { feedUrl })
            )
            val cleanBody = htmlToReadableText(bestHtmlOrText)
            val title = htmlToPlainSingleLine(rawTitle).ifBlank {
                cleanBody.lineSequence().firstOrNull()?.take(90).orEmpty()
            }
            if (title.isBlank() && cleanBody.isBlank()) return@mapIndexedNotNull null

            val pubDateStr = obj["date_published"]?.jsonPrimitive?.contentOrNull
                ?: obj["published"]?.jsonPrimitive?.contentOrNull
                ?: obj["pubDate"]?.jsonPrimitive?.contentOrNull
                ?: obj["created_at"]?.jsonPrimitive?.contentOrNull
            val modDateStr = obj["date_modified"]?.jsonPrimitive?.contentOrNull
                ?: obj["updated"]?.jsonPrimitive?.contentOrNull
            val explicitDate = parseExplicitDateMillis(pubDateStr) ?: parseExplicitDateMillis(modDateStr)
            ParsedRssItem(
                guid = id.ifBlank { url.ifBlank { title.hashCode().toString() } },
                link = resolveUrl(feedUrl, url) ?: url,
                title = title,
                content = cleanBody.ifBlank { title },
                imageUrl = resolvedImg,
                publishedAt = explicitDate ?: (fallbackBaseMs - idx * 1000L),
                hasExplicitDate = explicitDate != null
            )
        }
        val items = normalizeUndatedFeedItems(rawItems, null, fallbackBaseMs)
        return ParsedRssFeed(
            feedUrl = feedUrl,
            siteUrl = homePageUrl,
            title = htmlToPlainSingleLine(feedTitle),
            iconUrl = resolveUrl(feedUrl, icon).orEmpty(),
            items = items
        )
    }

    private fun extractFirstAttachmentImage(obj: JsonObject): String? {
        val attachments = obj["attachments"] as? JsonArray ?: return null
        for (att in attachments) {
            val attObj = att as? JsonObject ?: continue
            val mime = attObj["mime_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val attUrl = attObj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (mime.startsWith("image/") || looksLikeImageUrl(attUrl)) return attUrl
        }
        return null
    }

    private fun parseXmlFeed(feedUrl: String, xmlText: String): ParsedRssFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlText))

        var feedTitle = ""
        var siteUrl = ""
        var feedIcon = ""
        var channelDateStr = ""
        var insideChannelImage = false

        var insideItem = false
        var itemDepth = 0
        var itemGuid = ""
        var itemLink = ""
        var itemTitle = ""
        var itemDescription = ""
        var itemContentEncoded = ""
        var itemPubDate = ""
        var itemUpdatedDate = ""
        val itemDirectImages = mutableListOf<String>()

        val parsedItems = mutableListOf<ParsedRssItem>()
        val fallbackBaseMs = System.currentTimeMillis()

        fun consumeItemElement(preserveMarkup: Boolean = false): String {
            val content = readElementContent(parser, preserveMarkup)
            itemDepth--
            return content
        }

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val rawName = parser.name.orEmpty()
                        val localName = rawName.substringAfter(':').lowercase(Locale.ROOT)
                        val fullLower = rawName.lowercase(Locale.ROOT)

                        if (!insideItem && (localName == "item" || localName == "entry")) {
                            insideItem = true
                            itemDepth = 1
                            itemGuid = ""
                            itemLink = ""
                            itemTitle = ""
                            itemDescription = ""
                            itemContentEncoded = ""
                            itemPubDate = ""
                            itemUpdatedDate = ""
                            itemDirectImages.clear()
                        } else if (!insideItem) {
                            when (localName) {
                                "image" -> insideChannelImage = true
                                "title" -> if (!insideChannelImage && feedTitle.isBlank()) {
                                    feedTitle = readElementContent(parser, false)
                                }
                                "icon", "logo" -> if (feedIcon.isBlank()) {
                                    feedIcon = readElementContent(parser, false).trim()
                                }
                                "url" -> if (insideChannelImage && feedIcon.isBlank()) {
                                    feedIcon = readElementContent(parser, false).trim()
                                }
                                "lastbuilddate", "pubdate", "updated", "date" -> if (!insideChannelImage && channelDateStr.isBlank()) {
                                    channelDateStr = readElementContent(parser, false).trim()
                                }
                                "link" -> {
                                    val rel = parser.getAttributeValue(null, "rel")?.lowercase(Locale.ROOT)
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) {
                                        if (rel == null || rel == "alternate") {
                                            if (siteUrl.isBlank()) siteUrl = href.trim()
                                        } else if (rel.contains("icon") && feedIcon.isBlank()) {
                                            feedIcon = href.trim()
                                        }
                                    } else {
                                        val textLink = readElementContent(parser, false).trim()
                                        if (textLink.isNotBlank() && siteUrl.isBlank()) siteUrl = textLink
                                    }
                                }
                            }
                            if (fullLower == "itunes:image" && feedIcon.isBlank()) {
                                feedIcon = parser.getAttributeValue(null, "href").orEmpty()
                            }
                        } else {
                            // Inside <item> or <entry>
                            itemDepth++
                            val isDirectItemChild = itemDepth == 2
                            val isMediaChild = itemDepth == 2 || itemDepth == 3

                            when {
                                isDirectItemChild && localName == "title" && !fullLower.startsWith("media:") -> {
                                    val t = consumeItemElement(preserveMarkup = false)
                                    if (itemTitle.isBlank()) itemTitle = t
                                }
                                isDirectItemChild && (localName == "guid" || localName == "id") -> {
                                    val g = consumeItemElement(preserveMarkup = false).trim()
                                    if (itemGuid.isBlank()) itemGuid = g
                                }
                                isDirectItemChild && localName == "link" -> {
                                    val rel = parser.getAttributeValue(null, "rel")?.lowercase(Locale.ROOT)
                                    val type = parser.getAttributeValue(null, "type")?.lowercase(Locale.ROOT).orEmpty()
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) {
                                        if (rel == "enclosure" && (type.startsWith("image/") || looksLikeImageUrl(href))) {
                                            itemDirectImages.add(href.trim())
                                        } else if (rel == null || rel == "alternate") {
                                            if (itemLink.isBlank()) itemLink = href.trim()
                                        }
                                    } else {
                                        val l = consumeItemElement(preserveMarkup = false).trim()
                                        if (l.isNotBlank() && itemLink.isBlank()) itemLink = l
                                    }
                                }
                                isDirectItemChild && (
                                    fullLower == "content:encoded" ||
                                        (localName == "content" && parser.getAttributeValue(null, "url") == null && !fullLower.startsWith("media:"))
                                    ) -> {
                                    val c = consumeItemElement(preserveMarkup = true)
                                    if (c.length > itemContentEncoded.length) itemContentEncoded = c
                                }
                                (isDirectItemChild || fullLower == "media:description") &&
                                    (localName == "description" || localName == "summary") -> {
                                    val d = consumeItemElement(preserveMarkup = true)
                                    if (d.length > itemDescription.length) itemDescription = d
                                }
                                isDirectItemChild && localName in setOf(
                                    "pubdate", "published", "date", "issued", "created"
                                ) -> {
                                    val dt = consumeItemElement(preserveMarkup = false).trim()
                                    if (itemPubDate.isBlank() || parseExplicitDateMillis(itemPubDate) == null) {
                                        if (dt.isNotBlank()) itemPubDate = dt
                                    }
                                }
                                isDirectItemChild && localName in setOf("updated", "modified", "lastbuilddate") -> {
                                    val dt = consumeItemElement(preserveMarkup = false).trim()
                                    if (itemUpdatedDate.isBlank() || parseExplicitDateMillis(itemUpdatedDate) == null) {
                                        if (dt.isNotBlank()) itemUpdatedDate = dt
                                    }
                                }
                                isMediaChild && (
                                    fullLower == "media:content" || fullLower == "media:thumbnail" || localName == "thumbnail"
                                    ) -> {
                                    val mediaUrl = parser.getAttributeValue(null, "url")?.trim().orEmpty()
                                    val medium = parser.getAttributeValue(null, "medium")?.lowercase(Locale.ROOT).orEmpty()
                                    val mime = parser.getAttributeValue(null, "type")?.lowercase(Locale.ROOT).orEmpty()
                                    if (mediaUrl.isNotBlank() &&
                                        (fullLower == "media:thumbnail" || medium == "image" || mime.startsWith("image/") || looksLikeImageUrl(mediaUrl) || medium.isEmpty())
                                    ) {
                                        if (!looksLikeVideoOrAudioUrl(mediaUrl)) {
                                            itemDirectImages.add(mediaUrl)
                                        }
                                    }
                                }
                                isDirectItemChild && localName == "enclosure" -> {
                                    val encUrl = parser.getAttributeValue(null, "url")?.trim().orEmpty()
                                    val mime = parser.getAttributeValue(null, "type")?.lowercase(Locale.ROOT).orEmpty()
                                    if (encUrl.isNotBlank() && (mime.startsWith("image/") || looksLikeImageUrl(encUrl))) {
                                        itemDirectImages.add(encUrl)
                                    }
                                }
                                isMediaChild && fullLower == "itunes:image" -> {
                                    val href = parser.getAttributeValue(null, "href")?.trim().orEmpty()
                                    if (href.isNotBlank()) itemDirectImages.add(href)
                                }
                                isDirectItemChild && (localName == "image" || localName == "featuredimage" || localName == "cover" || localName == "thumb") -> {
                                    val attrHref = parser.getAttributeValue(null, "url")
                                        ?: parser.getAttributeValue(null, "href")
                                        ?: parser.getAttributeValue(null, "src")
                                    if (!attrHref.isNullOrBlank()) {
                                        if (attrHref.startsWith("http") || attrHref.startsWith("/")) {
                                            itemDirectImages.add(attrHref.trim())
                                        }
                                    } else {
                                        val inner = consumeItemElement(preserveMarkup = true).trim()
                                        val nestedUrl = extractTagText(inner, "url").ifBlank { inner.replace(Regex("<[^>]+>"), "").trim() }
                                        if (nestedUrl.startsWith("http") || nestedUrl.startsWith("/")) {
                                            itemDirectImages.add(nestedUrl)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val localName = parser.name.orEmpty().substringAfter(':').lowercase(Locale.ROOT)
                        if (!insideItem && localName == "image") {
                            insideChannelImage = false
                        } else if (insideItem) {
                            itemDepth--
                            if (itemDepth <= 0 || localName == "item" || localName == "entry") {
                                if (itemDepth <= 0) {
                                    insideItem = false
                                    itemDepth = 0
                                    val baseForItem = itemLink.ifBlank { siteUrl.ifBlank { feedUrl } }
                                    val richHtml = itemContentEncoded.ifBlank { itemDescription }
                                    val fallbackHtml = itemDescription
                                    val candidateImg = itemDirectImages.firstOrNull { isCleanImageCandidate(it) }
                                        ?: extractBestImageFromHtml(richHtml, baseForItem)
                                        ?: extractBestImageFromHtml(fallbackHtml, baseForItem)

                                    val cleanContent = htmlToReadableText(richHtml.ifBlank { fallbackHtml })
                                    val cleanTitle = htmlToPlainSingleLine(itemTitle).ifBlank {
                                        cleanContent.lineSequence().firstOrNull()?.take(90).orEmpty()
                                    }
                                    val resolvedLink = resolveUrl(feedUrl, itemLink) ?: itemLink
                                    if (cleanTitle.isNotBlank() || cleanContent.isNotBlank()) {
                                        val explicitDate = parseExplicitDateMillis(itemPubDate)
                                            ?: parseExplicitDateMillis(itemUpdatedDate)
                                        parsedItems.add(
                                            ParsedRssItem(
                                                guid = itemGuid.ifBlank { resolvedLink.ifBlank { cleanTitle } },
                                                link = resolvedLink,
                                                title = cleanTitle.ifBlank { "Untitled Post" },
                                                content = cleanContent.ifBlank { cleanTitle },
                                                imageUrl = resolveUrl(baseForItem, candidateImg),
                                                publishedAt = explicitDate ?: (fallbackBaseMs - parsedItems.size * 1000L),
                                                hasExplicitDate = explicitDate != null
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Preserve all valid items already parsed before any malformed XML tail
            if (parsedItems.isEmpty()) throw IllegalStateException("Malformed XML with 0 items")
        }

        val channelFallbackMs = parseExplicitDateMillis(channelDateStr)
        val normalizedItems = normalizeUndatedFeedItems(parsedItems, channelFallbackMs, fallbackBaseMs)
        val resolvedSite = resolveUrl(feedUrl, siteUrl) ?: extractOriginUrl(feedUrl)
        return ParsedRssFeed(
            feedUrl = feedUrl,
            siteUrl = resolvedSite,
            title = htmlToPlainSingleLine(feedTitle).ifBlank { extractHost(resolvedSite.ifBlank { feedUrl }) },
            iconUrl = resolveUrl(resolvedSite.ifBlank { feedUrl }, feedIcon).orEmpty(),
            items = normalizedItems
        )
    }

    private fun parseMalformedXmlViaRegex(feedUrl: String, xml: String): ParsedRssFeed? {
        val blockRegex = Regex("<(item|entry)\\b[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val matches = blockRegex.findAll(xml).toList()
        if (matches.isEmpty()) return null

        val channelHeader = xml.substringBefore(matches.first().value)
        val feedTitle = extractTagText(channelHeader, "title").let { htmlToPlainSingleLine(it) }
            .ifBlank { extractHost(feedUrl) }
        val channelDateStr = extractFirstNonBlankTag(channelHeader, listOf("lastBuildDate", "pubDate", "updated", "dc:date"))
        val channelFallbackMs = parseExplicitDateMillis(channelDateStr)
        val fallbackBaseMs = System.currentTimeMillis()

        val rawItems = matches.mapIndexedNotNull { idx, match ->
            val block = match.groupValues[2]
            val title = htmlToPlainSingleLine(extractTagText(block, "title"))
            val link = extractHrefAttr(block) ?: extractTagText(block, "link").trim()
            val guid = extractTagText(block, "guid").ifBlank { extractTagText(block, "id") }.trim()
            val encoded = extractTagText(block, "content:encoded").ifBlank { extractTagText(block, "content") }
            val desc = extractTagText(block, "description").ifBlank { extractTagText(block, "summary") }
            val pub = extractFirstNonBlankTag(
                block,
                listOf("pubDate", "dc:date", "published", "atom:published", "dcterms:issued", "issued", "dcterms:created", "created", "date")
            )
            val updated = extractFirstNonBlankTag(
                block,
                listOf("updated", "atom:updated", "dcterms:modified", "modified", "a10:updated")
            )
            val mediaUrl = Regex("""<(?:media:content|media:thumbnail|enclosure)\b[^>]*\burl\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.getOrNull(1)
            val base = link.ifBlank { feedUrl }
            val img = mediaUrl?.takeIf { isCleanImageCandidate(it) && !looksLikeVideoOrAudioUrl(it) }
                ?: extractBestImageFromHtml(encoded, base)
                ?: extractBestImageFromHtml(desc, base)
            val body = htmlToReadableText(encoded.ifBlank { desc })
            if (title.isBlank() && body.isBlank()) return@mapIndexedNotNull null
            val explicitDate = parseExplicitDateMillis(pub) ?: parseExplicitDateMillis(updated)
            ParsedRssItem(
                guid = guid.ifBlank { link.ifBlank { title } },
                link = resolveUrl(feedUrl, link) ?: link,
                title = title.ifBlank { "Untitled" },
                content = body.ifBlank { title },
                imageUrl = resolveUrl(base, img),
                publishedAt = explicitDate ?: (fallbackBaseMs - idx * 1000L),
                hasExplicitDate = explicitDate != null
            )
        }
        val items = normalizeUndatedFeedItems(rawItems, channelFallbackMs, fallbackBaseMs)
        return ParsedRssFeed(
            feedUrl = feedUrl,
            siteUrl = extractOriginUrl(feedUrl),
            title = feedTitle,
            iconUrl = "",
            items = items
        )
    }

    /**
     * Ensures any isolated item missing an explicit date inside a dated feed inherits a neighbor-relative
     * timestamp instead of jumping artificially to `System.currentTimeMillis()`.
     */
    private fun normalizeUndatedFeedItems(
        items: List<ParsedRssItem>,
        channelFallbackMs: Long?,
        fallbackBaseMs: Long
    ): List<ParsedRssItem> {
        if (items.isEmpty() || items.all { it.hasExplicitDate }) return items
        if (items.none { it.hasExplicitDate }) {
            val anchor = channelFallbackMs?.takeIf { it <= fallbackBaseMs + 300_000L } ?: fallbackBaseMs
            return items.mapIndexed { idx, item ->
                item.copy(publishedAt = anchor - idx * 1000L)
            }
        }
        val result = items.toMutableList()
        // Forward pass: undated items after a dated item sit 1s below the preceding item
        for (i in 1 until result.size) {
            if (!result[i].hasExplicitDate && (result[i - 1].hasExplicitDate || i > 1)) {
                val prevTs = result[i - 1].publishedAt
                if (prevTs > 0L) {
                    result[i] = result[i].copy(publishedAt = prevTs - 1000L)
                }
            }
        }
        // Backward pass: undated items at the very top sit 1s above the first dated item below them (clamped to now)
        val firstDatedIdx = result.indexOfFirst { it.hasExplicitDate }
        if (firstDatedIdx > 0) {
            val baseDated = result[firstDatedIdx].publishedAt
            for (i in (firstDatedIdx - 1) downTo 0) {
                val candidate = minOf(fallbackBaseMs, baseDated + (firstDatedIdx - i) * 1000L)
                result[i] = result[i].copy(publishedAt = candidate)
            }
        }
        return result
    }

    /**
     * Reads strictly up to 24 KB of HTML `<head>` to locate `og:image`/`twitter:image`,
     * immediately cancelling the HTTP stream once `</head>` or the meta tag is found so the
     * full webpage body is NEVER downloaded.
     */
    private fun fetchOpenGraphImageHeadOnly(articleUrl: String): String? {
        val probeTransport = ogClient
            ?: if (httpClient === NetworkClient.rssHttpClient) NetworkClient.ogProbeHttpClient else httpClient
        val req = Request.Builder()
            .url(articleUrl)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1")
            .get()
            .build()
        val call = probeTransport.newCall(req)
        return runCatching {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    call.cancel()
                    return@use null
                }
                val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (contentType.isNotBlank() && !contentType.contains("html") && !contentType.contains("xml")) {
                    call.cancel()
                    return@use null
                }
                val headSnippet = resp.body?.charStream()?.use { reader ->
                    val buf = CharArray(2048)
                    val sb = StringBuilder()
                    var n: Int
                    while (reader.read(buf).also { n = it } != -1 && sb.length < 24_000) {
                        sb.append(buf, 0, n)
                        if (sb.contains("</head>", ignoreCase = true)) {
                            call.cancel()
                            break
                        }
                    }
                    if (sb.length >= 24_000) {
                        call.cancel()
                    }
                    sb.toString()
                }.orEmpty()
                extractMetaImageFromHead(headSnippet, resp.request.url.toString())
            }
        }.getOrNull()
    }

    private fun extractMetaImageFromHead(html: String, baseUrl: String): String? {
        val metaRegex = Regex("""<meta\b[^>]+>""", RegexOption.IGNORE_CASE)
        for (match in metaRegex.findAll(html)) {
            val tag = match.value
            val prop = extractAttr(tag, "property")?.lowercase(Locale.ROOT)
                ?: extractAttr(tag, "name")?.lowercase(Locale.ROOT)
                ?: continue
            if (prop in setOf("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src")) {
                val content = decodeHtmlEntities(extractAttr(tag, "content").orEmpty()).trim()
                val resolved = resolveUrl(baseUrl, content)
                if (!resolved.isNullOrBlank() && isCleanImageCandidate(resolved)) {
                    return resolved
                }
            }
        }
        return null
    }

    fun extractBestImageFromHtml(html: String, baseUrl: String): String? {
        if (html.isBlank()) return null
        val decoded = if (html.contains("&lt;img", ignoreCase = true)) decodeHtmlEntities(html) else html
        val imgTagRegex = Regex("""<img\b[^>]+>""", RegexOption.IGNORE_CASE)
        for (match in imgTagRegex.findAll(decoded)) {
            val tag = match.value
            // Skip explicit 1x1 tracking pixels
            val width = extractAttr(tag, "width")?.toIntOrNull()
            val height = extractAttr(tag, "height")?.toIntOrNull()
            if ((width != null && width in 1..32) || (height != null && height in 1..32)) continue

            val srcsetBest = extractAttr(tag, "srcset")?.let { parseBestFromSrcset(it) }
                ?: extractAttr(tag, "data-srcset")?.let { parseBestFromSrcset(it) }

            val candidates = listOfNotNull(
                srcsetBest,
                extractAttr(tag, "data-lazy-src"),
                extractAttr(tag, "data-original"),
                extractAttr(tag, "data-src"),
                extractAttr(tag, "data-url"),
                extractAttr(tag, "src")
            )
            for (cand in candidates) {
                val clean = decodeHtmlEntities(cand).trim()
                if (!isCleanImageCandidate(clean)) continue
                val resolved = resolveUrl(baseUrl, clean)
                if (!resolved.isNullOrBlank()) return resolved
            }
        }
        return null
    }

    /**
     * Selects crisp mobile HD resolution (640w..1400w, e.g. 1080w/1200w) instead of
     * multi-megabyte 4K/2560w/3840w raw DSLR uploads when responsive `srcset` is available.
     */
    private fun parseBestFromSrcset(srcset: String): String? {
        var bestUrl: String? = null
        var bestScore = -1
        srcset.split(",").forEach { entry ->
            val parts = entry.trim().split(Regex("\\s+"))
            val url = parts.firstOrNull()?.trim().orEmpty()
            if (url.isNotEmpty() && !url.startsWith("data:")) {
                val descriptor = parts.getOrNull(1).orEmpty()
                val widthPx = when {
                    descriptor.endsWith("w", ignoreCase = true) -> descriptor.dropLast(1).toIntOrNull() ?: 600
                    descriptor.endsWith("x", ignoreCase = true) -> ((descriptor.dropLast(1).toFloatOrNull() ?: 1f) * 600).toInt()
                    else -> 600
                }
                val score = when {
                    widthPx in 640..1400 -> 10_000 + widthPx
                    widthPx > 1400 -> 5_000 - (widthPx - 1400).coerceAtMost(4_900)
                    else -> widthPx
                }
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = url
                }
            }
        }
        return bestUrl
    }

    private fun discoverCandidateFeedUrls(baseUrl: String, html: String): List<String> {
        val candidates = linkedSetOf<String>()
        val linkRegex = Regex("""<link\b[^>]+>""", RegexOption.IGNORE_CASE)
        for (m in linkRegex.findAll(html)) {
            val tag = m.value
            val rel = extractAttr(tag, "rel")?.lowercase(Locale.ROOT).orEmpty()
            val type = extractAttr(tag, "type")?.lowercase(Locale.ROOT).orEmpty()
            val href = extractAttr(tag, "href") ?: continue
            if (rel.contains("alternate") && (
                    type.contains("rss") || type.contains("atom") ||
                        type.contains("xml") || type.contains("json")
                    )
            ) {
                resolveUrl(baseUrl, decodeHtmlEntities(href))?.let { candidates.add(it) }
            }
        }
        val origin = extractOriginUrl(baseUrl)
        if (origin.isNotBlank()) {
            listOf("/feed", "/rss", "/rss.xml", "/feed.xml", "/atom.xml", "/index.xml").forEach { suffix ->
                candidates.add(origin.trimEnd('/') + suffix)
            }
        }
        candidates.remove(baseUrl)
        return candidates.toList()
    }

    fun resolveFeedIcon(existingIcon: String, siteOrFeedUrl: String, htmlSnippet: String = ""): String {
        if (existingIcon.isNotBlank() && existingIcon.startsWith("http")) return existingIcon
        if (htmlSnippet.isNotBlank()) {
            val linkRegex = Regex("""<link\b[^>]+>""", RegexOption.IGNORE_CASE)
            for (m in linkRegex.findAll(htmlSnippet)) {
                val tag = m.value
                val rel = extractAttr(tag, "rel")?.lowercase(Locale.ROOT).orEmpty()
                if (rel.contains("apple-touch-icon") || rel == "icon" || rel == "shortcut icon") {
                    val href = extractAttr(tag, "href")
                    val resolved = resolveUrl(siteOrFeedUrl, href)
                    if (!resolved.isNullOrBlank()) return resolved
                }
            }
        }
        val host = extractHost(siteOrFeedUrl)
        return if (host.isNotBlank()) {
            "https://www.google.com/s2/favicons?domain=$host&sz=128"
        } else {
            ""
        }
    }

    private fun extractReadableHtmlBody(html: String): String {
        if (html.isBlank()) return ""
        val sanitized = html.replace(
            Regex("""<(script|style|noscript|iframe|svg|form|nav|footer|aside)\b[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            " "
        )
        val articleMatch = Regex("""<article\b[^>]*>(.*?)</article>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(sanitized)?.groupValues?.getOrNull(1)
        val mainMatch = Regex("""<main\b[^>]*>(.*?)</main>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(sanitized)?.groupValues?.getOrNull(1)
        val targetHtml = articleMatch ?: mainMatch ?: sanitized

        val paragraphs = Regex("""<p\b[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(targetHtml)
            .map { htmlToPlainSingleLine(it.groupValues[1]) }
            .filter { it.length >= 30 }
            .toList()

        return if (paragraphs.size >= 2) {
            paragraphs.joinToString("\n\n")
        } else {
            htmlToReadableText(targetHtml)
        }
    }

    fun htmlToReadableText(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""
        // 1. Unescape CDATA wrappers and common escaped markup tags (&lt;p&gt; etc.)
        var step = rawHtml
            .replace("<![CDATA[", "")
            .replace("]]>", "")
        if (step.contains("&lt;") || step.contains("&gt;")) {
            step = step.replace("&lt;", "<").replace("&gt;", ">")
        }

        // 2. Remove non-content elements and scripts/styles
        val withoutNoise = step
            .replace(Regex("""<(script|style|noscript|iframe|svg|form|nav|footer|header|aside)\b[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""<(br|/p|/div|/li|/h[1-6]|/blockquote|/tr|/article|/section)\b[^>]*>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), " ")

        // 3. Decode entities
        val unescaped = decodeHtmlEntities(withoutNoise)
        return unescaped
            .lines()
            .map { it.replace(Regex("""[ \t\x0B\f\r]+"""), " ").trim() }
            .filter { line ->
                val cleanLine = line.trim()
                cleanLine.isNotEmpty() &&
                    !cleanLine.startsWith("<![CDATA[") &&
                    !cleanLine.equals("]]>", ignoreCase = true)
            }
            .joinToString("\n\n")
            .trim()
    }

    private fun htmlToPlainSingleLine(raw: String): String {
        if (raw.isBlank()) return ""
        val unCdata = raw.replace("<![CDATA[", "").replace("]]>", "")
        val stripped = unCdata
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""<[^>]+>"""), " ")
        return decodeHtmlEntities(stripped).replace(Regex("""\s+"""), " ").trim()
    }

    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        var res = runCatching {
            Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
        }.getOrDefault(text)

        // Handle double-encoded entities if still present
        if (res.contains('&')) {
            res = res.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&ndash;", "–")
                .replace("&mdash;", "—")
                .replace("&lsquo;", "‘")
                .replace("&rsquo;", "’")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&hellip;", "…")
        }
        return res
    }

    private fun sanitizeXml(xml: String): String {
        // Strip leading junk before first '<', strip invalid XML 1.0 control chars, and fix bare '&'
        val idx = xml.indexOf('<')
        val sliced = if (idx > 0) xml.substring(idx) else xml
        val withoutControlChars = sliced.replace(Regex("""[\x00-\x08\x0B\x0C\x0E-\x1F]"""), "")
        return withoutControlChars.replace(
            Regex("""&(?!(?:[a-zA-Z][a-zA-Z0-9]+|#[0-9]+|#x[0-9a-fA-F]+);)"""),
            "&amp;"
        )
    }

    /**
     * Reads text/CDATA (and optionally nested XHTML markup) from the current START_TAG up to
     * and including its matching END_TAG, keeping parser tag depth 100% synchronized.
     */
    private fun readElementContent(parser: XmlPullParser, preserveInnerMarkup: Boolean): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    depth++
                    if (preserveInnerMarkup) {
                        sb.append('<').append(parser.name.orEmpty())
                        for (i in 0 until parser.attributeCount) {
                            sb.append(' ')
                                .append(parser.getAttributeName(i))
                                .append("=\"")
                                .append(parser.getAttributeValue(i).orEmpty().replace("\"", "&quot;"))
                                .append('"')
                        }
                        sb.append('>')
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth > 0 && preserveInnerMarkup) {
                        sb.append("</").append(parser.name.orEmpty()).append('>')
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    sb.append(parser.text.orEmpty())
                }
            }
        }
        return sb.toString()
    }

    private fun extractFirstNonBlankTag(xml: String, tags: List<String>): String {
        for (tag in tags) {
            val v = extractTagText(xml, tag)
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private fun extractTagText(xml: String, tag: String): String {
        val escapedTag = Regex.escape(tag)
        val m = Regex("""<$escapedTag\b[^>]*>(.*?)</$escapedTag>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(xml)
        val raw = m?.groupValues?.getOrNull(1).orEmpty()
        return raw.removeSurrounding("<![CDATA[", "]]>").trim()
    }

    private fun extractHrefAttr(xml: String): String? {
        val m = Regex("""<link\b[^>]*\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(xml)
        return m?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractHtmlTitle(html: String): String {
        return htmlToPlainSingleLine(extractTagText(html, "title"))
    }

    private fun extractAttr(tag: String, attrName: String): String? {
        val regex = Regex("""\b$attrName\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
        val match = regex.find(tag) ?: return null
        return match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
    }

    private fun isCleanImageCandidate(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (lower.isBlank() || lower.startsWith("data:") || lower.startsWith("javascript:")) return false
        val blockedFragments = listOf(
            "feedburner.com/~ff", "feeds.feedburner.com/~r", "doubleclick.net",
            "1x1", "pixel.wp.com", "spacer.gif", "blank.gif", "s.w.org/images/core/emoji",
            "gravatar.com/avatar"
        )
        return blockedFragments.none { lower.contains(it) }
    }

    private fun looksLikeImageUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase(Locale.ROOT)
        return listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif").any { clean.endsWith(it) }
    }

    private fun looksLikeVideoOrAudioUrl(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase(Locale.ROOT)
        return listOf(".mp3", ".m4a", ".wav", ".ogg", ".mp4", ".webm", ".m3u8").any { clean.endsWith(it) }
    }

    fun normalizeInputUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun resolveUrl(baseUrl: String, candidate: String?): String? {
        val raw = candidate?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) return raw
        if (raw.startsWith("//")) return "https:$raw"
        return runCatching {
            URI(baseUrl).resolve(raw).toString()
        }.getOrNull()
    }

    fun extractOriginUrl(url: String): String {
        return runCatching {
            val u = URI(url)
            if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}" else ""
        }.getOrDefault("")
    }

    fun extractHost(url: String): String {
        return runCatching {
            URI(normalizeInputUrl(url)).host?.removePrefix("www.").orEmpty()
        }.getOrDefault("")
    }

    fun canonicalizeUrl(raw: String): String {
        val trimmed = raw.trim().substringBefore('#')
        if (trimmed.isEmpty()) return ""
        return runCatching {
            val u = URI(trimmed)
            val trackingKeys = setOf(
                "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
                "fbclid", "gclid", "mc_cid", "mc_eid", "ref"
            )
            val cleanQuery = u.rawQuery
                ?.split('&')
                ?.filter { pair ->
                    val key = pair.substringBefore('=').lowercase(Locale.ROOT)
                    key.isNotEmpty() && key !in trackingKeys
                }
                ?.joinToString("&")
                ?.takeIf { it.isNotEmpty() }

            val cleanPath = u.rawPath?.trimEnd('/').orEmpty()
            buildString {
                if (!u.scheme.isNullOrBlank()) append(u.scheme.lowercase(Locale.ROOT)).append("://")
                if (!u.host.isNullOrBlank()) append(u.host.lowercase(Locale.ROOT).removePrefix("www."))
                if (u.port > 0 && u.port != 80 && u.port != 443) append(':').append(u.port)
                append(cleanPath)
                if (cleanQuery != null) append('?').append(cleanQuery)
            }
        }.getOrDefault(trimmed.trimEnd('/'))
    }

    fun parseExplicitDateMillis(rawDate: String?): Long? {
        if (rawDate.isNullOrBlank()) return null

        // 1. Clean CDATA, HTML entities, Arabic/Persian digits, localized commas, and parenthetical tz comments
        var str = decodeHtmlEntities(
            rawDate.trim()
                .removeSurrounding("<![CDATA[", "]]>")
                .trim()
        )
        if (str.isEmpty()) return null

        val normalizedChars = StringBuilder(str.length)
        for (ch in str) {
            when (ch) {
                in '\u0660'..'\u0669' -> normalizedChars.append(('0'.code + (ch.code - 0x0660)).toChar())
                in '\u06F0'..'\u06F9' -> normalizedChars.append(('0'.code + (ch.code - 0x06F0)).toChar())
                '،' -> normalizedChars.append(',')
                else -> normalizedChars.append(ch)
            }
        }
        str = normalizedChars.toString()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*\([^)]*\)\s*$"""), "")
            .trim()
        if (str.isEmpty()) return null

        // 2. Unix epoch seconds (10 digits) or milliseconds (13 digits)
        if (str.length == 10 && str.all { it.isDigit() }) {
            return str.toLongOrNull()?.times(1000L)?.takeIf { it > 0L }
        }
        if (str.length == 13 && str.all { it.isDigit() }) {
            return str.toLongOrNull()?.takeIf { it > 0L }
        }

        // 3. Fast deterministic ISO-8601 / RFC-3339 / SQL timestamp parser
        parseIsoOrSqlDateMillis(str)?.let { return it }

        // 4. Normalize localized month names & AM/PM markers before RFC-822 / human-readable parsing
        val localizedNormalized = normalizeLocalizedMonthsAndMeridiem(str)

        // 5. Fast deterministic RFC-822 / RFC-1123 / RFC-2822 / RFC-850 parser (weekday-independent!)
        parseRfcDateMillis(localizedNormalized)?.let { return it }

        // 6. Fallback SimpleDateFormat patterns (US / slash / natural date strings)
        val utcTz = TimeZone.getTimeZone("UTC")
        val fallbackPatterns = listOf(
            "MMM d, yyyy h:mm:ss a zzz",
            "MMM d, yyyy h:mm a zzz",
            "MMM d, yyyy HH:mm:ss zzz",
            "MMM d, yyyy HH:mm zzz",
            "MMM d, yyyy h:mm:ss a",
            "MMM d, yyyy h:mm a",
            "MMM d, yyyy HH:mm:ss",
            "MMM d, yyyy HH:mm",
            "MMM d, yyyy",
            "MMMM d, yyyy HH:mm:ss",
            "MMMM d, yyyy",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm"
        )
        val withoutLeadingWeekday = localizedNormalized.replace(Regex("""^[^,0-9]{2,20},\s*"""), "")
        for (candidate in listOf(withoutLeadingWeekday, localizedNormalized)) {
            for (pattern in fallbackPatterns) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        if (!pattern.contains('z') && !pattern.contains('Z') && !pattern.contains('X')) {
                            timeZone = utcTz
                        }
                    }.parse(candidate)?.time
                }.getOrNull()
                if (parsed != null && parsed > 0L) return parsed
            }
        }
        return null
    }

    private fun parseIsoOrSqlDateMillis(input: String): Long? {
        val m = ISO_DATE_REGEX.matchEntire(input) ?: return null
        val year = m.groupValues[1].toIntOrNull() ?: return null
        val month = m.groupValues[2].toIntOrNull() ?: return null
        val day = m.groupValues[3].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null

        val hour = m.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val minute = m.groupValues[5].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val second = m.groupValues[6].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val fracRaw = m.groupValues[7]
        val millis = if (fracRaw.isEmpty()) {
            0
        } else {
            fracRaw.take(3).padEnd(3, '0').toIntOrNull() ?: 0
        }
        val tzRaw = m.groupValues[8].trim()
        val offsetMillis = parseTimezoneOffsetMillis(tzRaw) ?: 0

        return buildUtcMillis(year, month, day, hour, minute, second, millis, offsetMillis)
    }

    private fun parseRfcDateMillis(input: String): Long? {
        // Strip optional weekday prefix ("Tue, ", "Tuesday, ", "Wed 28 Oct...", localized weekday, etc.)
        val body = input
            .replace(Regex("""^[^,0-9]{1,25},\s*"""), "")
            .replace(
                Regex(
                    """^(?:mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\.?\s+""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
        val m = RFC_DATE_REGEX.matchEntire(body) ?: return null

        val day = m.groupValues[1].toIntOrNull() ?: return null
        val monthToken = m.groupValues[2].lowercase(Locale.ROOT).trimEnd('.')
        val month = ENGLISH_MONTH_MAP[monthToken] ?: ENGLISH_MONTH_MAP[monthToken.take(3)] ?: return null
        val rawYear = m.groupValues[3].toIntOrNull() ?: return null
        val year = when {
            rawYear in 0..69 -> 2000 + rawYear
            rawYear in 70..99 -> 1900 + rawYear
            else -> rawYear
        }
        if (day !in 1..31) return null

        var hour = m.groupValues[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val minute = m.groupValues[5].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val second = m.groupValues[6].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val amPm = m.groupValues[7].uppercase(Locale.ROOT)
        if (amPm == "PM" && hour in 1..11) hour += 12
        if (amPm == "AM" && hour == 12) hour = 0

        val tzToken = m.groupValues[8].trim()
        val offsetMillis = parseTimezoneOffsetMillis(tzToken) ?: 0

        return buildUtcMillis(year, month, day, hour, minute, second, 0, offsetMillis)
    }

    private fun parseTimezoneOffsetMillis(tzToken: String): Int? {
        if (tzToken.isEmpty()) return 0
        val upper = tzToken.uppercase(Locale.ROOT)
            .removePrefix("UTC")
            .removePrefix("GMT")
            .trim()
        if (upper.isEmpty() || upper == "Z" || upper == "UT") return 0

        NAMED_TZ_OFFSETS_MINUTES[tzToken.uppercase(Locale.ROOT)]?.let { return it * 60_000 }

        val m = Regex("""^([+-])(\d{1,2})(?::?(\d{2}))?$""").matchEntire(upper) ?: return null
        val sign = if (m.groupValues[1] == "-") -1 else 1
        val hours = m.groupValues[2].toIntOrNull() ?: return null
        val mins = m.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        return sign * (hours * 3_600_000 + mins * 60_000)
    }

    private fun buildUtcMillis(
        year: Int,
        month1To12: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int,
        tzOffsetMillis: Int
    ): Long? {
        return runCatching {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
            cal.isLenient = false
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month1To12 - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, second)
            cal.set(Calendar.MILLISECOND, millis)
            val utcInstant = cal.timeInMillis - tzOffsetMillis
            utcInstant.takeIf { it > 0L }
        }.getOrNull()
    }

    private fun normalizeLocalizedMonthsAndMeridiem(input: String): String {
        var s = input
        for ((localized, eng) in LOCALIZED_MONTH_REPLACEMENTS) {
            if (s.contains(localized, ignoreCase = true)) {
                s = s.replace(localized, eng, ignoreCase = true)
            }
        }
        s = s.replace(Regex("""(?<=\d)\s*ص\b"""), " AM")
            .replace(Regex("""(?<=\d)\s*م\b"""), " PM")
            .replace("صباحاً", "AM")
            .replace("صباحا", "AM")
            .replace("مساءً", "PM")
            .replace("مساء", "PM")
        return s
    }

    companion object {
        private val ISO_DATE_REGEX = Regex(
            """^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[Tt\s]+(\d{1,2}):(\d{2})(?::(\d{2})(?:\.(\d+))?)?(?:\s*(Z|z|UTC|GMT|UT|(?:UTC|GMT)?[+-]\d{1,2}:?\d{0,2}|[A-Za-z]{2,5}))?)?$"""
        )

        private val RFC_DATE_REGEX = Regex(
            """^(\d{1,2})[\s\-/]+([A-Za-z.]+)[\s\-/]+(\d{2,4})(?:[\sT,]+(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?(?:\s*(AM|PM|am|pm))?(?:\s*(.+))?)?$"""
        )

        private val ENGLISH_MONTH_MAP = mapOf(
            "jan" to 1, "january" to 1,
            "feb" to 2, "february" to 2,
            "mar" to 3, "march" to 3,
            "apr" to 4, "april" to 4,
            "may" to 5,
            "jun" to 6, "june" to 6,
            "jul" to 7, "july" to 7,
            "aug" to 8, "august" to 8,
            "sep" to 9, "sept" to 9, "september" to 9,
            "oct" to 10, "october" to 10,
            "nov" to 11, "november" to 11,
            "dec" to 12, "december" to 12
        )

        private val NAMED_TZ_OFFSETS_MINUTES = mapOf(
            "Z" to 0, "UTC" to 0, "GMT" to 0, "UT" to 0, "WET" to 0,
            "BST" to 60, "CET" to 60, "WEST" to 60, "WAT" to 60,
            "CEST" to 120, "EET" to 120, "CAT" to 120, "SAST" to 120,
            "EEST" to 180, "MSK" to 180, "AST" to 180, "EAT" to 180, "TRT" to 180,
            "GST" to 240, "AZT" to 240,
            "PKT" to 300,
            "IST" to 330,
            "WIB" to 420, "ICT" to 420,
            "CST_CN" to 480, "SGT" to 480, "HKT" to 480, "AWST" to 480,
            "JST" to 540, "KST" to 540,
            "AEST" to 600, "AEDT" to 660, "NZST" to 720, "NZDT" to 780,
            "EDT" to -240, "EST" to -300, "CDT" to -300, "CST" to -360,
            "MDT" to -360, "MST" to -420, "PDT" to -420, "PST" to -480,
            "BRT" to -180, "ART" to -180
        )

        private val LOCALIZED_MONTH_REPLACEMENTS = listOf(
            "كانون الثاني" to "Jan", "يناير" to "Jan", "جانفي" to "Jan", "janvier" to "Jan", "janv" to "Jan", "enero" to "Jan",
            "شباط" to "Feb", "فبراير" to "Feb", "فيفري" to "Feb", "février" to "Feb", "fevrier" to "Feb", "févr" to "Feb", "febrero" to "Feb",
            "آذار" to "Mar", "اذار" to "Mar", "مارس" to "Mar", "mars" to "Mar", "marzo" to "Mar", "märz" to "Mar",
            "نيسان" to "Apr", "أبريل" to "Apr", "ابريل" to "Apr", "أفريل" to "Apr", "avril" to "Apr", "abril" to "Apr",
            "أيار" to "May", "ايار" to "May", "مايو" to "May", "ماي" to "May", "mai" to "May", "mayo" to "May",
            "حزيران" to "Jun", "يونيو" to "Jun", "يونيه" to "Jun", "جوان" to "Jun", "juin" to "Jun", "junio" to "Jun",
            "تموز" to "Jul", "يوليو" to "Jul", "يوليه" to "Jul", "جويلية" to "Jul", "juillet" to "Jul", "julio" to "Jul",
            "آب" to "Aug", "اب" to "Aug", "أغسطس" to "Aug", "اغسطس" to "Aug", "أوت" to "Aug", "août" to "Aug", "aout" to "Aug", "agosto" to "Aug",
            "أيلول" to "Sep", "ايلول" to "Sep", "سبتمبر" to "Sep", "شتنبر" to "Sep", "septembre" to "Sep", "septiembre" to "Sep",
            "تشرين الأول" to "Oct", "تشرين الاول" to "Oct", "أكتوبر" to "Oct", "اكتوبر" to "Oct", "octobre" to "Oct", "octubre" to "Oct", "oktober" to "Oct",
            "تشرين الثاني" to "Nov", "نوفمبر" to "Nov", "نونبر" to "Nov", "novembre" to "Nov", "noviembre" to "Nov",
            "كانون الأول" to "Dec", "كانون الاول" to "Dec", "ديسمبر" to "Dec", "دجنبر" to "Dec", "décembre" to "Dec", "decembre" to "Dec", "diciembre" to "Dec", "dezember" to "Dec"
        )
    }
}
