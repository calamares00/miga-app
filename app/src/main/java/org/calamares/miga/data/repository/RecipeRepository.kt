package org.calamares.miga.data.repository

import org.calamares.miga.data.local.dao.RecipeCategoryLink
import org.calamares.miga.data.content.CleanupProposal
import org.calamares.miga.data.content.CleanupAction
import org.calamares.miga.data.model.ContentItem
import org.calamares.miga.data.model.ContentKind
import androidx.room.withTransaction
import org.calamares.miga.L10n
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.TokenCipher
import org.calamares.miga.data.local.entity.RecipeNoteEntity
import org.calamares.miga.data.model.RecipeNote
import org.calamares.miga.data.local.entity.CategoryEntity
import org.calamares.miga.data.local.entity.IngredientCatalogEntity
import org.calamares.miga.data.local.entity.IngredientCategoryEntity
import org.calamares.miga.data.local.entity.IngredientEntity
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.local.entity.RecipeEntity
import org.calamares.miga.data.local.entity.RecipePhotoEntity
import org.calamares.miga.data.local.entity.RecipeTagCrossRef
import org.calamares.miga.data.local.entity.RecipeUtensilCrossRef
import org.calamares.miga.data.local.entity.RecipeWithDetails
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
import org.calamares.miga.data.model.RecipeDraft
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.StepGroup
import org.calamares.miga.data.model.SyncConnection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Thrown when trying to delete a recipe book that still contains recipes. */
class RecipeBookNotEmptyException(val recipeCount: Int) : Exception()

/** Result of [BookRepository.wipeUserRecipesAndBooks]. */
data class WipeResult(val bookCount: Int, val recipeCount: Int)

