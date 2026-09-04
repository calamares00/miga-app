package com.bmo00.miga.data.repository

import androidx.room.withTransaction
import com.bmo00.miga.data.local.AppDatabase
import com.bmo00.miga.data.local.entity.CategoryEntity
import com.bmo00.miga.data.local.entity.IngredientCatalogEntity
import com.bmo00.miga.data.local.entity.IngredientCategoryEntity
import com.bmo00.miga.data.local.entity.IngredientEntity
import com.bmo00.miga.data.local.entity.RecipeBookEntity
import com.bmo00.miga.data.local.entity.RecipeEntity
import com.bmo00.miga.data.local.entity.RecipePhotoEntity
import com.bmo00.miga.data.local.entity.RecipeTagCrossRef
import com.bmo00.miga.data.local.entity.RecipeUtensilCrossRef
import com.bmo00.miga.data.local.entity.RecipeWithDetails
import com.bmo00.miga.data.local.entity.StepEntity
import com.bmo00.miga.data.local.entity.TagEntity
import com.bmo00.miga.data.local.entity.UtensilEntity
import com.bmo00.miga.data.model.Difficulty
import com.bmo00.miga.data.model.Ingredient
import com.bmo00.miga.data.model.IngredientCatalogItem
import com.bmo00.miga.data.model.IngredientGroup
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeBook
import com.bmo00.miga.data.model.RecipeBookDraft
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.model.RecipeDraft
import com.bmo00.miga.data.model.RecipePhoto
import com.bmo00.miga.data.model.StepGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Se lanza al intentar borrar un libro de recetas que todavía tiene recetas dentro. */
class RecipeBookNotEmptyException(val recipeCount: Int) : Exception()

class RecipeRepository(private val db: AppDatabase) {

    private val recipeDao = db.recipeDao()
    private val categoryDao = db.categoryDao()
    private val tagDao = db.tagDao()
    private val utensilDao = db.utensilDao()
    private val recipeBookDao = db.recipeBookDao()
    private val ingredientCatalogDao = db.ingredientCatalogDao()
    private val ingredientCategoryDao = db.ingredientCategoryDao()

    fun observeRecipesForBook(bookId: Long): Flow<List<Recipe>> =
        recipeDao.observeAllWithDetailsForBook(bookId).map { list -> list.map { it.toDomain() } }

    fun observeRecipe(id: Long): Flow<Recipe?> =
        recipeDao.observeWithDetails(id).map { it?.toDomain() }

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()
    fun observeTags(): Flow<List<TagEntity>> = tagDao.observeAll()
    fun observeUtensils(): Flow<List<UtensilEntity>> = utensilDao.observeAll()
    fun observeIngredientNames(): Flow<List<String>> =
        ingredientCatalogDao.observeAll().map { list -> list.map { it.name } }
    fun observeIngredientCatalog(): Flow<List<IngredientCatalogEntity>> = ingredientCatalogDao.observeAll()

    suspend fun getAllRecipesOnce(): List<Recipe> =
        recipeDao.getAllWithDetailsOnce().map { it.toDomain() }

    suspend fun getRecipesForBookOnce(bookId: Long): List<Recipe> =
        recipeDao.getAllWithDetailsForBookOnce(bookId).map { it.toDomain() }

    suspend fun toggleFavorite(id: Long, favorite: Boolean) {
        recipeDao.setFavorite(id, favorite)
    }

    suspend fun markCooked(id: Long) {
        recipeDao.incrementTimesCooked(id)
    }

    suspend fun deleteRecipe(id: Long) {
        recipeDao.deleteRecipe(id)
    }

    suspend fun moveRecipeToBook(recipeId: Long, newBookId: Long) {
        recipeDao.updateRecipeBook(recipeId, newBookId)
    }

