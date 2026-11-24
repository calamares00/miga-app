package org.calamares.miga.data.tips

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.calamares.miga.data.model.searchKey
import java.util.UUID

/**
 * A tip the assistant found while importing a link (a reel with a trick, a page about keeping
 * herbs fresh), kept in the user's tips library apart from the ones bundled with the app, with
 * where it came from. Stored as JSON in the settings, so backups carry it.
 */
@Serializable
data class ImportedTip(
    val id: String,
    val category: String,
    val title: String,
    val body: String,
    val keywords: List<String> = emptyList(),
    /** Where it came from, as recipe sources are written ("[@user](profile) - [reel](post)" or a link). */
    val source: String = "",
    val addedAt: Long = 0
) {
    fun toCookingTip(): CookingTip = CookingTip(
        id = id,
        category = runCatching { TipCategory.valueOf(category) }.getOrDefault(TipCategory.KITCHEN),
        title = title,
        body = body,
        keywords = keywords.map(::searchKey).distinct(),
        source = source,
        imported = true
    )

    companion object {
        /** Imported tips' ids start like this, so they never clash with the bundled ones. */
        const val ID_PREFIX = "imported-"

        fun create(category: String, title: String, body: String, keywords: List<String>, source: String, now: Long = System.currentTimeMillis()) =
            ImportedTip(
                id = ID_PREFIX + UUID.randomUUID(),
                category = runCatching { TipCategory.valueOf(category.trim().uppercase()) }.getOrDefault(TipCategory.KITCHEN).name,
                title = title.trim().take(MAX_TITLE),
                body = body.trim().take(MAX_BODY),
                keywords = keywords.map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_KEYWORDS),
                source = source,
                addedAt = now
            )

        private const val MAX_TITLE = 120
        private const val MAX_BODY = 1200
        private const val MAX_KEYWORDS = 6

        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(serializer())

        fun decode(text: String?): List<ImportedTip> =
            text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

        fun encode(tips: List<ImportedTip>): String = json.encodeToString(serializer, tips)
    }
}
