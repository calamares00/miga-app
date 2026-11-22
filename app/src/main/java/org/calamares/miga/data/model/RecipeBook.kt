package org.calamares.miga.data.model

data class RecipeBook(
    val id: Long = 0L,
    val uid: String,
    val name: String,
    val coverPhotoUri: String?,
    /** Id of the installed pack (see PacksCatalogClient); null for a user's own editable book. */
    val packId: String? = null,
    val packVersion: Int? = null,
    /** Sync connection the book belongs to (see SyncConnection); null for a local book. */
    val syncConnectionId: Long? = null,
    /** Shown first in the books list (see RecipeBookSummary.isPinned). */
    val isPinned: Boolean = false,
    /** Series of an installed pack, from the catalogue (see RecipeBookEntity.packSeries). */
    val packSeries: String? = null,
    /** Who shared the book with the user, and when (see RecipeBookEntity.sharedBy). */
    val sharedBy: String? = null,
    val sharedAt: Long? = null,
    /** Dedication, author and the look of a cover without a photo (see RecipeBookEntity.description). */
    val description: String = "",
    val author: String = "",
    val coverColor: String? = null,
    val coverEmoji: String? = null
) {
    /**
     * Pack books are read-only: they cannot be renamed, get a new cover or have recipes added or
     * removed.
     */
    val isPack: Boolean get() = packId != null

    /**
     * Synced books are fully editable, unlike packs; they only differ by a badge and by what the
     * sync engine does with them.
     */
    val isSynced: Boolean get() = syncConnectionId != null
}

data class RecipeBookSummary(
    val id: Long,
    val name: String,
    val coverPhotoUri: String?,
    val recipeCount: Int,
    val packId: String? = null,
    val packVersion: Int? = null,
    val syncConnectionId: Long? = null,
    /** Shown first in the books list, with a pin on its cover. */
    val isPinned: Boolean = false,
    val packSeries: String? = null,
    /** Who shared the book with the user (see RecipeBookEntity.sharedBy), shown under its name. */
    val sharedBy: String? = null,
    /** Shown on the cover (see RecipeBookEntity.author, coverColor and coverEmoji). */
    val author: String = "",
    val coverColor: String? = null,
    val coverEmoji: String? = null
) {
    val isPack: Boolean get() = packId != null
    val isSynced: Boolean get() = syncConnectionId != null
}

data class RecipeBookDraft(
    val id: Long = 0L,
    /** Only set on import, to keep the uid of the exported book; null generates a new one. */
    val uid: String? = null,
    val name: String,
    val coverPhotoUri: String?,
    val description: String = "",
    val author: String = "",
    val coverColor: String? = null,
    val coverEmoji: String? = null
)

fun RecipeBook.toDraft() = RecipeBookDraft(
    id = id, uid = uid, name = name, coverPhotoUri = coverPhotoUri,
    description = description, author = author, coverColor = coverColor, coverEmoji = coverEmoji
)

/**
 * What a user writes about a book besides its name: dedication, author and the look of a cover
 * without a photo. Imported values are trimmed to sensible lengths.
 */
data class BookDetails(
    val description: String = "",
    val author: String = "",
    val coverColor: String? = null,
    val coverEmoji: String? = null
) {
    fun cleaned() = BookDetails(
        description = description.trim().take(MAX_DESCRIPTION),
        author = author.trim().take(MAX_AUTHOR),
        coverColor = coverColor?.takeIf { BookColor.fromName(it) != null },
        coverEmoji = coverEmoji?.trim()?.take(MAX_EMOJI)?.ifEmpty { null }
    )

    /**
     * These details updated with what a synced copy sends: null means "not sent" (an older app or
     * server) and keeps the current value; an empty text clears it.
     */
    fun updatedWith(description: String?, author: String?, coverColor: String?, coverEmoji: String?) = BookDetails(
        description = description ?: this.description,
        author = author ?: this.author,
        coverColor = if (coverColor == null) this.coverColor else coverColor.ifEmpty { null },
        coverEmoji = if (coverEmoji == null) this.coverEmoji else coverEmoji.ifEmpty { null }
    ).cleaned()

    companion object {
        const val MAX_DESCRIPTION = 300
        const val MAX_AUTHOR = 60
        private const val MAX_EMOJI = 16
    }
}

val RecipeBook.details: BookDetails get() = BookDetails(description, author, coverColor, coverEmoji)

fun emptyRecipeBookDraft() = RecipeBookDraft(id = 0L, name = "", coverPhotoUri = null)
