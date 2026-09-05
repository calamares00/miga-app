package com.bmo00.miga.data.repository

import androidx.room.withTransaction
import com.bmo00.miga.data.export.RecipeExportDto
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
import com.bmo00.miga.data.model.HealthColorLevel
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
import com.bmo00.miga.data.model.StepGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

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
        if (recipeBookDao.getOnce(recipe.recipeBookId)?.packId != null) return
        recipeDao.deleteRecipe(id)
    }

    /** No-op si [newBookId] es un libro-pack: no se puede añadir contenido a uno (moverlo FUERA de un pack sí está permitido). */
    suspend fun moveRecipeToBook(recipeId: Long, newBookId: Long) {
        if (recipeBookDao.getOnce(newBookId)?.packId != null) return
        recipeDao.updateRecipeBook(recipeId, newBookId)
    }

    /** Defensa en profundidad equivalente a la de [saveRecipeBook]: no-op si el libro destino es un pack. */
    suspend fun saveRecipe(draft: RecipeDraft): Long = db.withTransaction {
        val targetBookId = if (draft.id == 0L) draft.recipeBookId else recipeDao.getRecipeOnce(draft.id)?.recipeBookId ?: draft.recipeBookId
        if (recipeBookDao.getOnce(targetBookId)?.packId != null) return@withTransaction draft.id

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

    suspend fun saveHealthRating(recipeId: Long, color: HealthColorLevel, description: String, fingerprint: String, analyzedAt: Long) {
        recipeDao.updateHealthRating(recipeId, color.name, description, fingerprint, analyzedAt)
    }

    /**
     * Huella de [ingredientGroups]+[stepGroups]: si cambia respecto a la guardada junto a una
     * valoración de salud, esa valoración ya no es válida para el contenido actual de la receta.
     */
    fun computeHealthFingerprint(ingredientGroups: List<IngredientGroup>, stepGroups: List<StepGroup>): String {
        val canonical = buildString {
            ingredientGroups.forEach { group ->
                append(group.name.orEmpty()).append('|')
                group.ingredients.forEach { ingredient ->
                    append(ingredient.name).append(',').append(ingredient.quantity).append(',').append(ingredient.unit.orEmpty()).append(';')
                }
            }
            append("##")
            stepGroups.forEach { group ->
                append(group.name.orEmpty()).append('|')
                group.instructions.forEach { append(it).append(';') }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
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
            list.map { RecipeBookSummary(it.book.id, it.book.name, it.book.coverPhotoUri, it.recipeCount, it.book.packId, it.book.packVersion) }
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
        return if (draft.id == 0L) {
            recipeBookDao.insert(
                RecipeBookEntity(
                    uid = draft.uid ?: UUID.randomUUID().toString(),
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
                    uid = existing?.uid ?: draft.uid ?: UUID.randomUUID().toString(),
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

fun RecipeBookEntity.toDomain() = RecipeBook(id, uid, name, coverPhotoUri, packId, packVersion)

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
