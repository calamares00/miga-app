package org.calamares.miga.data.repository

import androidx.room.withTransaction
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.entity.IngredientCatalogEntity
import org.calamares.miga.data.local.entity.ShoppingHistoryEntity
import org.calamares.miga.data.local.entity.ShoppingListEntity
import org.calamares.miga.data.local.entity.ShoppingListItemEntity
import org.calamares.miga.data.local.entity.ShoppingStoreEntity
import org.calamares.miga.data.local.entity.ShoppingTemplateEntity
import org.calamares.miga.data.model.Ingredient
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
import org.calamares.miga.data.model.UNCATEGORIZED_INGREDIENT_LABEL
import org.calamares.miga.data.sync.ShoppingItemSyncDto
import org.calamares.miga.data.sync.ShoppingListSyncDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * The shopping lists: items, history, stores, templates and the lists shared through a sync server.
 * Reached through [RecipeRepository.shopping].
 */
class ShoppingRepository internal constructor(
    private val db: AppDatabase,
    private val recipeRepository: RecipeRepository,
    private val shoppingContext: ShoppingContext,
    private val onSyncChangeEnqueued: (connectionId: Long) -> Unit
) {

    private val ingredientCatalogDao = db.ingredientCatalogDao()
    private val ingredientCategoryDao = db.ingredientCategoryDao()
    private val shoppingListDao = db.shoppingListDao()
    private val shoppingHistoryDao = db.shoppingHistoryDao()
    private val shoppingTemplateDao = db.shoppingTemplateDao()
    private val shoppingStoreDao = db.shoppingStoreDao()
    private val shoppingListsDao = db.shoppingListsDao()
    private val syncConnectionDao = db.syncConnectionDao()

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

    /**
     * Edits an item's name and quantity. [aisle] (an ingredient category name, or null for none) is
     * remembered in the ingredient catalogue for that name, so the item and later ones with the
     * same name are grouped there.
     */
    suspend fun updateShoppingListItem(id: Long, name: String, quantity: Double?, unit: String?, aisle: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        db.withTransaction {
            shoppingListDao.updateDetails(
                id = id,
                name = trimmed,
                normalizedName = trimmed.lowercase(),
                quantity = quantity,
                unit = unit?.trim()?.takeIf { it.isNotEmpty() },
                now = System.currentTimeMillis(),
                updatedBy = currentShoppingAuthor()
            )
            val categoryId = aisle?.let { recipeRepository.resolveIngredientCategoryId(it) }
            val existing = ingredientCatalogDao.observeAll().first().firstOrNull { it.name.trim().lowercase() == trimmed.lowercase() }
            if (existing != null) {
                if (existing.categoryId != categoryId) ingredientCatalogDao.updateCategory(existing.id, categoryId)
            } else if (categoryId != null) {
                ingredientCatalogDao.insert(IngredientCatalogEntity(name = trimmed, categoryId = categoryId))
            }
        }
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
    internal suspend fun purgeShoppingTombstonesIfNotShared() {
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
}
