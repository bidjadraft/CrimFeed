package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import com.example.data.model.AppSettings
import com.example.data.model.ReaderAiMode
import com.example.data.model.SummaryProvider
import com.example.data.model.TargetLanguage
import com.example.data.opml.OpmlManager
import com.example.data.preferences.SettingsRepository
import com.example.data.remote.GeminiService
import com.example.data.remote.NetworkClient
import com.example.data.remote.OllamaService
import com.example.data.remote.ParsedRssFeed
import com.example.data.remote.ParsedRssItem
import com.example.data.remote.SmartRssParser
import com.example.data.repository.RssRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CrimsonFeed", appName)
    }

    @Test
    fun `parse dynamic free models txt filters comments and strips prefixes`() {
        val service = GeminiService()
        val sampleTxt = """
            # Daily Free Models
            models/gemini-2.5-flash | Fast free
            2. gemini-3.1-pro-preview
            gemma-3-27b-it
        """.trimIndent()
        val parsed = service.parseModelsTxt(sampleTxt)
        assertEquals(listOf("gemini-2.5-flash", "gemini-3.1-pro-preview", "gemma-3-27b-it"), parsed)
    }

    @Test
    fun `extract smart image prefers mobile hd srcset over 4k raw and skips tracking pixel`() {
        val parser = SmartRssParser()
        val html = """
            <img src="https://feeds.feedburner.com/~r/test" width="1" height="1" />
            <p>Intro text</p>
            <img src="/low.jpg" srcset="/medium.jpg 600w, https://cdn.example.com/hero-hd.webp 1200w, https://cdn.example.com/huge-4k.jpg 3840w" />
        """.trimIndent()
        val extracted = parser.extractBestImageFromHtml(html, "https://example.com/post")
        assertEquals("https://cdn.example.com/hero-hd.webp", extracted)
        assertTrue(parser.htmlToReadableText(html).contains("Intro text"))
    }

    @Test
    fun `opml export and import preserves custom lists and auto translate flags`() {
        val lists = listOf(
            CustomListEntity(id = 1, name = "أخبار عربية"),
            CustomListEntity(id = 2, name = "Empty Custom List")
        )
        val feeds = listOf(
            FeedEntity(
                id = 10,
                url = "https://example.org/arabic-rss.xml",
                siteUrl = "https://example.org",
                title = "Arabic Feed",
                groupName = "أخبار عربية",
                autoTranslate = true
            ),
            FeedEntity(
                id = 11,
                url = "https://blog.example.net/atom.xml",
                siteUrl = "https://blog.example.net",
                title = "Root Blog",
                groupName = "",
                autoTranslate = false
            )
        )

        val xml = OpmlManager.exportToOpmlXml(feeds = feeds, customLists = lists)
        val imported = OpmlManager.parseOpmlXml(xml)

        assertTrue(imported.listNames.contains("أخبار عربية"))
        assertTrue(imported.listNames.contains("Empty Custom List"))
        assertEquals(2, imported.feeds.size)

        val first = imported.feeds.first { it.xmlUrl == "https://example.org/arabic-rss.xml" }
        assertEquals("أخبار عربية", first.listName)
        assertEquals(true, first.autoTranslate)

        val second = imported.feeds.first { it.xmlUrl == "https://blog.example.net/atom.xml" }
        assertEquals("", second.listName)
        assertEquals(false, second.autoTranslate)
    }

    @Test
    fun `cleared read posts and older archive posts never reappear on sync`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.rssDao()
            val repo = RssRepository(dao = dao, settingsRepository = SettingsRepository(context))

            val feedId = dao.insertFeed(
                FeedEntity(url = "https://example.com/rss.xml", title = "Example News")
            )
            val initialFeed = dao.getFeedByIdOnce(feedId)!!

            val t1 = 1_760_000_000_000L
            val t2 = 1_760_003_600_000L
            val t3 = 1_760_007_200_000L

            val batch1 = ParsedRssFeed(
                feedUrl = initialFeed.url,
                siteUrl = "https://example.com",
                title = "Example News",
                iconUrl = "",
                items = listOf(
                    ParsedRssItem("id-3", "https://example.com/p3", "Post 3", "Body 3", null, t3, hasExplicitDate = true),
                    ParsedRssItem("id-2", "https://example.com/p2", "Post 2", "Body 2", null, t2, hasExplicitDate = true),
                    ParsedRssItem("id-1", "https://example.com/p1", "Post 1", "Body 1", null, t1, hasExplicitDate = true)
                )
            )

            // 1. Initial fetch adds 3 posts
            val addedInitial = repo.persistParsedArticles(initialFeed, batch1)
            assertEquals(3, addedInitial)
            assertEquals(3, dao.getArticlesByFeedIdOnce(feedId).size)

            // 2. Mark all read -> Clear Read History -> 0 posts remain in articles table
            repo.markAllRead()
            repo.clearReadHistory()
            assertEquals(0, dao.getArticlesByFeedIdOnce(feedId).size)

            // 3. Refresh feed containing the 3 cleared posts (even with changed UTM link!) + an older archive post (t0 < t3)
            val feedAfterFirstSync = dao.getFeedByIdOnce(feedId)!!
            val t0Older = 1_759_990_000_000L
            val batch2SameAndOlder = ParsedRssFeed(
                feedUrl = feedAfterFirstSync.url,
                siteUrl = "https://example.com",
                title = "Example News",
                iconUrl = "",
                items = listOf(
                    ParsedRssItem("id-3-modified", "https://example.com/p3?utm_source=rss", "Post 3", "Body 3", null, t3, hasExplicitDate = true),
                    ParsedRssItem("id-2", "https://example.com/p2", "Post 2", "Body 2", null, t2, hasExplicitDate = true),
                    ParsedRssItem("id-1", "https://example.com/p1", "Post 1", "Body 1", null, t1, hasExplicitDate = true),
                    ParsedRssItem("id-0-old", "https://example.com/p0", "Ancient Post", "Body 0", null, t0Older, hasExplicitDate = true)
                )
            )
            val addedAfterClear = repo.persistParsedArticles(feedAfterFirstSync, batch2SameAndOlder)
            assertEquals(0, addedAfterClear)
            assertEquals(0, dao.getArticlesByFeedIdOnce(feedId).size)

            // 4. Now a genuinely newer post (t4 > t3) is published at the top of the feed
            val feedAfterSecondSync = dao.getFeedByIdOnce(feedId)!!
            val t4Newer = 1_760_010_800_000L
            val batch3WithOneNew = batch2SameAndOlder.copy(
                items = listOf(
                    ParsedRssItem("id-4", "https://example.com/p4", "Brand New Post 4", "Body 4", null, t4Newer, hasExplicitDate = true)
                ) + batch2SameAndOlder.items
            )
            val addedNewOnly = repo.persistParsedArticles(feedAfterSecondSync, batch3WithOneNew)
            assertEquals(1, addedNewOnly)
            val remaining = dao.getArticlesByFeedIdOnce(feedId)
            assertEquals(1, remaining.size)
            assertEquals("Brand New Post 4", remaining.first().title)
        } finally {
            db.close()
        }
    }

    @Test
    fun `clear read returns backup for 5s undo and restores articles cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.rssDao()
            val repo = RssRepository(dao = dao, settingsRepository = SettingsRepository(context))
            val feedId = dao.insertFeed(FeedEntity(url = "https://example.com/undo.xml", title = "Undo Feed"))
            val feed = dao.getFeedByIdOnce(feedId)!!

            val batch = ParsedRssFeed(
                feedUrl = feed.url,
                siteUrl = "https://example.com",
                title = "Undo Feed",
                iconUrl = "",
                items = listOf(
                    ParsedRssItem("u-2", "https://example.com/u2", "Undo Post 2", "Body 2", null, 1_760_100_000_000L, true),
                    ParsedRssItem("u-1", "https://example.com/u1", "Undo Post 1", "Body 1", null, 1_760_050_000_000L, true)
                )
            )
            repo.persistParsedArticles(feed, batch)
            repo.markAllRead()

            val clearedBackup = repo.clearReadHistory()
            assertEquals(2, clearedBackup.size)
            assertEquals(0, dao.getArticlesByFeedIdOnce(feedId).size)

            repo.restoreClearedArticles(clearedBackup)
            val restored = dao.getArticlesByFeedIdOnce(feedId)
            assertEquals(2, restored.size)
            assertTrue(restored.all { it.isRead })
        } finally {
            db.close()
        }
    }

    @Test
    fun `gemini single paragraph mode collapses multi line and bullet output into one paragraph`() {
        val service = GeminiService()
        val raw = """
            ### Breaking News Title
            - First point with **bold** details.
            
            * Second paragraph explaining the context.
            
            Third paragraph concluding the story.
        """.trimIndent()
        val parsed = service.parseCleanBody(
            rawOutput = raw,
            originalTitle = "Breaking News Title",
            usedModel = "gemini-2.5-flash",
            singleParagraph = true
        )
        assertTrue(!parsed.cleanBody.contains("\n"))
        assertEquals(
            "First point with bold details. Second paragraph explaining the context. Third paragraph concluding the story.",
            parsed.cleanBody
        )
    }

    @Test
    fun `syncing unchanged feeds fetches live XML without false 304 lockouts while skipping all webpage and image downloads`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val feedUrl = "https://news.example.com/rss.xml"
        val requestedUrls = CopyOnWriteArrayList<String>()
        val ogPageRequests = AtomicInteger(0)
        var serverBuildTimestamp = "Tue, 27 Oct 2030 23:59:00 GMT" // Broken future Last-Modified!
        val brokenStaticEtag = "\"static-broken-etag\""
        var includeBrandNewPost = false

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                requestedUrls.add(url)

                if (url == feedUrl) {
                    // Simulate a buggy server/CDN that would falsely return 304 if conditional headers were sent!
                    if (req.header("If-None-Match") != null || req.header("If-Modified-Since") != null) {
                        return@addInterceptor Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(304)
                            .message("Not Modified")
                            .header("ETag", brokenStaticEtag)
                            .header("Last-Modified", serverBuildTimestamp)
                            .body("".toResponseBody("application/rss+xml".toMediaType()))
                            .build()
                    }

                    val newItemXml = if (includeBrandNewPost) {
                        """
                        <item>
                            <guid>post-new-99</guid>
                            <title>Fresh Breaking Story</title>
                            <link>https://news.example.com/articles/fresh-99</link>
                            <description>Short summary without inline image</description>
                            <pubDate>Tue, 27 Oct 2026 09:00:00 GMT</pubDate>
                        </item>
                        """.trimIndent()
                    } else {
                        ""
                    }

                    val existingItemsXml = (1..8).joinToString("\n") { i ->
                        """
                        <item>
                            <guid>existing-$i</guid>
                            <title>Existing Story $i</title>
                            <link>https://news.example.com/articles/existing-$i</link>
                            <description>Summary $i with no inline img tag</description>
                            <pubDate>Tue, 27 Oct 2026 0${9 - i}:00:00 GMT</pubDate>
                        </item>
                        """.trimIndent()
                    }

                    val xmlBody = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <rss version="2.0">
                            <channel>
                                <title>Example Wire</title>
                                <link>https://news.example.com</link>
                                <lastBuildDate>$serverBuildTimestamp</lastBuildDate>
                                $newItemXml
                                $existingItemsXml
                            </channel>
                        </rss>
                    """.trimIndent()

                    return@addInterceptor Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("ETag", brokenStaticEtag)
                        .header("Last-Modified", serverBuildTimestamp)
                        .body(xmlBody.toResponseBody("application/rss+xml".toMediaType()))
                        .build()
                } else if (url.startsWith("https://news.example.com/articles/")) {
                    ogPageRequests.incrementAndGet()
                    val html = """
                        <html>
                        <head>
                            <meta property="og:image" content="https://cdn.example.com/og-${url.substringAfterLast('/')}.jpg" />
                        </head>
                        <body><p>Large article page body</p></body>
                        </html>
                    """.trimIndent()
                    return@addInterceptor Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "text/html; charset=utf-8")
                        .body(html.toResponseBody("text/html".toMediaType()))
                        .build()
                } else {
                    throw AssertionError("Unexpected network call to $url during RSS sync! Images must never be fetched during sync.")
                }
            }
            .build()

        try {
            val dao = db.rssDao()
            val parser = SmartRssParser(httpClient = mockClient)
            val repo = RssRepository(
                dao = dao,
                settingsRepository = SettingsRepository(context),
                rssParser = parser
            )

            // Step 1: Add feed initially (fetches RSS + up to 5 head probes for initial imageless items, ZERO image binary downloads)
            val addedFeed = repo.addFeed(feedUrl, "", false, AppSettings()).getOrThrow()
            assertEquals(8, dao.getArticlesByFeedIdOnce(addedFeed.id).size)

            // Step 2: Refresh when nothing changed -> fetches ONLY the tiny RSS XML (1 request), ZERO og:image page requests!
            requestedUrls.clear()
            ogPageRequests.set(0)
            val syncUnchangedAdded = repo.syncAllFeeds(AppSettings()).getOrThrow()
            assertEquals(0, syncUnchangedAdded)
            assertEquals(listOf(feedUrl), requestedUrls.toList())
            assertEquals(0, ogPageRequests.get())

            // Step 3: Now 1 genuinely brand-new post appears EVEN THOUGH server keeps static ETag & future Last-Modified!
            // Sync MUST still detect the new post immediately and probe og:image ONLY for that 1 new post!
            includeBrandNewPost = true
            serverBuildTimestamp = "Tue, 27 Oct 2030 23:59:00 GMT"
            requestedUrls.clear()
            ogPageRequests.set(0)
            val syncOneNewAdded = repo.syncAllFeeds(AppSettings()).getOrThrow()
            assertEquals(1, syncOneNewAdded)
            assertEquals(1, ogPageRequests.get())
            assertEquals(
                listOf(feedUrl, "https://news.example.com/articles/fresh-99"),
                requestedUrls.toList()
            )
            val latestArticle = dao.getArticlesByFeedIdOnce(addedFeed.id).first { it.guid == "post-new-99" }
            assertEquals("https://cdn.example.com/og-fresh-99.jpg", latestArticle.imageUrl)
        } finally {
            db.close()
        }
    }

    @Test
    fun `syncAllFeeds updates all feeds in one batch without exception and sorts strictly by feed publication time`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val hitFeedUrls = CopyOnWriteArrayList<String>()
        val transientFeedAttempts = AtomicInteger(0)
        var phaseTwoRefresh = false

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                hitFeedUrls.add(url)

                fun xmlResponse(code: Int, body: String): Response = Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(body.toResponseBody("application/xml".toMediaType()))
                    .build()

                when {
                    // Feed 1: Atom feed with nested <source><updated> (must NOT overwrite <entry><published>!)
                    url == "https://f1.example.com/atom.xml" -> {
                        val extra = if (phaseTwoRefresh) {
                            """
                            <entry>
                                <id>f1-new</id>
                                <title>Feed1 Atom 10:30</title>
                                <link href="https://f1.example.com/2" />
                                <published>2026-10-20T10:30:00.123456Z</published>
                                <updated>2026-10-20T11:59:00Z</updated>
                                <source>
                                    <title>Syndicated Source</title>
                                    <updated>2026-10-20T12:00:00Z</updated>
                                </source>
                                <summary><img src="https://cdn.example.com/f1.jpg"/>Body</summary>
                            </entry>
                            """.trimIndent()
                        } else ""
                        xmlResponse(
                            200,
                            """
                            <?xml version="1.0" encoding="utf-8"?>
                            <feed xmlns="http://www.w3.org/2005/Atom">
                                <title>Feed 1 Atom</title>
                                $extra
                                <entry>
                                    <id>f1-base</id>
                                    <title>Feed1 Base 08:00</title>
                                    <link href="https://f1.example.com/1" />
                                    <published>2026-10-20T08:00:00Z</published>
                                    <summary><img src="https://cdn.example.com/f1b.jpg"/>Base</summary>
                                </entry>
                            </feed>
                            """.trimIndent()
                        )
                    }

                    // Feed 2: Sticky post at slot #0 + editorial-lag post drafted 15m before previous post!
                    url == "https://f2.example.com/rss.xml" -> {
                        val editorialAndStickyItems = if (phaseTwoRefresh) {
                            """
                            <item>
                                <guid>f2-sticky</guid>
                                <title>Pinned Welcome Post</title>
                                <link>https://f2.example.com/pinned</link>
                                <description>&lt;img src="https://cdn.example.com/pin.jpg"/&gt;Pinned</description>
                                <pubDate>Mon, 19 Oct 2026 06:00:00 GMT</pubDate>
                            </item>
                            <item>
                                <guid>f2-editorial-lag</guid>
                                <title>Feed2 Editorial 09:45</title>
                                <link>https://f2.example.com/ed-945</link>
                                <description>&lt;img src="https://cdn.example.com/f2.jpg"/&gt;Drafted at 09:45, approved after 10:00 post</description>
                                <pubDate>Tue, 20 Oct 2026 12:45:00 +03:00</pubDate>
                            </item>
                            """.trimIndent()
                        } else {
                            """
                            <item>
                                <guid>f2-sticky</guid>
                                <title>Pinned Welcome Post</title>
                                <link>https://f2.example.com/pinned</link>
                                <description>&lt;img src="https://cdn.example.com/pin.jpg"/&gt;Pinned</description>
                                <pubDate>Mon, 19 Oct 2026 06:00:00 GMT</pubDate>
                            </item>
                            """.trimIndent()
                        }
                        xmlResponse(
                            200,
                            """
                            <rss version="2.0"><channel><title>Feed 2 Sticky &amp; Editorial</title>
                            $editorialAndStickyItems
                            <item>
                                <guid>f2-1000</guid>
                                <title>Feed2 Morning 10:00</title>
                                <link>https://f2.example.com/1000</link>
                                <description>&lt;img src="https://cdn.example.com/f2m.jpg"/&gt;Morning</description>
                                <pubDate>Tue, 20 Oct 2026 10:00:00 GMT</pubDate>
                            </item>
                            </channel></rss>
                            """.trimIndent()
                        )
                    }

                    // Feed 3: Transient HTTP 503 on first attempt during phase 2, succeeds on automatic retry!
                    url == "https://f3.example.com/rdf.xml" -> {
                        if (phaseTwoRefresh && transientFeedAttempts.incrementAndGet() == 1) {
                            return@addInterceptor xmlResponse(503, "Service Temporarily Unavailable")
                        }
                        val newRdf = if (phaseTwoRefresh) {
                            """
                            <item>
                                <guid>f3-new</guid>
                                <title>Feed3 Breaking 11:15</title>
                                <link>https://f3.example.com/new</link>
                                <description>&lt;img src="https://cdn.example.com/f3.jpg"/&gt;Breaking</description>
                                <dc:date>2026-10-20T11:15:00Z</dc:date>
                            </item>
                            """.trimIndent()
                        } else ""
                        xmlResponse(
                            200,
                            """
                            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <channel><title>Feed 3 RDF</title>
                            $newRdf
                            <item>
                                <guid>f3-base</guid>
                                <title>Feed3 Base 07:30</title>
                                <link>https://f3.example.com/base</link>
                                <description>&lt;img src="https://cdn.example.com/f3b.jpg"/&gt;Base</description>
                                <dc:date>2026-10-20T07:30:00Z</dc:date>
                            </item>
                            </channel></rss>
                            """.trimIndent()
                        )
                    }

                    // Feed 4: Dead feed returning 404 (must NOT prevent Feeds 1..12 from refreshing!)
                    url == "https://dead.example.com/rss.xml" -> xmlResponse(404, "Not Found")

                    // Feeds 5..12: 8 more concurrent feeds to verify >6 feeds all refresh in one wave
                    url.startsWith("https://bulk.example.com/feed-") -> {
                        val idx = url.substringAfter("feed-").substringBefore(".xml").toInt()
                        val minStr = String.format("%02d", idx)
                        val phaseTwoItem = if (phaseTwoRefresh) {
                            """
                            <item>
                                <guid>bulk-$idx-new</guid>
                                <title>Bulk $idx Post 10:$minStr</title>
                                <link>https://bulk.example.com/$idx/new</link>
                                <description>&lt;img src="https://cdn.example.com/b$idx.jpg"/&gt;Body</description>
                                <pubDate>20 Oct 2026 10:$minStr:00 GMT</pubDate>
                            </item>
                            """.trimIndent()
                        } else ""
                        xmlResponse(
                            200,
                            """
                            <rss version="2.0"><channel><title>Bulk Feed $idx</title>
                            $phaseTwoItem
                            <item>
                                <guid>bulk-$idx-base</guid>
                                <title>Bulk $idx Base 07:$minStr</title>
                                <link>https://bulk.example.com/$idx/base</link>
                                <description>&lt;img src="https://cdn.example.com/b$idx-0.jpg"/&gt;Base</description>
                                <pubDate>20 Oct 2026 07:$minStr:00 GMT</pubDate>
                            </item>
                            </channel></rss>
                            """.trimIndent()
                        )
                    }

                    else -> xmlResponse(404, "Unknown")
                }
            }
            .build()

        try {
            val dao = db.rssDao()
            val parser = SmartRssParser(httpClient = mockClient)
            val repo = RssRepository(
                dao = dao,
                settingsRepository = SettingsRepository(context),
                rssParser = parser
            )

            // Register all 12 feeds (including 1 dead feed)
            val allUrls = listOf(
                "https://f1.example.com/atom.xml",
                "https://f2.example.com/rss.xml",
                "https://f3.example.com/rdf.xml",
                "https://dead.example.com/rss.xml"
            ) + (5..12).map { "https://bulk.example.com/feed-$it.xml" }

            allUrls.forEachIndexed { idx, u ->
                dao.insertFeed(FeedEntity(url = u, title = "Feed ${idx + 1}"))
            }

            // Initial syncAllFeeds: all 12 feeds must be checked; 11 valid feeds populate initial items
            val initialAdded = repo.syncAllFeeds(AppSettings()).getOrThrow()
            assertEquals(12, initialAdded) // f1(1) + f2(2) + f3(1) + bulk 5..12 (8) = 12

            // Phase 2 refresh: every feed publishes a new item (including sticky/editorial-lag on f2 & 503 retry on f3)
            hitFeedUrls.clear()
            phaseTwoRefresh = true
            val refreshAdded = repo.syncAllFeeds(AppSettings()).getOrThrow()

            // Every single one of the 12 feeds was checked without exception
            assertTrue(allUrls.all { it in hitFeedUrls })
            // f1(1) + f2(1 editorial-lag below sticky) + f3(1 after 503 retry) + bulk 5..12 (8) = 11 new items!
            assertEquals(11, refreshAdded)

            // Verify strict descending chronological ordering across ALL feeds by publication time!
            val orderedStream = dao.observeAllArticlesWithFeed().first()
            assertEquals(23, orderedStream.size)

            for (i in 0 until orderedStream.lastIndex) {
                val current = orderedStream[i].article
                val next = orderedStream[i + 1].article
                assertTrue(
                    "Expected ${current.title} (${current.publishedAt}) >= ${next.title} (${next.publishedAt})",
                    current.publishedAt >= next.publishedAt
                )
            }

            // Top items must match exact chronological order:
            // 1) Feed3 Breaking 11:15Z
            // 2) Feed1 Atom 10:30Z (used <published> 10:30, NOT <updated> 11:59 or <source><updated> 12:00!)
            // 3..10) Bulk 12..5 (10:12Z down to 10:05Z)
            // 11) Feed2 Morning 10:00Z
            // 12) Feed2 Editorial 09:45Z (12:45 +03:00 == 09:45Z!)
            assertEquals("Feed3 Breaking 11:15", orderedStream[0].article.title)
            assertEquals("Feed1 Atom 10:30", orderedStream[1].article.title)
            assertEquals("Bulk 12 Post 10:12", orderedStream[2].article.title)
            assertEquals("Bulk 5 Post 10:05", orderedStream[9].article.title)
            assertEquals("Feed2 Morning 10:00", orderedStream[10].article.title)
            assertEquals("Feed2 Editorial 09:45", orderedStream[11].article.title)
        } finally {
            db.close()
        }
    }

    @Test
    fun `ollama free models list and default model match exact priority order`() {
        val expectedOrder = listOf(
            "gpt-oss:120b",
            "gpt-oss:20b",
            "gemma4:31b",
            "beam",
            "nemotron-3-nano:30b",
            "nemotron-3-super",
            "nemotron-3-ultra"
        )
        assertEquals(expectedOrder, AppSettings.DEFAULT_OLLAMA_MODELS)
        assertEquals("gpt-oss:120b", AppSettings().selectedOllamaModel)
    }

    @Test
    fun `ollama summarization uses exact curl schema Bearer auth stream false and identical Gemini prompt with failover`() = runBlocking {
        val triedModels = CopyOnWriteArrayList<String>()
        val capturedAuthHeaders = CopyOnWriteArrayList<String>()
        val capturedPayloads = CopyOnWriteArrayList<String>()
        var switchedToModel = ""

        val mockOllamaClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                assertEquals("https://ollama.com/api/chat", req.url.toString())
                assertEquals("POST", req.method)
                capturedAuthHeaders.add(req.header("Authorization").orEmpty())

                val buf = Buffer()
                req.body!!.writeTo(buf)
                val bodyText = buf.readUtf8()
                capturedPayloads.add(bodyText)

                val jsonObj = NetworkClient.json.parseToJsonElement(bodyText).jsonObject
                val model = jsonObj["model"]!!.jsonPrimitive.content
                triedModels.add(model)

                // stream: false MUST always be serialized explicitly
                assertFalse(jsonObj["stream"]!!.jsonPrimitive.boolean)

                when (model) {
                    "gpt-oss:120b" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .body("""{"error":"rate limit"}""".toResponseBody("application/json".toMediaType()))
                        .build()

                    "gpt-oss:20b" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Overloaded")
                        .body("""{"error":"overloaded"}""".toResponseBody("application/json".toMediaType()))
                        .build()

                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            """
                            {
                              "model": "$model",
                              "message": {
                                "role": "assistant",
                                "content": "<think>Analyzing key points...</think>\n### Sample Title\n- **ملخص** شامل ودقيق في فقرة واحدة.\n\nيغطي جميع تفاصيل الخبر بوضوح."
                              },
                              "done": true
                            }
                            """.trimIndent().toResponseBody("application/json".toMediaType())
                        )
                        .build()
                }
            }
            .build()

        val ollamaService = OllamaService(httpClient = mockOllamaClient)
        val title = "Sample Title"
        val articleBody = "Full article source content with important details that exceed 500 chars. ".repeat(10)

        val expectedGeminiPrompt = GeminiService.buildArticleAiPrompt(
            articleTitle = title,
            articleBody = articleBody,
            mode = ReaderAiMode.SUMMARIZE,
            targetLanguage = TargetLanguage.ARABIC
        )

        val output = ollamaService.summarizeArticle(
            apiKey = "Bearer test_placeholder_token_only",
            articleTitle = title,
            articleBody = articleBody,
            targetLanguage = TargetLanguage.ARABIC,
            availableModels = AppSettings.DEFAULT_OLLAMA_MODELS,
            selectedModel = "gpt-oss:120b",
            onModelSwitched = { switchedToModel = it }
        ).getOrThrow()

        // Verified failover sequence: gpt-oss:120b -> gpt-oss:20b -> gemma4:31b
        assertEquals(listOf("gpt-oss:120b", "gpt-oss:20b", "gemma4:31b"), triedModels.toList())
        assertEquals("gemma4:31b", output.usedModel)
        assertEquals("gemma4:31b", switchedToModel)
        assertTrue(capturedAuthHeaders.all { it == "Bearer test_placeholder_token_only" })

        // Verify prompt inside messages[0].content is 100% identical to Gemini's summarization prompt
        val firstJson = NetworkClient.json.parseToJsonElement(capturedPayloads.first()).jsonObject
        val messages = firstJson["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        val msgObj = messages[0].jsonObject
        assertEquals("user", msgObj["role"]!!.jsonPrimitive.content)
        assertEquals(expectedGeminiPrompt, msgObj["content"]!!.jsonPrimitive.content)

        // Verify thinking blocks, duplicate title, bullets, and newlines are collapsed into one single paragraph
        assertEquals("ملخص شامل ودقيق في فقرة واحدة. يغطي جميع تفاصيل الخبر بوضوح.", output.cleanBody)
    }

    @Test
    fun `rssRepository routes summarization to Ollama when selected and caches per summary provider`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val ollamaCalls = AtomicInteger(0)

        val mockOllamaClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                ollamaCalls.incrementAndGet()
                val req = chain.request()
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"model":"gpt-oss:120b","message":{"role":"assistant","content":"فقرة تلخيصية واحدة من أولاما بدون فواصل."},"done":true}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()

        try {
            val dao = db.rssDao()
            val repo = RssRepository(
                dao = dao,
                settingsRepository = SettingsRepository(context),
                ollamaService = OllamaService(httpClient = mockOllamaClient)
            )
            val feedId = dao.insertFeed(FeedEntity(url = "https://example.com/s.xml", title = "Summary Feed"))
            val feed = dao.getFeedByIdOnce(feedId)!!
            val longContent = "Detailed investigative report paragraph with complete facts. ".repeat(12)
            repo.persistParsedArticles(
                feed,
                ParsedRssFeed(
                    feedUrl = feed.url,
                    siteUrl = "https://example.com",
                    title = "Summary Feed",
                    iconUrl = "",
                    items = listOf(
                        ParsedRssItem("sum-1", "https://example.com/sum-1", "Report", longContent, null, 1_760_200_000_000L, true)
                    )
                )
            )
            val article = dao.getArticlesByFeedIdOnce(feedId).single()
            val ollamaSettings = AppSettings(
                summaryProvider = SummaryProvider.OLLAMA,
                selectedOllamaModel = "gpt-oss:120b",
                ollamaApiKeyOverride = "unit_test_key_placeholder"
            )

            val firstRun = repo.processArticleInReader(
                articleId = article.id,
                mode = ReaderAiMode.SUMMARIZE,
                settings = ollamaSettings
            ).getOrThrow()
            assertEquals("فقرة تلخيصية واحدة من أولاما بدون فواصل.", firstRun.cleanBody)
            assertEquals("gpt-oss:120b", firstRun.usedModel)
            assertEquals(1, ollamaCalls.get())

            // Second call with same provider serves cached Ollama summary with 0 extra API calls
            val cachedRun = repo.processArticleInReader(
                articleId = article.id,
                mode = ReaderAiMode.SUMMARIZE,
                settings = ollamaSettings
            ).getOrThrow()
            assertEquals("فقرة تلخيصية واحدة من أولاما بدون فواصل.", cachedRun.cleanBody)
            assertEquals(1, ollamaCalls.get())
        } finally {
            db.close()
        }
    }

    @Test
    fun `syncAllFeeds updates 100 percent of 25 feeds across rate limited shared hosts pinned posts broken OG pages and Wave 2 recovery`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val activeSharedHostCalls = AtomicInteger(0)
        val maxObservedSharedHostConcurrency = AtomicInteger(0)
        val wave2FlakyFeedHits = AtomicInteger(0)
        var refreshRound = 1

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()

                fun resp(code: Int, body: String): Response = Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Error")
                    .body(body.toResponseBody("application/rss+xml".toMediaType()))
                    .build()

                when {
                    // 12 feeds sharing the exact same rate-limited domain (rejects with 429 if > 2 concurrent!)
                    url.startsWith("https://shared-burst.example.org/channel-") -> {
                        val concurrent = activeSharedHostCalls.incrementAndGet()
                        maxObservedSharedHostConcurrency.updateAndGet { prev -> maxOf(prev, concurrent) }
                        try {
                            Thread.sleep(15L)
                            if (concurrent > 2) {
                                return@addInterceptor resp(429, "Too Many Concurrent Requests on Host")
                            }
                            val ch = url.substringAfter("channel-").substringBefore(".xml").toInt()
                            val extra = if (refreshRound == 2) {
                                """
                                <item>
                                    <guid>sh-$ch-r2</guid>
                                    <title>Shared $ch Round 2</title>
                                    <link>https://shared-burst.example.org/$ch/r2</link>
                                    <description>&lt;img src="https://cdn.example.org/sh$ch.jpg"/&gt;Body</description>
                                    <pubDate>Wed, 28 Oct 2026 12:${String.format("%02d", ch)}:00 GMT</pubDate>
                                </item>
                                """.trimIndent()
                            } else ""
                            resp(
                                200,
                                """
                                <rss version="2.0"><channel><title>Shared $ch</title>
                                $extra
                                <item>
                                    <guid>sh-$ch-r1</guid>
                                    <title>Shared $ch Round 1</title>
                                    <link>https://shared-burst.example.org/$ch/r1</link>
                                    <description>&lt;img src="https://cdn.example.org/sh$ch-1.jpg"/&gt;Body</description>
                                    <pubDate>Wed, 28 Oct 2026 08:${String.format("%02d", ch)}:00 GMT</pubDate>
                                </item>
                                </channel></rss>
                                """.trimIndent()
                            )
                        } finally {
                            activeSharedHostCalls.decrementAndGet()
                        }
                    }

                    // 10 distinct regional feeds
                    url.startsWith("https://portal-") && url.endsWith(".example.net/rss.xml") -> {
                        val idx = url.substringAfter("https://portal-").substringBefore(".example.net").toInt()
                        val extra = if (refreshRound == 2) {
                            """
                            <item>
                                <guid>p-$idx-r2</guid>
                                <title>Portal $idx Round 2</title>
                                <link>https://portal-$idx.example.net/r2</link>
                                <description>Imageless post whose OG page returns 500</description>
                                <pubDate>Wed 28 Oct 2026 13:${String.format("%02d", idx)}:00 GMT</pubDate>
                            </item>
                            """.trimIndent()
                        } else ""
                        resp(
                            200,
                            """
                            <rss version="2.0"><channel><title>Portal $idx</title>
                            $extra
                            <item>
                                <guid>p-$idx-r1</guid>
                                <title>Portal $idx Round 1</title>
                                <link>https://portal-$idx.example.net/r1</link>
                                <description>&lt;img src="https://cdn.example.net/p$idx.jpg"/&gt;Body</description>
                                <pubDate>Wed 28 Oct 2026 09:${String.format("%02d", idx)}:00 GMT</pubDate>
                            </item>
                            </channel></rss>
                            """.trimIndent()
                        )
                    }

                    // Broken article pages for OG probe -> MUST NOT prevent Portal articles from saving!
                    url.contains(".example.net/r2") -> resp(504, "Gateway Timeout on Article HTML")

                    // Feed 23: Old pinned post at slot #0 + brand-new undated/rotated posts below it on Round 2
                    url == "https://pinned-rotated.example.io/feed.xml" -> {
                        val itemsXml = if (refreshRound == 1) {
                            """
                            <item>
                                <guid>pin-2025</guid>
                                <title>Pinned Channel Rules</title>
                                <link>https://pinned-rotated.example.io/rules</link>
                                <description>&lt;img src="https://cdn.example.io/pin.jpg"/&gt;Rules</description>
                                <pubDate>Wed, 01 Jan 2026 00:00:00 GMT</pubDate>
                            </item>
                            <item>
                                <guid>old-rotated-1</guid>
                                <title>Yesterday News</title>
                                <link>https://pinned-rotated.example.io/y1</link>
                                <description>&lt;img src="https://cdn.example.io/y1.jpg"/&gt;Yesterday</description>
                                <pubDate>Tue, 27 Oct 2026 10:00:00 GMT</pubDate>
                            </item>
                            """.trimIndent()
                        } else {
                            // Yesterday's post rotated completely out; pinned post stays at #0, 2 brand-new posts below it!
                            """
                            <item>
                                <guid>pin-2025</guid>
                                <title>Pinned Channel Rules</title>
                                <link>https://pinned-rotated.example.io/rules</link>
                                <description>&lt;img src="https://cdn.example.io/pin.jpg"/&gt;Rules</description>
                                <pubDate>Wed, 01 Jan 2026 00:00:00 GMT</pubDate>
                            </item>
                            <item>
                                <guid>fresh-b</guid>
                                <title>Fresh Below Sticky B</title>
                                <link>https://pinned-rotated.example.io/fb</link>
                                <description>&lt;img src="https://cdn.example.io/fb.jpg"/&gt;FB</description>
                                <pubDate>Wed, 28 Oct 2026 14:10:00 GMT</pubDate>
                            </item>
                            <item>
                                <guid>fresh-a</guid>
                                <title>Fresh Below Sticky A</title>
                                <link>https://pinned-rotated.example.io/fa</link>
                                <description>&lt;img src="https://cdn.example.io/fa.jpg"/&gt;FA</description>
                                <pubDate>Wed, 28 Oct 2026 14:05:00 GMT</pubDate>
                            </item>
                            """.trimIndent()
                        }
                        resp(200, "<rss version=\"2.0\"><channel><title>Pinned Rotated</title>$itemsXml</channel></rss>")
                    }

                    // Feed 24: Fails ALL 3 attempts in Wave 1 during Round 2, then succeeds in Wave 2 recovery pass!
                    url == "https://flaky-wave2.example.io/rss.xml" -> {
                        if (refreshRound == 2 && wave2FlakyFeedHits.incrementAndGet() <= 3) {
                            return@addInterceptor resp(503, "Temporary Wave 1 Outage")
                        }
                        val extra = if (refreshRound == 2) {
                            """
                            <item>
                                <guid>w2-recovered</guid>
                                <title>Wave 2 Recovered Story</title>
                                <link>https://flaky-wave2.example.io/w2</link>
                                <description>&lt;img src="https://cdn.example.io/w2.jpg"/&gt;Recovered</description>
                                <pubDate>Wed, 28 Oct 2026 14:30:00 GMT</pubDate>
                            </item>
                            """.trimIndent()
                        } else ""
                        resp(
                            200,
                            """
                            <rss version="2.0"><channel><title>Flaky Wave 2</title>
                            $extra
                            <item>
                                <guid>w2-base</guid>
                                <title>Wave 2 Base</title>
                                <link>https://flaky-wave2.example.io/base</link>
                                <description>&lt;img src="https://cdn.example.io/w2b.jpg"/&gt;Base</description>
                                <pubDate>Wed, 28 Oct 2026 07:00:00 GMT</pubDate>
                            </item>
                            </channel></rss>
                            """.trimIndent()
                        )
                    }

                    // Feed 25: Blocks Browser UA with 403 on attempt 0, succeeds on attempt 1 via RSS_READER_USER_AGENT!
                    url == "https://ua-strict.example.io/rss" -> {
                        val ua = req.header("User-Agent").orEmpty()
                        if (!ua.startsWith("CrimsonFeed/")) {
                            return@addInterceptor resp(403, "Blocked Generic Browser UA")
                        }
                        val extra = if (refreshRound == 2) {
                            """
                            <item>
                                <guid>ua-r2</guid>
                                <title>UA Fallback Round 2</title>
                                <link>https://ua-strict.example.io/r2</link>
                                <description>&lt;img src="https://cdn.example.io/ua2.jpg"/&gt;UA2</description>
                                <pubDate>Wed, 28 Oct 2026 14:45:00 GMT</pubDate>
                            </item>
                            """.trimIndent()
                        } else ""
                        resp(
                            200,
                            """
                            <rss version="2.0"><channel><title>UA Strict</title>
                            $extra
                            <item>
                                <guid>ua-r1</guid>
                                <title>UA Fallback Round 1</title>
                                <link>https://ua-strict.example.io/r1</link>
                                <description>&lt;img src="https://cdn.example.io/ua1.jpg"/&gt;UA1</description>
                                <pubDate>Wed, 28 Oct 2026 06:00:00 GMT</pubDate>
                            </item>
                            </channel></rss>
                            """.trimIndent()
                        )
                    }

                    else -> resp(404, "Not Found")
                }
            }
            .build()

        try {
            val dao = db.rssDao()
            val parser = SmartRssParser(httpClient = mockClient, ogClient = mockClient)
            val repo = RssRepository(
                dao = dao,
                settingsRepository = SettingsRepository(context),
                rssParser = parser
            )

            val all25Urls = (1..12).map { "https://shared-burst.example.org/channel-$it.xml" } +
                (1..10).map { "https://portal-$it.example.net/rss.xml" } +
                listOf(
                    "https://pinned-rotated.example.io/feed.xml",
                    "https://flaky-wave2.example.io/rss.xml",
                    "https://ua-strict.example.io/rss"
                )
            assertEquals(25, all25Urls.size)

            all25Urls.forEachIndexed { idx, url ->
                dao.insertFeed(FeedEntity(url = url, title = "Feed #${idx + 1}"))
            }

            // Round 1: Initial sync must populate all 25 feeds (24 * 1 + 1 * 2 = 26 articles)
            val round1Added = repo.syncAllFeeds(AppSettings()).getOrThrow()
            assertEquals(26, round1Added)
            assertTrue("Per-host semaphore must cap concurrency <= 2", maxObservedSharedHostConcurrency.get() <= 2)

            // Round 2: Every single one of the 25 feeds publishes new items (12 + 10 + 2 + 1 + 1 = 26 new items!)
            refreshRound = 2
            val round2Added = repo.syncAllFeeds(AppSettings()).getOrThrow()
            assertEquals(26, round2Added)

            // Verify every single feed in DB has all expected articles with zero skipped feeds!
            val feedsAfter = dao.getAllFeedsOnce()
            assertEquals(25, feedsAfter.size)
            for (f in feedsAfter) {
                val count = dao.getArticlesByFeedIdOnce(f.id).size
                val expectedCount = if (f.url.contains("pinned-rotated")) 4 else 2
                assertEquals("Feed ${f.url} missed posts!", expectedCount, count)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `ollama fetches free models dynamically from ollama txt and sets gemma as default when present`() = runBlocking {
        assertEquals(
            "https://raw.githubusercontent.com/bidjadraft/Free-Models/refs/heads/main/ollama.txt",
            OllamaService.FREE_OLLAMA_MODELS_URL
        )

        var remoteTxtBody = """
            # Free Ollama Cloud Models
            1. gpt-oss:120b | Flagship
            2. deepseek-v3.1:671b-cloud
            - ollama/gemma4:31b # Google Gemma
            * qwen3:32b
            beam
        """.trimIndent()

        val mockHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                assertEquals(OllamaService.FREE_OLLAMA_MODELS_URL, req.url.toString())
                assertEquals("no-cache", req.header("Cache-Control"))
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(remoteTxtBody.toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val settingsRepo = SettingsRepository(context)
            val ollamaService = OllamaService(httpClient = mockHttpClient)
            val rssRepo = RssRepository(
                dao = db.rssDao(),
                settingsRepository = settingsRepo,
                ollamaService = ollamaService
            )

            // 1. Fetch ollama.txt where gemma4:31b is at index 2 -> gemma4:31b MUST become default selectedOllamaModel!
            val fetchedWithGemma = rssRepo.refreshFreeOllamaModels().getOrThrow()
            assertEquals(
                listOf("gpt-oss:120b", "deepseek-v3.1:671b-cloud", "gemma4:31b", "qwen3:32b", "beam"),
                fetchedWithGemma
            )
            val settingsAfterGemmaSync = settingsRepo.settingsFlow.first()
            assertEquals(fetchedWithGemma, settingsAfterGemmaSync.availableOllamaModels)
            assertEquals("gemma4:31b", settingsAfterGemmaSync.selectedOllamaModel)
            assertTrue(settingsAfterGemmaSync.lastOllamaModelsSyncMs > 0L)

            // 2. If ollama.txt updates WITHOUT any gemma model (and user did not manually lock another choice),
            //    default falls back cleanly to the first model in ollama.txt
            remoteTxtBody = """
                qwen3:32b
                gpt-oss:20b
            """.trimIndent()
            val fetchedWithoutGemma = rssRepo.refreshFreeOllamaModels().getOrThrow()
            assertEquals(listOf("qwen3:32b", "gpt-oss:20b"), fetchedWithoutGemma)
            assertEquals("qwen3:32b", settingsRepo.settingsFlow.first().selectedOllamaModel)

            // 3. When a gemma model reappears in ollama.txt (e.g. gemma3:27b), it automatically becomes default again!
            remoteTxtBody = """
                qwen3:32b
                gemma3:27b
                gpt-oss:120b
            """.trimIndent()
            rssRepo.refreshFreeOllamaModels().getOrThrow()
            assertEquals("gemma3:27b", settingsRepo.settingsFlow.first().selectedOllamaModel)

            // 4. Explicit manual user choice in Settings is preserved if still in list
            settingsRepo.setSelectedOllamaModel("gpt-oss:120b", userInitiated = true)
            rssRepo.refreshFreeOllamaModels().getOrThrow()
            assertEquals("gpt-oss:120b", settingsRepo.settingsFlow.first().selectedOllamaModel)
        } finally {
            db.close()
        }
    }
}

