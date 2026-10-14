package org.calamares.miga.data.model

/**
 * Light formatting for free text such as steps and notes: a small Markdown subset stored as plain
 * text, so it travels unchanged through backups, sync, packs and exports.
 *
 * Supported: **bold** (or __bold__), *italic* (or _italic_), ~~strikethrough~~, bullet lists
 * ("- item", "* item", "• item"), numbered lists ("1. item" or "1) item"), headings ("# Title")
 * and backslash escapes ("\*" is a literal asterisk). Anything else is shown as typed. Pure
 * logic, so it can be unit tested.
 */
object RichText {

    enum class LineKind { PARAGRAPH, BULLET, NUMBERED, HEADING }

    /**
     * A line of the source text, [start] to [end] (exclusive, without the line break). The list or
     * heading prefix goes from [start] to [contentStart]; [number] is set for numbered items.
     */
    data class Line(val start: Int, val end: Int, val contentStart: Int, val kind: LineKind, val number: Int?)

    /**
     * Formatting of every character of [text]: [marker] characters are syntax (asterisks, list
     * prefixes, escapes) that renderers drop and the editor dims; the rest carry their style.
     */
    class Parsed(
        val text: String,
        val lines: List<Line>,
        val bold: BooleanArray,
        val italic: BooleanArray,
        val strike: BooleanArray,
        val marker: BooleanArray
    )

    /** A piece of visible text with a single style. */
    data class Run(val text: String, val bold: Boolean = false, val italic: Boolean = false, val strike: Boolean = false)

    /** A line ready to render: its kind, list number and styled runs (syntax removed). */
    data class Block(val kind: LineKind, val number: Int?, val runs: List<Run>) {
        val plainText: String get() = runs.joinToString("") { it.text }
    }

    private val HEADING_PREFIX = Regex("""^\s*#{1,3}\s+""")
    internal val BULLET_PREFIX = Regex("""^(\s*)([-*•+])(\s+)""")
    internal val NUMBERED_PREFIX = Regex("""^(\s*)(\d{1,3})([.)])(\s+)""")
    private const val ESCAPABLE = "\\*_~#-+.•"

