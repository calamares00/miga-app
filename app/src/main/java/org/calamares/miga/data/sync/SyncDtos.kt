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
    val revision: Long = 0
)

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
    val isFavorite: Boolean = false,
    /**
     * Personal rating (1-5), null when not rated. Shared by every device in the namespace, like
     * isFavorite (not per person).
     */
    val rating: Int? = null,
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
    val shoppingLists: List<ShoppingListSyncDto> = emptyList()
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