/**
 * Single source of truth for the app data. Recipes, their notes and the catalogues (categories,
 * tags, utensils, ingredients) are handled here; shopping lists, books and sync servers each have
 * their own class, reached through [shopping], [books] and [sync].
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

    /** Shopping lists, their stores and templates. */
    val shopping = ShoppingRepository(db, this, shoppingContext, onSyncChangeEnqueued)

    /** Sync servers, the outbox and the changes received from them. */
    val sync = SyncRepository(db, this, shopping, onSyncChangeEnqueued)

    /** Recipe books and installed packs. */
    val books = BookRepository(db, this, sync, onSyncChangeEnqueued)

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
    private val recipeNoteDao = db.recipeNoteDao()
    private val contentDao = db.contentDao()

    fun observeRecipesForBook(bookId: Long): Flow<List<Recipe>> =
        recipeDao.observeAllWithDetailsForBook(bookId).map { list -> list.map { it.toDomain() } }

    /** Every recipe in every book, used by the global search. */
    fun observeAllRecipes(): Flow<List<Recipe>> =
        recipeDao.observeAllWithDetails().map { list -> list.map { it.toDomain() } }

    fun observeRecipe(id: Long): Flow<Recipe?> =
        recipeDao.observeWithDetails(id).map { it?.toDomain() }

    fun observeSamplePhotoUri(): Flow<String?> = recipeDao.observeSamplePhotoUri()
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

    // --- Personal notes (journal) ---

    fun observeRecipeNotes(recipeId: Long): Flow<List<RecipeNote>> =
        recipeNoteDao.observeForRecipe(recipeId).map { notes -> notes.map { RecipeNote(it.id, it.text, it.createdAt) } }

    suspend fun getRecipeNotes(recipeId: Long): List<RecipeNote> =
        recipeNoteDao.getForRecipe(recipeId).map { RecipeNote(it.id, it.text, it.createdAt) }

    suspend fun addRecipeNote(recipeId: Long, text: String, createdAt: Long = System.currentTimeMillis()) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) recipeNoteDao.insert(RecipeNoteEntity(recipeId = recipeId, text = trimmed, createdAt = createdAt))
    }

    suspend fun updateRecipeNote(id: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) recipeNoteDao.delete(id) else recipeNoteDao.updateText(id, trimmed)
    }

    suspend fun deleteRecipeNote(id: Long) = recipeNoteDao.delete(id)

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
            sync.enqueueSyncChange(it, SyncEntityType.RECIPE, recipe.uid, SyncChangeType.DELETE)
            onSyncChangeEnqueued(it)
        }
    }

    /**
     * No-op when either book is a pack: pack recipes are read-only (they can be copied, see
     * [copyRecipeToBook]) and content cannot be added to a pack.
     */
    suspend fun moveRecipeToBook(recipeId: Long, newBookId: Long) {
        val recipe = recipeDao.getRecipeOnce(recipeId) ?: return
        if (recipeBookDao.getOnce(recipe.recipeBookId)?.packId != null) return
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
                        // An imported recipe keeps its uid unless this phone already has it (a book
                        // exported and imported again here): two recipes must never share one.
                        uid = draft.uid?.takeIf { recipeDao.findByUid(it) == null } ?: UUID.randomUUID().toString(),
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
                        originCountry = RecipeOrigin.normalizeCountry(draft.originCountry),
                        sharedBy = draft.sharedBy
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
                        originCountry = RecipeOrigin.normalizeCountry(draft.originCountry),
                        // Read-only for the user: only a newer shared copy changes it.
                        sharedBy = draft.sharedBy ?: existing?.sharedBy
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
                        unit = ingredient.unit?.trim()?.takeIf { it.isNotBlank() },
                        optional = ingredient.optional
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
            // sync engine does not treat them as new photos on every save. A restored photo keeps
            // the uid saved in the backup, or the server would see it as a second copy.
            val existingPhotos = recipeDao.getPhotosOnce(recipeId)
            val existingUidByUri = existingPhotos.associate { it.uri to it.uid }
            recipeDao.deletePhotos(recipeId)
            val usedUids = mutableSetOf<String>()
            val newPhotoEntities = draft.photos.mapIndexed { index, photo ->
                val uid = existingUidByUri[photo.uri]?.takeIf { it !in usedUids }
                    ?: photo.uid?.takeIf { it !in usedUids && recipeDao.findPhotoByUid(it) == null }
                    ?: UUID.randomUUID().toString()
                usedUids += uid
                RecipePhotoEntity(
                    recipeId = recipeId,
                    uri = photo.uri,
                    position = index,
                    isCover = photo.isCover,
                    uid = uid
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
                    sync.enqueueSyncChange(connectionId, SyncEntityType.RECIPE, saved.uid, SyncChangeType.UPSERT)
                    // Photos added or removed in this save. Removing a single photo does not go
                    // through deleteRecipe (which cascades on the server), so it is queued
                    // separately, with the recipe uid as parentUid because the photo row will no
                    // longer exist.
                    val oldUids = existingPhotos.mapNotNull { it.uid }.toSet()
                    val newUids = newPhotoEntities.mapNotNull { it.uid }.toSet()
                    (newUids - oldUids).forEach { photoUid ->
                        sync.enqueueSyncChange(connectionId, SyncEntityType.PHOTO, photoUid, SyncChangeType.UPSERT, parentUid = saved.uid)
                    }
                    (oldUids - newUids).forEach { photoUid ->
                        sync.enqueueSyncChange(connectionId, SyncEntityType.PHOTO, photoUid, SyncChangeType.DELETE, parentUid = saved.uid)
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

    // --- Content management (Settings > Manage content) ---

    /** Entries of [kind] with how many recipes (or ingredients) use each, kept up to date. */
    fun observeContent(kind: ContentKind): Flow<List<ContentItem>> = when (kind) {
        ContentKind.CATEGORY -> contentDao.observeCategories()
        ContentKind.EQUIPMENT -> contentDao.observeUtensils()
        ContentKind.TAG -> contentDao.observeTags()
        ContentKind.INGREDIENT -> contentDao.observeIngredients()
        ContentKind.INGREDIENT_CATEGORY -> contentDao.observeIngredientCategories()
    }.map { rows -> rows.map { ContentItem(kind, it.id, it.name, it.usage) } }

    /**
     * Renames an entry. When another entry of the same kind already has [newName] (in any case),
     * both are merged into that one: its recipes (or ingredients) move to it and this entry goes.
     *
     * Recipes link categories, equipment and tags by id, so they show the new name at once; they
     * are also marked as changed so synced books upload it. Ingredients are written in the recipes
     * by name: with [updateRecipes] the user's own recipes (not packs) that use the old name get
     * the new one.
     */
    suspend fun renameContent(kind: ContentKind, id: Long, newName: String, updateRecipes: Boolean = true) {
        val name = newName.trim().replace(Regex("""\s+"""), " ")
        if (name.isEmpty()) return
        val connections = db.withTransaction<Set<Long>> {
            val oldName = contentName(kind, id) ?: return@withTransaction emptySet()
            if (oldName == name) return@withTransaction emptySet()
            val affected = recipesUsing(kind, id, oldName, updateRecipes)
            val target = when (kind) {
                ContentKind.CATEGORY -> contentDao.categoryNamed(name, id)
                ContentKind.EQUIPMENT -> contentDao.utensilNamed(name, id)
                ContentKind.TAG -> contentDao.tagNamed(name, id)
                ContentKind.INGREDIENT -> contentDao.ingredientNamed(name, id)
                ContentKind.INGREDIENT_CATEGORY -> contentDao.ingredientCategoryNamed(name, id)
            }
            if (target != null) mergeInto(kind, id, target) else renameRow(kind, id, name)
            if (kind == ContentKind.INGREDIENT && updateRecipes) {
                // Merged or not, the recipes take the name exactly as now written.
                val finalName = if (target != null) contentDao.ingredientName(target) ?: name else name
                contentDao.renameIngredientInOwnRecipes(oldName, finalName)
            }
            touchRecipesLocked(affected)
        }
        connections.forEach { onSyncChangeEnqueued(it) }
    }

    /**
     * Deletes entries. Recipes lose the category, equipment or tag (and are marked as changed for
     * sync); deleting an ingredient only removes it from the suggestions and the shopping
     * categories, as the recipes keep their own text; ingredients of a deleted ingredient category
     * are left without one.
     */
    suspend fun deleteContent(kind: ContentKind, ids: Collection<Long>) {
        val connections = db.withTransaction<Set<Long>> {
            val affected = mutableSetOf<Long>()
            ids.forEach { id ->
                if (kind != ContentKind.INGREDIENT) affected += recipesUsing(kind, id, null, updateRecipes = false)
                when (kind) {
                    ContentKind.CATEGORY -> contentDao.deleteCategory(id)
                    ContentKind.EQUIPMENT -> contentDao.deleteUtensil(id)
                    ContentKind.TAG -> contentDao.deleteTag(id)
                    ContentKind.INGREDIENT -> contentDao.deleteIngredient(id)
                    ContentKind.INGREDIENT_CATEGORY -> contentDao.deleteIngredientCategory(id)
                }
            }
            touchRecipesLocked(affected)
        }
        connections.forEach { onSyncChangeEnqueued(it) }
    }

    /**
     * Merges [ids] into [intoId]: what used them now uses it, and they go. Ingredients are renamed
     * in the user's own recipes too.
     */
    suspend fun mergeContent(kind: ContentKind, ids: Collection<Long>, intoId: Long, updateRecipes: Boolean = true) {
        val connections = db.withTransaction<Set<Long>> {
            val targetName = contentName(kind, intoId) ?: return@withTransaction emptySet()
            val affected = mutableSetOf<Long>()
            ids.filter { it != intoId }.forEach { id ->
                val name = contentName(kind, id) ?: return@forEach
                affected += recipesUsing(kind, id, name, updateRecipes)
                mergeInto(kind, id, intoId)
                if (kind == ContentKind.INGREDIENT && updateRecipes) contentDao.renameIngredientInOwnRecipes(name, targetName)
            }
            touchRecipesLocked(affected)
        }
        connections.forEach { onSyncChangeEnqueued(it) }
    }

    /** Every list of Manage content as it is now, and the names in use on the shopping lists. */
    suspend fun contentForCleanup(): Pair<Map<ContentKind, List<ContentItem>>, Set<String>> =
        ContentKind.entries.associateWith { observeContent(it).first() } to
            contentDao.shoppingNames().map { it.trim().lowercase() }.toSet()

    /**
     * Applies the proposals of a cleanup (see ContentCleanup) and returns how to undo it. Merges go
     * first, then renames, then deletions. Ingredients are only changed in the list: recipes keep
     * their ingredients as written, since case or plural there is the author's choice.
     */
    suspend fun applyCleanup(proposals: List<CleanupProposal>): ContentSnapshot {
        val snapshot = db.withTransaction {
            ContentSnapshot(
                categories = contentDao.allCategories(),
                utensils = contentDao.allUtensils(),
                tags = contentDao.allTags(),
                ingredients = contentDao.allIngredients(),
                ingredientCategories = contentDao.allIngredientCategories(),
                utensilLinks = contentDao.allUtensilLinks(),
                tagLinks = contentDao.allTagLinks(),
                recipeCategories = contentDao.allRecipeCategories(),
                affectedRecipes = proposals.flatMap { proposal ->
                    proposal.items.flatMap { item -> recipesUsing(proposal.kind, item.id, item.name, updateRecipes = false) }
                }.toSet()
            )
        }
        val order = listOf(CleanupAction.MERGE, CleanupAction.RENAME, CleanupAction.DELETE)
        proposals.sortedBy { order.indexOf(it.action) }.forEach { proposal ->
            when (proposal.action) {
                CleanupAction.MERGE -> {
                    val target = proposal.target ?: return@forEach
                    mergeContent(proposal.kind, proposal.items.map { it.id }, target.id, updateRecipes = false)
                    val newName = proposal.newName
                    if (newName != null && newName != target.name && !target.isDefault) {
                        renameContent(proposal.kind, target.id, newName, updateRecipes = false)
                    }
                }
                CleanupAction.RENAME -> proposal.newName?.let { renameContent(proposal.kind, proposal.items.single().id, it, updateRecipes = false) }
                CleanupAction.DELETE -> deleteContent(proposal.kind, proposal.items.map { it.id })
            }
        }
        return snapshot
    }

    /** Puts every list back as it was before [applyCleanup], with the recipes' links. */
    suspend fun undoCleanup(snapshot: ContentSnapshot) {
        val connections = db.withTransaction<Set<Long>> {
            val recipes = contentDao.allRecipeCategories()
            val recipeIds = recipes.map { it.id }.toSet()
            // Ingredients first (they point to their categories), then the rest; links cascade away
            // with their entries and come back from the snapshot.
            contentDao.clearIngredients()
            contentDao.clearIngredientCategories()
            contentDao.clearCategories()
            contentDao.clearUtensils()
            contentDao.clearTags()
            contentDao.insertIngredientCategories(snapshot.ingredientCategories)
            contentDao.insertIngredients(snapshot.ingredients)
            contentDao.insertCategories(snapshot.categories)
            contentDao.insertUtensils(snapshot.utensils)
            contentDao.insertTags(snapshot.tags)
            // A recipe deleted meanwhile would break the foreign key.
            contentDao.insertUtensilLinks(snapshot.utensilLinks.filter { it.recipeId in recipeIds })
            contentDao.insertTagLinks(snapshot.tagLinks.filter { it.recipeId in recipeIds })
            val before = snapshot.recipeCategories.associate { it.id to it.categoryId }
            recipes.forEach { recipe ->
                if (recipe.id in before) contentDao.setRecipeCategory(recipe.id, before[recipe.id])
            }
            touchRecipesLocked(snapshot.affectedRecipes.filter { it in recipeIds })
        }
        connections.forEach { onSyncChangeEnqueued(it) }
    }

    suspend fun addContent(kind: ContentKind, name: String) = when (kind) {
        ContentKind.CATEGORY -> addCategory(name)
        ContentKind.EQUIPMENT -> addUtensil(name)
        ContentKind.TAG -> addTag(name)
        ContentKind.INGREDIENT -> addIngredientName(name)
        ContentKind.INGREDIENT_CATEGORY -> addIngredientCategory(name)
    }

    private suspend fun contentName(kind: ContentKind, id: Long): String? = when (kind) {
        ContentKind.CATEGORY -> contentDao.categoryName(id)
        ContentKind.EQUIPMENT -> contentDao.utensilName(id)
        ContentKind.TAG -> contentDao.tagName(id)
        ContentKind.INGREDIENT -> contentDao.ingredientName(id)
        ContentKind.INGREDIENT_CATEGORY -> contentDao.ingredientCategoryName(id)
    }

    /** The recipes a change of this entry shows up in (none for ingredient categories). */
    private suspend fun recipesUsing(kind: ContentKind, id: Long, name: String?, updateRecipes: Boolean): List<Long> = when (kind) {
        ContentKind.CATEGORY -> contentDao.recipesWithCategory(id)
        ContentKind.EQUIPMENT -> contentDao.recipesWithUtensil(id)
        ContentKind.TAG -> contentDao.recipesWithTag(id)
        ContentKind.INGREDIENT -> if (updateRecipes && name != null) contentDao.ownRecipesWithIngredient(name) else emptyList()
        ContentKind.INGREDIENT_CATEGORY -> emptyList()
    }

    private suspend fun renameRow(kind: ContentKind, id: Long, name: String) = when (kind) {
        ContentKind.CATEGORY -> contentDao.renameCategory(id, name)
        ContentKind.EQUIPMENT -> contentDao.renameUtensil(id, name)
        ContentKind.TAG -> contentDao.renameTag(id, name)
        ContentKind.INGREDIENT -> contentDao.renameIngredient(id, name)
        ContentKind.INGREDIENT_CATEGORY -> contentDao.renameIngredientCategory(id, name)
    }

    /** Moves everything that uses [fromId] to [toId] and deletes [fromId]. */
    private suspend fun mergeInto(kind: ContentKind, fromId: Long, toId: Long) {
        when (kind) {
            ContentKind.CATEGORY -> {
                contentDao.moveCategory(fromId, toId)
                contentDao.deleteCategory(fromId)
            }
            ContentKind.EQUIPMENT -> {
                contentDao.moveUtensil(fromId, toId)
                contentDao.deleteUtensil(fromId)
            }
            ContentKind.TAG -> {
                contentDao.moveTag(fromId, toId)
                contentDao.deleteTag(fromId)
            }
            ContentKind.INGREDIENT -> {
                contentDao.inheritIngredientCategory(fromId, toId)
                contentDao.deleteIngredient(fromId)
            }
            ContentKind.INGREDIENT_CATEGORY -> {
                contentDao.moveIngredientCategory(fromId, toId)
                contentDao.deleteIngredientCategory(fromId)
            }
        }
    }

    /**
     * Marks [recipeIds] as changed now and queues those of synced books for upload, after a change
     * in a label they show. Call inside a transaction; returns the connections to notify after it.
     */
    private suspend fun touchRecipesLocked(recipeIds: Collection<Long>): Set<Long> {
        if (recipeIds.isEmpty()) return emptySet()
        val now = System.currentTimeMillis()
        val connections = mutableSetOf<Long>()
        // SQLite limits the number of parameters of a query.
        recipeIds.distinct().chunked(500).forEach { chunk ->
            contentDao.touchRecipes(chunk, now)
            contentDao.syncTargets(chunk).forEach { target ->
                sync.enqueueSyncChange(target.connectionId, SyncEntityType.RECIPE, target.uid, SyncChangeType.UPSERT)
                connections += target.connectionId
            }
        }
        return connections
    }

    // --- Categories ---

    suspend fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) resolveCategoryId(trimmed)
    }

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

    fun observeIngredientCatalogWithCategoryById(): Flow<Map<Long, IngredientCatalogItem>> =
        observeIngredientCatalogWithCategory().map { list -> list.associateBy { it.id } }

    suspend fun changeIngredientCategory(id: Long, categoryId: Long?) {
        ingredientCatalogDao.updateCategory(id, categoryId)
    }

    // --- Ingredient categories (not the same as recipe categories) ---

    fun observeIngredientCategories(): Flow<List<IngredientCategoryEntity>> = ingredientCategoryDao.observeAll()

    suspend fun addIngredientCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) ingredientCategoryDao.insert(IngredientCategoryEntity(name = trimmed))
    }

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

    /**
     * Translates the default catalogue from [from] to [to] ("es" or "en") after the app language
     * changes: ingredients and their categories, recipe categories and kitchen equipment. Only names
     * that are still the [from] default change, so what the user renamed or created stays as it is;
     * when the translated name already exists both are merged. Supermarket aisle orders follow the
     * renamed categories.
     */
    suspend fun relocalizeDefaults(from: String, to: String) = db.withTransaction {
        if (from == to) return@withTransaction
        val fromSeed = IngredientCatalogSeed.forLanguage(from)
        val toSeed = IngredientCatalogSeed.forLanguage(to)

        val renamedAisles = mutableMapOf<String, String>()
        fromSeed.zip(toSeed).forEach { (old, new) ->
            if (old.first == new.first) return@forEach
            val category = ingredientCategoryDao.findByName(old.first) ?: return@forEach
            val target = ingredientCategoryDao.findByName(new.first)
            if (target == null) {
                ingredientCategoryDao.update(category.copy(name = new.first))
            } else {
                ingredientCatalogDao.moveToCategory(fromId = category.id, toId = target.id)
                ingredientCategoryDao.delete(category)
            }
            renamedAisles[category.name] = new.first
        }

        fromSeed.flatMap { it.second }.zip(toSeed.flatMap { it.second }).distinctBy { it.first }.forEach { (old, new) ->
            if (old == new) return@forEach
            val ingredient = ingredientCatalogDao.findByName(old) ?: return@forEach
            if (ingredientCatalogDao.findByName(new) == null) ingredientCatalogDao.update(ingredient.copy(name = new))
            else ingredientCatalogDao.delete(ingredient)
        }

        DefaultCatalog.categories(from).zip(DefaultCatalog.categories(to)).forEach { (old, new) ->
            if (old == new) return@forEach
            val category = categoryDao.findByName(old) ?: return@forEach
            val target = categoryDao.findByName(new)
            if (target == null) {
                categoryDao.update(category.copy(name = new))
            } else {
                categoryDao.moveRecipes(fromId = category.id, toId = target.id)
                categoryDao.delete(category)
            }
        }

        KitchenEquipment.defaults(from).zip(KitchenEquipment.defaults(to)).forEach { (old, new) ->
            if (old == new) return@forEach
            val utensil = utensilDao.findByName(old) ?: return@forEach
            val target = utensilDao.findByName(new)
            if (target == null) {
                utensilDao.update(utensil.copy(name = new))
            } else {
                utensilDao.moveRecipes(fromId = utensil.id, toId = target.id)
                utensilDao.delete(utensil)
            }
        }

        if (renamedAisles.isNotEmpty()) {
            shoppingStoreDao.observeAll().first().forEach { store ->
                val aisles = store.aisleOrder.split("\n").map(String::trim).filter(String::isNotEmpty)
                val translated = aisles.map { renamedAisles[it] ?: it }.distinct()
                if (translated != aisles) shoppingStoreDao.update(store.copy(aisleOrder = translated.joinToString("\n")))
            }
        }
    }

    internal suspend fun resolveIngredientCategoryId(name: String): Long {
        ingredientCategoryDao.findByName(name)?.let { return it.id }
        return ingredientCategoryDao.insert(IngredientCategoryEntity(name = name))
    }

    internal suspend fun resolveCategoryId(name: String): Long {
        val trimmed = name.trim()
        categoryDao.findByName(trimmed)?.let { return it.id }
        return categoryDao.insert(CategoryEntity(name = trimmed))
    }

    internal suspend fun resolveTagId(name: String): Long {
        val trimmed = name.trim()
        tagDao.findByName(trimmed)?.let { return it.id }
        return tagDao.insert(TagEntity(name = trimmed))
    }

    /**
     * Id of the equipment called [name], created if missing. A usual variant of a default
     * ("Airfryer") resolves to the default ("Freidora de aire" or "Air fryer", whichever exists).
     */
    internal suspend fun resolveUtensilId(name: String): Long {
        val trimmed = name.trim()
        utensilDao.findByName(trimmed)?.let { return it.id }
        KitchenEquipment.equivalents(trimmed).forEach { equivalent -> utensilDao.findByName(equivalent)?.let { return it.id } }
        val canonical = KitchenEquipment.canonical(trimmed, appLanguage())
        utensilDao.findByName(canonical)?.let { return it.id }
        return utensilDao.insert(UtensilEntity(name = canonical))
    }

    internal fun appLanguage(): String = if (L10n.locale().language == "es") "es" else "en"
}

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
