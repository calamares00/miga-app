package org.calamares.miga.data.model

/**
 * The photo credit that pack recipes carry at the end of their notes
 * ("📷 Foto: [Author](https://…) · CC BY 3.0", added by the packs build). The editor shows it
 * apart, as plain text, and keeps it when the notes are edited.
 */
object PhotoCredit {
    private val CREDIT = Regex("""(?s)\n*(📷 (?:Foto|Photo): .*)$""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")

    /** The notes without the credit, and the credit (null when there is none). */
    fun split(notes: String): Pair<String, String?> {
        val match = CREDIT.find(notes) ?: return notes to null
        return notes.substring(0, match.range.first) to match.groupValues[1].trim()
    }

    fun join(notes: String, credit: String?): String = when {
        credit == null -> notes
        notes.isBlank() -> credit
        else -> notes.trimEnd() + "\n\n" + credit
    }

    /** "📷 Foto: Author · CC BY 3.0": the Markdown link as its text. */
    fun plain(credit: String): String = LINK.replace(credit, "$1")
}
