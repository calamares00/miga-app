package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import org.calamares.miga.data.local.entity.ShoppingStoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingStoreDao {
    @Query("SELECT * FROM shopping_stores ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ShoppingStoreEntity>>

    @Insert
    suspend fun insert(store: ShoppingStoreEntity): Long

    @Update
    suspend fun update(store: ShoppingStoreEntity)

    @Query("DELETE FROM shopping_stores WHERE id = :id")
    suspend fun delete(id: Long)
}
