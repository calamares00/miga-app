package com.bmo00.miga.data.sync

import com.bmo00.miga.data.export.IngredientGroupDto
import com.bmo00.miga.data.export.StepGroupDto
import kotlinx.serialization.Serializable

/**
 * DTOs del protocolo de sincronización con miga-server - mismo shape que los DTOs del servidor
 * (ver Dtos.kt en ese repo). Reutiliza [IngredientGroupDto]/[StepGroupDto] de
 * `data/export/ExportDto.kt` (idéntica forma, sin campos de sincronización) en vez de
 * duplicarlos; a diferencia de [com.bmo00.miga.data.export.RecipeExportDto] (que deliberadamente
 * no lleva marcas de tiempo), estos sí las llevan porque son necesarias para "última escritura
 * gana" y para el cursor de sincronización.
 */
@Serializable
data class BookSyncDto(
    val uid: String,
    val name: String,
    val hasCoverPhoto: Boolean = false,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** true si esto es un tombstone de "desvinculado" (admin, desde /ui) y no de "borrado" real:
     *  ver [SyncEngine.applyChanges] - solo tiene sentido cuando [deletedAt] != null. */
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
    val ingredientGroups: List<IngredientGroupDto> = emptyList(),
    val stepGroups: List<StepGroupDto> = emptyList(),
    val tags: List<String> = emptyList(),
    val utensils: List<String> = emptyList(),
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Ver [BookSyncDto.unlinked]. */
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
    /** Ver [BookSyncDto.unlinked]. */
    val unlinked: Boolean = false,
    val revision: Long = 0
)

@Serializable
data class ChangesResponseDto(
    val latestRevision: Long,
    val books: List<BookSyncDto> = emptyList(),
    val recipes: List<RecipeSyncDto> = emptyList(),
    val photos: List<PhotoMetaDto> = emptyList()
)

@Serializable
data class RevisionDto(val revision: Long)

@Serializable
internal data class SyncErrorDto(val message: String)
