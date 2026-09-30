package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bmo00.miga.data.local.entity.SyncConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConnectionDao {
    @Query("SELECT * FROM sync_connections ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SyncConnectionEntity>>

    @Query("SELECT * FROM sync_connections ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<SyncConnectionEntity>

    @Query("SELECT * FROM sync_connections WHERE id = :id")
    suspend fun getOnce(id: Long): SyncConnectionEntity?

    @Insert
    suspend fun insert(connection: SyncConnectionEntity): Long

    @Update
    suspend fun update(connection: SyncConnectionEntity)

    @Query("UPDATE sync_connections SET lastSyncedRevision = :revision, lastSyncedAt = :syncedAt, lastSyncError = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, revision: Long, syncedAt: Long)

    @Query("UPDATE sync_connections SET lastSyncError = :reason WHERE id = :id")
    suspend fun markError(id: Long, reason: String)

    @Query("SELECT * FROM sync_connections WHERE syncShopping = 1 LIMIT 1")
    suspend fun getShoppingSyncConnection(): SyncConnectionEntity?

    @Query("UPDATE sync_connections SET syncShopping = 0, shoppingPulled = 0")
    suspend fun clearShoppingSync()

    @Query("UPDATE sync_connections SET syncShopping = 1, shoppingPulled = 0 WHERE id = :id")
    suspend fun enableShoppingSync(id: Long)

    @Query("UPDATE sync_connections SET shoppingPulled = 1 WHERE id = :id")
    suspend fun markShoppingPulled(id: Long)

    @Delete
    suspend fun delete(connection: SyncConnectionEntity)
}
