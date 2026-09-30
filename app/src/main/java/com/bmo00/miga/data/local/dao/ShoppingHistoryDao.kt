package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bmo00.miga.data.local.entity.ShoppingHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingHistoryDao {

    @Query("SELECT * FROM shopping_history ORDER BY uses DESC, lastUsedAt DESC LIMIT 200")
    fun observeAll(): Flow<List<ShoppingHistoryEntity>>

    @Query("SELECT * FROM shopping_history WHERE normalizedName = :normalizedName")
    suspend fun find(normalizedName: String): ShoppingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ShoppingHistoryEntity)
}
