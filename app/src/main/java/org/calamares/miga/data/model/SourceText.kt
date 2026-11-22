package org.calamares.miga.data.model

/**
 * The "Source" of a recipe imported from a social media post: "[@user](profile) - [reel](post)",
 * links that the recipe screen shows as such (see FormattedText). The last link is the one the
 * recipe comes from: the copy button copies it, and exports write its address.
 */
object SourceText {

    private val LINK = Regex("""\[([^\]\n]+)]\((https?://[^\s)]+)\)""")
    private val BARE_URL = Regex("""https?://[^\s<>"]+[^\s<>".,;:!?)\]'»]""")

    /** [author] (linked to [authorUrl] when known), then each of [links] as label and address. */
    fun post(author: String?, authorUrl: String?, links: List<Pair<String, String>>): String {
        val who = author?.let { name -> authorUrl?.let { "[$name]($it)" } ?: name }
        return (listOfNotNull(who) + links.map { (label, url) -> "[$label]($url)" }).joinToString(" - ")
    }

    /** The address to copy: the last link, or the first address written as it is. */
    fun mainLink(source: String): String? =
        LINK.findAll(source).lastOrNull()?.groupValues?.get(2) ?: BARE_URL.find(source)?.value

    /** For PDFs and plain text: the labels, then the address of the last link in brackets. */
    fun plain(source: String): String {
        val last = LINK.findAll(source).lastOrNull() ?: return source
        return LINK.replace(source) { it.groupValues[1] } + " (" + last.groupValues[2] + ")"
    }
}
