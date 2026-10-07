package org.calamares.miga.data.repository

import androidx.room.withTransaction
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.export.IngredientDto
import org.calamares.miga.data.export.IngredientGroupDto
import org.calamares.miga.data.export.RecipeExportDto
import org.calamares.miga.data.export.StepGroupDto
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.local.TokenCipher
import org.calamares.miga.data.local.entity.CategoryEntity
import org.calamares.miga.data.local.entity.IngredientCatalogEntity
import org.calamares.miga.data.local.entity.IngredientCategoryEntity
import org.calamares.miga.data.local.entity.IngredientEntity
import org.calamares.miga.data.local.entity.PendingSyncChangeEntity
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.local.entity.RecipeEntity
import org.calamares.miga.data.local.entity.RecipePhotoEntity
import org.calamares.miga.data.local.entity.RecipeTagCrossRef
import org.calamares.miga.data.local.entity.RecipeUtensilCrossRef
import org.calamares.miga.data.local.entity.RecipeWithDetails
import org.calamares.miga.data.local.entity.ShoppingHistoryEntity
import org.calamares.miga.data.local.entity.ShoppingListEntity
import org.calamares.miga.data.local.entity.ShoppingListItemEntity
import org.calamares.miga.data.local.entity.ShoppingStoreEntity
import org.calamares.miga.data.local.entity.ShoppingTemplateEntity
import org.calamares.miga.data.local.entity.StepEntity
import org.calamares.miga.data.local.entity.SyncChangeType
import org.calamares.miga.data.local.entity.SyncConnectionEntity
import org.calamares.miga.data.local.entity.SyncEntityType
import org.calamares.miga.data.local.entity.TagEntity
import org.calamares.miga.data.local.entity.UtensilEntity
import org.calamares.miga.data.model.KitchenEquipment
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.model.HealthFingerprint
import org.calamares.miga.data.model.HealthRating
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientCatalogItem
import org.calamares.miga.data.model.IngredientGroup
import org.calamares.miga.data.model.NutritionInfo
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBook
import org.calamares.miga.data.model.RecipeBookDraft
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.model.RecipeDraft
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.ProductInfo
import org.calamares.miga.data.model.ProductInfoCodec
import org.calamares.miga.data.model.DEFAULT_SHOPPING_LIST_NAME
import org.calamares.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import org.calamares.miga.data.model.ShoppingListGroup
import org.calamares.miga.data.model.ShoppingListInfo
import org.calamares.miga.data.model.ShoppingListItem
import org.calamares.miga.data.model.ShoppingStore
import org.calamares.miga.data.model.ShoppingSuggestion
import org.calamares.miga.data.model.ShoppingTemplate
import org.calamares.miga.data.model.ShoppingTemplateCodec
import org.calamares.miga.data.model.TemplateItem
import org.calamares.miga.data.share.ShoppingListShareCodec
import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.model.SyncConnection
import org.calamares.miga.data.model.UNCATEGORIZED_INGREDIENT_LABEL
import org.calamares.miga.data.sync.ShoppingItemSyncDto
import org.calamares.miga.data.sync.ShoppingListSyncDto
import org.calamares.miga.data.sync.BookSyncDto
import org.calamares.miga.data.sync.PhotoMetaDto
import org.calamares.miga.data.sync.RECIPE_SYNC_SCHEMA
import org.calamares.miga.data.sync.RecipeSyncDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Thrown when trying to delete a recipe book that still contains recipes. */
class RecipeBookNotEmptyException(val recipeCount: Int) : Exception()

/** Result of [RecipeRepository.wipeUserRecipesAndBooks]. */
data class WipeResult(val bookCount: Int, val recipeCount: Int)

/**
 * Single source of truth for the app data.
 *
 * [onSyncChangeEnqueued] is called right after one or more changes of a connection are queued in
 * the sync outbox (see saveRecipe, saveRecipeBook, deleteRecipe, deleteRecipeBook and
 * linkBookToSyncConnection). It lets MigaApp start an immediate background upload without making
 * the save wait for the network; if that fails, the outbox is picked up by the next sync anyway.
 */
