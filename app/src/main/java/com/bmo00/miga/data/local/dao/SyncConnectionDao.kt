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

    @Delete
    suspend fun delete(connection: SyncConnectionEntity)
}
