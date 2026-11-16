package org.calamares.miga.data.model

/**
 * Lists pasted into the recipe editor: one ingredient or step per line, with the bullets and
 * numbers they usually carry ("- 200 g harina", "2. Bate los huevos", "Paso 3:") removed.
 */
object PastedLists {

    /** Bullets, "1." / "2)" numbering (not "1.5 kg") and "Paso 3:" / "Step 3." labels. */
    private val MARKER = Regex("""^\s*(?:[-•*·▪◦–—]+|\(?\d{1,2}[.)](?!\d)|(?:paso|step)\s*\d{1,2}\s*[:.)-]?)\s*""", RegexOption.IGNORE_CASE)

    /** The items of [text], one per non-blank line, without their markers. */
    fun lines(text: String): List<String> =
        text.lines().map { it.replace(MARKER, "").trim() }.filter { it.isNotEmpty() }

    /**
     * The lines when going from [old] to [new] pasted several lines at once; null for ordinary
     * typing, Enter included (it adds a single character).
     */
    fun pastedLines(old: String, new: String): List<String>? {
        if ('\n' !in new || new.length - old.length < 2) return null
        return lines(new).takeIf { it.size >= 2 }
    }

    /** An ingredient line as quantity, unit and name ("2 cucharadas de aceite"). */
    fun ingredient(line: String): ParsedShoppingEntry = ShoppingEntryParser.parseLine(line)
}
