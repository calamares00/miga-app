package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.calamares.miga.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

/** Number of unchecked items in a list. */
data class ShoppingListPendingCount(val listUid: String, val pending: Int)

/**
 * Shopping list items. Every read of the visible list excludes rows with
 * [ShoppingListItemEntity.deletedAt] (tombstones waiting to be uploaded), and every local write
 * marks the row as syncDirty with a new updatedAt. See RecipeRepository for the sync logic.
 */
@Dao
interface ShoppingListDao {

    @Query("SELECT * FROM shopping_list_items WHERE deletedAt IS NULL AND listUid = :listUid ORDER BY normalizedName ASC")
    fun observeAll(listUid: String): Flow<List<ShoppingListItemEntity>>

    @Query("SELECT listUid AS listUid, COUNT(*) AS pending FROM shopping_list_items WHERE deletedAt IS NULL AND checked = 0 GROUP BY listUid")
    fun observePendingCounts(): Flow<List<ShoppingListPendingCount>>

    /**
     * A row an addition can be merged into: same normalised name and same unit (SQLite's null-safe
     * IS also matches null with null) and a non-null quantity. The quantity being added is checked
     * by the repository.
     */
    @Query("SELECT * FROM shopping_list_items WHERE deletedAt IS NULL AND listUid = :listUid AND normalizedName = :normalizedName AND unit IS :unit AND quantity IS NOT NULL LIMIT 1")
    suspend fun findMergeable(listUid: String, normalizedName: String, unit: String?): ShoppingListItemEntity?

    /** Live item (no tombstone) with that normalised name, checked or not. */
    @Query("SELECT * FROM shopping_list_items WHERE deletedAt IS NULL AND listUid = :listUid AND normalizedName = :normalizedName LIMIT 1")
    suspend fun findLiveByName(listUid: String, normalizedName: String): ShoppingListItemEntity?

    @Insert
    suspend fun insert(item: ShoppingListItemEntity): Long

    @Update
    suspend fun update(item: ShoppingListItemEntity)

    @Query("UPDATE shopping_list_items SET checked = :checked, updatedAt = :now, updatedBy = :updatedBy, syncDirty = 1 WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean, now: Long, updatedBy: String?)

    @Query("UPDATE shopping_list_items SET deletedAt = :now, updatedAt = :now, syncDirty = 1 WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE shopping_list_items SET deletedAt = :now, updatedAt = :now, syncDirty = 1 WHERE deletedAt IS NULL AND listUid = :listUid")
    suspend fun softDeleteAll(listUid: String, now: Long)

    @Query("UPDATE shopping_list_items SET deletedAt = :now, updatedAt = :now, syncDirty = 1 WHERE deletedAt IS NULL AND listUid = :listUid AND checked = 1")
    suspend fun softDeleteChecked(listUid: String, now: Long)

    /**
     * Hard-deletes the items of a list removed on another device (the deletion already comes from
     * the server).
     */
    @Query("DELETE FROM shopping_list_items WHERE listUid = :listUid")
    suspend fun deleteByListUid(listUid: String)

    @Query("SELECT * FROM shopping_list_items WHERE uid = :uid")
    suspend fun findByUid(uid: String): ShoppingListItemEntity?

    @Query("SELECT * FROM shopping_list_items WHERE syncDirty = 1 ORDER BY updatedAt ASC")
    suspend fun getDirty(): List<ShoppingListItemEntity>

    /**
     * Only clears the flag if the row did not change again while it was being uploaded (same
     * updatedAt).
     */
    @Query("UPDATE shopping_list_items SET syncDirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markSynced(uid: String, updatedAt: Long)

    @Query("DELETE FROM shopping_list_items WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    /** Tombstones already uploaded (or without active sync) no longer need to be kept. */
    @Query("DELETE FROM shopping_list_items WHERE deletedAt IS NOT NULL AND syncDirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM shopping_list_items WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones()

    @Query("UPDATE shopping_list_items SET syncDirty = 1 WHERE deletedAt IS NULL")
    suspend fun markAllLiveDirty()
}
