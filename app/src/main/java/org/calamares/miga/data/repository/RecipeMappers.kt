package org.calamares.miga.data.repository

import org.calamares.miga.data.local.dao.RecipeCategoryLink
import org.calamares.miga.data.local.TokenCipher
import org.calamares.miga.data.local.entity.RecipeNoteEntity
import org.calamares.miga.data.model.RecipeNote
import org.calamares.miga.data.local.entity.CategoryEntity
import org.calamares.miga.data.local.entity.IngredientCatalogEntity
import org.calamares.miga.data.local.entity.IngredientCategoryEntity
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.local.entity.RecipeTagCrossRef
import org.calamares.miga.data.local.entity.RecipeUtensilCrossRef
import org.calamares.miga.data.local.entity.RecipeWithDetails
import org.calamares.miga.data.local.entity.SyncConnectionEntity
import org.calamares.miga.data.local.entity.TagEntity
import org.calamares.miga.data.local.entity.UtensilEntity
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.model.HealthRating
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.NutritionInfo
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBook
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.model.SyncConnection
import kotlinx.coroutines.flow.map

fun RecipeBookEntity.toDomain() =
    RecipeBook(id, uid, name, coverPhotoUri, packId, packVersion, syncConnectionId, isPinned, packSeries, sharedBy, sharedAt)

/**
 * The "?: accessToken" fallback is deliberate: decrypt returns null (it does not throw) for rows
 * stored before the token was encrypted, and in that case the plain value is still valid.
 */
fun SyncConnectionEntity.toDomain(): SyncConnection {
    val decryptedToken = TokenCipher.decrypt(accessToken) ?: accessToken
    return SyncConnection(id, label, serverUrl, namespaceId, decryptedToken, lastSyncedRevision, lastSyncedAt, lastSyncError, syncShopping, shoppingPulled)
}

fun RecipeWithDetails.toDomain(): Recipe {
    val sortedIngredients = ingredients.sortedBy { it.position }
    val ingredientGroups = LinkedHashMap<String?, MutableList<Ingredient>>()
    sortedIngredients.forEach { entity ->
        ingredientGroups.getOrPut(entity.groupName) { mutableListOf() }
            .add(Ingredient(entity.name, entity.quantity, entity.unit, entity.optional))
    }

    val sortedSteps = steps.sortedBy { it.position }
    val stepGroups = LinkedHashMap<String?, MutableList<String>>()
    sortedSteps.forEach { entity ->
        stepGroups.getOrPut(entity.groupName) { mutableListOf() }.add(entity.instruction)
    }

    return Recipe(
        id = recipe.id,
        uid = recipe.uid,
        recipeBookId = recipe.recipeBookId,
        recipeBookName = recipeBook?.name.orEmpty(),
        name = recipe.name,
        categoryId = recipe.categoryId,
        categoryName = category?.name,
        difficulty = Difficulty.parse(recipe.difficulty),
        prepTimeMinutes = recipe.prepTimeMinutes,
        cookTimeMinutes = recipe.cookTimeMinutes,
        servings = recipe.servings,
        notes = recipe.notes.orEmpty(),
        source = recipe.source.orEmpty(),
        isFavorite = recipe.isFavorite,
        timesCooked = recipe.timesCooked,
        createdAt = recipe.createdAt,
        updatedAt = recipe.updatedAt,
        photos = photos.sortedBy { it.position }.map { RecipePhoto(it.uri, it.isCover, it.uid) },
        ingredientGroups = orderGroupsMainFirst(ingredientGroups).map { (name, items) -> IngredientGroup(name, items) },
        stepGroups = orderGroupsMainFirst(stepGroups).map { (name, items) -> StepGroup(name, items) },
        tags = tags.map { it.name }.sorted(),
        utensils = utensils.map { it.name }.sorted(),
        healthRating = recipe.healthColor?.let { colorName ->
            HealthRating(
                color = runCatching { HealthColorLevel.valueOf(colorName) }.getOrDefault(HealthColorLevel.YELLOW),
                description = recipe.healthDescription.orEmpty(),
                fingerprint = recipe.healthFingerprint.orEmpty(),
                analyzedAt = recipe.healthAnalyzedAt ?: 0L
            )
        },
        nutritionInfo = recipe.nutritionCalories?.let { calories ->
            NutritionInfo(
                caloriesPerServing = calories,
                proteinGrams = recipe.nutritionProteinGrams ?: 0.0,
                carbsGrams = recipe.nutritionCarbsGrams ?: 0.0,
                fatGrams = recipe.nutritionFatGrams ?: 0.0,
                fingerprint = recipe.nutritionFingerprint.orEmpty(),
                analyzedAt = recipe.nutritionAnalyzedAt ?: 0L
            )
        },
        rating = recipe.rating,
        origin = recipe.origin,
        originCountry = recipe.originCountry,
        sharedBy = recipe.sharedBy,
        journal = journal.sortedWith(compareByDescending<RecipeNoteEntity> { it.createdAt }.thenByDescending { it.id })
            .map { RecipeNote(it.id, it.text, it.createdAt) }
    )
}

private fun <T> orderGroupsMainFirst(groups: LinkedHashMap<String?, MutableList<T>>): List<Pair<String?, List<T>>> {
    val main = groups[null]
    val rest = groups.entries.filter { it.key != null }.map { it.key to it.value.toList() }
    val result = mutableListOf<Pair<String?, List<T>>>()
    if (main != null) result.add(null to main.toList())
    result.addAll(rest)
    return result
}

/** The lists of Manage content before a cleanup, to undo it (see RecipeRepository.applyCleanup). */
class ContentSnapshot(
    val categories: List<CategoryEntity>,
    val utensils: List<UtensilEntity>,
    val tags: List<TagEntity>,
    val ingredients: List<IngredientCatalogEntity>,
    val ingredientCategories: List<IngredientCategoryEntity>,
    val utensilLinks: List<RecipeUtensilCrossRef>,
    val tagLinks: List<RecipeTagCrossRef>,
    val recipeCategories: List<RecipeCategoryLink>,
    /** Recipes that showed a change, marked again for sync when undone. */
    val affectedRecipes: Set<Long>
)
