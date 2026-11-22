package org.calamares.miga.data.follow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.calamares.miga.data.search.RecipeUrlFetcher
import org.calamares.miga.data.search.SocialPostRecipe
import org.calamares.miga.data.search.YouTubeRecipe
import org.w3c.dom.Element
import java.net.URI
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory

/** Where a followed source or a saved post lives. Names are stored; do not rename them. */
enum class SocialNetwork { YOUTUBE, INSTAGRAM }

/** What a post is. Names are stored; do not rename them. */
enum class PostKind { VIDEO, SHORT, REEL, PHOTO }

/** A link to something Favourites can follow or save. */
sealed interface SocialLink {
    val url: String

    /** A channel, by its handle ("@lacocinadelila") or its id ("UC…"). */
    data class YouTubeChannel(override val url: String) : SocialLink
    data class YouTubeVideo(override val url: String, val id: String) : SocialLink
    data class InstagramProfile(override val url: String, val username: String) : SocialLink
    data class InstagramPost(override val url: String, val shortcode: String) : SocialLink

    val isSource: Boolean get() = this is YouTubeChannel || this is InstagramProfile
}

/** A channel's or profile's details, as its page gives them. */
data class SourceInfo(
    val network: SocialNetwork,
    /** The channel id ("UC…") or the username. */
    val key: String,
    val handle: String?,
    val name: String,
    val avatarUrl: String?,
    val url: String
)

/** One of the latest posts of a channel or profile. */
data class FeedPost(
    val postKey: String,
    val url: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val publishedAt: Long,
    val kind: PostKind
)

/**
 * Reading YouTube channels and Instagram profiles without an account: a channel's public feed
 * (its latest 15 videos) and a profile's page for embedding in websites (its latest 6 posts).
 * The parsing is pure, so it can be tested with saved pages.
 */
object SocialFeeds {

    private val INSTAGRAM_HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
    private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    private val CHANNEL_PATH = Regex("^/(?:@[\\w.\\-]+|channel/UC[\\w-]{22}|c/[^/]+|user/[^/]+)(?:/.*)?$")
    private val USERNAME = Regex("^[A-Za-z0-9._]{1,30}$")
    private val INSTAGRAM_PATHS_NOT_PROFILES = setOf(
        "p", "reel", "reels", "tv", "explore", "stories", "accounts", "direct", "about", "legal", "developer", "web", "emails", "challenge"
    )
    private val SHORTCODE = Regex("/(?:[^/]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)")

    /** What [url] points to, or null when it is none of the four. */
    fun parse(url: String): SocialLink? {
        val trimmed = url.trim()
        YouTubeRecipe.videoId(trimmed)?.let { return SocialLink.YouTubeVideo(YouTubeRecipe.watchUrl(it), it) }
        if (SocialPostRecipe.isInstagram(trimmed)) {
            val code = SHORTCODE.find(URI(trimmed).path.orEmpty())?.groupValues?.get(1) ?: return null
            return SocialLink.InstagramPost(trimmed.substringBefore('?'), code)
        }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty()
        if (host in YOUTUBE_HOSTS && CHANNEL_PATH.matches(path)) {
            val base = "/" + path.trim('/').split('/').let { parts -> if (parts[0].startsWith("@")) parts.take(1) else parts.take(2) }.joinToString("/")
            return SocialLink.YouTubeChannel("https://www.youtube.com$base")
        }
        if (host in INSTAGRAM_HOSTS) {
            val first = path.trim('/').split('/').firstOrNull().orEmpty()
            if (USERNAME.matches(first) && first.lowercase() !in INSTAGRAM_PATHS_NOT_PROFILES) {
                return SocialLink.InstagramProfile("https://www.instagram.com/$first/", first)
            }
        }
        return null
    }

    // --- YouTube ---

