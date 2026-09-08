package com.bmo00.miga.data.repository

import androidx.room.withTransaction
import com.bmo00.miga.data.export.IngredientDto
import com.bmo00.miga.data.export.IngredientGroupDto
import com.bmo00.miga.data.export.RecipeExportDto
import com.bmo00.miga.data.export.StepGroupDto
import com.bmo00.miga.data.local.AppDatabase
import com.bmo00.miga.data.local.PhotoStorage
import com.bmo00.miga.data.local.entity.CategoryEntity
import com.bmo00.miga.data.local.entity.IngredientCatalogEntity
import com.bmo00.miga.data.local.entity.IngredientCategoryEntity
import com.bmo00.miga.data.local.entity.IngredientEntity
import com.bmo00.miga.data.local.entity.PendingSyncChangeEntity
import com.bmo00.miga.data.local.entity.RecipeBookEntity
import com.bmo00.miga.data.local.entity.RecipeEntity
import com.bmo00.miga.data.local.entity.RecipePhotoEntity
import com.bmo00.miga.data.local.entity.RecipeTagCrossRef
import com.bmo00.miga.data.local.entity.RecipeUtensilCrossRef
import com.bmo00.miga.data.local.entity.RecipeWithDetails
import com.bmo00.miga.data.local.entity.ShoppingListItemEntity
import com.bmo00.miga.data.local.entity.StepEntity
import com.bmo00.miga.data.local.entity.SyncChangeType
import com.bmo00.miga.data.local.entity.SyncConnectionEntity
import com.bmo00.miga.data.local.entity.SyncEntityType
import com.bmo00.miga.data.local.entity.TagEntity
import com.bmo00.miga.data.local.entity.UtensilEntity
import com.bmo00.miga.data.model.Difficulty
import com.bmo00.miga.data.model.HealthColorLevel
import com.bmo00.miga.data.model.HealthFingerprint
import com.bmo00.miga.data.model.HealthRating
import com.bmo00.miga.data.model.Ingredient
import com.bmo00.miga.data.model.IngredientCatalogItem
import com.bmo00.miga.data.model.IngredientGroup
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeBook
import com.bmo00.miga.data.model.RecipeBookDraft
import com.bmo00.miga.data.model.RecipeBookSummary
import com.bmo00.miga.data.model.RecipeDraft
import com.bmo00.miga.data.model.RecipePhoto
import com.bmo00.miga.data.model.ShoppingListGroup
import com.bmo00.miga.data.model.ShoppingListItem
import com.bmo00.miga.data.model.StepGroup
import com.bmo00.miga.data.model.SyncConnection
import com.bmo00.miga.data.model.UNCATEGORIZED_INGREDIENT_LABEL
import com.bmo00.miga.data.sync.BookSyncDto
import com.bmo00.miga.data.sync.PhotoMetaDto
import com.bmo00.miga.data.sync.RecipeSyncDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Se lanza al intentar borrar un libro de recetas que todavía tiene recetas dentro. */
class RecipeBookNotEmptyException(val recipeCount: Int) : Exception()

/** Resultado de [RecipeRepository.wipeUserRecipesAndBooks]. */
data class WipeResult(val bookCount: Int, val recipeCount: Int)

class RecipeRepository(private val db: AppDatabase) {

    private val recipeDao = db.recipeDao()
    private val categoryDao = db.categoryDao()
    private val tagDao = db.tagDao()
    private val utensilDao = db.utensilDao()
    private val recipeBookDao = db.recipeBookDao()
    private val ingredientCatalogDao = db.ingredientCatalogDao()
    private val ingredientCategoryDao = db.ingredientCategoryDao()
    private val shoppingListDao = db.shoppingListDao()
    private val syncConnectionDao = db.syncConnectionDao()
    private val pendingSyncChangeDao = db.pendingSyncChangeDao()

    fun observeRecipesForBook(bookId: Long): Flow<List<Recipe>> =
        recipeDao.observeAllWithDetailsForBook(bookId).map { list -> list.map { it.toDomain() } }

    /** Todas las recetas de todos los libros, usado en la búsqueda global desde la página principal. */
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

    suspend fun markCooked(id: Long) {
        recipeDao.incrementTimesCooked(id)
    }

