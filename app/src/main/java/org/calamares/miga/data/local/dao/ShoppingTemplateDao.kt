package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.calamares.miga.data.local.entity.ShoppingTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingTemplateDao {
    @Query("SELECT * FROM shopping_templates ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ShoppingTemplateEntity>>

    @Insert
    suspend fun insert(template: ShoppingTemplateEntity): Long

    @Query("DELETE FROM shopping_templates WHERE id = :id")
    suspend fun delete(id: Long)
}
