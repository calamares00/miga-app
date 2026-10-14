package org.calamares.miga.data.model

/**
 * The Markdown subset used by the documents shipped with the app (privacy policy, help): headings,
 * paragraphs, bullet and numbered lists with one nested level, and inline formatting (see
 * [RichText]). Lines of a paragraph are joined, so the files can be wrapped or not.
 */
object MarkdownDocument {

    enum class Kind { HEADING, PARAGRAPH, BULLET, NUMBERED }

    /**
     * One block. [level] is the heading level (1 for "#") or, for the rest, the nesting depth (0 at
     * the top, 1 inside a list item). [number] is set for numbered items.
     */
    data class Block(val kind: Kind, val text: String, val level: Int = 0, val number: Int? = null)

    /** A part of a document that starts with a "## " heading, as shown in the help. */
    data class Section(val title: String, val blocks: List<Block>)

    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val NUMBERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        var previousBlank = true
        var inList = false

        fun appendToLast(text: String) {
            val last = blocks.removeAt(blocks.lastIndex)
            blocks += last.copy(text = last.text + " " + text)
        }

        for (raw in markdown.replace("\r\n", "\n").lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) {
                previousBlank = true
                continue
            }
            val indented = line.first().isWhitespace()
            val depth = if (indented) 1 else 0
            val heading = HEADING.find(line)
            val bullet = BULLET.find(line)
            val numbered = NUMBERED.find(line)
            when {
                heading != null && !indented -> {
                    blocks += Block(Kind.HEADING, heading.groupValues[2].trim(), level = heading.groupValues[1].length)
                    inList = false
                }
                bullet != null -> {
                    blocks += Block(Kind.BULLET, bullet.groupValues[2].trim(), level = depth)
                    inList = true
                }
                numbered != null -> {
                    blocks += Block(Kind.NUMBERED, numbered.groupValues[3].trim(), level = depth, number = numbered.groupValues[2].toInt())
                    inList = true
                }
                // An indented paragraph after a blank line belongs to the list item above it.
                previousBlank && indented && inList -> blocks += Block(Kind.PARAGRAPH, line.trim(), level = 1)
                previousBlank || blocks.isEmpty() || blocks.last().kind == Kind.HEADING -> {
                    blocks += Block(Kind.PARAGRAPH, line.trim())
                    inList = false
                }
                else -> appendToLast(line.trim())
            }
            previousBlank = false
        }
        return blocks
    }

    /**
     * The document split at its "## " headings. Text before the first one, if any, becomes a
     * section without a title.
     */
    fun sections(markdown: String): List<Section> {
        val sections = mutableListOf<Section>()
        var title: String? = null
        var current = mutableListOf<Block>()
        for (block in parse(markdown)) {
            if (block.kind == Kind.HEADING && block.level == 2) {
                if (title != null || current.isNotEmpty()) sections += Section(title.orEmpty(), current)
                title = block.text
                current = mutableListOf()
            } else {
                current += block
            }
        }
        if (title != null || current.isNotEmpty()) sections += Section(title.orEmpty(), current)
        return sections
    }
}
