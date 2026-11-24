package org.calamares.miga.data.sync

import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.StepGroupDto
import kotlinx.serialization.Serializable

/**
 * DTOs of the sync protocol with miga-server; same shape as the server's DTOs (see Dtos.kt in that
 * repository). They reuse [IngredientGroupDto] and [StepGroupDto] from the export format. Unlike
 * [org.calamares.miga.data.export.RecipeExportDto], they carry timestamps, needed for
 * last-write-wins and for the sync cursor.
 */
@Serializable
data class BookSyncDto(
    val uid: String,
    val name: String,
    val hasCoverPhoto: Boolean = false,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /**
     * True when this tombstone means "unlinked" (by an admin from the web UI) rather than really
     * deleted; see [SyncEngine.applyChanges]. Only meaningful when [deletedAt] is set.
     */
    val unlinked: Boolean = false,
    val revision: Long = 0,
    /**
     * Description, author and the look of a cover without a photo (see RecipeBookEntity.description).
     * Null means "not sent" (an older app or server), and the copy that receives it keeps its own
     * values; an empty text or a missing colour is sent as "" to clear them.
     */
    val description: String? = null,
    val author: String? = null,
    val coverColor: String? = null,
    val coverEmoji: String? = null
)

/** Recipe schema this app version syncs (see [RecipeSyncDto.schema]). */
const val RECIPE_SYNC_SCHEMA = 2

@Serializable
data class RecipeSyncDto(
    val uid: String,
    val bookUid: String,
    val name: String,
    val categoryName: String? = null,
    val difficulty: String,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val servings: Int,
    val notes: String = "",
    val source: String = "",
    /** Kept for older servers and apps; always false now: favourites are personal and local to each device. */
    val isFavorite: Boolean = false,
    /** Kept for older servers and apps; always null now: the rating is personal and local to each device, like isFavorite. */
    val rating: Int? = null,
    /** Where the recipe comes from and its country code (see RecipeOrigin). */
    val origin: String? = null,
    val originCountry: String? = null,
    /**
     * Recipe schema the sender understands: 2 with [origin]. miga-server 0.3.0 answers with 2; an
     * older server does not know the field (it reads as 1) and sends the origin back as null, which
     * must not clear what the device has. The app always sends [RECIPE_SYNC_SCHEMA].
     */
    val schema: Int = 1,
    val ingredientGroups: List<IngredientGroupDto> = emptyList(),
    val stepGroups: List<StepGroupDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val utensils: List<String> = emptyList(),
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** See [BookSyncDto.unlinked]. */
    val unlinked: Boolean = false,
    val revision: Long = 0
)

@Serializable
data class PhotoMetaDto(
    val uid: String,
    val recipeUid: String,
    val isCover: Boolean = false,
    val position: Int = 0,
    val contentType: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** See [BookSyncDto.unlinked]. */
    val unlinked: Boolean = false,
    val revision: Long = 0
)

/** Item of the shared shopping list (see ShoppingItems in miga-server). */
@Serializable
data class ShoppingItemSyncDto(
    val uid: String,
    val listId: String = "main",
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val checked: Boolean = false,
    val imageUrl: String? = null,
    /** ProductInfo as JSON (see data/model/ProductInfo.kt); the server treats it as opaque text. */
    val productInfo: String? = null,
    val addedBy: String? = null,
    val updatedBy: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val revision: Long = 0
)

/** An extra shopping list. The default "main" list has no row and no DTO. */
@Serializable
data class ShoppingListSyncDto(
    val uid: String,
    val name: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val revision: Long = 0
)

@Serializable
data class ChangesResponseDto(
    val latestRevision: Long,
    val books: List<BookSyncDto> = emptyList(),
    val recipes: List<RecipeSyncDto> = emptyList(),
    val photos: List<PhotoMetaDto> = emptyList(),
    val shoppingItems: List<ShoppingItemSyncDto> = emptyList(),
    val shoppingLists: List<ShoppingListSyncDto> = emptyList(),
    /** More changes follow: ask again from [latestRevision]. Servers before 1.1.0 never set it. */
    val hasMore: Boolean = false
)

@Serializable
data class CreateInvitationRequest(val label: String)

/**
 * Response of POST /sync/invitations: a new token for the same namespace to invite another device.
 */
@Serializable
data class InvitationDto(val namespaceId: String, val tokenId: String, val token: String, val label: String, val createdAt: Long)

@Serializable
data class RevisionDto(val revision: Long)

@Serializable
internal data class SyncErrorDto(val message: String)

// --- Books each device syncs (miga-server 1.3.0) ---

/** A book of the server as this device sees it when choosing what to sync. */
@Serializable
data class ServerBookDto(
    val uid: String,
    val name: String,
    val hasCoverPhoto: Boolean = false,
    val coverColor: String? = null,
    val coverEmoji: String? = null,
    val description: String = "",
    val author: String = "",
    val recipeCount: Int = 0,
    val photoCount: Int = 0,
    /** Photos and cover, in bytes: about what following it downloads. */
    val bytes: Long = 0,
    val updatedAt: Long = 0,
    /** Name of the device that uploaded it first, when the server knows it. */
    val createdBy: String? = null,
    /** This device syncs it. */
    val followed: Boolean = false,
    /** How many devices sync it. */
    val devices: Int = 0
)

@Serializable
data class BookCatalogDto(
    /** This device chose its books; false means it gets all of them. */
    val selective: Boolean = false,
    val followNewBooks: Boolean = true,
    val books: List<ServerBookDto> = emptyList()
)

@Serializable
data class BookSelectionRequest(val books: List<String>, val followNewBooks: Boolean)

/** A whole book, downloaded when this device starts following it. */
@Serializable
data class BookContentDto(
    val book: BookSyncDto,
    val recipes: List<RecipeSyncDto> = emptyList(),
    val photos: List<PhotoMetaDto> = emptyList()
)
