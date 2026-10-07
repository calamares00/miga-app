package org.calamares.miga.data.model

/** Editable recipe used by the editor and by every save operation. */
data class RecipeDraft(
    val id: Long = 0L,
    /** Only set on import, to keep the uid of the exported recipe; null generates a new one. */
    val uid: String? = null,
    val recipeBookId: Long,
    val name: String,
    val categoryName: String?,
    val difficulty: Difficulty,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val servings: Int,
    val notes: String,
    val source: String,
    val isFavorite: Boolean,
    val photos: List<RecipePhoto>,
    val ingredientGroups: List<IngredientGroup>,
    val stepGroups: List<StepGroup>,
    val tagNames: List<String>,
    val utensilNames: List<String>,
    /** See Recipe.origin. */
    val origin: String? = null,
    val originCountry: String? = null
)

fun Recipe.toDraft() = RecipeDraft(
    id = id,
    uid = uid,
    recipeBookId = recipeBookId,
    name = name,
    categoryName = categoryName,
    difficulty = difficulty,
    prepTimeMinutes = prepTimeMinutes,
    cookTimeMinutes = cookTimeMinutes,
    servings = servings,
    notes = notes,
    source = source,
    isFavorite = isFavorite,
    photos = photos,
    ingredientGroups = ingredientGroups,
    stepGroups = stepGroups,
    tagNames = tags,
    utensilNames = utensils,
    origin = origin,
    originCountry = originCountry
)

fun emptyRecipeDraft(recipeBookId: Long) = RecipeDraft(
    id = 0L,
    recipeBookId = recipeBookId,
    name = "",
    categoryName = null,
    difficulty = Difficulty.MEDIUM,
    prepTimeMinutes = null,
    cookTimeMinutes = null,
    servings = 4,
    notes = "",
    source = "",
    isFavorite = false,
    photos = emptyList(),
    ingredientGroups = listOf(IngredientGroup(name = null, ingredients = emptyList())),
    stepGroups = listOf(StepGroup(name = null, instructions = emptyList())),
    tagNames = emptyList(),
    utensilNames = emptyList()
)
