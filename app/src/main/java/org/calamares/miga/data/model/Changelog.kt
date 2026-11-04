package org.calamares.miga.data.model

/**
 * Release notes of one build, read from the bundled assets (see SettingsRepository.readChangelog).
 * [versionName] comes from the notes' first line ("Novedades de la versión 1.0.6:" / "What's new
 * in 1.0.6:"), which is not repeated in [items].
 */
data class ChangelogEntry(val versionCode: Int, val versionName: String?, val items: List<String>) {
    /** "1.0.6 (204)": the version with its build number, or just "(204)" without a version line. */
    val title: String get() = versionName?.let { "$it ($versionCode)" } ?: "($versionCode)"
}

object ChangelogParser {

    /** A first line ending in a version number and a colon. */
    private val HEADER = Regex("""(\d+(?:\.\d+)+)\s*:\s*$""")
    private val BULLET = Regex("""^[•*-]\s+""")

    /** Pure logic: [text] is the content of one changelog file. */
    fun parse(versionCode: Int, text: String): ChangelogEntry {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val versionName = lines.firstOrNull()?.let { HEADER.find(it)?.groupValues?.get(1) }
        val items = (if (versionName != null) lines.drop(1) else lines)
            .map { it.replace(BULLET, "").trim() }
            .filter { it.isNotEmpty() }
        return ChangelogEntry(versionCode, versionName, items)
    }
}
