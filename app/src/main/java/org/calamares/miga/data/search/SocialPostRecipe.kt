package org.calamares.miga.data.search

import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.StepGroupDto
import org.calamares.miga.data.model.PastedLists
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.calamares.miga.data.vision.RecipeVisionResultDto
import java.net.URI

/** The text of a social media post (an Instagram reel, say), its photo and who posted it. */
data class SocialPost(
    val text: String,
    val imageUrl: String?,
    /** "@poesiadefogon", when the page says it. */
    val author: String?,
    /** The post's own address, without tracking parameters. */
    val url: String,
    val site: String,
    /** The author's profile, when known. */
    val authorUrl: String? = null
)

/**
 * What Instagram's public embed page ("…/p/<code>/embed/captioned/") says about a post: the photo
 * at full size and without the play button that the page's own preview ("og:image") has drawn on
 * reels, the caption and the video.
 */
data class InstagramMedia(val caption: String?, val imageUrl: String?, val videoUrl: String?, val author: String?)

/**
 * Recipes shared as posts on Instagram and similar sites: they carry no schema.org Recipe, but the
 * public page puts the whole caption, where the recipe is written, in its Open Graph tags
 * ("og:description"), with a photo ("og:image"). [read] takes them out; [parse] turns a caption
 * written in the usual way ("INGREDIENTES:" and a list, then "PASO A PASO:" or numbered steps)
 * into a recipe without AI. Other captions go to the cooking assistant as plain text.
 */
object SocialPostRecipe {

    private val INSTAGRAM_HOSTS = setOf("instagram.com", "www.instagram.com", "m.instagram.com", "instagr.am")
    private val INSTAGRAM_POST = Regex("^/(?:[^/]+/)?(?:p|reel|reels|tv)/[^/]+")

    /** An Instagram post or reel ("instagram.com/reel/…", also with the account in the path). */
    /** Sites where what is shared is a post whose caption holds the recipe. */
    private val SOCIAL_HOSTS = listOf("instagram.com", "tiktok.com", "facebook.com", "fb.watch", "x.com", "twitter.com", "threads.net", "pinterest.com", "pinterest.es")

