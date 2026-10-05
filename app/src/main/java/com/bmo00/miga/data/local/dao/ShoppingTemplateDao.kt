package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bmo00.miga.data.local.entity.ShoppingTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingTemplateDao {
    @Query("SELECT * FROM shopping_templates ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ShoppingTemplateEntity>>

    @Insert
    suspend fun insert(template: ShoppingTemplateEntity): Long

    @Query("SELECT * FROM shopping_templates WHERE id = :id")
    suspend fun get(id: Long): ShoppingTemplateEntity?

    @Query("UPDATE shopping_templates SET body = :body WHERE id = :id")
    suspend fun setBody(id: Long, body: String)

    @Query("UPDATE shopping_templates SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM shopping_templates WHERE id = :id")
    suspend fun delete(id: Long)
}
