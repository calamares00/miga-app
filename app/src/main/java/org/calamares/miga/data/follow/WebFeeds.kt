package org.calamares.miga.data.follow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.calamares.miga.data.search.RecipeUrlFetcher
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.net.URI
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

/** What a website's page says about the site: its name, icon and the feeds it announces. */
data class SiteInfo(val name: String, val iconUrl: String?, val feeds: List<String>)

/** A post as WordPress's API gives it; [mediaId] is its featured picture (0 when it has none). */
data class WpPost(val link: String, val title: String, val excerpt: String, val publishedAt: Long, val mediaId: Long)

/**
 * Recipe websites followed in Favourites. Most of them publish a feed (RSS or Atom) with their
 * latest posts; those made with WordPress, most of them, also have a public API that gives more
 * posts page by page, their pictures and a search. The parsing is pure, so it can be tested.
 */
object WebFeeds {

    /** How many recipes are asked for at a time. */
    const val PAGE_SIZE = 50

    /** Where websites usually keep their feed, tried when the page announces none. */
    val COMMON_FEEDS = listOf("/feed/", "/rss", "/index.xml", "/feed.xml", "/atom.xml", "/rss.xml")

    private val ALTERNATE = Regex("""<link\b[^>]*>""", RegexOption.IGNORE_CASE)
    private fun attribute(tag: String, name: String) =
        Regex("""\b$name\s*=\s*("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE).find(tag)?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }
    private fun meta(html: String, key: String): String? =
        Regex("""<meta\s+(?:property|name)="${Regex.escape(key)}"\s+content="([^"]*)"""", RegexOption.IGNORE_CASE).find(html)
            ?.groupValues?.get(1)?.let(RecipeUrlFetcher::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() }

    /** The site's name, icon and announced feeds (comments feeds left out), from one of its pages. */
    fun readSite(html: String, pageUrl: String): SiteInfo {
        val links = ALTERNATE.findAll(html).map { it.value }.toList()
        val feeds = links.filter { tag ->
            attribute(tag, "rel")?.lowercase()?.contains("alternate") == true &&
                attribute(tag, "type")?.lowercase() in setOf("application/rss+xml", "application/atom+xml")
        }.mapNotNull { tag -> attribute(tag, "href")?.let { absolute(pageUrl, RecipeUrlFetcher.decodeEntities(it)) } }
            .filterNot { "comments" in it.lowercase() || "/comentarios" in it.lowercase() }
            .distinct()
        val icon = links.firstOrNull { attribute(it, "rel")?.lowercase()?.contains("apple-touch-icon") == true }
            ?: links.firstOrNull { attribute(it, "rel")?.lowercase()?.split(" ")?.contains("icon") == true }
        val title = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
            ?.groupValues?.get(1)?.let(RecipeUrlFetcher::decodeEntities)?.trim()
        return SiteInfo(
            // A page title is the site's name only when short ("Recetas de cocina, postres y… (DAP)" is not).
            name = meta(html, "og:site_name") ?: meta(html, "application-name")
                ?: title?.substringBefore(" | ")?.substringBefore(" - ")?.trim()?.takeIf { it.isNotBlank() && it.length <= MAX_SITE_NAME }
                ?: hostOf(pageUrl),
            iconUrl = icon?.let { attribute(it, "href") }?.let { absolute(pageUrl, RecipeUrlFetcher.decodeEntities(it)) } ?: meta(html, "og:image"),
            feeds = feeds
        )
    }

    /**
     * The feed to follow among [feeds]: the one of the page's own section ("/recetas/feed/" for
     * "/recetas/"), else the site's main one.
     */
    fun chooseFeed(feeds: List<String>, pageUrl: String): String? {
        val path = runCatching { URI(pageUrl).path }.getOrNull().orEmpty().trimEnd('/')
        if (path.isNotEmpty()) feeds.firstOrNull { runCatching { URI(it).path }.getOrNull().orEmpty().startsWith("$path/") }?.let { return it }
        return feeds.firstOrNull()
    }

    fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")

    fun originOf(url: String): String = runCatching { URI(url).let { "${it.scheme}://${it.host}" } }.getOrNull().orEmpty()

    fun absolute(base: String, href: String): String = runCatching { URI(base).resolve(href.trim()).toString() }.getOrDefault(href)

    /** A link without its query and fragment, the same with or without the last slash: how web posts are told apart. */
    fun normalize(url: String): String = url.substringBefore('#').substringBefore('?').trimEnd('/').lowercase()

    /** Page [page] (1 is the first) of a WordPress feed. */
    fun pagedFeedUrl(feedUrl: String, page: Int): String = if (page <= 1) feedUrl else feedUrl + (if ('?' in feedUrl) "&" else "?") + "paged=$page"

    // --- Feeds (RSS 2.0 and Atom) ---

    /** The posts of an RSS or Atom feed, newest first; it copes with the badly written ones some sites serve. */
    fun readFeed(xml: String, feedUrl: String): List<FeedPost> {
        val document = parse(xml) ?: parse(sanitize(xml)) ?: return readBrokenFeed(xml, feedUrl)
        val root = document.documentElement ?: return emptyList()
        val items = root.descendants("item").ifEmpty { root.descendants("entry") }
        return items.mapNotNull { item ->
            val link = item.firstChild("link")?.let { it.getAttribute("href").ifBlank { it.textContent } }?.trim()?.takeIf { it.isNotEmpty() }
                ?: item.firstChild("guid")?.textContent?.trim()?.takeIf { it.startsWith("http") }
                ?: return@mapNotNull null
            val url = absolute(feedUrl, link)
            val html = item.firstChild("encoded")?.textContent ?: item.firstChild("content")?.textContent ?: ""
            val summary = item.firstChild("description")?.textContent ?: item.firstChild("summary")?.textContent ?: ""
            FeedPost(
                postKey = normalize(url),
                url = url,
                title = plainText(item.firstChild("title")?.textContent.orEmpty()).take(MAX_TITLE),
                description = plainText(summary.ifBlank { html }).take(MAX_TEXT),
                thumbnailUrl = imageOf(item, html + summary)?.let { absolute(url, it) },
                publishedAt = dateOf(item),
                kind = PostKind.ARTICLE
            )
        }.distinctBy { it.postKey }.sortedByDescending { it.publishedAt }
    }

    private fun parse(xml: String) = runCatching {
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    }.getOrNull()

    /**
     * What breaks some sites' feeds: characters XML does not allow, "&" that start no entity and
     * doubled quotes in attributes (width=""1199"").
     */
    internal fun sanitize(xml: String): String = xml
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .replace(Regex("&(?!(?:#\\d+|#x[0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);)"), "&amp;")
        .replace(Regex("=\"\"([^\"\\s>/])"), "=\"$1")

    private val ITEM = Regex("""<(item|entry)\b[^>]*>(.*?)</\1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private fun tag(block: String, name: String): String? =
        Regex("""<$name\b[^>]*>(.*?)</$name>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(block)?.groupValues?.get(1)
            ?.replace(Regex("""^\s*<!\[CDATA\[|]]>\s*$"""), "")?.trim()

    /** A feed still too broken for an XML parser, read entry by entry. */
    private fun readBrokenFeed(xml: String, feedUrl: String): List<FeedPost> = ITEM.findAll(xml).mapNotNull { match ->
        val block = match.groupValues[2]
        val link = tag(block, "link")?.takeIf { it.startsWith("http") }
            ?: Regex("""<link\b[^>]*href="([^"]+)"""").find(block)?.groupValues?.get(1)
            ?: return@mapNotNull null
        val url = absolute(feedUrl, RecipeUrlFetcher.decodeEntities(link))
        val text = tag(block, "description") ?: tag(block, "summary") ?: ""
        val date = (tag(block, "pubDate") ?: tag(block, "published") ?: tag(block, "updated"))?.let { value ->
            runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        } ?: 0L
        val image = Regex("""<media:(?:content|thumbnail)\b[^>]*url="([^"]+)"""").find(block)?.groupValues?.get(1)
            ?: IMG.find(RecipeUrlFetcher.decodeEntities(block))?.groupValues?.get(1)
        FeedPost(
            postKey = normalize(url), url = url, title = plainText(tag(block, "title").orEmpty()).take(MAX_TITLE),
            description = plainText(text).take(MAX_TEXT), thumbnailUrl = image?.let { absolute(url, RecipeUrlFetcher.decodeEntities(it)) },
            publishedAt = date, kind = PostKind.ARTICLE
        )
    }.toList().distinctBy { it.postKey }.sortedByDescending { it.publishedAt }

    private fun Element.descendants(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.firstChild(localName: String): Element? {
        var node: Node? = firstChild
        while (node != null) {
            if (node is Element && (node.localName ?: node.nodeName.substringAfter(':')) == localName) return node
            node = node.nextSibling
        }
        return null
    }

    private val IMG = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    private fun imageOf(item: Element, html: String): String? {
        var node: Node? = item.firstChild
        while (node != null) {
            if (node is Element) {
                val name = node.localName ?: node.nodeName
                when {
                    name == "thumbnail" || (name == "content" && node.getAttribute("url").isNotBlank() && node.getAttribute("medium") != "video") ->
                        node.getAttribute("url").takeIf { it.isNotBlank() }?.let { return it }
                    name == "enclosure" && node.getAttribute("type").startsWith("image") -> return node.getAttribute("url")
                    name == "group" -> (node.getElementsByTagNameNS("*", "thumbnail").item(0) as? Element)?.getAttribute("url")?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
            node = node.nextSibling
        }
        return IMG.find(html)?.groupValues?.get(1)?.let(RecipeUrlFetcher::decodeEntities)
    }

    private fun dateOf(item: Element): Long {
        val text = (item.firstChild("pubDate") ?: item.firstChild("published") ?: item.firstChild("updated") ?: item.firstChild("date"))
            ?.textContent?.trim() ?: return 0L
        return runCatching { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: 0L
    }

    /** Text without HTML tags, entities decoded and spaces joined. */
    internal fun plainText(html: String): String =
        RecipeUrlFetcher.decodeEntities(html.replace(Regex("(?is)<script.*?</script>|<style.*?</style>"), " ").replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ").trim()

    private const val MAX_TITLE = 160
    private const val MAX_SITE_NAME = 40
    private const val MAX_TEXT = 400

    // --- WordPress ---

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.rendered(key: String) = (this[key] as? JsonObject)?.text("rendered").orEmpty()
    private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.longOrNull

    fun wpApiOf(url: String) = originOf(url) + "/wp-json/wp/v2/"

    private const val POST_FIELDS = "link,title,excerpt,date_gmt,featured_media"

    /** One page of posts (of [category], or of the whole site), or the posts matching [search]. */
    fun wpPostsUrl(api: String, category: Long?, page: Int, search: String? = null): String =
        api + "posts?per_page=$PAGE_SIZE&page=$page&_fields=$POST_FIELDS" +
            (category?.let { "&categories=$it" } ?: "") +
            (search?.let { "&search=" + URLEncoder.encode(it, "UTF-8") } ?: "")

    /** The posts at these addresses, by the last part of their path, to find their pictures. */
    fun wpPostsBySlugUrl(api: String, links: List<String>): String =
        api + "posts?per_page=100&_fields=link,featured_media&slug=" +
            links.mapNotNull { slugOf(it) }.joinToString(",") { URLEncoder.encode(it, "UTF-8") }

    fun slugOf(link: String): String? = runCatching { URI(link).path }.getOrNull()?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

    fun wpCategoryUrl(api: String, slug: String) = api + "categories?slug=" + URLEncoder.encode(slug, "UTF-8") + "&_fields=id,count"

    /** A category with posts in it (some sites keep "Recetas" empty and use subcategories). */
    fun readWpCategory(body: String): Long? = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull()
        ?.mapNotNull { it as? JsonObject }?.firstOrNull { (it.number("count") ?: 0L) > 0 }?.number("id")

    /** Null when the answer is not WordPress's list of posts (the site has no such API). */
    fun readWpPosts(body: String): List<WpPost>? {
        val array = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return null
        return array.mapNotNull { element ->
            val post = element as? JsonObject ?: return@mapNotNull null
            val link = post.text("link") ?: return@mapNotNull null
            WpPost(
                link = link,
                title = plainText(post.rendered("title")),
                excerpt = plainText(post.rendered("excerpt")).removeSuffix("[…]").removeSuffix("[&hellip;]").trim(),
                publishedAt = post.text("date_gmt")?.let { runCatching { OffsetDateTime.parse(it + "Z").toInstant().toEpochMilli() }.getOrNull() } ?: 0L,
                mediaId = post.number("featured_media") ?: 0L
            )
        }
    }

    fun wpMediaUrl(api: String, ids: List<Long>) =
        api + "media?per_page=100&include=" + ids.joinToString(",") + "&_fields=id,source_url,media_details.sizes.medium_large.source_url,media_details.sizes.medium.source_url"

    /** Picture address by media id, at a size for a list (medium) when the site has one. */
    fun readWpMedia(body: String): Map<Long, String> = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull()
        ?.mapNotNull { element ->
            val media = element as? JsonObject ?: return@mapNotNull null
            val id = media.number("id") ?: return@mapNotNull null
            val sizes = (media["media_details"] as? JsonObject)?.get("sizes") as? JsonObject
            val sized = listOf("medium_large", "medium").firstNotNullOfOrNull { (sizes?.get(it) as? JsonObject)?.text("source_url") }
            (sized ?: media.text("source_url"))?.let { id to it }
        }?.toMap().orEmpty()

    fun toFeedPosts(posts: List<WpPost>, media: Map<Long, String>): List<FeedPost> = posts.map { post ->
        FeedPost(
            postKey = normalize(post.link),
            url = post.link,
            title = post.title.take(MAX_TITLE),
            description = post.excerpt.take(MAX_TEXT),
            thumbnailUrl = media[post.mediaId],
            publishedAt = post.publishedAt,
            kind = PostKind.ARTICLE
        )
    }
}
