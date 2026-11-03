package com.example.data.opml

import android.util.Xml
import com.example.data.local.CustomListEntity
import com.example.data.local.FeedEntity
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OpmlFeedEntry(
    val title: String,
    val xmlUrl: String,
    val htmlUrl: String = "",
    val listName: String = "",
    val autoTranslate: Boolean = false
)

data class OpmlImportResult(
    val listNames: List<String>,
    val feeds: List<OpmlFeedEntry>
)

object OpmlManager {

    /**
     * Generates a standard OPML 2.0 XML document preserving Custom Lists as folders.
     */
    fun exportToOpmlXml(
        feeds: List<FeedEntity>,
        customLists: List<CustomListEntity>
    ): String {
        val dateCreated = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("""  <head>""")
        sb.appendLine("""    <title>CrimsonFeed Subscriptions</title>""")
        sb.appendLine("""    <dateCreated>$dateCreated</dateCreated>""")
        sb.appendLine("""  </head>""")
        sb.appendLine("""  <body>""")

        val groupedFeeds = feeds.groupBy { it.groupName.trim() }
        val allListNames = (customLists.map { it.name.trim() } + groupedFeeds.keys)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }

        // Export Custom Lists (including empty custom lists so list structure is preserved)
        for (listName in allListNames) {
            val escapedList = escapeXmlAttr(listName)
            val matchingFeeds = feeds.filter { it.groupName.trim().equals(listName, ignoreCase = true) }
            if (matchingFeeds.isEmpty()) {
                sb.appendLine("""    <outline text="$escapedList" title="$escapedList" />""")
            } else {
                sb.appendLine("""    <outline text="$escapedList" title="$escapedList">""")
                matchingFeeds.forEach { feed ->
                    sb.appendLine("      " + buildFeedOutlineTag(feed))
                }
                sb.appendLine("""    </outline>""")
            }
        }

        // Export ungrouped top-level feeds
        feeds.filter { it.groupName.isBlank() }.forEach { feed ->
            sb.appendLine("    " + buildFeedOutlineTag(feed))
        }

        sb.appendLine("""  </body>""")
        sb.appendLine("""</opml>""")
        return sb.toString()
    }

    private fun buildFeedOutlineTag(feed: FeedEntity): String {
        val t = escapeXmlAttr(feed.title.ifBlank { feed.url })
        val xml = escapeXmlAttr(feed.url)
        val html = escapeXmlAttr(feed.siteUrl)
        val autoAttr = if (feed.autoTranslate) """ autoTranslate="true"""" else ""
        return """<outline type="rss" text="$t" title="$t" xmlUrl="$xml" htmlUrl="$html"$autoAttr />"""
    }

    /**
     * Parses any OPML 1.0 / 2.0 XML document, preserving nested folder outlines as Custom Lists.
     */
    fun parseOpmlXml(rawXml: String): OpmlImportResult {
        val idx = rawXml.indexOf('<')
        val sanitized = (if (idx > 0) rawXml.substring(idx) else rawXml)
            .replace(Regex("""&(?!(?:[a-zA-Z][a-zA-Z0-9]+|#[0-9]+|#x[0-9a-fA-F]+);)"""), "&amp;")

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(sanitized))

        val folderStack = ArrayDeque<String?>() // null = feed outline, non-null = folder/list outline
        val discoveredLists = linkedSetOf<String>()
        val discoveredFeeds = mutableListOf<OpmlFeedEntry>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = getAttrIgnoreCase(parser, "xmlUrl")
                            ?: getAttrIgnoreCase(parser, "url")
                        val htmlUrl = getAttrIgnoreCase(parser, "htmlUrl").orEmpty()
                        val title = (getAttrIgnoreCase(parser, "title")
                            ?: getAttrIgnoreCase(parser, "text")).orEmpty().trim()
                        val categoryAttr = getAttrIgnoreCase(parser, "category")?.trim().orEmpty()
                            .removePrefix("/")
                            .substringBefore('/')
                            .trim()
                        val autoTrans = getAttrIgnoreCase(parser, "autoTranslate")
                            ?.equals("true", ignoreCase = true) == true

                        if (!xmlUrl.isNullOrBlank()) {
                            val activeFolder = folderStack.lastOrNull { !it.isNullOrBlank() }
                                ?: categoryAttr
                            if (activeFolder.isNotBlank()) {
                                discoveredLists.add(activeFolder)
                            }
                            discoveredFeeds.add(
                                OpmlFeedEntry(
                                    title = title.ifBlank { xmlUrl.trim() },
                                    xmlUrl = xmlUrl.trim(),
                                    htmlUrl = htmlUrl.trim(),
                                    listName = activeFolder,
                                    autoTranslate = autoTrans
                                )
                            )
                            folderStack.addLast(null)
                        } else {
                            // Folder / Custom List outline
                            val folderName = title
                            if (folderName.isNotBlank()) {
                                discoveredLists.add(folderName)
                            }
                            folderStack.addLast(folderName.takeIf { it.isNotBlank() })
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true) && folderStack.isNotEmpty()) {
                        folderStack.removeLast()
                    }
                }
            }
            eventType = parser.next()
        }

        return OpmlImportResult(
            listNames = discoveredLists.toList(),
            feeds = discoveredFeeds.distinctBy { it.xmlUrl }
        )
    }

    private fun getAttrIgnoreCase(parser: XmlPullParser, target: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).equals(target, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun escapeXmlAttr(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "&apos;")
    }
}