    /** No-op si la receta pertenece a un libro-pack (ver [RecipeBook.isPack]): son de solo lectura. */
    suspend fun deleteRecipe(id: Long) {
        val recipe = recipeDao.getRecipeOnce(id) ?: return
        val book = recipeBookDao.getOnce(recipe.recipeBookId) ?: return
        if (book.packId != null) return
        recipeDao.deleteRecipe(id)
        book.syncConnectionId?.let { enqueueSyncChange(it, SyncEntityType.RECIPE, recipe.uid, SyncChangeType.DELETE) }
    }

    /** No-op si [newBookId] es un libro-pack: no se puede añadir contenido a uno (moverlo FUERA de un pack sí está permitido). */
    suspend fun moveRecipeToBook(recipeId: Long, newBookId: Long) {
        if (recipeBookDao.getOnce(newBookId)?.packId != null) return
        recipeDao.updateRecipeBook(recipeId, newBookId)
    }

    /** Defensa en profundidad equivalente a la de [saveRecipeBook]: no-op si el libro destino es un pack. */
    suspend fun saveRecipe(draft: RecipeDraft): Long = db.withTransaction {
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
                    updatedAt = now
                )
            )
        } else {
            val existing = recipeDao.getRecipeOnce(draft.id)
            // Si los ingredientes o pasos han cambiado desde el último análisis de salud, la
            // valoración cacheada ya no es válida para el contenido nuevo: se limpia para que se
            // vuelva a calcular la próxima vez que se abra la receta. Si no han cambiado, se
            // conserva tal cual (edición de notas/raciones/fotos/etc. no invalida nada).
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
                    healthAnalyzedAt = if (keepHealth) existing?.healthAnalyzedAt else null
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

        // Fotos: se reescriben por completo, pero conservando el uid de las que ya existían (por
        // uri) para que el motor de sincronización no las trate como fotos nuevas en cada guardado.
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

        // Utensilios
        recipeDao.deleteUtensilCrossRefs(recipeId)
        val utensilIds = draft.utensilNames.filter { it.isNotBlank() }.map { resolveUtensilId(it) }
        if (utensilIds.isNotEmpty()) {
            recipeDao.insertUtensilCrossRefs(utensilIds.map { RecipeUtensilCrossRef(recipeId, it) })
        }

        targetBook?.syncConnectionId?.let { connectionId ->
            recipeDao.getRecipeOnce(recipeId)?.let { saved ->
                enqueueSyncChange(connectionId, SyncEntityType.RECIPE, saved.uid, SyncChangeType.UPSERT)
                // Fotos añadidas/quitadas en este guardado (no las que ya estaban, esas no cambian):
                // el borrado de una foto suelta no pasa por deleteRecipe (que sí cascada en el
                // servidor), así que hace falta encolarlo aparte, con el uid de la receta como
                // parentUid porque la fila de la foto ya no existe para poder consultarlo después.
                val oldUids = existingPhotos.mapNotNull { it.uid }.toSet()
                val newUids = newPhotoEntities.map { it.uid }.toSet()
                (newUids - oldUids).forEach { photoUid ->
                    enqueueSyncChange(connectionId, SyncEntityType.PHOTO, photoUid, SyncChangeType.UPSERT, parentUid = saved.uid)
                }
                (oldUids - newUids).forEach { photoUid ->
                    enqueueSyncChange(connectionId, SyncEntityType.PHOTO, photoUid, SyncChangeType.DELETE, parentUid = saved.uid)
                }
            }
        }

        recipeId
    }

    suspend fun saveHealthRating(recipeId: Long, color: HealthColorLevel, description: String, fingerprint: String, analyzedAt: Long) {
        recipeDao.updateHealthRating(recipeId, color.name, description, fingerprint, analyzedAt)
    }

    /**
     * Huella de [ingredientGroups]+[stepGroups]: si cambia respecto a la guardada junto a una
     * valoración de salud, esa valoración ya no es válida para el contenido actual de la receta.
     * Delegado a [HealthFingerprint] (función pura, con sus propios tests unitarios).
     */
    fun computeHealthFingerprint(ingredientGroups: List<IngredientGroup>, stepGroups: List<StepGroup>): String =
        HealthFingerprint.compute(ingredientGroups, stepGroups)

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

    // --- Lista de la compra ---

    /** Lista persistente agrupada por categoría de ingrediente (ver ingredient_categories); sin categoría al final. */
    fun observeShoppingList(): Flow<List<ShoppingListGroup>> =
        combine(shoppingListDao.observeAll(), ingredientCatalogDao.observeAll(), ingredientCategoryDao.observeAll()) { items, catalog, categories ->
            val categoryNameById = categories.associateBy({ it.id }, { it.name })
            val categoryIdByIngredientName = catalog.associate { it.name.trim().lowercase() to it.categoryId }
            items.map { entity ->
                val categoryId = categoryIdByIngredientName[entity.normalizedName]
                val categoryName = categoryId?.let { categoryNameById[it] } ?: UNCATEGORIZED_INGREDIENT_LABEL
                ShoppingListItem(entity.id, entity.name, entity.quantity, entity.unit, entity.checked, categoryName)
            }
                .groupBy { it.categoryName }
                .toSortedMap(compareBy { if (it == UNCATEGORIZED_INGREDIENT_LABEL) "￿" else it.lowercase() })
                .map { (category, groupItems) -> ShoppingListGroup(category, groupItems.sortedBy { it.name.lowercase() }) }
        }

    /**
     * Añade ingredientes (de una receta, o de varias) a la lista, fusionando por nombre
     * normalizado + unidad cuando ambos lados tienen cantidad; si no, inserta una fila nueva.
     */
    suspend fun addIngredientsToShoppingList(ingredients: List<Ingredient>) = db.withTransaction {
        val now = System.currentTimeMillis()
        ingredients.forEach { ingredient ->
            val trimmedName = ingredient.name.trim()
            if (trimmedName.isEmpty()) return@forEach
            val normalized = trimmedName.lowercase()
            val trimmedUnit = ingredient.unit?.trim()?.takeIf { it.isNotBlank() }
            val existing = if (ingredient.quantity != null) shoppingListDao.findMergeable(normalized, trimmedUnit) else null
            if (existing != null) {
                shoppingListDao.update(existing.copy(quantity = existing.quantity!! + ingredient.quantity!!))
            } else {
                shoppingListDao.insert(
                    ShoppingListItemEntity(name = trimmedName, normalizedName = normalized, quantity = ingredient.quantity, unit = trimmedUnit, createdAt = now)
                )
            }
        }
    }

    /** Artículo manual sin receta de origen (p.ej. "papel de aluminio"); pasa por la misma fusión que los de receta. */
    suspend fun addManualShoppingListItem(name: String, quantity: Double?, unit: String?) {
        addIngredientsToShoppingList(listOf(Ingredient(name, quantity, unit)))
    }

    suspend fun setShoppingListItemChecked(id: Long, checked: Boolean) {
        shoppingListDao.setChecked(id, checked)
    }

    suspend fun deleteShoppingListItem(id: Long) {
        shoppingListDao.delete(id)
    }

    suspend fun clearShoppingList() {
        shoppingListDao.clearAll()
    }

    suspend fun clearCheckedShoppingListItems() {
        shoppingListDao.clearChecked()
    }

    // --- Libros de recetas ---

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

    /** Todos los libros, usados al exportar toda la app (para incluir sus portadas en el ZIP). */
    suspend fun getAllRecipeBooksOnce(): List<RecipeBook> =
        recipeBookDao.observeAllWithCounts().first().map { it.book.toDomain() }

    suspend fun findRecipeBookByPackId(packId: String): RecipeBook? =
        recipeBookDao.findByPackId(packId)?.toDomain()

    /**
     * Defensa en profundidad: un libro-pack es de solo lectura (ver [RecipeBook.isPack]); la UI ya
     * bloquea su edición, pero si algo la saltase esto evita que se sobrescriba sin querer en vez
     * de fallar de forma confusa. No-op silencioso, igual que pide el plan de la feature.
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
            existing?.syncConnectionId?.let { enqueueSyncChange(it, SyncEntityType.BOOK, uid, SyncChangeType.UPSERT) }
            draft.id
        }
    }

    /** Lanza [RecipeBookNotEmptyException] si el libro todavía contiene recetas. */
    suspend fun deleteRecipeBook(id: Long) {
        val count = recipeBookDao.countRecipes(id)
        if (count > 0) throw RecipeBookNotEmptyException(count)
        val book = recipeBookDao.getOnce(id)
        recipeBookDao.delete(id)
        book?.syncConnectionId?.let { enqueueSyncChange(it, SyncEntityType.BOOK, book.uid, SyncChangeType.DELETE) }
    }

    /**
     * Borra todos los libros propios del usuario (no los packs instalados, de solo lectura) junto
     * con sus recetas y las fotos asociadas (portadas de libro + fotos de receta). Pensado para
     * dejar la app en estado limpio justo antes de importar una copia de seguridad completa.
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
     * Usado al importar una copia de seguridad: busca un libro por nombre o lo crea si no existe.
     * [uid]/[coverPhotoUri] solo se usan al crear un libro nuevo; si ya existe uno con ese nombre
     * se reutiliza tal cual, sin tocar su portada (igual que con la recategorización de ingredientes).
     */
    suspend fun getOrCreateRecipeBookIdByName(name: String, uid: String? = null, coverPhotoUri: String? = null): Long {
        val trimmed = name.trim().ifBlank { "Sin nombre" }
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

    // --- Packs de recetas descargables ---

    /**
     * Instala o actualiza un pack: busca el libro por [packId] (nunca por nombre, para no chocar
     * con un libro propio homónimo); crea o actualiza sus recetas por [RecipeExportDto.uid]
     * (conservando isFavorite/timesCooked/createdAt si ya existían) y borra las que ya no estén
     * en esta versión. A diferencia de [saveRecipe]/[saveRecipeBook] no pasa por sus guardas de
     * solo-lectura: esta es la única vía legítima de escribir en un libro-pack.
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
            // Solo cuenta como "ya existía" si sigue en este mismo libro: si el usuario la movió a
            // otro libro (acción permitida en una receta de pack), la próxima actualización crea
            // una copia nueva en el pack en vez de tocar la que el usuario movió.
            val existingRecipe = recipeDao.findByUid(dto.uid)?.takeIf { it.recipeBookId == bookId }
            val recipeId = if (existingRecipe == null) {
                recipeDao.insertRecipe(
                    RecipeEntity(
                        uid = dto.uid,
                        name = dto.name.trim(),
                        categoryId = categoryId,
                        recipeBookId = bookId,
                        difficulty = dto.difficulty,
                        prepTimeMinutes = dto.prepTimeMinutes,
                        cookTimeMinutes = dto.cookTimeMinutes,
                        servings = dto.servings,
                        notes = dto.notes,
                        source = dto.source,
                        isFavorite = false,
                        timesCooked = 0,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                recipeDao.updateRecipe(
                    existingRecipe.copy(
                        name = dto.name.trim(),
                        categoryId = categoryId,
                        difficulty = dto.difficulty,
                        prepTimeMinutes = dto.prepTimeMinutes,
                        cookTimeMinutes = dto.cookTimeMinutes,
                        servings = dto.servings,
                        notes = dto.notes,
                        source = dto.source,
                        updatedAt = now
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
            val utensilIds = dto.utensils.filter { it.isNotBlank() }.map { resolveUtensilId(it) }
            if (utensilIds.isNotEmpty()) recipeDao.insertUtensilCrossRefs(utensilIds.map { RecipeUtensilCrossRef(recipeId, it) })

            if (dto.health != null) {
                val colorLevel = runCatching { HealthColorLevel.valueOf(dto.health.colorLevel) }.getOrDefault(HealthColorLevel.YELLOW)
                recipeDao.updateHealthRating(recipeId, colorLevel.name, dto.health.description, dto.health.fingerprint, dto.health.analyzedAt)
            }
        }

        // El pack manda en su propio contenido: una receta que ya no está en esta versión se borra.
        val currentUids = recipes.map { it.uid }
        if (currentUids.isEmpty()) recipeDao.deleteAllForBook(bookId) else recipeDao.deleteRecipesNotInUidSet(bookId, currentUids)

        bookId
    }

    /** Desinstala un pack: borra sus recetas y el libro sin pasar por el guard de [deleteRecipeBook] (que bloquearía siempre un libro no vacío). */
    suspend fun uninstallPack(bookId: Long) = db.withTransaction {
        recipeDao.deleteAllForBook(bookId)
        recipeBookDao.delete(bookId)
    }

    // --- Servidor de sincronización (namespaces self-hosted) ---

    fun observeSyncConnections(): Flow<List<SyncConnection>> =
        syncConnectionDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getSyncConnectionOnce(id: Long): SyncConnection? = syncConnectionDao.getOnce(id)?.toDomain()

    suspend fun addSyncConnection(label: String, serverUrl: String, namespaceId: String, accessToken: String): Long =
        syncConnectionDao.insert(
            SyncConnectionEntity(
                label = label.trim(),
                serverUrl = serverUrl.trim().trimEnd('/'),
                namespaceId = namespaceId.trim(),
                accessToken = accessToken.trim(),
                createdAt = System.currentTimeMillis()
            )
        )

    /** Quita la conexión: sus libros pasan a ser locales (no se borra nada de su contenido) y se
     *  descarta cualquier cambio pendiente de subir para ella. */
    suspend fun removeSyncConnection(id: Long) = db.withTransaction {
        recipeBookDao.clearSyncConnection(id)
        pendingSyncChangeDao.clearAllForConnection(id)
        syncConnectionDao.getOnce(id)?.let { syncConnectionDao.delete(it) }
    }

    suspend fun markSyncSuccess(connectionId: Long, revision: Long) =
        syncConnectionDao.markSynced(connectionId, revision, System.currentTimeMillis())

    suspend fun markSyncError(connectionId: Long, reason: String) = syncConnectionDao.markError(connectionId, reason)

    /** Vincula un libro propio existente a una conexión: pasa a sincronizarse (lectura-escritura,
     *  no de solo lectura como un pack) y se encola para subirse entero en el próximo sync. */
    suspend fun linkBookToSyncConnection(bookId: Long, connectionId: Long) = db.withTransaction {
        val book = recipeBookDao.getOnce(bookId) ?: return@withTransaction
        recipeBookDao.update(book.copy(syncConnectionId = connectionId, updatedAt = System.currentTimeMillis()))
        enqueueSyncChange(connectionId, SyncEntityType.BOOK, book.uid, SyncChangeType.UPSERT)
        recipeDao.getAllWithDetailsForBookOnce(bookId).forEach { details ->
            enqueueSyncChange(connectionId, SyncEntityType.RECIPE, details.recipe.uid, SyncChangeType.UPSERT)
        }
    }

    /** Deja de sincronizar un libro: pasa a ser local, sin borrar nada de su contenido ni del servidor. */
    suspend fun unlinkBookFromSyncConnection(bookId: Long) {
        val book = recipeBookDao.getOnce(bookId) ?: return
        recipeBookDao.update(book.copy(syncConnectionId = null))
    }

    // --- Outbox: encolar cambios locales pendientes de subir ---

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

    // --- Construir el DTO de sincronización de un libro/receta local (para subirlo) ---

    suspend fun getRecipeBookSyncDto(bookId: Long): BookSyncDto? {
        val book = recipeBookDao.getOnce(bookId) ?: return null
        return BookSyncDto(uid = book.uid, name = book.name, hasCoverPhoto = book.coverPhotoUri != null, updatedAt = book.updatedAt)
    }

    suspend fun getRecipeBookSyncDtoByUid(uid: String): BookSyncDto? {
        val book = recipeBookDao.findByUid(uid) ?: return null
        return getRecipeBookSyncDto(book.id)
    }

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
            updatedAt = details.recipe.updatedAt
        )
    }

    /** Datos necesarios para subir una foto suelta ya existente localmente (ver [SyncEngine.pushPhotoChange]). */
    data class PhotoPushInfo(val uri: String, val recipeUid: String, val isCover: Boolean, val position: Int)

    suspend fun getPhotoPushInfo(photoUid: String): PhotoPushInfo? {
        val photo = recipeDao.findPhotoByUid(photoUid) ?: return null
        val recipe = recipeDao.getRecipeOnce(photo.recipeId) ?: return null
        return PhotoPushInfo(photo.uri, recipe.uid, photo.isCover, photo.position)
    }

    /** Usado antes de descargar una foto remota, para no descargarla (y guardar un fichero
     *  huérfano) si ya existe localmente con ese uid. */
    suspend fun hasLocalPhoto(photoUid: String): Boolean = recipeDao.findPhotoByUid(photoUid) != null

    // --- Aplicar cambios recibidos del servidor (usado por SyncEngine) ---
    // Los libros se procesan en dos pasadas (altas primero, bajas al final) para no chocar con la
    // restricción de clave foránea RESTRICT de recipes.recipeBookId: una receta sincronizada nueva
    // necesita que su libro ya exista localmente, y un libro no se puede borrar mientras todavía
    // tenga recetas locales (por eso las recetas también se procesan antes que los borrados de libro).

    /** Devuelve el id local del libro tras aplicar el alta/edición, o null si se ha ignorado (la
     *  copia local era más reciente, o es un tombstone). */
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
                    difficulty = dto.difficulty,
                    prepTimeMinutes = dto.prepTimeMinutes,
                    cookTimeMinutes = dto.cookTimeMinutes,
                    servings = dto.servings,
                    notes = dto.notes,
                    source = dto.source,
                    isFavorite = dto.isFavorite,
                    timesCooked = 0,
                    createdAt = dto.updatedAt,
                    updatedAt = dto.updatedAt
                )
            )
        } else {
            recipeDao.updateRecipe(
                existing.copy(
                    name = dto.name.trim(),
                    categoryId = categoryId,
                    recipeBookId = bookId,
                    difficulty = dto.difficulty,
                    prepTimeMinutes = dto.prepTimeMinutes,
                    cookTimeMinutes = dto.cookTimeMinutes,
                    servings = dto.servings,
                    notes = dto.notes,
                    source = dto.source,
                    isFavorite = dto.isFavorite,
                    updatedAt = dto.updatedAt,
                    healthColor = if (keepHealth) existing.healthColor else null,
                    healthDescription = if (keepHealth) existing.healthDescription else null,
                    healthFingerprint = if (keepHealth) existing.healthFingerprint else null,
                    healthAnalyzedAt = if (keepHealth) existing.healthAnalyzedAt else null
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
        val utensilIds = dto.utensils.filter { it.isNotBlank() }.map { resolveUtensilId(it) }
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
     * Aplica el alta/edición de una foto suelta ya descargada (ver [SyncEngine]): [localUri] es la
     * ruta donde el motor de sincronización ya ha guardado sus bytes con [PhotoStorage]. Sin
     * comparación de "última escritura gana" (las fotos no se editan en el sitio, solo se añaden o
     * se quitan): si ya existe una fila local con ese uid, se deja tal cual salvo posición/portada.
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

    /** Borra la fila local de una foto ya borrada en el servidor y devuelve su [RecipePhotoEntity.uri]
     *  para que el motor de sincronización borre también el fichero físico; null si no había nada que borrar. */
    suspend fun applyRemotePhotoDeletion(dto: PhotoMetaDto): String? {
        if (dto.deletedAt == null) return null
        val existing = recipeDao.findPhotoByUid(dto.uid) ?: return null
        recipeDao.deletePhotoByUid(dto.uid)
        return existing.uri
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

fun RecipeBookEntity.toDomain() = RecipeBook(id, uid, name, coverPhotoUri, packId, packVersion, syncConnectionId)

fun SyncConnectionEntity.toDomain() = SyncConnection(id, label, serverUrl, namespaceId, accessToken, lastSyncedRevision, lastSyncedAt, lastSyncError)

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
        utensils = utensils.map { it.name }.sorted(),
        healthRating = recipe.healthColor?.let { colorName ->
            HealthRating(
                color = runCatching { HealthColorLevel.valueOf(colorName) }.getOrDefault(HealthColorLevel.YELLOW),
                description = recipe.healthDescription.orEmpty(),
                fingerprint = recipe.healthFingerprint.orEmpty(),
                analyzedAt = recipe.healthAnalyzedAt ?: 0L
            )
        }
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
