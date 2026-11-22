package org.calamares.miga.data.search

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/** What the public page of a YouTube video says about it. */
data class YouTubeVideo(
    val id: String,
    val title: String,
    val author: String?,
    /** The description as the uploader wrote it; null when only oEmbed answered. */
    val description: String?,
    val thumbnailUrl: String?,
    val seconds: Int?,
    /** "@lacocinadelila", when the page says it. */
    val handle: String? = null,
    /** The channel's page. */
    val channelUrl: String? = null
) {
    val url: String get() = YouTubeRecipe.watchUrl(id)
}

/**
 * Recipes in YouTube videos. A video's page has its description (where many cooks write the
 * recipe, or a link to it on their website) and its thumbnail; Gemini can also watch a public
 * video by its address, hearing what is said and reading what is written on screen. Captions
 * cannot be downloaded without a session, so the other providers only get the description.
 */
object YouTubeRecipe {

    private val HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com", "www.youtube-nocookie.com")
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val PATH_ID = Regex("^/(?:shorts|live|embed|v)/([A-Za-z0-9_-]{11})")

    /** The id of the video at [url] (watch, shorts, live, embed or youtu.be links); null for anything else. */
    fun videoId(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val id = when {
            host == "youtu.be" || host == "www.youtu.be" -> uri.path.orEmpty().trim('/').substringBefore('/')
            host in HOSTS && uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&')
                .firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            host in HOSTS -> PATH_ID.find(uri.path.orEmpty())?.groupValues?.get(1)
            else -> null
        }
        return id?.takeIf { ID.matches(it) }
    }

    fun isYouTube(url: String) = videoId(url) != null

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    fun oEmbedUrl(id: String) = "https://www.youtube.com/oembed?format=json&url=" + java.net.URLEncoder.encode(watchUrl(id), "UTF-8")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    /**
     * The JSON object after [marker] in [html], read by hand (strings and escapes included): it is
     * long, and regular expressions over it overflow the stack.
     */
    internal fun jsonObjectAfter(html: String, marker: String): String? {
        val start = html.indexOf(marker).takeIf { it >= 0 }?.let { html.indexOf('{', it + marker.length) }?.takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var i = start
        while (i < html.length) {
            val c = html[i]
            if (inString) {
                when (c) {
                    '\\' -> i++
                    '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> if (--depth == 0) return html.substring(start, i + 1)
                }
            }
            i++
        }
        return null
    }

    /** The video in its watch page; null when the page does not carry it (consent page, removed...). */
    fun read(html: String, id: String): YouTubeVideo? {
        val player = jsonObjectAfter(html, "ytInitialPlayerResponse")?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: return null
        val details = player.obj("videoDetails") ?: return null
        if (details.text("videoId") != id) return null
        val thumbnails = (details.obj("thumbnail")?.get("thumbnails") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.text("url") }
        val profile = player.obj("microformat")?.obj("playerMicroformatRenderer")?.text("ownerProfileUrl")?.replaceFirst("http://", "https://")
        val channelUrl = profile ?: details.text("channelId")?.let { "https://www.youtube.com/channel/$it" }
        return YouTubeVideo(
            id = id,
            title = details.text("title") ?: return null,
            author = details.text("author"),
            description = details.text("shortDescription"),
            thumbnailUrl = bestThumbnail(html) ?: thumbnails?.lastOrNull()?.substringBefore('?'),
            seconds = details.text("lengthSeconds")?.toIntOrNull(),
            handle = handleIn(channelUrl),
            channelUrl = channelUrl
        )
    }

    private val OG_IMAGE = Regex("""<meta\s+(?:property|name)="og:image"\s+content="([^"]+)"""")

    private fun bestThumbnail(html: String) = OG_IMAGE.find(html)?.groupValues?.get(1)?.let(RecipeUrlFetcher::decodeEntities)

    /** Title, channel and thumbnail from YouTube's oEmbed answer, when the page itself is not readable. */
    fun readOEmbed(body: String, id: String): YouTubeVideo? {
        val data = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val channelUrl = data.text("author_url")
        return YouTubeVideo(id, data.text("title") ?: return null, data.text("author_name"), null, data.text("thumbnail_url"), null, handleIn(channelUrl), channelUrl)
    }

    /** "@name" from a channel address such as youtube.com/@name. */
    private fun handleIn(channelUrl: String?): String? =
        channelUrl?.let { runCatching { URI(it).path }.getOrNull() }?.trim('/')?.takeIf { it.startsWith("@") && '/' !in it }

    /** The video as a post: its title, then its description, for [SocialPostRecipe]. */
    fun asPost(video: YouTubeVideo): SocialPost = SocialPost(
        text = SocialPostRecipe.cleanText(listOfNotNull(video.title, video.description).joinToString("\n\n")),
        imageUrl = video.thumbnailUrl,
        author = video.handle ?: video.author,
        url = video.url,
        site = "YouTube",
        authorUrl = video.channelUrl
    )

    private val URL = Regex("""https?://[^\s<>"«»()]+""")

    /** Sites in descriptions that are not where a recipe is written: networks, shorteners, shops. */
    private val NOT_RECIPE_SITES = listOf(
        "youtube.com", "youtu.be", "instagram.com", "facebook.com", "fb.com", "tiktok.com", "twitter.com", "x.com",
        "pinterest.", "pin.it", "goo.gl", "bit.ly", "tinyurl.com", "linktr.ee", "amzn.", "amazon.", "spotify.com",
        "patreon.com", "twitch.tv", "threads.net", "whatsapp.com", "t.me", "paypal.", "gofundme.com", "discord."
    )

    /**
     * Links in the description that may lead to the recipe on the cook's website ("Receta
     * completa: lacocinadelila.com/..."), first ones first, at most [max].
     */
    fun recipePageLinks(description: String?, max: Int = 2): List<String> =
        URL.findAll(description.orEmpty()).map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
            .filter { link ->
                val host = runCatching { URI(link).host?.lowercase() }.getOrNull() ?: return@filter false
                val path = runCatching { URI(link).path }.getOrNull().orEmpty()
                NOT_RECIPE_SITES.none { host == it || host.endsWith(".$it") || (it.endsWith(".") && (host.startsWith(it) || host.contains(".$it"))) } &&
                    path.trim('/').isNotEmpty()
            }
            .distinct().take(max).toList()
}