    private val EXTERNAL_ID = Regex(""""externalId"\s*:\s*"(UC[\w-]{22})"""")
    private val CANONICAL_CHANNEL = Regex("""<link rel="canonical" href="https://www\.youtube\.com/channel/(UC[\w-]{22})"""")
    private val VANITY = Regex(""""vanityChannelUrl"\s*:\s*"https?://www\.youtube\.com/(@[^"]+)"""")

    private fun meta(html: String, key: String): String? =
        Regex("""<meta\s+(?:property|name)="${Regex.escape(key)}"\s+content="([^"]*)"""").find(html)
            ?.groupValues?.get(1)?.let(RecipeUrlFetcher::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() }

    /** A channel's details from its page ("youtube.com/@name"); null when the page has no channel. */
    fun readYouTubeChannel(html: String): SourceInfo? {
        val id = EXTERNAL_ID.find(html)?.groupValues?.get(1) ?: CANONICAL_CHANNEL.find(html)?.groupValues?.get(1) ?: return null
        val handle = VANITY.find(html)?.groupValues?.get(1)
        return SourceInfo(
            network = SocialNetwork.YOUTUBE,
            key = id,
            handle = handle,
            name = meta(html, "og:title") ?: handle ?: id,
            avatarUrl = meta(html, "og:image"),
            url = handle?.let { "https://www.youtube.com/$it" } ?: "https://www.youtube.com/channel/$id"
        )
    }

    fun youTubeFeedUrl(channelId: String) = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"

    /** The videos in a channel's feed (Atom with Media RSS), newest first. */
    fun readYouTubeFeed(xml: String): List<FeedPost> {
        val document = runCatching {
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                .parse(xml.byteInputStream(Charsets.UTF_8))
        }.getOrNull() ?: return emptyList()
        val entries = document.getElementsByTagNameNS(ATOM, "entry")
        return (0 until entries.length).mapNotNull { index ->
            val entry = entries.item(index) as Element
            val id = entry.child(YT, "videoId") ?: return@mapNotNull null
            val link = entry.getElementsByTagNameNS(ATOM, "link").let { links ->
                (0 until links.length).map { links.item(it) as Element }.firstOrNull { it.getAttribute("rel") == "alternate" }?.getAttribute("href")
            }
            val group = entry.getElementsByTagNameNS(MEDIA, "group").item(0) as? Element
            val thumbnail = (group?.getElementsByTagNameNS(MEDIA, "thumbnail")?.item(0) as? Element)?.getAttribute("url")
            FeedPost(
                postKey = id,
                url = YouTubeRecipe.watchUrl(id),
                title = entry.child(ATOM, "title").orEmpty(),
                description = group?.let { (it.getElementsByTagNameNS(MEDIA, "description").item(0))?.textContent }.orEmpty().trim(),
                thumbnailUrl = thumbnail?.takeIf { it.isNotBlank() },
                publishedAt = entry.child(ATOM, "published")?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L,
                kind = if (link?.contains("/shorts/") == true) PostKind.SHORT else PostKind.VIDEO
            )
        }.sortedByDescending { it.publishedAt }
    }

    private fun Element.child(namespace: String, name: String): String? =
        getElementsByTagNameNS(namespace, name).item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private const val ATOM = "http://www.w3.org/2005/Atom"
    private const val YT = "http://www.youtube.com/xml/schemas/2015"
    private const val MEDIA = "http://search.yahoo.com/mrss/"

    // --- Instagram ---

    fun instagramProfileEmbedUrl(username: String) = "https://www.instagram.com/$username/embed/"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    /** A profile and its latest posts from its embed page; null when the page does not carry them. */
    fun readInstagramProfile(html: String): Pair<SourceInfo, List<FeedPost>>? {
        val raw = SocialPostRecipe.contextJsonLiteral(html) ?: return null
        val context = runCatching {
            (json.parseToJsonElement(json.decodeFromString<String>("\"$raw\"")) as? JsonObject)?.obj("context")
        }.getOrNull() ?: return null
        val username = context.text("username") ?: return null
        val info = SourceInfo(
            network = SocialNetwork.INSTAGRAM,
            key = username,
            handle = "@$username",
            name = context.text("full_name") ?: "@$username",
            avatarUrl = context.text("profile_pic_url"),
            url = "https://www.instagram.com/$username/"
        )
        val posts = (context["graphql_media"] as? JsonArray).orEmpty().mapNotNull { item ->
            val media = (item as? JsonObject)?.obj("shortcode_media") ?: return@mapNotNull null
            val code = media.text("shortcode") ?: return@mapNotNull null
            val video = media.text("__typename") == "GraphVideo"
            val caption = ((media.obj("edge_media_to_caption")?.get("edges") as? JsonArray)?.firstOrNull() as? JsonObject)?.obj("node")?.text("text").orEmpty()
            val (title, description) = titleAndText(caption)
            FeedPost(
                postKey = code,
                url = "https://www.instagram.com/${if (video) "reel" else "p"}/$code/",
                title = title,
                description = description,
                thumbnailUrl = media.text("display_url"),
                publishedAt = ((media["taken_at_timestamp"] as? JsonPrimitive)?.longOrNull ?: 0L) * 1000,
                kind = if (video) PostKind.REEL else PostKind.PHOTO
            )
        }.sortedByDescending { it.publishedAt }
        return info to posts
    }

    /** A caption's first line as the title and the rest as its text. */
    internal fun titleAndText(caption: String): Pair<String, String> {
        val lines = SocialPostRecipe.cleanText(caption).lines().map { it.trim() }
        val first = lines.indexOfFirst { it.isNotEmpty() }
        if (first < 0) return "" to ""
        // "#publi Sorbete de limón": hashtags before the title are labels, not part of it.
        val title = lines[first].replace(LEADING_HASHTAGS, "").ifBlank { lines[first] }
        return title.take(MAX_TITLE) to lines.drop(first + 1).joinToString("\n").trim()
    }

    private const val MAX_TITLE = 120
    private val LEADING_HASHTAGS = Regex("""^(?:#\S+\s+)+""")
}