    suspend fun saveRecipe(draft: RecipeDraft): Long = db.withTransaction {
        val categoryId = draft.categoryName?.takeIf { it.isNotBlank() }?.let { resolveCategoryId(it) }
        val now = System.currentTimeMillis()

        val recipeId = if (draft.id == 0L) {
            recipeDao.insertRecipe(
                RecipeEntity(
                    name = draft.name.trim(),
                    categoryId = categoryId,
                    recipeBookId = draft.recipeBookId,
                    difficulty = draft.difficulty.name,
                    prepTimeMinutes = draft.prepTimeMinutes,
                    cookTimeMinutes = draft.cookTimeMinutes,
                    servings = draft.servings,
                    notes = draft.notes,
                    source = draft.source,
                    isFavorite = draft.isFavorite,
                    timesCooked = 0,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val existing = recipeDao.getRecipeOnce(draft.id)
            recipeDao.updateRecipe(
                RecipeEntity(
                    id = draft.id,
                    name = draft.name.trim(),
                    categoryId = categoryId,
                    recipeBookId = existing?.recipeBookId ?: draft.recipeBookId,
                    difficulty = draft.difficulty.name,
                    prepTimeMinutes = draft.prepTimeMinutes,
                    cookTimeMinutes = draft.cookTimeMinutes,
                    servings = draft.servings,
                    notes = draft.notes,
                    source = draft.source,
                    isFavorite = draft.isFavorite,
                    timesCooked = existing?.timesCooked ?: 0,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
            draft.id
        }

        // Ingredientes: se reescriben por completo en cada guardado.
        recipeDao.deleteIngredients(recipeId)
        var ingredientPosition = 0
        val ingredientEntities = draft.ingredientGroups.flatMap { group ->
            group.ingredients.map { ingredient ->
                IngredientEntity(
                    recipeId = recipeId,
                    groupName = group.name,
                    position = ingredientPosition++,
                    name = ingredient.name.trim(),
                    quantity = ingredient.quantity,
                    unit = ingredient.unit?.trim()?.takeIf { it.isNotBlank() }
                )
            }
        }
        if (ingredientEntities.isNotEmpty()) {
            recipeDao.insertIngredients(ingredientEntities)
            ingredientEntities.map { it.name }.filter { it.isNotBlank() }.distinct().forEach { name ->
                addIngredientName(name)
            }
        }

        // Pasos: se reescriben por completo en cada guardado.
        recipeDao.deleteSteps(recipeId)
        var stepPosition = 0
        val stepEntities = draft.stepGroups.flatMap { group ->
            group.instructions.map { instruction ->
                StepEntity(
                    recipeId = recipeId,
                    groupName = group.name,
                    position = stepPosition++,
                    instruction = instruction.trim()
                )
            }
        }
        if (stepEntities.isNotEmpty()) recipeDao.insertSteps(stepEntities)

        // Fotos
        recipeDao.deletePhotos(recipeId)
        if (draft.photos.isNotEmpty()) {
            recipeDao.insertPhotos(
                draft.photos.mapIndexed { index, photo ->
                    RecipePhotoEntity(
                        recipeId = recipeId,
                        uri = photo.uri,
                        position = index,
                        isCover = photo.isCover
                    )
                }
            )
        }

        // Tags
        recipeDao.deleteTagCrossRefs(recipeId)
        val tagIds = draft.tagNames.filter { it.isNotBlank() }.map { resolveTagId(it) }
        if (tagIds.isNotEmpty()) {
            recipeDao.insertTagCrossRefs(tagIds.map { RecipeTagCrossRef(recipeId, it) })
        }

        // Utensilios
        recipeDao.deleteUtensilCrossRefs(recipeId)
        val utensilIds = draft.utensilNames.filter { it.isNotBlank() }.map { resolveUtensilId(it) }
        if (utensilIds.isNotEmpty()) {
            recipeDao.insertUtensilCrossRefs(utensilIds.map { RecipeUtensilCrossRef(recipeId, it) })
        }

        recipeId
    }

    // --- Categorías ---

    suspend fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) categoryDao.insert(CategoryEntity(name = trimmed))
    }

    suspend fun renameCategory(id: Long, newName: String) {
        val category = categoryDao.getOnce(id) ?: return
        categoryDao.update(category.copy(name = newName.trim()))
    }

    suspend fun deleteCategory(id: Long) {
        val category = categoryDao.getOnce(id) ?: return
        categoryDao.delete(category)
    }

    suspend fun countRecipesUsingCategory(id: Long): Int = categoryDao.countRecipesUsing(id)

    /** Crea el catálogo inicial de categorías si la base de datos está vacía. */
    suspend fun seedDefaultCategoriesIfEmpty() {
        listOf("Postres", "Cremas", "Pastas").forEach { name ->
            if (categoryDao.findByName(name) == null) {
                categoryDao.insert(CategoryEntity(name = name))
            }
        }
    }

    // --- Utensilios ---

    suspend fun addUtensil(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) utensilDao.insert(UtensilEntity(name = trimmed))
    }

    suspend fun renameUtensil(id: Long, newName: String) {
        val utensil = utensilDao.getOnce(id) ?: return
        utensilDao.update(utensil.copy(name = newName.trim()))
    }

    suspend fun deleteUtensil(id: Long) {
        val utensil = utensilDao.getOnce(id) ?: return
        utensilDao.delete(utensil)
    }

    suspend fun countRecipesUsingUtensil(id: Long): Int = utensilDao.countRecipesUsing(id)

    /** Crea el catálogo inicial de utensilios habituales si la base de datos está vacía. */
    suspend fun seedDefaultUtensilsIfEmpty() {
        val defaults = listOf(
            "Horno", "Microondas", "Sartén", "Olla", "Batidora", "Robot de cocina",
            "Thermomix", "Airfryer", "Nevera", "Congelador", "Parrilla / Plancha", "Wok", "Cuchillo"
        )
        defaults.forEach { name ->
            if (utensilDao.findByName(name) == null) {
                utensilDao.insert(UtensilEntity(name = name))
            }
        }
    }

    // --- Etiquetas ---

    suspend fun addTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) tagDao.insert(TagEntity(name = trimmed))
    }

    // --- Catálogo de ingredientes (autocompletado) ---

    suspend fun addIngredientName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) ingredientCatalogDao.insert(IngredientCatalogEntity(name = trimmed))
    }

    suspend fun renameIngredientName(id: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val existing = ingredientCatalogDao.getOnce(id) ?: return
        ingredientCatalogDao.update(existing.copy(name = trimmed))
    }

    suspend fun deleteIngredientName(id: Long) {
        ingredientCatalogDao.delete(IngredientCatalogEntity(id = id, name = ""))
    }

    /** Fila del catálogo de ingredientes con el nombre de su categoría ya resuelto para la UI. */
    fun observeIngredientCatalogWithCategory(): Flow<List<IngredientCatalogItem>> =
        combine(ingredientCatalogDao.observeAll(), ingredientCategoryDao.observeAll()) { ingredients, categories ->
            val namesById = categories.associateBy({ it.id }, { it.name })
            ingredients.map { entity ->
                IngredientCatalogItem(
                    id = entity.id,
                    name = entity.name,
                    categoryId = entity.categoryId,
                    categoryName = entity.categoryId?.let { namesById[it] }
                )
            }
        }

    suspend fun changeIngredientCategory(id: Long, categoryId: Long?) {
        ingredientCatalogDao.updateCategory(id, categoryId)
    }

    // --- Categorías de ingredientes (distintas de las categorías de receta) ---

    fun observeIngredientCategories(): Flow<List<IngredientCategoryEntity>> = ingredientCategoryDao.observeAll()

    suspend fun addIngredientCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) ingredientCategoryDao.insert(IngredientCategoryEntity(name = trimmed))
    }

    suspend fun renameIngredientCategory(id: Long, newName: String) {
        val category = ingredientCategoryDao.getOnce(id) ?: return
        ingredientCategoryDao.update(category.copy(name = newName.trim()))
    }

    suspend fun deleteIngredientCategory(id: Long) {
        val category = ingredientCategoryDao.getOnce(id) ?: return
        ingredientCategoryDao.delete(category)
    }

    suspend fun countIngredientsUsingCategory(id: Long): Int = ingredientCategoryDao.countIngredientsUsing(id)

    /**
     * Añade el catálogo base de ingredientes con categoría (ver IngredientCatalogSeed). Los
     * ingredientes que el usuario ya tuviera creados NO se tocan ni se recategorizan: solo se
     * insertan los nombres que todavía no existan, así que se puede llamar en cada arranque.
     */
    suspend fun seedIngredientCatalogDefaults() {
        IngredientCatalogSeed.DEFAULT_INGREDIENTS.forEach { (categoryName, names) ->
            val categoryId = resolveIngredientCategoryId(categoryName)
            names.forEach { ingredientName ->
                if (ingredientCatalogDao.findByName(ingredientName) == null) {
                    ingredientCatalogDao.insert(IngredientCatalogEntity(name = ingredientName, categoryId = categoryId))
                }
            }
        }
    }

    private suspend fun resolveIngredientCategoryId(name: String): Long {
        ingredientCategoryDao.findByName(name)?.let { return it.id }
        ingredientCategoryDao.insert(IngredientCategoryEntity(name = name))
        return ingredientCategoryDao.findByName(name)!!.id
    }

    // --- Libros de recetas ---

    fun observeRecipeBooks(): Flow<List<RecipeBookSummary>> =
        recipeBookDao.observeAllWithCounts().map { list ->
            list.map { RecipeBookSummary(it.book.id, it.book.name, it.book.coverPhotoUri, it.recipeCount) }
        }

    fun observeRecipeBook(id: Long): Flow<RecipeBook?> =
        recipeBookDao.observeOne(id).map { it?.let { entity -> RecipeBook(entity.id, entity.name, entity.coverPhotoUri) } }

    suspend fun getRecipeBookOnce(id: Long): RecipeBook? =
        recipeBookDao.getOnce(id)?.let { RecipeBook(it.id, it.name, it.coverPhotoUri) }

    suspend fun saveRecipeBook(draft: RecipeBookDraft): Long {
        return if (draft.id == 0L) {
            recipeBookDao.insert(
                RecipeBookEntity(
                    name = draft.name.trim(),
                    coverPhotoUri = draft.coverPhotoUri,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            val existing = recipeBookDao.getOnce(draft.id)
            recipeBookDao.update(
                RecipeBookEntity(
                    id = draft.id,
                    name = draft.name.trim(),
                    coverPhotoUri = draft.coverPhotoUri,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
            )
            draft.id
        }
    }

    /** Lanza [RecipeBookNotEmptyException] si el libro todavía contiene recetas. */
    suspend fun deleteRecipeBook(id: Long) {
        val count = recipeBookDao.countRecipes(id)
        if (count > 0) throw RecipeBookNotEmptyException(count)
        recipeBookDao.delete(id)
    }

    /** Usado al importar una copia de seguridad: busca un libro por nombre o lo crea si no existe. */
    suspend fun getOrCreateRecipeBookIdByName(name: String): Long {
        val trimmed = name.trim().ifBlank { "Sin nombre" }
        recipeBookDao.findByName(trimmed)?.let { return it.id }
        return recipeBookDao.insert(RecipeBookEntity(name = trimmed, coverPhotoUri = null, createdAt = System.currentTimeMillis()))
    }

    private suspend fun resolveCategoryId(name: String): Long {
        val trimmed = name.trim()
        categoryDao.findByName(trimmed)?.let { return it.id }
        categoryDao.insert(CategoryEntity(name = trimmed))
        return categoryDao.findByName(trimmed)!!.id
    }

    private suspend fun resolveTagId(name: String): Long {
        val trimmed = name.trim()
        tagDao.findByName(trimmed)?.let { return it.id }
        tagDao.insert(TagEntity(name = trimmed))
        return tagDao.findByName(trimmed)!!.id
    }

    private suspend fun resolveUtensilId(name: String): Long {
        val trimmed = name.trim()
        utensilDao.findByName(trimmed)?.let { return it.id }
        utensilDao.insert(UtensilEntity(name = trimmed))
        return utensilDao.findByName(trimmed)!!.id
    }
}

fun RecipeWithDetails.toDomain(): Recipe {
    val sortedIngredients = ingredients.sortedBy { it.position }
    val ingredientGroups = LinkedHashMap<String?, MutableList<Ingredient>>()
    sortedIngredients.forEach { entity ->
        ingredientGroups.getOrPut(entity.groupName) { mutableListOf() }
            .add(Ingredient(entity.name, entity.quantity, entity.unit))
    }

    val sortedSteps = steps.sortedBy { it.position }
    val stepGroups = LinkedHashMap<String?, MutableList<String>>()
    sortedSteps.forEach { entity ->
        stepGroups.getOrPut(entity.groupName) { mutableListOf() }.add(entity.instruction)
    }

    return Recipe(
        id = recipe.id,
        recipeBookId = recipe.recipeBookId,
        recipeBookName = recipeBook?.name.orEmpty(),
        name = recipe.name,
        categoryId = recipe.categoryId,
        categoryName = category?.name,
        difficulty = runCatching { Difficulty.valueOf(recipe.difficulty) }.getOrDefault(Difficulty.MEDIA),
        prepTimeMinutes = recipe.prepTimeMinutes,
        cookTimeMinutes = recipe.cookTimeMinutes,
        servings = recipe.servings,
        notes = recipe.notes.orEmpty(),
        source = recipe.source.orEmpty(),
        isFavorite = recipe.isFavorite,
        timesCooked = recipe.timesCooked,
        createdAt = recipe.createdAt,
        updatedAt = recipe.updatedAt,
        photos = photos.sortedBy { it.position }.map { RecipePhoto(it.uri, it.isCover) },
        ingredientGroups = orderGroupsMainFirst(ingredientGroups).map { (name, items) -> IngredientGroup(name, items) },
        stepGroups = orderGroupsMainFirst(stepGroups).map { (name, items) -> StepGroup(name, items) },
        tags = tags.map { it.name }.sorted(),
        utensils = utensils.map { it.name }.sorted()
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