    fun isSocialNetwork(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return SOCIAL_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun isInstagram(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.host?.lowercase() in INSTAGRAM_HOSTS && INSTAGRAM_POST.containsMatchIn(uri.path.orEmpty())
    }

    private val INSTAGRAM_CODE = Regex("/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)")

    /** The embed page of an Instagram post ([InstagramMedia]); null for other links. */
    fun instagramEmbedUrl(url: String): String? {
        if (!isInstagram(url)) return null
        val code = INSTAGRAM_CODE.find(URI(url.trim()).path.orEmpty())?.groupValues?.get(1) ?: return null
        return "https://www.instagram.com/p/$code/embed/captioned/"
    }

    private val CONTEXT_JSON_START = Regex(""""contextJSON"\s*:\s*"""")

    /**
     * The JSON string literal after "contextJSON": (escapes kept), read by hand: it is long, and a
     * regular expression with alternation would overflow the stack.
     */
    internal fun contextJsonLiteral(html: String): String? {
        val start = CONTEXT_JSON_START.find(html)?.range?.last?.plus(1) ?: return null
        var i = start
        while (i < html.length) {
            when (html[i]) {
                '\\' -> i += 2
                '"' -> return html.substring(start, i)
                else -> i++
            }
        }
        return null
    }
    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    /** The post in Instagram's embed page; null when the page does not carry it (private, changed...). */
    fun readInstagramEmbed(html: String): InstagramMedia? {
        val raw = contextJsonLiteral(html) ?: return null
        val media = runCatching {
            // A JSON document inside a JSON string: undo the string first.
            val context = lenientJson.decodeFromString<String>("\"$raw\"")
            (lenientJson.parseToJsonElement(context) as? JsonObject)?.obj("gql_data")?.obj("shortcode_media")
        }.getOrNull() ?: return null
        val caption = ((media.obj("edge_media_to_caption")?.get("edges") as? JsonArray)?.firstOrNull() as? JsonObject)
            ?.obj("node")?.text("text")
        return InstagramMedia(
            caption = caption,
            imageUrl = media.text("display_url"),
            videoUrl = media.text("video_url"),
            author = media.obj("owner")?.text("username")?.let { "@$it" }
        )
    }

    /**
     * [post] (from the post's page) completed with its [embed]: the photo without the play button
     * and, when the page did not show the caption, the caption. Null when neither has text.
     */
    fun withEmbed(post: SocialPost?, embed: InstagramMedia?, url: String): SocialPost? {
        embed ?: return post
        if (post != null) {
            val author = post.author ?: embed.author
            return post.copy(imageUrl = embed.imageUrl ?: post.imageUrl, author = author, authorUrl = post.authorUrl ?: author?.let(::instagramProfile))
        }
        val text = embed.caption?.let(::cleanText)?.takeIf { it.isNotBlank() } ?: return null
        return SocialPost(text, embed.imageUrl, embed.author, withoutQuery(url), "Instagram", embed.author?.let(::instagramProfile))
    }

    private fun meta(html: String, key: String): String? {
        val tag = Regex("""<meta\s[^>]*(?:property|name)\s*=\s*["']${Regex.escape(key)}["'][^>]*>""", RegexOption.IGNORE_CASE).find(html)?.value
            ?: return null
        val content = Regex("""content\s*=\s*("([^"]*)"|'([^']*)')""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(tag)
            ?: return null
        val raw = content.groupValues[2].ifEmpty { content.groupValues[3] }
        return RecipeUrlFetcher.decodeEntities(raw).trim().takeIf { it.isNotEmpty() }
    }

    /** Instagram wraps the caption: «21K likes, 232 comments - user on September 3, 2026: "…"» (or the same in Spanish). */
    private val INSTAGRAM_WRAPPER = Regex("""^.{0,120}?\s-\s+([\w.]+)\s+[^:"\n]{0,60}:\s*"(.*)"\s*\.?\s*$""", RegexOption.DOT_MATCHES_ALL)
    private val TITLE_WRAPPER = Regex("""^(.*?)\s+(?:on|en)\s+Instagram:\s*"(.*)"\s*$""", RegexOption.DOT_MATCHES_ALL)
    private val HANDLE = Regex("""\(@([\w.]+)\)""")

    /**
     * The post in [html] (the page at [pageUrl]), when it is a social media post with text: always
     * for Instagram, and for other sites when the page says it is a post (Open Graph type
     * "article" or "video") with a long description. Null otherwise.
     */
    fun read(html: String, pageUrl: String): SocialPost? {
        val instagram = isInstagram(pageUrl)
        val description = meta(html, "og:description") ?: meta(html, "description") ?: return null
        val type = meta(html, "og:type").orEmpty()
        // Only social networks: on a blog or a magazine ("article" too) the description is just a
        // summary, and the page's own text is what has the recipe or the tip.
        if (!instagram && (!isSocialNetwork(pageUrl) || description.length < MIN_TEXT || !(type.startsWith("video") || type == "article"))) return null
        var text = description
        var author: String? = null
        INSTAGRAM_WRAPPER.find(description)?.let { match ->
            author = "@" + match.groupValues[1]
            text = match.groupValues[2]
        } ?: TITLE_WRAPPER.find(meta(html, "og:title").orEmpty())?.let { match ->
            // The title carries the whole caption too, when the description is cut short.
            if (match.groupValues[2].length > text.length) text = match.groupValues[2]
        }
        author = author ?: HANDLE.find(meta(html, "twitter:title").orEmpty())?.groupValues?.get(1)?.let { "@$it" }
        text = cleanText(text)
        if (text.isBlank()) return null
        return SocialPost(
            text = text,
            imageUrl = meta(html, "og:image"),
            author = author,
            url = withoutQuery(meta(html, "og:url") ?: pageUrl),
            site = meta(html, "og:site_name") ?: if (instagram) "Instagram" else URI(pageUrl).host.orEmpty(),
            authorUrl = if (instagram) author?.let(::instagramProfile) else null
        )
    }

    private const val MIN_TEXT = 80

    /** Hashtag lines and the dots people use to push them down go; the rest stays. */
    internal fun cleanText(text: String): String = text.lines()
        .map { it.trimEnd() }
        .filterNot { line -> line.isNotBlank() && (line.matches(FILLER) || line.trim().split(SPACES).all { it.startsWith("#") }) }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private val FILLER = Regex("[.·•\\s]+")
    private val SPACES = Regex("\\s+")

    /** "@poesiadefogon" lives at instagram.com/poesiadefogon/. */
    private fun instagramProfile(handle: String) = "https://www.instagram.com/${handle.removePrefix("@")}/"

    /** Whether an Instagram address is a reel (a video) rather than a photo post. */
    fun isReel(url: String) = Regex("/(?:reel|reels|tv)/").containsMatchIn(url)

    private fun withoutQuery(url: String): String = url.substringBefore('?').substringBefore('#')

    // --- Parsing a caption ---------------------------------------------------------------------

    private val INGREDIENT_HEADERS = listOf("ingredientes", "ingredients", "lo que necesitas", "necesitas", "you'll need", "you will need")
    private val STEP_HEADERS = listOf(
        "paso a paso", "pasos", "preparacion", "elaboracion", "instrucciones", "como se hace", "modo de preparacion",
        "procedimiento", "method", "steps", "directions", "instructions", "how to make", "step by step"
    )
    private val SERVINGS = Regex("""(?i)(?:para\s+)?(\d{1,2})\s*(?:personas?|raciones?|comensales|porciones|servings?|people|portions?)|(?:serves|for)\s+(\d{1,2})""")
    /** "1. ", "2) ", "Paso 3: " or a number before a capital letter ("1️⃣ Cortamos", once [plain]); not "2 pechugas". */
    private val STEP_NUMBER = Regex("""^(?:\d{1,2}\s*[.)]\s+|\d{1,2}\s+(?=\p{Lu}|¡|¿)|(?i:paso|step)\s*\d{1,2}\s*[:.)-]?\s*)""")

    /**
     * Emoji and decorative symbols at the start or end of a line ("▪️", "📝", "🍋🍯"), keeping the
     * digits of keycap numbers ("1️⃣" is "1 ").
     */
    private fun plain(line: String): String {
        val cleaned = line.replace("\uFE0F", "").replace("\u20E3", " ").replace("\u200D", "")
        return cleaned
            .replace(Regex("""^[^\p{L}\p{N}(¡¿"«]+"""), "")
            .replace(Regex("""[^\p{L}\p{N})."»!?…]+$"""), "")
            .trim()
    }

    private fun key(line: String) = java.text.Normalizer.normalize(line.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    private fun headerOf(line: String, headers: List<String>): Boolean {
        val text = key(plain(line)).replace(Regex("\\(.*?\\)"), "").trim().trimEnd(':').trim()
        return text.length <= 40 && headers.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") || text.startsWith("$it:") }
    }

    /** A line that starts with a symbol, not a letter or a number: "🔸Una vez terminada…". */
    private fun startsWithSymbol(line: String): Boolean = line.trimStart().firstOrNull()?.let { !it.isLetterOrDigit() } == true

    /**
     * The recipe in [post]'s text when it has an ingredients list and steps under the usual
     * headers; null otherwise (then the cooking assistant reads the text).
     */
    fun parse(post: SocialPost): SchemaRecipe? {
        val lines = post.text.lines()
        val ingredientsAt = lines.indexOfFirst { headerOf(it, INGREDIENT_HEADERS) }
        if (ingredientsAt < 0) return null
        val stepsAt = lines.indexOfFirst { headerOf(it, STEP_HEADERS) }.takeIf { it > ingredientsAt }
        // Without a steps heading the list ends at the first blank line after it, and what follows
        // only counts as steps when they are numbered (descriptions go on with greetings and links).
        val listEnd = stepsAt ?: (ingredientsAt + 1 until lines.size).firstOrNull { i ->
            lines[i].isBlank() && (ingredientsAt + 1 until i).any { plain(lines[it]).isNotEmpty() }
        } ?: lines.size
        val ingredients = lines.subList(ingredientsAt + 1, listEnd).map(::plain).filter { it.isNotEmpty() }
            .let { list -> if (stepsAt == null) list.takeWhile { !looksLikeStep(it) } else list }
            .map { PastedLists.ingredient(it) }
            .filter { it.name.isNotBlank() }
            .map { IngredientDto(spoonSize(it.name, it.unit), it.quantity, it.unit) }
        val stepSource = lines.subList(if (stepsAt != null) stepsAt + 1 else listEnd, lines.size)
        val (steps, tips) = steps(stepSource, onlyNumbered = stepsAt == null)
        if (ingredients.size < 2 || steps.isEmpty()) return null

        val intro = lines.subList(0, ingredientsAt).map(::plain).filter { it.isNotEmpty() }
        val title = intro.firstOrNull()?.let(::sentenceCase)?.take(MAX_TITLE) ?: return null
        val servings = (listOf(lines[ingredientsAt]) + intro).firstNotNullOfOrNull { line ->
            SERVINGS.find(line)?.let { match -> (match.groupValues[1].ifEmpty { match.groupValues[2] }).toIntOrNull() }
        }
        // The author goes in the source, with the post (see SourceText).
        val notes = (intro.drop(1) + tips).filterNot(::isCallToAction).joinToString("\n\n")
        val recipe = RecipeVisionResultDto(
            name = title,
            servings = servings ?: 4,
            notes = notes,
            source = post.url,
            ingredientGroups = listOf(IngredientGroupDto(null, ingredients)),
            stepGroups = listOf(StepGroupDto(null, steps))
        )
        return SchemaRecipe(recipe, post.imageUrl)
    }

    private const val MAX_TITLE = 120

    /** "TARTA INVISIBLE DE MANZANA" reads "Tarta invisible de manzana"; other titles stay. */
    private fun sentenceCase(title: String): String {
        val letters = title.filter { it.isLetter() }
        if (letters.length < 3 || letters.any { it.isLowerCase() }) return title
        return title.lowercase().replaceFirstChar { it.titlecase() }
    }

    private val CALLS_TO_ACTION = listOf(
        "comentario", "guarda esta receta", "guardala", "sigueme", "siguenos", "comparte", "link en", "enlace en mi bio",
        "comment", "save this", "follow", "link in bio", "share this"
    )

    /** "Cuéntame en comentarios… y guarda esta receta": for the post, not the recipe. */
    private fun isCallToAction(line: String): Boolean = key(line).let { text -> CALLS_TO_ACTION.any { it in text } }

    private val WEBSITE = Regex("""(?i)\b(?:https?://)?((?:[a-z0-9-]+\.)+(?:com|es|net|org|eu|info|blog|cat|co|io|me|recipes|kitchen|cooking))\b(?:/\S*)?""")
    private val QUANTITY_LINE = Regex("""^\d""")

    /**
     * The website a post sends to for the recipe ("Receta completa en mi web: cocinaconcoqui.com")
     * when its own text has no ingredients list: then neither Miga nor the assistant can import it
     * from the post. Null when the post has a list or names no website.
     */
    fun recipeElsewhere(post: SocialPost): String? {
        val lines = post.text.lines()
        if (lines.any { headerOf(it, INGREDIENT_HEADERS) }) return null
        if (lines.count { QUANTITY_LINE.containsMatchIn(plain(it)) } >= 3) return null
        return WEBSITE.findAll(post.text).map { it.groupValues[1].lowercase().removePrefix("www.") }
            .firstOrNull { "instagram" !in it }
    }

    private val SPOON_SIZE = Regex("""^(?:soperas?|grandes?|de\s+postre)(?:\s+(?:rasas?|colmadas?))?\s+(?:de\s+)?""", RegexOption.IGNORE_CASE)

    /** "2 cucharadas soperas rasas de miel": the unit is "cucharada" and the ingredient "miel". */
    private fun spoonSize(name: String, unit: String?): String =
        if (unit?.startsWith("cuchar") == true) name.replace(SPOON_SIZE, "").ifBlank { name } else name

    /** "1. Batimos..." or "Paso 1": a step, even right under the ingredients. Not "2 Huevos". */
    private val STRONG_STEP = Regex("""^(?:\d{1,2}\s*[.)]\s+|(?i:paso|step)\s*\d)""")

    private fun looksLikeStep(line: String) = STRONG_STEP.containsMatchIn(line)

    /**
     * Steps from the lines after the header: numbered steps ("1️⃣", "1.", "Paso 1") with their
     * following lines joined to them, or one step per line when nothing is numbered. Lines after
     * the last numbered step that start with a symbol ("🔸Una vez terminada…") are tips.
     */
    private fun steps(source: List<String>, onlyNumbered: Boolean = false): Pair<List<String>, List<String>> {
        val numbered = source.count { STEP_NUMBER.containsMatchIn(plain(it)) && plain(it).isNotEmpty() } >= 2
        if (onlyNumbered && !numbered) return emptyList<String>() to emptyList()
        val steps = mutableListOf<String>()
        val tips = mutableListOf<String>()
        for (raw in source) {
            val line = plain(raw)
            if (line.isEmpty()) continue
            if (!numbered) {
                steps += line
                continue
            }
            when {
                STEP_NUMBER.containsMatchIn(line) -> steps += line.replace(STEP_NUMBER, "").trim()
                steps.isEmpty() -> Unit
                startsWithSymbol(raw) || tips.isNotEmpty() -> tips += line
                else -> steps[steps.lastIndex] = steps.last() + " " + line
            }
        }
        return steps.filter { it.isNotBlank() } to tips
    }
}