    fun parse(text: String): Parsed {
        val n = text.length
        val bold = BooleanArray(n)
        val italic = BooleanArray(n)
        val strike = BooleanArray(n)
        val marker = BooleanArray(n)
        val lines = mutableListOf<Line>()
        var lineStart = 0
        while (lineStart <= n) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) n else it }
            val content = text.substring(lineStart, lineEnd)
            val heading = HEADING_PREFIX.find(content)
            val numbered = NUMBERED_PREFIX.find(content)
            val bullet = BULLET_PREFIX.find(content)
            val line = when {
                heading != null -> Line(lineStart, lineEnd, lineStart + heading.range.last + 1, LineKind.HEADING, null)
                numbered != null -> Line(
                    lineStart, lineEnd, lineStart + numbered.range.last + 1, LineKind.NUMBERED, numbered.groupValues[2].toIntOrNull()
                )
                bullet != null -> Line(lineStart, lineEnd, lineStart + bullet.range.last + 1, LineKind.BULLET, null)
                else -> Line(lineStart, lineEnd, lineStart, LineKind.PARAGRAPH, null)
            }
            for (i in line.start until line.contentStart) marker[i] = true
            Inline(text, bold, italic, strike, marker).parse(line.contentStart, line.end, line.kind == LineKind.HEADING, false, false)
            lines += line
            lineStart = lineEnd + 1
        }
        return Parsed(text, lines, bold, italic, strike, marker)
    }

    /** One block per line, with the syntax removed and the styles grouped into runs. */
    fun blocks(text: String): List<Block> {
        val parsed = parse(text)
        return parsed.lines.map { line ->
            val runs = mutableListOf<Run>()
            val current = StringBuilder()
            var style: Triple<Boolean, Boolean, Boolean>? = null
            for (i in line.contentStart until line.end) {
                if (parsed.marker[i]) continue
                val charStyle = Triple(parsed.bold[i], parsed.italic[i], parsed.strike[i])
                if (style != null && charStyle != style && current.isNotEmpty()) {
                    runs += Run(current.toString(), style.first, style.second, style.third)
                    current.clear()
                }
                style = charStyle
                current.append(text[i])
            }
            if (current.isNotEmpty() && style != null) runs += Run(current.toString(), style.first, style.second, style.third)
            Block(line.kind, line.number, runs)
        }
    }

    /** Readable plain text: syntax removed, bullets as "•" (for text sharing and speech). */
    fun toPlainText(text: String): String = blocks(text).joinToString("\n") { block ->
        when (block.kind) {
            LineKind.BULLET -> "• " + block.plainText
            LineKind.NUMBERED -> "${block.number ?: 1}. " + block.plainText
            else -> block.plainText
        }
    }

    /** True when [text] uses any formatting, so callers can keep the plain path otherwise. */
    fun hasFormatting(text: String): Boolean = parse(text).marker.any { it }

    /** Inline parser for one line: fills the style arrays for [start, end). */
    private class Inline(
        val text: String,
        val bold: BooleanArray,
        val italic: BooleanArray,
        val strike: BooleanArray,
        val marker: BooleanArray
    ) {
        fun parse(start: Int, end: Int, isBold: Boolean, isItalic: Boolean, isStrike: Boolean) {
            var k = start
            while (k < end) {
                val c = text[k]
                if (c == '\\' && k + 1 < end && text[k + 1] in ESCAPABLE) {
                    marker[k] = true
                    style(k + 1, isBold, isItalic, isStrike)
                    k += 2
                    continue
                }
                val double = if (k + 1 < end) text.substring(k, k + 2) else ""
                if (double == "**" || double == "__" || double == "~~") {
                    val close = findDoubleClose(double, k + 2, end)
                    if (close != null) {
                        marker[k] = true; marker[k + 1] = true
                        marker[close] = true; marker[close + 1] = true
                        if (double == "~~") parse(k + 2, close, isBold, isItalic, true)
                        else parse(k + 2, close, true, isItalic, isStrike)
                        k = close + 2
                        continue
                    }
                }
                if ((c == '*' || c == '_') && opensSingle(c, k, end)) {
                    val close = findSingleClose(c, k + 1, end)
                    if (close != null) {
                        marker[k] = true
                        marker[close] = true
                        parse(k + 1, close, isBold, true, isStrike)
                        k = close + 1
                        continue
                    }
                }
                style(k, isBold, isItalic, isStrike)
                k++
            }
        }

        private fun style(index: Int, isBold: Boolean, isItalic: Boolean, isStrike: Boolean) {
            bold[index] = isBold
            italic[index] = isItalic
            strike[index] = isStrike
        }

        /** Closing "**" (or "__", "~~") after non-empty, non-space content; the last of a "***" run. */
        private fun findDoubleClose(delimiter: String, from: Int, end: Int): Int? {
            if (from >= end || text[from].isWhitespace()) return null
            var close = text.indexOf(delimiter, from + 1)
            while (close in (from + 1) until end - 1) {
                if (!text[close - 1].isWhitespace()) {
                    // In "***bold italic***" the closing "**" is the last two asterisks.
                    if (close + 2 < end && text[close + 2] == delimiter[0]) close++
                    if (close + 1 < end) return close
                }
                close = text.indexOf(delimiter, close + 1)
            }
            return null
        }

        /** "*" and "_" open italics when followed by text; "_" also needs a word boundary before. */
        private fun opensSingle(c: Char, k: Int, end: Int): Boolean {
            if (k + 1 >= end || text[k + 1].isWhitespace() || text[k + 1] == c) return false
            return c == '*' || k == 0 || !text[k - 1].isLetterOrDigit()
        }

        private fun findSingleClose(c: Char, from: Int, end: Int): Int? {
            var j = from + 1
            while (j < end) {
                if (text[j] == c) {
                    // A double delimiter inside belongs to nested bold.
                    if (j + 1 < end && text[j + 1] == c) {
                        j += 2
                        continue
                    }
                    val boundaryAfter = c == '*' || j + 1 >= end || !text[j + 1].isLetterOrDigit()
                    if (!text[j - 1].isWhitespace() && boundaryAfter) return j
                }
                j++
            }
            return null
        }
    }
}

/**
 * Editing helpers behind the formatting toolbar: they work on the text and the selection and
 * return the new ones. Pure logic, so it can be unit tested.
 */
object RichTextEditing {

    data class Edit(val text: String, val selectionStart: Int, val selectionEnd: Int)

