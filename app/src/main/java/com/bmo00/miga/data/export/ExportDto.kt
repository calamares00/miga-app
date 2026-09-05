package com.bmo00.miga.data.export

import kotlinx.serialization.Serializable

/**
 * Versión del esquema de cada JSON exportado. Un archivo sin la clave "version" (todo lo
 * exportado antes de que existiera este campo) se trata como versión 0; ver
 * [RecipeExporter] para las migraciones que llevan un JSON antiguo hasta la versión actual.
 *
 * v1 -> v2: se añaden "uid" (receta/libro) y "photos"/"books" para poder llevar las fotos en
 * el ZIP de exportación (ver [RecipeExporter]).
 * v2 -> v3: se añade "health" (valoración de salud con IA), opcional con default null; un JSON
 * v2 sin esa clave ya se interpreta bien gracias a ignoreUnknownKeys/el default, no hace falta
 * generar nada en la migración.
 */
const val CURRENT_RECIPE_SCHEMA_VERSION = 3
const val CURRENT_LIBRARY_SCHEMA_VERSION = 3

@Serializable
data class LibraryExportDto(
    val version: Int = CURRENT_LIBRARY_SCHEMA_VERSION,
    val exportedAt: Long,
    val books: List<BookExportDto> = emptyList(),
    val recipes: List<RecipeExportDto>
)

/** Metadatos de un libro incluidos junto a sus recetas al exportar un libro o toda la app. */
@Serializable
data class BookExportDto(
    val uid: String,
    val name: String,
    /** Nombre del fichero de portada dentro de "books/<uid>/" en el ZIP, o null si no tiene. */
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
    /** Fotos de la receta; los ficheros correspondientes viven en "recipes/<uid>/" dentro del ZIP. */
    val photos: List<PhotoExportDto> = emptyList(),
    /** Valoración de salud con IA cacheada (ver HealthRating); null si nunca se ha analizado. */
    val health: RecipeHealthDto? = null
)

@Serializable
data class RecipeHealthDto(
    val colorLevel: String,
    val description: String,
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
