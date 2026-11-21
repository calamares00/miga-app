package org.calamares.miga.data.share

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.calamares.miga.data.search.SocialPostRecipe

/** A link shared to Miga ("Share" in Instagram or the browser): the recipe to import, and the text it came in. */
data class SharedRecipeLink(val url: String, val text: String)

/**
 * Links shared to Miga as text: a recipe page or an Instagram post is imported as a recipe (the
 * navigation asks in which book) instead of becoming shopping list items. Kept until consumed,
 * like [ShoppingIntents]. Must run before [ShoppingIntents.handle].
 */
object RecipeLinkIntents {

    private val URL = Regex("""https?://[^\s<>"«»]+""")

    /** Words around a link that still make it "just a link" (Instagram adds none, Chrome the page title). */
    private const val MAX_OTHER_CHARS = 200

    private val _event = MutableStateFlow<SharedRecipeLink?>(null)
    val event: StateFlow<SharedRecipeLink?> = _event

    /**
     * The link to import in shared [text]: an Instagram post anywhere in it, or the only link of a
     * short text (a page shared from the browser, with its title). Null for notes and lists, which
     * stay shopping items.
     */
    fun recipeLinkIn(text: String): String? {
        val links = URL.findAll(text).map { it.value.trimEnd('.', ',', ';', ':', ')', '!', '?') }.toList()
        links.firstOrNull { SocialPostRecipe.isInstagram(it) }?.let { return it }
        val link = links.singleOrNull() ?: return null
        return link.takeIf { text.replace(URL, "").trim().length <= MAX_OTHER_CHARS }
    }

    /** Returns true when [intent] shares a recipe link and it has been stored as an event. */
    fun handle(intent: Intent?): Boolean {
        intent ?: return false
        if (intent.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return false
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val url = recipeLinkIn(text) ?: return false
        _event.value = SharedRecipeLink(url, text)
        return true
    }

    fun consume() {
        _event.value = null
    }
}