    /**
     * Toggles [marker] ("**" bold, "*" italic, "~~" strikethrough) around the selection. With no
     * selection it inserts an empty pair and leaves the cursor in the middle.
     */
    fun toggleWrap(text: String, selectionStart: Int, selectionEnd: Int, marker: String): Edit {
        var start = minOf(selectionStart, selectionEnd)
        var end = maxOf(selectionStart, selectionEnd)
        if (start == end) {
            return Edit(text.substring(0, start) + marker + marker + text.substring(end), start + marker.length, start + marker.length)
        }
        // Markdown needs the markers next to the text, so surrounding spaces stay outside.
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start == end) return Edit(text, selectionStart, selectionEnd)

        val symbol = marker[0]
        val before = countRun(text, start - 1, -1, symbol)
        val after = countRun(text, end, 1, symbol)
        val present = when (marker) {
            "*" -> minOf(before, after) % 2 == 1
            else -> minOf(before, after) >= marker.length
        }
        return if (present) {
            val newText = text.substring(0, start - marker.length) + text.substring(start, end) + text.substring(end + marker.length)
            Edit(newText, start - marker.length, end - marker.length)
        } else {
            val newText = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
            Edit(newText, start + marker.length, end + marker.length)
        }
    }

    /**
     * Turns the lines touched by the selection into a bullet ("- ") or numbered ("1. ") list, or
     * back into plain lines when all of them already are that kind of list.
     */
    fun toggleList(text: String, selectionStart: Int, selectionEnd: Int, numbered: Boolean): Edit {
        val start = minOf(selectionStart, selectionEnd)
        val end = maxOf(selectionStart, selectionEnd)
        val firstLine = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (start == 0 || it < 0) 0 else it + 1 }
        val lastLineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        val block = text.substring(firstLine, lastLineEnd).split('\n')
        val target = if (numbered) RichText.NUMBERED_PREFIX else RichText.BULLET_PREFIX
        val allListed = block.all { target.containsMatchIn(it) }
        var number = 0
        val newLines = block.map { line ->
            val content = stripListPrefix(line)
            when {
                allListed -> content
                numbered -> "${++number}. $content"
                else -> "- $content"
            }
        }
        val newBlock = newLines.joinToString("\n")
        val newText = text.substring(0, firstLine) + newBlock + text.substring(lastLineEnd)
        return if (start == end) {
            val cursor = firstLine + newLines.first().length
            Edit(newText, cursor, cursor)
        } else {
            Edit(newText, firstLine, firstLine + newBlock.length)
        }
    }

    /**
     * Called after the user typed a line break at [cursor] (the position after it). On a list item
     * the next item is started; on an empty item the list is ended instead. Returns null when
     * nothing needs to change.
     */
    fun continueList(text: String, cursor: Int): Edit? {
        if (cursor <= 0 || cursor > text.length || text[cursor - 1] != '\n') return null
        val lineEnd = cursor - 1
        val lineStart = text.lastIndexOf('\n', lineEnd - 1).let { if (lineEnd == 0 || it < 0) 0 else it + 1 }
        val line = text.substring(lineStart, lineEnd)
        val bullet = RichText.BULLET_PREFIX.find(line)
        val numbered = RichText.NUMBERED_PREFIX.find(line)
        val prefix = when {
            numbered != null -> numbered.value
            bullet != null -> bullet.value
            else -> return null
        }
        if (line.substring(prefix.length).isBlank()) {
            // Enter on an empty item ends the list: remove its prefix and the new line break.
            val newText = text.substring(0, lineStart) + text.substring(cursor)
            return Edit(newText, lineStart, lineStart)
        }
        val nextPrefix = if (numbered != null) {
            val (indent, number, delimiter, space) = numbered.destructured
            "$indent${(number.toIntOrNull() ?: 0) + 1}$delimiter$space"
        } else {
            prefix
        }
        val newText = text.substring(0, cursor) + nextPrefix + text.substring(cursor)
        return Edit(newText, cursor + nextPrefix.length, cursor + nextPrefix.length)
    }

    private fun stripListPrefix(line: String): String {
        RichText.NUMBERED_PREFIX.find(line)?.let { return line.substring(it.value.length) }
        RichText.BULLET_PREFIX.find(line)?.let { return line.substring(it.value.length) }
        return line
    }

    /** Number of consecutive [symbol] characters starting at [from] and moving by [step]. */
    private fun countRun(text: String, from: Int, step: Int, symbol: Char): Int {
        var count = 0
        var i = from
        while (i in text.indices && text[i] == symbol) {
            count++
            i += step
        }
        return count
    }
}
