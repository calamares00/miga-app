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
    val syncConnectionId: Long? = null
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
    val syncConnectionId: Long? = null
) {
    val isPack: Boolean get() = packId != null
    val isSynced: Boolean get() = syncConnectionId != null
}

data class RecipeBookDraft(
    val id: Long = 0L,
    /** Only set on import, to keep the uid of the exported book; null generates a new one. */
    val uid: String? = null,
    val name: String,
    val coverPhotoUri: String?
)

fun RecipeBook.toDraft() = RecipeBookDraft(id = id, uid = uid, name = name, coverPhotoUri = coverPhotoUri)

fun emptyRecipeBookDraft() = RecipeBookDraft(id = 0L, name = "", coverPhotoUri = null)
