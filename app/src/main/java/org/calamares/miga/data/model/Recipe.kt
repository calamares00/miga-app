package org.calamares.miga.data.model

data class Recipe(
    val id: Long = 0L,
    val uid: String,
    val recipeBookId: Long,
    val recipeBookName: String,
    val name: String,
    val categoryId: Long?,
    val categoryName: String?,
    val difficulty: Difficulty,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val servings: Int,
    val notes: String,
    val source: String,
    val isFavorite: Boolean,
    val timesCooked: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val photos: List<RecipePhoto>,
    val ingredientGroups: List<IngredientGroup>,
    val stepGroups: List<StepGroup>,
    val tags: List<String>,
    val utensils: List<String>,
    val healthRating: HealthRating? = null,
    val nutritionInfo: NutritionInfo? = null,
    /** Personal rating from 1 to 5 stars; null when not rated. */
    val rating: Int? = null,
    /** Where it comes from (see RecipeOrigin): free text and the country code for the flag. */
    val origin: String? = null,
    val originCountry: String? = null,
    /** Dated personal notes, newest first (see RecipeNote). */
    val journal: List<RecipeNote> = emptyList()
) {
    val totalTimeMinutes: Int?
        get() = if (prepTimeMinutes == null && cookTimeMinutes == null) {
            null
        } else {
            (prepTimeMinutes ?: 0) + (cookTimeMinutes ?: 0)
        }

    val coverPhotoUri: String?
        get() = photos.firstOrNull { it.isCover }?.uri ?: photos.firstOrNull()?.uri
}

data class RecipePhoto(
    val uri: String,
    val isCover: Boolean
)

data class IngredientGroup(
    /** Null for the main recipe; a name marks a sub-recipe (for example "Tomato sauce"). */
    val name: String?,
    val ingredients: List<Ingredient>
)

data class Ingredient(
    val name: String,
    val quantity: Double?,
    val unit: String?,
    /** Can be left out ("to taste", a garnish): shown as optional, not written in the name. */
    val optional: Boolean = false
)

data class StepGroup(
    /** Null for the main recipe; a name marks a sub-recipe (for example "Tomato sauce"). */
    val name: String?,
    val instructions: List<String>
)

data class RecipeSummary(
    val id: Long,
    val name: String,
    val categoryName: String?,
    val coverPhotoUri: String?,
    val difficulty: Difficulty,
    val totalTimeMinutes: Int?,
    val isFavorite: Boolean,
    val timesCooked: Int,
    val tags: List<String>,
    val utensils: List<String>,
    val createdAt: Long,
    val prepTimeMinutes: Int?,
    val originCountry: String? = null
)

fun Recipe.toSummary() = RecipeSummary(
    id = id,
    name = name,
    categoryName = categoryName,
    coverPhotoUri = coverPhotoUri,
    difficulty = difficulty,
    totalTimeMinutes = totalTimeMinutes,
    isFavorite = isFavorite,
    timesCooked = timesCooked,
    tags = tags,
    utensils = utensils,
    createdAt = createdAt,
    prepTimeMinutes = prepTimeMinutes,
    originCountry = originCountry
)

/** "(optional)", ", opcional"… at the end of an ingredient name, as people often write it. */
private val OPTIONAL_MARKER = Regex("""\s*[(\[,–-]?\s*\b(opcional|optional)\b\s*[)\]]?\s*$""", RegexOption.IGNORE_CASE)

/**
 * [name] without an "optional" note at its end, and whether it had one: "perejil (opcional)" is
 * "perejil", optional. The flag is how the app marks it, so it is not kept in the name.
 */
fun splitOptionalMarker(name: String): Pair<String, Boolean> {
    val match = OPTIONAL_MARKER.find(name) ?: return name.trim() to false
    val stripped = name.substring(0, match.range.first).trim()
    return if (stripped.isEmpty()) name.trim() to false else stripped to true
}
