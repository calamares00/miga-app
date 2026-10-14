package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.calamares.miga.data.local.entity.ShoppingListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListsDao {
    @Query("SELECT * FROM shopping_lists WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ShoppingListEntity>>

    @Insert
    suspend fun insert(list: ShoppingListEntity): Long

    @Update
    suspend fun update(list: ShoppingListEntity)

    @Query("SELECT * FROM shopping_lists WHERE uid = :uid")
    suspend fun findByUid(uid: String): ShoppingListEntity?

    @Query("SELECT * FROM shopping_lists WHERE syncDirty = 1 ORDER BY updatedAt ASC")
    suspend fun getDirty(): List<ShoppingListEntity>

    /**
     * Only clears the flag if the list did not change again while it was being uploaded (same
     * updatedAt).
     */
    @Query("UPDATE shopping_lists SET syncDirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markSynced(uid: String, updatedAt: Long)

    @Query("DELETE FROM shopping_lists WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    @Query("DELETE FROM shopping_lists WHERE deletedAt IS NOT NULL AND syncDirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM shopping_lists WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones()

    @Query("UPDATE shopping_lists SET syncDirty = 1 WHERE deletedAt IS NULL")
    suspend fun markAllLiveDirty()
}
