package org.calamares.miga.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_books")
data class RecipeBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identifier (UUID) independent of the local id, used by export/import and sync. */
    val uid: String,
    val name: String,
    val coverPhotoUri: String?,
    val createdAt: Long,
    /** Id of the installed pack (see PacksCatalogClient); null for a user's own editable book. */
    val packId: String? = null,
    /** Installed pack version; null when [packId] is null. */
    val packVersion: Int? = null,
    /**
     * Time of the last real change (name or cover), used by sync to decide which copy is newer.
     * Defaults to [createdAt].
     */
    val updatedAt: Long = createdAt,
    /**
     * Sync connection (see SyncConnectionEntity) the book belongs to; null for a local book. Unlike
     * a pack, a synced book is fully editable.
     */
    val syncConnectionId: Long? = null,
    /** Pinned by the user to the top of the books list; local to this device, not synced. */
    val isPinned: Boolean = false,
    /**
     * Series of an installed pack ("World cuisine", "Specials"...), from the catalogue, used to group
     * packs in the books list; null for other books or a pack whose catalogue gives none.
     */
    val packSeries: String? = null,
    /**
     * Who shared this book with the user (their "Your name" when sending it as a file or link), and
     * when; null for the user's own books. Read-only: set only by importing a shared book.
     */
    val sharedBy: String? = null,
    val sharedAt: Long? = null,
    /**
     * A few words about the book ("Our everyday family recipes", or a dedication), shown under
     * its title and on its PDF cover; empty for none.
     */
    @ColumnInfo(defaultValue = "") val description: String = "",
    /** Whose recipes they are ("Grandma Carmen"), shown on the cover and in the PDF; empty for none. */
    @ColumnInfo(defaultValue = "") val author: String = "",
    /** Colour of the cover without a photo (a BookColor name); null picks one from the name. */
    val coverColor: String? = null,
    /** Emoji drawn on the cover without a photo; null for none. */
    val coverEmoji: String? = null
)
