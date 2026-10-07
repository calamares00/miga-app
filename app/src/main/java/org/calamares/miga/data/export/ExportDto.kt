package org.calamares.miga.data.export

import kotlinx.serialization.Serializable
import org.calamares.miga.data.model.TemplateItem

/**
 * Schema version of every exported JSON. Files without a "version" key (exported before it existed)
 * are treated as version 0; RecipeExporter migrates old files up to the current version.
 *
 * v1 -> v2: "uid" (recipe and book) and "photos"/"books" so the ZIP can carry the photos. v2 -> v3:
 * optional "health" (AI health rating). v3 -> v4: optional "nutrition" (AI nutrition estimate). v4
 * -> v5: optional "rating" (personal 1-5 stars). Optional fields default to null, so older files
 * need no migration step for them.
 */
const val CURRENT_RECIPE_SCHEMA_VERSION = 5
const val CURRENT_LIBRARY_SCHEMA_VERSION = 5

@Serializable
data class LibraryExportDto(
    val version: Int = CURRENT_LIBRARY_SCHEMA_VERSION,
    val exportedAt: Long,
    val books: List<BookExportDto> = emptyList(),
    val recipes: List<RecipeExportDto>,
    /** Shopping list templates (absent in older backups). */
    val shoppingTemplates: List<TemplateBackupDto> = emptyList(),
    /** Supermarkets and their aisle order (absent in older backups). */
    val shoppingStores: List<StoreBackupDto> = emptyList()
)

@Serializable
data class TemplateBackupDto(val name: String, val items: List<TemplateItem> = emptyList())

@Serializable
data class StoreBackupDto(val name: String, val argb: Long, val aisleOrder: List<String> = emptyList())

/** Book metadata exported together with its recipes when exporting a book or the whole library. */
@Serializable
data class BookExportDto(
    val uid: String,
    val name: String,
    /**
     * File name of the cover inside "books/<uid>/" in the ZIP, or null when the book has no cover.
     */
    val coverPhotoFileName: String? = null
)

@Serializable
data class RecipeExportDto(
    val version: Int = CURRENT_RECIPE_SCHEMA_VERSION,
    val uid: String,
    val name: String,
    val recipeBookName: String,
    val categoryName: String?,
    val difficulty: String,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val servings: Int,
    val notes: String,
    val source: String,
    val isFavorite: Boolean,
    val ingredientGroups: List<IngredientGroupDto>,
    val stepGroups: List<StepGroupDto>,
    val tags: List<String>,
    val utensils: List<String>,
    /** Recipe photos; the files live in "recipes/<uid>/" inside the ZIP. */
    val photos: List<PhotoExportDto> = emptyList(),
    /** Cached AI health rating; null when the recipe was never analysed. */
    val health: RecipeHealthDto? = null,
    /** Cached AI nutrition estimate; null when the recipe was never analysed. */
    val nutrition: RecipeNutritionDto? = null,
    /** Personal rating from 1 to 5 stars; null when not rated. */
    val rating: Int? = null,
    /** Where the recipe comes from and its country code (see RecipeOrigin); absent in older files. */
    val origin: String? = null,
    val originCountry: String? = null
)

@Serializable
data class RecipeHealthDto(
    val colorLevel: String,
    val description: String,
    val fingerprint: String,
    val analyzedAt: Long
)

@Serializable
data class RecipeNutritionDto(
    val caloriesPerServing: Int,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fingerprint: String,
    val analyzedAt: Long
)

@Serializable
data class PhotoExportDto(
    val fileName: String,
    val isCover: Boolean
)

@Serializable
data class IngredientGroupDto(
    val name: String?,
    val ingredients: List<IngredientDto>
)

@Serializable
data class IngredientDto(
    val name: String,
    val quantity: Double?,
    val unit: String?
)

@Serializable
data class StepGroupDto(
    val name: String?,
    val instructions: List<String>
)
