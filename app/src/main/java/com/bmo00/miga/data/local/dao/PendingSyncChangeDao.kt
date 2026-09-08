package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bmo00.miga.data.local.entity.PendingSyncChangeEntity

@Dao
interface PendingSyncChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(change: PendingSyncChangeEntity)

    @Query("SELECT * FROM pending_sync_changes WHERE syncConnectionId = :syncConnectionId ORDER BY createdAt ASC")
    suspend fun getPendingForConnection(syncConnectionId: Long): List<PendingSyncChangeEntity>

    @Query("DELETE FROM pending_sync_changes WHERE id = :id")
    suspend fun clear(id: Long)

    @Query("DELETE FROM pending_sync_changes WHERE syncConnectionId = :syncConnectionId")
    suspend fun clearAllForConnection(syncConnectionId: Long)
}
