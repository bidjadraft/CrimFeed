package com.example.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkClient {
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    const val RSS_READER_USER_AGENT =
        "CrimsonFeed/2.94 (+https://crimsonfeed.app; Android RSS Reader)"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
        explicitNulls = false
    }

    @Volatile
    private var rssDiskCache: Cache? = null

    fun initHttpCache(baseCacheDir: File) {
        if (rssDiskCache != null) return
        synchronized(this) {
            if (rssDiskCache == null) {
                val dir = File(baseCacheDir, "rss_http_cache").apply { mkdirs() }
                rssDiskCache = Cache(dir, 12L * 1024L * 1024L)
            }
        }
    }

    val aiHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Dedicated RSS/XML client.
     * - High-capacity Dispatcher without artificial wall-clock callTimeout so queued feeds never
     *   time out while waiting for a connection slot.
     * - Per-host burst pacing is enforced in SmartRssParser to prevent HTTP 429 / 503 blocks.
     * - Never manually sets "Accept-Encoding" so OkHttp's BridgeInterceptor automatically negotiates gzip.
     */
    val rssHttpClient: OkHttpClient by lazy {
        val rssDispatcher = Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 6
        }
        OkHttpClient.Builder()
            .dispatcher(rssDispatcher)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()
                if (req.header("User-Agent") == null) {
                    builder.header("User-Agent", BROWSER_USER_AGENT)
                }
                if (req.header("Accept") == null) {
                    builder.header(
                        "Accept",
                        "application/rss+xml, application/atom+xml, application/rdf+xml, application/feed+json, application/xml, text/xml;q=0.9, */*;q=0.5"
                    )
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    /**
     * Fast, bounded-timeout client used strictly for optional post-save `<head>` OpenGraph image probes
     * on genuinely brand-new imageless posts so slow article webpages never delay feed syncing.
     */
    val ogProbeHttpClient: OkHttpClient by lazy {
        val ogDispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 4
        }
        OkHttpClient.Builder()
            .dispatcher(ogDispatcher)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()
                if (req.header("User-Agent") == null) {
                    builder.header("User-Agent", BROWSER_USER_AGENT)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    /**
     * Dedicated on-demand image transport used exclusively by Coil when a composable
     * enters the visible viewport on screen.
     */
    val imageHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val req = chain.request()
                val updated = req.newBuilder()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .build()
                chain.proceed(updated)
            }
            .build()
    }
}