class RecipeRepository(
    private val db: AppDatabase,
    private val shoppingContext: ShoppingContext = ShoppingContext.Default,
    private val onSyncChangeEnqueued: (connectionId: Long) -> Unit = {}
) {

    private val recipeDao = db.recipeDao()
    private val categoryDao = db.categoryDao()
    private val tagDao = db.tagDao()
    private val utensilDao = db.utensilDao()
    private val recipeBookDao = db.recipeBookDao()
    private val ingredientCatalogDao = db.ingredientCatalogDao()
    private val ingredientCategoryDao = db.ingredientCategoryDao()
    private val shoppingListDao = db.shoppingListDao()
    private val shoppingHistoryDao = db.shoppingHistoryDao()
    private val shoppingTemplateDao = db.shoppingTemplateDao()
    private val shoppingStoreDao = db.shoppingStoreDao()
    private val shoppingListsDao = db.shoppingListsDao()
    private val syncConnectionDao = db.syncConnectionDao()
    private val pendingSyncChangeDao = db.pendingSyncChangeDao()

    fun observeRecipesForBook(bookId: Long): Flow<List<Recipe>> =
        recipeDao.observeAllWithDetailsForBook(bookId).map { list -> list.map { it.toDomain() } }

    /** Every recipe in every book, used by the global search. */
    fun observeAllRecipes(): Flow<List<Recipe>> =
        recipeDao.observeAllWithDetails().map { list -> list.map { it.toDomain() } }

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

    /** [rating] from 1 to 5, or null to clear the rating. */
    suspend fun setRating(id: Long, rating: Int?) {
        recipeDao.setRating(id, rating)
    }

    suspend fun markCooked(id: Long) {
        recipeDao.incrementTimesCooked(id)
    }

    /**
     * No-op when the recipe belongs to a pack book (see [RecipeBook.isPack]), which is read-only.
     */
    suspend fun deleteRecipe(id: Long) {
        val recipe = recipeDao.getRecipeOnce(id) ?: return
        val book = recipeBookDao.getOnce(recipe.recipeBookId) ?: return
        if (book.packId != null) return
        recipeDao.deleteRecipe(id)
        book.syncConnectionId?.let {
            enqueueSyncChange(it, SyncEntityType.RECIPE, recipe.uid, SyncChangeType.DELETE)
            onSyncChangeEnqueued(it)
        }
    }

    /**
     * No-op when [newBookId] is a pack book, since content cannot be added to one. Moving a recipe
     * out of a pack is allowed.
     */
    suspend fun moveRecipeToBook(recipeId: Long, newBookId: Long) {
        if (recipeBookDao.getOnce(newBookId)?.packId != null) return
        recipeDao.updateRecipeBook(recipeId, newBookId)
    }

    /** Same safeguard as [saveRecipeBook]: no-op when the target book is a pack. */
    suspend fun saveRecipe(draft: RecipeDraft): Long {
        var syncedConnectionId: Long? = null
        val recipeId = db.withTransaction {
            val targetBookId = if (draft.id == 0L) draft.recipeBookId else recipeDao.getRecipeOnce(draft.id)?.recipeBookId ?: draft.recipeBookId
            val targetBook = recipeBookDao.getOnce(targetBookId)
            if (targetBook?.packId != null) return@withTransaction draft.id

            val categoryId = draft.categoryName?.takeIf { it.isNotBlank() }?.let { resolveCategoryId(it) }
            val now = System.currentTimeMillis()

            val recipeId = if (draft.id == 0L) {
                recipeDao.insertRecipe(
                    RecipeEntity(
                        uid = draft.uid ?: UUID.randomUUID().toString(),
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
                        updatedAt = now,
                        origin = draft.origin?.trim()?.ifEmpty { null },
                        originCountry = RecipeOrigin.normalizeCountry(draft.originCountry)
                    )
                )
            } else {
                val existing = recipeDao.getRecipeOnce(draft.id)
                // If the ingredients or steps changed since the last health analysis, the cached
                // rating no longer applies and is cleared so it is recalculated the next time the
                // recipe is opened. Editing anything else (notes, servings, photos...) keeps it.
                val newFingerprint = computeHealthFingerprint(draft.ingredientGroups, draft.stepGroups)
                val keepHealth = existing != null && existing.healthFingerprint == newFingerprint
                recipeDao.updateRecipe(
                    RecipeEntity(
                        id = draft.id,
                        uid = existing?.uid ?: draft.uid ?: UUID.randomUUID().toString(),
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
                        updatedAt = now,
                        healthColor = if (keepHealth) existing?.healthColor else null,
                        healthDescription = if (keepHealth) existing?.healthDescription else null,
                        healthFingerprint = if (keepHealth) existing?.healthFingerprint else null,
                        healthAnalyzedAt = if (keepHealth) existing?.healthAnalyzedAt else null,
                        // The nutrition estimate depends on the same content, so it is invalidated
                        // with the same rule.
                        nutritionCalories = if (keepHealth) existing?.nutritionCalories else null,
                        nutritionProteinGrams = if (keepHealth) existing?.nutritionProteinGrams else null,
                        nutritionCarbsGrams = if (keepHealth) existing?.nutritionCarbsGrams else null,
                        nutritionFatGrams = if (keepHealth) existing?.nutritionFatGrams else null,
                        nutritionFingerprint = if (keepHealth) existing?.nutritionFingerprint else null,
                        nutritionAnalyzedAt = if (keepHealth) existing?.nutritionAnalyzedAt else null,
                        rating = existing?.rating,
                        origin = draft.origin?.trim()?.ifEmpty { null },
                        originCountry = RecipeOrigin.normalizeCountry(draft.originCountry)
                    )
                )
                draft.id
            }

            // Ingredients are rewritten on every save.
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

            // Steps are rewritten on every save.
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

            // Photos are rewritten too, but existing ones (matched by uri) keep their uid so the
            // sync engine does not treat them as new photos on every save.
            val existingPhotos = recipeDao.getPhotosOnce(recipeId)
            val existingUidByUri = existingPhotos.associate { it.uri to it.uid }
            recipeDao.deletePhotos(recipeId)
            val newPhotoEntities = draft.photos.mapIndexed { index, photo ->
                RecipePhotoEntity(
                    recipeId = recipeId,
                    uri = photo.uri,
                    position = index,
                    isCover = photo.isCover,
                    uid = existingUidByUri[photo.uri] ?: UUID.randomUUID().toString()
                )
            }
            if (newPhotoEntities.isNotEmpty()) recipeDao.insertPhotos(newPhotoEntities)

            // Tags
            recipeDao.deleteTagCrossRefs(recipeId)
            val tagIds = draft.tagNames.filter { it.isNotBlank() }.map { resolveTagId(it) }
            if (tagIds.isNotEmpty()) {
                recipeDao.insertTagCrossRefs(tagIds.map { RecipeTagCrossRef(recipeId, it) })
            }

            // Utensils
            recipeDao.deleteUtensilCrossRefs(recipeId)
            val utensilIds = draft.utensilNames.filter { it.isNotBlank() }.map { resolveUtensilId(it) }.distinct()
            if (utensilIds.isNotEmpty()) {
                recipeDao.insertUtensilCrossRefs(utensilIds.map { RecipeUtensilCrossRef(recipeId, it) })
            }

            targetBook?.syncConnectionId?.let { connectionId ->
                recipeDao.getRecipeOnce(recipeId)?.let { saved ->
                    enqueueSyncChange(connectionId, SyncEntityType.RECIPE, saved.uid, SyncChangeType.UPSERT)
                    // Photos added or removed in this save. Removing a single photo does not go
                    // through deleteRecipe (which cascades on the server), so it is queued
                    // separately, with the recipe uid as parentUid because the photo row will no
                    // longer exist.
                    val oldUids = existingPhotos.mapNotNull { it.uid }.toSet()
                    val newUids = newPhotoEntities.mapNotNull { it.uid }.toSet()
                    (newUids - oldUids).forEach { photoUid ->
                        enqueueSyncChange(connectionId, SyncEntityType.PHOTO, photoUid, SyncChangeType.UPSERT, parentUid = saved.uid)
                    }
                    (oldUids - newUids).forEach { photoUid ->
                        enqueueSyncChange(connectionId, SyncEntityType.PHOTO, photoUid, SyncChangeType.DELETE, parentUid = saved.uid)
                    }
                    syncedConnectionId = connectionId
                }
            }

            recipeId
        }
        syncedConnectionId?.let { onSyncChangeEnqueued(it) }
        return recipeId
    }

    suspend fun saveHealthRating(recipeId: Long, color: HealthColorLevel, description: String, fingerprint: String, analyzedAt: Long) {
        recipeDao.updateHealthRating(recipeId, color.name, description, fingerprint, analyzedAt)
    }

    suspend fun saveNutritionInfo(
        recipeId: Long,
        caloriesPerServing: Int,
        proteinGrams: Double,
        carbsGrams: Double,
        fatGrams: Double,
        fingerprint: String,
        analyzedAt: Long
    ) {
        recipeDao.updateNutritionInfo(recipeId, caloriesPerServing, proteinGrams, carbsGrams, fatGrams, fingerprint, analyzedAt)
    }

    /**
     * Fingerprint of [ingredientGroups] and [stepGroups]. When it differs from the one stored with
     * a health rating, that rating no longer matches the recipe. See [HealthFingerprint].
     */
    fun computeHealthFingerprint(ingredientGroups: List<IngredientGroup>, stepGroups: List<StepGroup>): String =
        HealthFingerprint.compute(ingredientGroups, stepGroups)

    /**
     * Same fingerprint as [computeHealthFingerprint] (it depends on the same content), named
     * separately so nutrition call sites read clearly.
     */
    fun computeNutritionFingerprint(ingredientGroups: List<IngredientGroup>, stepGroups: List<StepGroup>): String =
        HealthFingerprint.compute(ingredientGroups, stepGroups)

    // --- Categories ---

    suspend fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) resolveCategoryId(trimmed)
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

    /** Creates the default categories when the database has none. */
    /** Creates the default categories that are missing. Called once, on the first start. */
    suspend fun seedDefaultCategories(language: String = "es") {
        DefaultCatalog.categories(language).forEach { name ->
            if (categoryDao.findByName(name) == null) {
                categoryDao.insert(CategoryEntity(name = name))
            }
        }
    }

    // --- Utensils ---

    suspend fun addUtensil(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) resolveUtensilId(trimmed)
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

    /** Creates the default kitchen equipment that is missing. Called once, on the first start. */
    suspend fun seedDefaultUtensils(language: String = "es") {
        KitchenEquipment.defaults(language).forEach { name -> resolveUtensilId(name) }
    }

    /**
     * Brings the kitchen equipment created by older versions in line with [KitchenEquipment]:
     * basics such as "Knife" or "Fridge" are removed (also from the recipes that had them, as they
     * say nothing about whether a recipe can be made) and usual variants are renamed to the default
     * name ("Airfryer" -> "Air fryer"), merging them when both exist. Called once.
     */
    suspend fun normalizeUtensils(language: String) {
        utensilDao.getAllOnce().forEach { utensil ->
            if (KitchenEquipment.isObvious(utensil.name)) {
                utensilDao.delete(utensil)
                return@forEach
            }
            val canonical = KitchenEquipment.canonical(utensil.name, language)
            if (canonical == utensil.name) return@forEach
            val existing = utensilDao.findByName(canonical)
            if (existing != null && existing.id != utensil.id) {
                utensilDao.moveRecipes(fromId = utensil.id, toId = existing.id)
                utensilDao.delete(utensil)
            } else {
                utensilDao.update(utensil.copy(name = canonical))
            }
        }
    }

    // --- Tags ---

    suspend fun addTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) resolveTagId(trimmed)
    }

    // --- Ingredient catalogue (autocomplete) ---

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

    /** Ingredient catalogue rows with their category name already resolved for the UI. */
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

    // --- Ingredient categories (not the same as recipe categories) ---

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
     * Adds the base ingredient catalogue with categories (see IngredientCatalogSeed). Ingredients
     * the user already has are not touched or recategorised; only missing names are inserted, so it
     * is safe to call on every start.
     */
    suspend fun seedIngredientCatalogDefaults(language: String = "es") {
        IngredientCatalogSeed.forLanguage(language).forEach { (categoryName, names) ->
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
        return ingredientCategoryDao.insert(IngredientCategoryEntity(name = name))
    }

    // --- Shopping list ---

    private suspend fun currentShoppingListUid(): String = shoppingContext.listUid.first()

    /** Name used to sign changes, or null when the user has not set one. */
    private suspend fun currentShoppingAuthor(): String? = shoppingContext.author.first().trim().takeIf { it.isNotEmpty() }

    /**
     * Current list grouped by ingredient category (see ingredient_categories), uncategorized last.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeShoppingList(): Flow<List<ShoppingListGroup>> =
        shoppingContext.listUid.flatMapLatest { listUid -> observeShoppingGroups(listUid) }

    /**
     * Like [observeShoppingList], but each emission comes with the uid of its list, so switching
     * lists never mixes data.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeShoppingListSnapshots(): Flow<Pair<String, List<ShoppingListGroup>>> =
        shoppingContext.listUid.flatMapLatest { listUid -> observeShoppingGroups(listUid).map { listUid to it } }

    private fun observeShoppingGroups(listUid: String): Flow<List<ShoppingListGroup>> =
        combine(shoppingListDao.observeAll(listUid), ingredientCatalogDao.observeAll(), ingredientCategoryDao.observeAll()) { items, catalog, categories ->
            val categoryNameById = categories.associateBy({ it.id }, { it.name })
            val categoryIdByIngredientName = catalog.associate { it.name.trim().lowercase() to it.categoryId }
            items.map { entity ->
                val categoryId = categoryIdByIngredientName[entity.normalizedName]
                val categoryName = categoryId?.let { categoryNameById[it] } ?: UNCATEGORIZED_INGREDIENT_LABEL
                ShoppingListItem(
                    id = entity.id,
                    name = entity.name,
                    quantity = entity.quantity,
                    unit = entity.unit,
                    checked = entity.checked,
                    categoryName = categoryName,
                    imageUrl = entity.imageUrl,
                    uid = entity.uid,
                    addedBy = entity.addedBy,
                    updatedBy = entity.updatedBy,
                    productInfo = ProductInfoCodec.decode(entity.productInfo)
                )
            }
                .groupBy { it.categoryName }
                .toSortedMap(compareBy { if (it == UNCATEGORIZED_INGREDIENT_LABEL) "￿" else it.lowercase() })
                .map { (category, groupItems) -> ShoppingListGroup(category, groupItems.sortedBy { it.name.lowercase() }) }
        }

    /**
     * Adds ingredients (from one or several recipes) to the current list. An entry is merged with
     * an existing one with the same normalised name and unit when both have a quantity; otherwise a
     * new row is inserted.
     */
    suspend fun addIngredientsToShoppingList(ingredients: List<Ingredient>, recordHistory: Boolean = false) {
        addIngredientsToShoppingListInTransaction(ingredients, recordHistory)
        notifyShoppingListChanged()
    }

    private suspend fun addIngredientsToShoppingListInTransaction(ingredients: List<Ingredient>, recordHistory: Boolean) {
        val listUid = currentShoppingListUid()
        val author = currentShoppingAuthor()
        db.withTransaction {
            val now = System.currentTimeMillis()
            ingredients.forEach { ingredient ->
                val trimmedName = ingredient.name.trim()
                if (trimmedName.isEmpty()) return@forEach
                val normalized = trimmedName.lowercase()
                val trimmedUnit = ingredient.unit?.trim()?.takeIf { it.isNotBlank() }
                val existing = if (ingredient.quantity != null) shoppingListDao.findMergeable(listUid, normalized, trimmedUnit) else null
                if (existing != null) {
                    shoppingListDao.update(
                        existing.copy(quantity = (existing.quantity ?: 0.0) + (ingredient.quantity ?: 0.0), updatedAt = now, updatedBy = author, syncDirty = true)
                    )
                } else {
                    shoppingListDao.insert(
                        ShoppingListItemEntity(
                            name = trimmedName,
                            normalizedName = normalized,
                            quantity = ingredient.quantity,
                            unit = trimmedUnit,
                            createdAt = now,
                            syncDirty = true,
                            listUid = listUid,
                            addedBy = author,
                            updatedBy = author
                        )
                    )
                }
                if (recordHistory) {
                    val previous = shoppingHistoryDao.find(normalized)
                    shoppingHistoryDao.upsert(
                        ShoppingHistoryEntity(
                            normalizedName = normalized,
                            name = trimmedName,
                            lastQuantity = ingredient.quantity ?: previous?.lastQuantity,
                            lastUnit = if (ingredient.quantity != null) trimmedUnit else previous?.lastUnit,
                            uses = (previous?.uses ?: 0) + 1,
                            lastUsedAt = now
                        )
                    )
                }
            }
        }
    }

    /**
     * Items typed, dictated or imported by the user. Same as recipe ingredients, but they also feed
     * the suggestion history.
     */
    suspend fun addShoppingListEntries(ingredients: List<Ingredient>) {
        addIngredientsToShoppingList(ingredients, recordHistory = true)
    }

    fun observeShoppingHistory(): Flow<List<ShoppingSuggestion>> =
        shoppingHistoryDao.observeAll().map { list -> list.map { ShoppingSuggestion(it.name, it.lastQuantity, it.lastUnit, it.uses) } }

    /** Re-inserts an item removed by mistake (undo) without merging it with existing rows. */
    suspend fun restoreShoppingListItem(item: ShoppingListItem) {
        val author = currentShoppingAuthor()
        shoppingListDao.insert(
            ShoppingListItemEntity(
                name = item.name,
                normalizedName = item.name.trim().lowercase(),
                quantity = item.quantity,
                unit = item.unit,
                checked = item.checked,
                createdAt = System.currentTimeMillis(),
                syncDirty = true,
                imageUrl = item.imageUrl,
                productInfo = item.productInfo?.let { ProductInfoCodec.encode(it) },
                listUid = currentShoppingListUid(),
                addedBy = item.addedBy ?: author,
                updatedBy = author
            )
        )
        notifyShoppingListChanged()
    }

    /**
     * Manual item without a source recipe (e.g. "aluminium foil"), merged like recipe ingredients.
     */
    suspend fun addManualShoppingListItem(name: String, quantity: Double?, unit: String?) {
        addShoppingListEntries(listOf(Ingredient(name, quantity, unit)))
    }

    /**
     * Scanned product with a photo. If a pending item with the same name already exists, only the
     * photo is added to it.
     */
    suspend fun addScannedShoppingProduct(name: String, imageUrl: String?, info: ProductInfo?, quantity: Double? = null, unit: String? = null) {
        val infoJson = info?.let { ProductInfoCodec.encode(it) }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val normalized = trimmed.lowercase()
        val now = System.currentTimeMillis()
        val listUid = currentShoppingListUid()
        val author = currentShoppingAuthor()
        db.withTransaction {
            val existing = shoppingListDao.findLiveByName(listUid, normalized)
            if (existing != null) {
                if ((existing.imageUrl == null && imageUrl != null) || (existing.productInfo == null && infoJson != null)) {
                    shoppingListDao.update(
                        existing.copy(
                            imageUrl = existing.imageUrl ?: imageUrl,
                            productInfo = existing.productInfo ?: infoJson,
                            updatedAt = now,
                            updatedBy = author,
                            syncDirty = true
                        )
                    )
                }
            } else {
                shoppingListDao.insert(
                    ShoppingListItemEntity(
                        name = trimmed,
                        normalizedName = normalized,
                        quantity = quantity,
                        unit = unit,
                        createdAt = now,
                        syncDirty = true,
                        imageUrl = imageUrl,
                        productInfo = infoJson,
                        listUid = listUid,
                        addedBy = author,
                        updatedBy = author
                    )
                )
            }
            val previous = shoppingHistoryDao.find(normalized)
            shoppingHistoryDao.upsert(
                ShoppingHistoryEntity(
                    normalizedName = normalized,
                    name = trimmed,
                    lastQuantity = previous?.lastQuantity,
                    lastUnit = previous?.lastUnit,
                    uses = (previous?.uses ?: 0) + 1,
                    lastUsedAt = now
                )
            )
        }
        notifyShoppingListChanged()
    }

    // --- Multiple shopping lists ---

    /** The default list (always first) followed by the user's extra lists. */
    fun observeShoppingLists(): Flow<List<ShoppingListInfo>> =
        shoppingListsDao.observeAll().map { rows ->
            listOf(ShoppingListInfo(DEFAULT_SHOPPING_LIST_UID, DEFAULT_SHOPPING_LIST_NAME)) + rows.map { ShoppingListInfo(it.uid, it.name) }
        }

    /** Pending (unchecked) item count per list, for the list picker. Empty lists are omitted. */
    fun observeShoppingListPendingCounts(): Flow<Map<String, Int>> =
        shoppingListDao.observePendingCounts().map { rows -> rows.associate { it.listUid to it.pending } }

    /** Creates an extra list and returns its uid, or null when the name is blank. */
    suspend fun createShoppingList(name: String): String? {
        val trimmed = name.trim().take(60)
        if (trimmed.isEmpty()) return null
        val now = System.currentTimeMillis()
        val entity = ShoppingListEntity(name = trimmed, createdAt = now, syncDirty = true)
        shoppingListsDao.insert(entity)
        notifyShoppingListChanged()
        return entity.uid
    }

    suspend fun renameShoppingList(uid: String, name: String) {
        val trimmed = name.trim().take(60)
        if (trimmed.isEmpty() || uid == DEFAULT_SHOPPING_LIST_UID) return
        val existing = shoppingListsDao.findByUid(uid) ?: return
        shoppingListsDao.update(existing.copy(name = trimmed, updatedAt = System.currentTimeMillis(), syncDirty = true))
        notifyShoppingListChanged()
    }

    /**
     * Deletes an extra list and all its items, leaving tombstones so other devices delete them too.
     */
    suspend fun deleteShoppingList(uid: String) {
        if (uid == DEFAULT_SHOPPING_LIST_UID) return
        val existing = shoppingListsDao.findByUid(uid) ?: return
        val now = System.currentTimeMillis()
        db.withTransaction {
            shoppingListDao.softDeleteAll(uid, now)
            shoppingListsDao.update(existing.copy(deletedAt = now, updatedAt = now, syncDirty = true))
        }
        notifyShoppingListChanged()
    }

    // --- Stores (aisle order) ---

    fun observeShoppingStores(): Flow<List<ShoppingStore>> =
        shoppingStoreDao.observeAll().map { list ->
            list.map { ShoppingStore(it.id, it.name, it.color, it.aisleOrder.split("\n").map(String::trim).filter(String::isNotEmpty)) }
        }

    /** Creates ([store].id == 0) or updates a store and returns its id. */
    suspend fun saveShoppingStore(store: ShoppingStore): Long {
        val name = store.name.trim()
        if (name.isEmpty()) return store.id
        val order = store.aisleOrder.joinToString("\n")
        return if (store.id == 0L) {
            shoppingStoreDao.insert(ShoppingStoreEntity(name = name, color = store.argb, aisleOrder = order, createdAt = System.currentTimeMillis()))
        } else {
            shoppingStoreDao.update(ShoppingStoreEntity(id = store.id, name = name, color = store.argb, aisleOrder = order, createdAt = System.currentTimeMillis()))
            store.id
        }
    }

    suspend fun deleteShoppingStore(id: Long) = shoppingStoreDao.delete(id)

    // --- List templates ---

    fun observeShoppingTemplates(): Flow<List<ShoppingTemplate>> =
        shoppingTemplateDao.observeAll().map { list ->
            list.map { ShoppingTemplate(it.id, it.name, ShoppingTemplateCodec.decode(it.body, ShoppingListShareCodec::fromLines)) }
        }

    /** Creates a template (possibly empty) and returns its id, or null when the name is blank. */
    suspend fun saveShoppingTemplate(name: String, items: List<TemplateItem>): Long? {
        if (name.isBlank()) return null
        val body = ShoppingTemplateCodec.encode(items)
        return shoppingTemplateDao.insert(ShoppingTemplateEntity(name = name.trim(), body = body, createdAt = System.currentTimeMillis()))
    }

    /** Replaces the items of a user template with [transform] applied to the current ones. */
    suspend fun updateShoppingTemplateItems(id: Long, transform: (List<TemplateItem>) -> List<TemplateItem>) {
        db.withTransaction {
            val entity = shoppingTemplateDao.get(id) ?: return@withTransaction
            val current = ShoppingTemplateCodec.decode(entity.body, ShoppingListShareCodec::fromLines)
            shoppingTemplateDao.setBody(id, ShoppingTemplateCodec.encode(transform(current)))
        }
    }

    suspend fun renameShoppingTemplate(id: Long, name: String) {
        if (name.isBlank()) return
        shoppingTemplateDao.rename(id, name.trim())
    }

    /** Adds a template's items to the current list. Products keep their photo and product sheet. */
    suspend fun applyShoppingTemplate(items: List<TemplateItem>) {
        val (products, plain) = items.partition { it.isProduct }
        if (plain.isNotEmpty()) addShoppingListEntries(plain.map { Ingredient(it.name, it.quantity, it.unit) })
        products.forEach { addScannedShoppingProduct(it.name, it.imageUrl, it.info, it.quantity, it.unit) }
    }

    suspend fun deleteShoppingTemplate(id: Long) = shoppingTemplateDao.delete(id)

    suspend fun setShoppingListItemChecked(id: Long, checked: Boolean) {
        shoppingListDao.setChecked(id, checked, System.currentTimeMillis(), currentShoppingAuthor())
        notifyShoppingListChanged()
    }

    /**
     * Soft delete (tombstone), so the deletion also reaches other devices when the list is shared.
     */
    suspend fun deleteShoppingListItem(id: Long) {
        shoppingListDao.softDelete(id, System.currentTimeMillis())
        notifyShoppingListChanged()
    }

    suspend fun clearShoppingList() {
        shoppingListDao.softDeleteAll(currentShoppingListUid(), System.currentTimeMillis())
        notifyShoppingListChanged()
    }

    suspend fun clearCheckedShoppingListItems() {
        shoppingListDao.softDeleteChecked(currentShoppingListUid(), System.currentTimeMillis())
        notifyShoppingListChanged()
    }

    // --- Shared shopping lists (sync) ---

    /**
     * If a connection shares the lists, requests a background upload (same mechanism as the book
     * outbox).
     */
    private suspend fun notifyShoppingListChanged() {
        syncConnectionDao.getShoppingSyncConnection()?.let { onSyncChangeEnqueued(it.id) }
        purgeShoppingTombstonesIfNotShared()
    }

    /**
     * Without a connection sharing the lists, tombstones are useless, so they are purged right
     * away.
     */
    private suspend fun purgeShoppingTombstonesIfNotShared() {
        if (syncConnectionDao.getShoppingSyncConnection() == null) {
            shoppingListDao.purgeAllTombstones()
            shoppingListsDao.purgeAllTombstones()
        }
    }

    /** Id of the connection that shares the shopping lists, or null when there is none. */
    suspend fun getShoppingSyncConnectionId(): Long? = syncConnectionDao.getShoppingSyncConnection()?.id

    /**
     * Enables shopping list sharing through [connectionId], or disables it with null. At most one
     * connection shares at a time. When enabled, every current item and list is marked for upload
     * and the next sync also downloads everything on the server; items from both sides are combined
     * without merging duplicates.
     */
    suspend fun setShoppingSyncConnection(connectionId: Long?) {
        db.withTransaction {
            syncConnectionDao.clearShoppingSync()
            if (connectionId != null) {
                syncConnectionDao.enableShoppingSync(connectionId)
                shoppingListDao.purgeAllTombstones()
                shoppingListsDao.purgeAllTombstones()
                shoppingListDao.markAllLiveDirty()
                shoppingListsDao.markAllLiveDirty()
            } else {
                shoppingListDao.purgeAllTombstones()
                shoppingListsDao.purgeAllTombstones()
            }
        }
        connectionId?.let { onSyncChangeEnqueued(it) }
    }

    suspend fun markShoppingInitialPullDone(connectionId: Long) = syncConnectionDao.markShoppingPulled(connectionId)

    /** Local list changes waiting to be uploaded, including tombstones (deletedAt != null). */
    suspend fun getDirtyShoppingLists(): List<ShoppingListSyncDto> =
        shoppingListsDao.getDirty().map { ShoppingListSyncDto(uid = it.uid, name = it.name, updatedAt = it.updatedAt, deletedAt = it.deletedAt) }

    suspend fun markShoppingListSynced(uid: String, updatedAt: Long) = db.withTransaction {
        shoppingListsDao.markSynced(uid, updatedAt)
        shoppingListsDao.purgeSyncedTombstones()
    }

    /** Local item changes waiting to be uploaded, including tombstones (deletedAt != null). */
    suspend fun getDirtyShoppingItems(): List<ShoppingItemSyncDto> =
        shoppingListDao.getDirty().map { entity ->
            ShoppingItemSyncDto(
                uid = entity.uid,
                listId = entity.listUid,
                name = entity.name,
                quantity = entity.quantity,
                unit = entity.unit,
                checked = entity.checked,
                imageUrl = entity.imageUrl,
                productInfo = entity.productInfo,
                addedBy = entity.addedBy,
                updatedBy = entity.updatedBy,
                updatedAt = entity.updatedAt,
                deletedAt = entity.deletedAt
            )
        }

    /**
     * After a successful upload: clears the dirty flag (unless the row changed meanwhile) and drops
     * the uploaded tombstone.
     */
    suspend fun markShoppingItemSynced(uid: String, updatedAt: Long) = db.withTransaction {
        shoppingListDao.markSynced(uid, updatedAt)
        shoppingListDao.purgeSyncedTombstones()
    }

    /**
     * Applies lists downloaded from the server with last-write-wins. Deleting a list also deletes
     * its local items.
     */
    suspend fun applyRemoteShoppingLists(lists: List<ShoppingListSyncDto>) = db.withTransaction {
        lists.forEach { dto ->
            if (dto.uid == DEFAULT_SHOPPING_LIST_UID) return@forEach
            val local = shoppingListsDao.findByUid(dto.uid)
            if (local != null && local.syncDirty && local.updatedAt > dto.updatedAt) return@forEach
            if (dto.deletedAt != null) {
                if (local != null) shoppingListsDao.deleteByUid(dto.uid)
                shoppingListDao.deleteByListUid(dto.uid)
                return@forEach
            }
            val name = dto.name.trim()
            if (name.isEmpty()) return@forEach
            if (local == null) {
                shoppingListsDao.insert(ShoppingListEntity(uid = dto.uid, name = name, createdAt = dto.updatedAt, updatedAt = dto.updatedAt))
            } else {
                shoppingListsDao.update(local.copy(name = name, updatedAt = dto.updatedAt, deletedAt = null, syncDirty = false))
            }
        }
    }

    /**
     * Applies items downloaded from the server with last-write-wins by updatedAt. A newer local
     * change that has not been uploaded yet is kept; it will be uploaded and win on the server.
     */
    suspend fun applyRemoteShoppingItems(items: List<ShoppingItemSyncDto>) = db.withTransaction {
        items.forEach { dto ->
            val local = shoppingListDao.findByUid(dto.uid)
            if (local != null && local.syncDirty && local.updatedAt > dto.updatedAt) return@forEach
            if (dto.deletedAt != null) {
                if (local != null) shoppingListDao.deleteByUid(dto.uid)
                return@forEach
            }
            val name = dto.name.trim()
            if (name.isEmpty()) return@forEach
            val listUid = dto.listId.ifBlank { DEFAULT_SHOPPING_LIST_UID }
            // An item of a list that no longer exists here is dropped; it would be orphaned.
            if (listUid != DEFAULT_SHOPPING_LIST_UID && shoppingListsDao.findByUid(listUid) == null) return@forEach
            val entity = ShoppingListItemEntity(
                id = local?.id ?: 0,
                name = name,
                normalizedName = name.lowercase(),
                quantity = dto.quantity,
                unit = dto.unit?.trim()?.takeIf { it.isNotBlank() },
                checked = dto.checked,
                createdAt = local?.createdAt ?: dto.updatedAt,
                imageUrl = dto.imageUrl,
                productInfo = dto.productInfo,
                uid = dto.uid,
                updatedAt = dto.updatedAt,
                deletedAt = null,
                syncDirty = false,
                listUid = listUid,
                addedBy = dto.addedBy,
                updatedBy = dto.updatedBy
            )
            if (local == null) shoppingListDao.insert(entity) else shoppingListDao.update(entity)
        }
    }

    // --- Recipe books ---

    fun observeRecipeBooks(): Flow<List<RecipeBookSummary>> =
        recipeBookDao.observeAllWithCounts().map { list ->
            list.map {
                RecipeBookSummary(
                    it.book.id, it.book.name, it.book.coverPhotoUri, it.recipeCount,
                    it.book.packId, it.book.packVersion, it.book.syncConnectionId
                )
            }
        }

    fun observeRecipeBook(id: Long): Flow<RecipeBook?> =
        recipeBookDao.observeOne(id).map { it?.toDomain() }

    suspend fun getRecipeBookOnce(id: Long): RecipeBook? =
        recipeBookDao.getOnce(id)?.toDomain()

    /** Every book, used when exporting the whole library (to include the covers in the ZIP). */
    suspend fun getAllRecipeBooksOnce(): List<RecipeBook> =
        recipeBookDao.observeAllWithCounts().first().map { it.book.toDomain() }

    suspend fun findRecipeBookByPackId(packId: String): RecipeBook? =
        recipeBookDao.findByPackId(packId)?.toDomain()

    /**
     * Safeguard: pack books are read-only (see [RecipeBook.isPack]). The UI already blocks editing
     * them; this keeps a pack from being overwritten if anything bypasses it.
     */
    suspend fun saveRecipeBook(draft: RecipeBookDraft): Long {
        if (draft.id != 0L && recipeBookDao.getOnce(draft.id)?.packId != null) return draft.id
        val now = System.currentTimeMillis()
        return if (draft.id == 0L) {
            recipeBookDao.insert(
                RecipeBookEntity(
                    uid = draft.uid ?: UUID.randomUUID().toString(),
                    name = draft.name.trim(),
                    coverPhotoUri = draft.coverPhotoUri,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val existing = recipeBookDao.getOnce(draft.id)
            val uid = existing?.uid ?: draft.uid ?: UUID.randomUUID().toString()
            recipeBookDao.update(
                RecipeBookEntity(
                    id = draft.id,
                    uid = uid,
                    name = draft.name.trim(),
                    coverPhotoUri = draft.coverPhotoUri,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    syncConnectionId = existing?.syncConnectionId
                )
            )
            existing?.syncConnectionId?.let {
                enqueueSyncChange(it, SyncEntityType.BOOK, uid, SyncChangeType.UPSERT)
                onSyncChangeEnqueued(it)
            }
            draft.id
        }
    }

    /** Throws [RecipeBookNotEmptyException] when the book still contains recipes. */
    suspend fun deleteRecipeBook(id: Long) {
        val count = recipeBookDao.countRecipes(id)
        if (count > 0) throw RecipeBookNotEmptyException(count)
        val book = recipeBookDao.getOnce(id)
        recipeBookDao.delete(id)
        book?.syncConnectionId?.let {
            enqueueSyncChange(it, SyncEntityType.BOOK, book.uid, SyncChangeType.DELETE)
            onSyncChangeEnqueued(it)
        }
    }

    /**
     * Deletes all of the user's own books (not installed read-only packs) together with their
     * recipes, covers and recipe photos. Used to start clean right before restoring a full backup.
     */
    suspend fun wipeUserRecipesAndBooks(): WipeResult {
        val ownBooks = getAllRecipeBooksOnce().filter { !it.isPack }
        val ownBookIds = ownBooks.map { it.id }.toSet()
        val ownRecipes = getAllRecipesOnce().filter { it.recipeBookId in ownBookIds }
        val photoUris = ownRecipes.flatMap { recipe -> recipe.photos.map { it.uri } } + ownBooks.mapNotNull { it.coverPhotoUri }

        db.withTransaction {
            ownBookIds.forEach { bookId ->
                recipeDao.deleteAllForBook(bookId)
                recipeBookDao.delete(bookId)
            }
        }
        photoUris.forEach { PhotoStorage.deleteFile(it) }
        return WipeResult(bookCount = ownBooks.size, recipeCount = ownRecipes.size)
    }

    /**
     * Used when importing a backup: finds a book by name or creates it. [uid] and [coverPhotoUri]
     * only apply to a new book; an existing book with that name is reused as is, cover included.
     */
    suspend fun getOrCreateRecipeBookIdByName(name: String, uid: String? = null, coverPhotoUri: String? = null): Long {
        val trimmed = name.trim().ifBlank { L10n.str(R.string.untitled) }
        recipeBookDao.findByName(trimmed)?.let { return it.id }
        return recipeBookDao.insert(
            RecipeBookEntity(
                uid = uid ?: UUID.randomUUID().toString(),
                name = trimmed,
                coverPhotoUri = coverPhotoUri,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    // --- Downloadable recipe packs ---

    /**
     * Installs or updates a pack. The book is found by [packId] (never by name, so it never clashes
     * with a user book of the same name). Recipes are created or updated by [RecipeExportDto.uid],
     * keeping isFavorite, timesCooked and createdAt, and those missing from this version are
     * deleted. Unlike [saveRecipe] and [saveRecipeBook] it skips the read-only checks: this is the
     * only legitimate way to write to a pack book.
     */
    suspend fun installOrUpdatePack(
        packId: String,
        packVersion: Int,
        bookName: String,
        bookUid: String,
        bookCoverUri: String?,
        recipes: List<RecipeExportDto>,
        photosByUid: Map<String, List<RecipePhoto>>
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val existingBook = recipeBookDao.findByPackId(packId)
        val bookId = if (existingBook == null) {
            recipeBookDao.insert(
                RecipeBookEntity(
                    uid = bookUid,
                    name = bookName,
                    coverPhotoUri = bookCoverUri,
                    createdAt = now,
                    packId = packId,
                    packVersion = packVersion
                )
            )
        } else {
            recipeBookDao.update(
                existingBook.copy(
                    name = bookName,
                    coverPhotoUri = bookCoverUri ?: existingBook.coverPhotoUri,
                    packVersion = packVersion
                )
            )
            existingBook.id
        }

        recipes.forEach { dto ->
            val categoryId = dto.categoryName?.takeIf { it.isNotBlank() }?.let { resolveCategoryId(it) }
            // A recipe only counts as existing if it is still in this book. If the user moved it to
            // another book (allowed for pack recipes), the next update creates a new copy in the
            // pack instead of touching the moved one.
            val existingRecipe = recipeDao.findByUid(dto.uid)?.takeIf { it.recipeBookId == bookId }
            val recipeId = if (existingRecipe == null) {
                recipeDao.insertRecipe(
                    RecipeEntity(
                        uid = dto.uid,
                        name = dto.name.trim(),
                        categoryId = categoryId,
                        recipeBookId = bookId,
                        difficulty = Difficulty.parse(dto.difficulty).name,
                        prepTimeMinutes = dto.prepTimeMinutes,
                        cookTimeMinutes = dto.cookTimeMinutes,
                        servings = dto.servings,
                        notes = dto.notes,
                        source = dto.source,
                        isFavorite = false,
                        timesCooked = 0,
                        createdAt = now,
                        updatedAt = now,
                        origin = dto.origin,
                        originCountry = RecipeOrigin.normalizeCountry(dto.originCountry)
                    )
                )
            } else {
                recipeDao.updateRecipe(
                    existingRecipe.copy(
                        name = dto.name.trim(),
                        categoryId = categoryId,
                        difficulty = Difficulty.parse(dto.difficulty).name,
                        prepTimeMinutes = dto.prepTimeMinutes,
                        cookTimeMinutes = dto.cookTimeMinutes,
                        servings = dto.servings,
                        notes = dto.notes,
                        source = dto.source,
                        updatedAt = now,
                        origin = dto.origin,
                        originCountry = RecipeOrigin.normalizeCountry(dto.originCountry)
                    )
                )
                existingRecipe.id
            }

            recipeDao.deleteIngredients(recipeId)
            var ingredientPosition = 0
            val ingredientEntities = dto.ingredientGroups.flatMap { group ->
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
            if (ingredientEntities.isNotEmpty()) recipeDao.insertIngredients(ingredientEntities)

            recipeDao.deleteSteps(recipeId)
            var stepPosition = 0
            val stepEntities = dto.stepGroups.flatMap { group ->
                group.instructions.map { instruction ->
                    StepEntity(recipeId = recipeId, groupName = group.name, position = stepPosition++, instruction = instruction.trim())
                }
            }
            if (stepEntities.isNotEmpty()) recipeDao.insertSteps(stepEntities)

            recipeDao.deletePhotos(recipeId)
            val photos = photosByUid[dto.uid].orEmpty()
            if (photos.isNotEmpty()) {
                recipeDao.insertPhotos(
                    photos.mapIndexed { index, photo -> RecipePhotoEntity(recipeId = recipeId, uri = photo.uri, position = index, isCover = photo.isCover) }
                )
            }

            recipeDao.deleteTagCrossRefs(recipeId)
            val tagIds = dto.tags.filter { it.isNotBlank() }.map { resolveTagId(it) }
            if (tagIds.isNotEmpty()) recipeDao.insertTagCrossRefs(tagIds.map { RecipeTagCrossRef(recipeId, it) })

            recipeDao.deleteUtensilCrossRefs(recipeId)
            val utensilIds = KitchenEquipment.clean(dto.utensils, appLanguage()).map { resolveUtensilId(it) }.distinct()
            if (utensilIds.isNotEmpty()) recipeDao.insertUtensilCrossRefs(utensilIds.map { RecipeUtensilCrossRef(recipeId, it) })

            if (dto.health != null) {
                val colorLevel = runCatching { HealthColorLevel.valueOf(dto.health.colorLevel) }.getOrDefault(HealthColorLevel.YELLOW)
                recipeDao.updateHealthRating(recipeId, colorLevel.name, dto.health.description, dto.health.fingerprint, dto.health.analyzedAt)
            }
            dto.nutrition?.let { nutrition ->
                recipeDao.updateNutritionInfo(
                    recipeId,
                    nutrition.caloriesPerServing,
                    nutrition.proteinGrams,
                    nutrition.carbsGrams,
                    nutrition.fatGrams,
                    nutrition.fingerprint,
                    nutrition.analyzedAt
                )
            }
        }

        // The pack owns its content: a recipe that is not in this version is deleted.
        val currentUids = recipes.map { it.uid }
        if (currentUids.isEmpty()) recipeDao.deleteAllForBook(bookId) else recipeDao.deleteRecipesNotInUidSet(bookId, currentUids)

        bookId
    }

    /**
     * Uninstalls a pack: deletes its recipes and the book, bypassing the non-empty check of
     * [deleteRecipeBook].
     */
    suspend fun uninstallPack(bookId: Long) = db.withTransaction {
        recipeDao.deleteAllForBook(bookId)
        recipeBookDao.delete(bookId)
    }

    // --- Sync server (self-hosted namespaces) ---

    fun observeSyncConnections(): Flow<List<SyncConnection>> =
        syncConnectionDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getSyncConnectionOnce(id: Long): SyncConnection? = syncConnectionDao.getOnce(id)?.toDomain()

    suspend fun addSyncConnection(label: String, serverUrl: String, namespaceId: String, accessToken: String): Long =
        syncConnectionDao.insert(
            SyncConnectionEntity(
                label = label.trim(),
                serverUrl = serverUrl.trim().trimEnd('/'),
                namespaceId = namespaceId.trim(),
                accessToken = TokenCipher.encrypt(accessToken.trim()),
                createdAt = System.currentTimeMillis()
            )
        )

    /**
     * Removes the connection. Its books become local (no content is deleted) and any pending upload
     * for it is discarded.
     */
    suspend fun removeSyncConnection(id: Long) = db.withTransaction {
        recipeBookDao.clearSyncConnection(id)
        pendingSyncChangeDao.clearAllForConnection(id)
        syncConnectionDao.getOnce(id)?.let { syncConnectionDao.delete(it) }
        purgeShoppingTombstonesIfNotShared()
    }

    suspend fun markSyncSuccess(connectionId: Long, revision: Long) =
        syncConnectionDao.markSynced(connectionId, revision, System.currentTimeMillis())

    suspend fun markSyncError(connectionId: Long, reason: String) = syncConnectionDao.markError(connectionId, reason)

    /**
     * Links an existing user book to a connection. It is synced read-write (unlike a pack) and
     * queued for a full upload, photos included, on the next sync.
     */
    suspend fun linkBookToSyncConnection(bookId: Long, connectionId: Long) {
        val linked = db.withTransaction {
            val book = recipeBookDao.getOnce(bookId) ?: return@withTransaction false
            recipeBookDao.update(book.copy(syncConnectionId = connectionId, updatedAt = System.currentTimeMillis()))
            enqueueBookResyncLocked(bookId, connectionId)
            true
        }
        if (linked) onSyncChangeEnqueued(connectionId)
    }

    /**
     * Queues the whole book again (metadata, every recipe and every photo) for upload to
     * [connectionId]. Must run inside an open transaction, hence "Locked"; it is used by
     * [linkBookToSyncConnection], [enqueueFullBookResync] and [enqueueFullConnectionResync]. It is
     * never called from automatic or periodic sync, since re-queueing everything each time would
     * waste battery and data; [org.calamares.miga.data.sync.SyncEngine.pushRecipePhotosIfPresent]
     * heals missing photos cheaply instead.
     */
    private suspend fun enqueueBookResyncLocked(bookId: Long, connectionId: Long) {
        val book = recipeBookDao.getOnce(bookId) ?: return
        recipeDao.backfillPhotoUids() // without a uid, photos older than sync would be skipped below
        enqueueSyncChange(connectionId, SyncEntityType.BOOK, book.uid, SyncChangeType.UPSERT)
        recipeDao.getAllWithDetailsForBookOnce(bookId).forEach { details ->
            enqueueSyncChange(connectionId, SyncEntityType.RECIPE, details.recipe.uid, SyncChangeType.UPSERT)
            recipeDao.getPhotosOnce(details.recipe.id).forEach { photo ->
                photo.uid?.let {
                    enqueueSyncChange(connectionId, SyncEntityType.PHOTO, it, SyncChangeType.UPSERT, parentUid = details.recipe.uid)
                }
            }
        }
    }

    /**
     * Forces a full re-upload (book, recipes and photos) of an already linked book. Used by the
     * manual "Sync now" button in the book editor to repair books linked before their photos were
     * queued too (see [enqueueBookResyncLocked]).
     */
    suspend fun enqueueFullBookResync(bookId: Long, connectionId: Long) {
        db.withTransaction { enqueueBookResyncLocked(bookId, connectionId) }
        onSyncChangeEnqueued(connectionId)
    }

    /**
     * Like [enqueueFullBookResync], for every book linked to [connectionId] at once. Used by the
     * connection's manual "Sync now" button.
     */
    suspend fun enqueueFullConnectionResync(connectionId: Long) {
        val books = recipeBookDao.findBySyncConnectionId(connectionId)
        if (books.isEmpty()) return
        db.withTransaction { books.forEach { enqueueBookResyncLocked(it.id, connectionId) } }
        onSyncChangeEnqueued(connectionId)
    }

    /**
     * Resets the connection cursor to 0 so the next sync downloads all server content again. This
     * is idempotent and recovers anything missed, such as photos whose download failed. Used by the
     * manual "Sync now" buttons.
     */
    suspend fun resetSyncCursor(connectionId: Long) = syncConnectionDao.resetCursor(connectionId)

    /**
     * Assigns a uid to old photos that lack one (see [RecipeDao.backfillPhotoUids]). Called on app
     * start.
     */
    suspend fun ensurePhotoUids() = recipeDao.backfillPhotoUids()

    /** Stops syncing a book. It becomes local; nothing is deleted locally or on the server. */
    suspend fun unlinkBookFromSyncConnection(bookId: Long) {
        val book = recipeBookDao.getOnce(bookId) ?: return
        recipeBookDao.update(book.copy(syncConnectionId = null))
    }

    // --- Outbox: queue local changes for upload ---

    private suspend fun enqueueSyncChange(
        connectionId: Long,
        entityType: SyncEntityType,
        uid: String,
        changeType: SyncChangeType,
        parentUid: String? = null
    ) {
        pendingSyncChangeDao.enqueue(
            PendingSyncChangeEntity(
                syncConnectionId = connectionId,
                entityType = entityType.name,
                uid = uid,
                changeType = changeType.name,
                createdAt = System.currentTimeMillis(),
                parentUid = parentUid
            )
        )
    }

    suspend fun getPendingSyncChanges(connectionId: Long): List<PendingSyncChangeEntity> =
        pendingSyncChangeDao.getPendingForConnection(connectionId)

    suspend fun clearPendingSyncChange(id: Long) = pendingSyncChangeDao.clear(id)

    // --- Build the sync DTO of a local book or recipe (for upload) ---

    suspend fun getRecipeBookSyncDto(bookId: Long): BookSyncDto? {
        val book = recipeBookDao.getOnce(bookId) ?: return null
        return BookSyncDto(uid = book.uid, name = book.name, hasCoverPhoto = book.coverPhotoUri != null, updatedAt = book.updatedAt)
    }

    suspend fun getRecipeBookSyncDtoByUid(uid: String): BookSyncDto? {
        val book = recipeBookDao.findByUid(uid) ?: return null
        return getRecipeBookSyncDto(book.id)
    }

    /**
     * Used to upload a book cover (see [SyncEngine.pushBookCoverIfPresent]); null when the book has
     * none.
     */
    suspend fun getRecipeBookCoverUri(bookUid: String): String? = recipeBookDao.findByUid(bookUid)?.coverPhotoUri

    suspend fun getRecipeSyncDtoByUid(uid: String): RecipeSyncDto? {
        val entity = recipeDao.findByUid(uid) ?: return null
        return getRecipeSyncDto(entity.id)
    }

    suspend fun getRecipeSyncDto(recipeId: Long): RecipeSyncDto? {
        val details = recipeDao.observeWithDetails(recipeId).first() ?: return null
        val book = recipeBookDao.getOnce(details.recipe.recipeBookId) ?: return null
        val recipe = details.toDomain()
        return RecipeSyncDto(
            uid = recipe.uid,
            bookUid = book.uid,
            name = recipe.name,
            categoryName = recipe.categoryName,
            difficulty = recipe.difficulty.name,
            prepTimeMinutes = recipe.prepTimeMinutes,
            cookTimeMinutes = recipe.cookTimeMinutes,
            servings = recipe.servings,
            notes = recipe.notes,
            source = recipe.source,
            isFavorite = recipe.isFavorite,
            ingredientGroups = recipe.ingredientGroups.map { g ->
                IngredientGroupDto(g.name, g.ingredients.map { IngredientDto(it.name, it.quantity, it.unit) })
            },
            stepGroups = recipe.stepGroups.map { g -> StepGroupDto(g.name, g.instructions) },
            tags = recipe.tags,
            utensils = recipe.utensils,
            updatedAt = details.recipe.updatedAt,
            rating = recipe.rating,
            origin = recipe.origin,
            originCountry = recipe.originCountry,
            schema = RECIPE_SYNC_SCHEMA
        )
    }

    /** What is needed to upload a single local photo (see [SyncEngine.pushPhotoChange]). */
    data class PhotoPushInfo(val uri: String, val recipeUid: String, val isCover: Boolean, val position: Int)

    suspend fun getPhotoPushInfo(photoUid: String): PhotoPushInfo? {
        val photo = recipeDao.findPhotoByUid(photoUid) ?: return null
        val recipe = recipeDao.getRecipeOnce(photo.recipeId) ?: return null
        return PhotoPushInfo(photo.uri, recipe.uid, photo.isCover, photo.position)
    }

    /**
     * Every current photo of a recipe, ready to upload (see
     * [SyncEngine.pushRecipePhotosIfPresent]). Photos without a `uid` (a nullable column from the
     * migration that newer photos always fill in) are skipped.
     */
    suspend fun getRecipePhotosForPush(recipeUid: String): List<Pair<String, PhotoPushInfo>> {
        recipeDao.backfillPhotoUids() // heals old photos without a uid, which would otherwise never be uploaded
        val recipe = recipeDao.findByUid(recipeUid) ?: return emptyList()
        return recipeDao.getPhotosOnce(recipe.id).mapNotNull { photo ->
            val uid = photo.uid ?: return@mapNotNull null
            uid to PhotoPushInfo(photo.uri, recipeUid, photo.isCover, photo.position)
        }
    }

    /**
     * Used before downloading a remote photo, so it is not downloaded (leaving an orphan file) when
     * it already exists locally.
     */
    suspend fun hasLocalPhoto(photoUid: String): Boolean = recipeDao.findPhotoByUid(photoUid) != null

    // --- Applying changes received from the server (used by SyncEngine) ---
    //
    // Books are processed in two passes, upserts first and deletions last, because of the RESTRICT
    // foreign key on recipes.recipeBookId: a new synced recipe needs its book to exist locally, and
    // a book cannot be deleted while it still has local recipes (which is also why recipes are
    // processed before book deletions).

    /**
     * Returns the local book id after applying the upsert, or null when it was ignored (the local
     * copy was newer, or it is a tombstone).
     */
    suspend fun applyRemoteBookUpsert(connectionId: Long, dto: BookSyncDto): Long? = db.withTransaction {
        if (dto.deletedAt != null) return@withTransaction null
        val existing = recipeBookDao.findByUid(dto.uid)
        if (existing != null && existing.updatedAt > dto.updatedAt) return@withTransaction null
        if (existing == null) {
            recipeBookDao.insert(
                RecipeBookEntity(
                    uid = dto.uid,
                    name = dto.name,
                    coverPhotoUri = null,
                    createdAt = dto.updatedAt,
                    updatedAt = dto.updatedAt,
                    syncConnectionId = connectionId
                )
            )
        } else {
            recipeBookDao.update(existing.copy(name = dto.name, updatedAt = dto.updatedAt, syncConnectionId = connectionId))
            existing.id
        }
    }

    suspend fun applyRemoteBookDeletion(dto: BookSyncDto) = db.withTransaction {
        if (dto.deletedAt == null) return@withTransaction
        val existing = recipeBookDao.findByUid(dto.uid) ?: return@withTransaction
        if (existing.updatedAt > dto.deletedAt) return@withTransaction
        recipeDao.deleteAllForBook(existing.id)
        recipeBookDao.delete(existing.id)
    }

    /**
     * Like [applyRemoteBookDeletion], for an "unlinked" tombstone ([BookSyncDto.unlinked]). The
     * book and all its recipes and photos are kept; only the sync link is cut, so it becomes a
     * normal editable local book no longer managed by [SyncEngine].
     */
    suspend fun applyRemoteBookUnlink(dto: BookSyncDto) = db.withTransaction {
        if (dto.deletedAt == null) return@withTransaction
        val existing = recipeBookDao.findByUid(dto.uid) ?: return@withTransaction
        if (existing.updatedAt > dto.deletedAt) return@withTransaction
        recipeBookDao.update(existing.copy(syncConnectionId = null))
    }

    /**
     * Applies a book cover that [SyncEngine] has already downloaded and stored at [localUri]. No
     * last-write-wins check of its own (that was decided when applying the book). Returns the
     * previous file uri, if different, so the engine can delete it.
     */
    suspend fun applyRemoteBookCover(bookId: Long, localUri: String): String? {
        val book = recipeBookDao.getOnce(bookId) ?: return null
        val oldUri = book.coverPhotoUri
        recipeBookDao.update(book.copy(coverPhotoUri = localUri))
        return oldUri?.takeIf { it != localUri }
    }

    suspend fun applyRemoteRecipeUpsert(dto: RecipeSyncDto): Long? = db.withTransaction {
        if (dto.deletedAt != null) return@withTransaction null
        val bookId = recipeBookDao.findByUid(dto.bookUid)?.id ?: return@withTransaction null
        val existing = recipeDao.findByUid(dto.uid)
        if (existing != null && existing.updatedAt > dto.updatedAt) return@withTransaction null

        val categoryId = dto.categoryName?.takeIf { it.isNotBlank() }?.let { resolveCategoryId(it) }
        val domainIngredientGroups = dto.ingredientGroups.map { g -> IngredientGroup(g.name, g.ingredients.map { Ingredient(it.name, it.quantity, it.unit) }) }
        val domainStepGroups = dto.stepGroups.map { g -> StepGroup(g.name, g.instructions) }
        val newFingerprint = computeHealthFingerprint(domainIngredientGroups, domainStepGroups)
        val keepHealth = existing != null && existing.healthFingerprint == newFingerprint

        val recipeId = if (existing == null) {
            recipeDao.insertRecipe(
                RecipeEntity(
                    uid = dto.uid,
                    name = dto.name.trim(),
                    categoryId = categoryId,
                    recipeBookId = bookId,
                    difficulty = Difficulty.parse(dto.difficulty).name,
                    prepTimeMinutes = dto.prepTimeMinutes,
                    cookTimeMinutes = dto.cookTimeMinutes,
                    servings = dto.servings,
                    notes = dto.notes,
                    source = dto.source,
                    isFavorite = dto.isFavorite,
                    timesCooked = 0,
                    createdAt = dto.updatedAt,
                    updatedAt = dto.updatedAt,
                    rating = dto.rating,
                    origin = dto.origin,
                    originCountry = RecipeOrigin.normalizeCountry(dto.originCountry)
                )
            )
        } else {
            recipeDao.updateRecipe(
                existing.copy(
                    name = dto.name.trim(),
                    categoryId = categoryId,
                    recipeBookId = bookId,
                    difficulty = Difficulty.parse(dto.difficulty).name,
                    prepTimeMinutes = dto.prepTimeMinutes,
                    cookTimeMinutes = dto.cookTimeMinutes,
                    servings = dto.servings,
                    notes = dto.notes,
                    source = dto.source,
                    isFavorite = dto.isFavorite,
                    updatedAt = dto.updatedAt,
                    rating = dto.rating,
                    // An older server does not know the fields and returns null: keep what the device has.
                    origin = if (dto.schema >= RECIPE_SYNC_SCHEMA) dto.origin else dto.origin ?: existing.origin,
                    originCountry = if (dto.schema >= RECIPE_SYNC_SCHEMA) {
                        RecipeOrigin.normalizeCountry(dto.originCountry)
                    } else {
                        RecipeOrigin.normalizeCountry(dto.originCountry) ?: existing.originCountry
                    },
                    healthColor = if (keepHealth) existing.healthColor else null,
                    healthDescription = if (keepHealth) existing.healthDescription else null,
                    healthFingerprint = if (keepHealth) existing.healthFingerprint else null,
                    healthAnalyzedAt = if (keepHealth) existing.healthAnalyzedAt else null,
                    nutritionCalories = if (keepHealth) existing.nutritionCalories else null,
                    nutritionProteinGrams = if (keepHealth) existing.nutritionProteinGrams else null,
                    nutritionCarbsGrams = if (keepHealth) existing.nutritionCarbsGrams else null,
                    nutritionFatGrams = if (keepHealth) existing.nutritionFatGrams else null,
                    nutritionFingerprint = if (keepHealth) existing.nutritionFingerprint else null,
                    nutritionAnalyzedAt = if (keepHealth) existing.nutritionAnalyzedAt else null
                )
            )
            existing.id
        }

        recipeDao.deleteIngredients(recipeId)
        var ingredientPosition = 0
        val ingredientEntities = dto.ingredientGroups.flatMap { group ->
            group.ingredients.map { ingredient ->
                IngredientEntity(
                    recipeId = recipeId, groupName = group.name, position = ingredientPosition++,
                    name = ingredient.name.trim(), quantity = ingredient.quantity,
                    unit = ingredient.unit?.trim()?.takeIf { it.isNotBlank() }
                )
            }
        }
        if (ingredientEntities.isNotEmpty()) recipeDao.insertIngredients(ingredientEntities)

        recipeDao.deleteSteps(recipeId)
        var stepPosition = 0
        val stepEntities = dto.stepGroups.flatMap { group ->
            group.instructions.map { instruction -> StepEntity(recipeId = recipeId, groupName = group.name, position = stepPosition++, instruction = instruction.trim()) }
        }
        if (stepEntities.isNotEmpty()) recipeDao.insertSteps(stepEntities)

        recipeDao.deleteTagCrossRefs(recipeId)
        val tagIds = dto.tags.filter { it.isNotBlank() }.map { resolveTagId(it) }
        if (tagIds.isNotEmpty()) recipeDao.insertTagCrossRefs(tagIds.map { RecipeTagCrossRef(recipeId, it) })

        recipeDao.deleteUtensilCrossRefs(recipeId)
        val utensilIds = KitchenEquipment.clean(dto.utensils, appLanguage()).map { resolveUtensilId(it) }.distinct()
        if (utensilIds.isNotEmpty()) recipeDao.insertUtensilCrossRefs(utensilIds.map { RecipeUtensilCrossRef(recipeId, it) })

        recipeId
    }

    suspend fun applyRemoteRecipeDeletion(dto: RecipeSyncDto) = db.withTransaction {
        if (dto.deletedAt == null) return@withTransaction
        val existing = recipeDao.findByUid(dto.uid) ?: return@withTransaction
        if (existing.updatedAt > dto.deletedAt) return@withTransaction
        recipeDao.deleteRecipe(existing.id)
    }

    /**
     * Applies a single photo that [SyncEngine] has already downloaded and stored at [localUri]. No
     * last-write-wins check, since photos are only added or removed, never edited in place: an
     * existing row with that uid only gets its position and cover flag updated.
     */
    suspend fun applyRemotePhotoUpsert(dto: PhotoMetaDto, localUri: String): Boolean {
        if (dto.deletedAt != null) return false
        val recipe = recipeDao.findByUid(dto.recipeUid) ?: return false
        val existing = recipeDao.findPhotoByUid(dto.uid)
        if (existing != null) {
            recipeDao.updatePhotoMetaByUid(dto.uid, dto.isCover, dto.position)
        } else {
            recipeDao.insertPhotos(
                listOf(RecipePhotoEntity(recipeId = recipe.id, uri = localUri, position = dto.position, isCover = dto.isCover, uid = dto.uid))
            )
        }
        return true
    }

    /**
     * Deletes the local row of a photo deleted on the server and returns its
     * [RecipePhotoEntity.uri] so the sync engine can delete the file; null when there was nothing
     * to delete.
     */
    suspend fun applyRemotePhotoDeletion(dto: PhotoMetaDto): String? {
        if (dto.deletedAt == null) return null
        val existing = recipeDao.findPhotoByUid(dto.uid) ?: return null
        recipeDao.deletePhotoByUid(dto.uid)
        return existing.uri
    }

    private suspend fun resolveCategoryId(name: String): Long {
        val trimmed = name.trim()
        categoryDao.findByName(trimmed)?.let { return it.id }
        return categoryDao.insert(CategoryEntity(name = trimmed))
    }

    private suspend fun resolveTagId(name: String): Long {
        val trimmed = name.trim()
        tagDao.findByName(trimmed)?.let { return it.id }
        return tagDao.insert(TagEntity(name = trimmed))
    }

    /**
     * Id of the equipment called [name], created if missing. A usual variant of a default
     * ("Airfryer") resolves to the default ("Freidora de aire" or "Air fryer", whichever exists).
     */
    private suspend fun resolveUtensilId(name: String): Long {
        val trimmed = name.trim()
        utensilDao.findByName(trimmed)?.let { return it.id }
        KitchenEquipment.equivalents(trimmed).forEach { equivalent -> utensilDao.findByName(equivalent)?.let { return it.id } }
        val canonical = KitchenEquipment.canonical(trimmed, appLanguage())
        utensilDao.findByName(canonical)?.let { return it.id }
        return utensilDao.insert(UtensilEntity(name = canonical))
    }

    private fun appLanguage(): String = if (L10n.locale().language == "es") "es" else "en"
}

fun RecipeBookEntity.toDomain() = RecipeBook(id, uid, name, coverPhotoUri, packId, packVersion, syncConnectionId)

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
            .add(Ingredient(entity.name, entity.quantity, entity.unit))
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
        photos = photos.sortedBy { it.position }.map { RecipePhoto(it.uri, it.isCover) },
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
        originCountry = recipe.originCountry
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
