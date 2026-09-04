package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bmo00.miga.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getOnce(id: Long): CategoryEntity?

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM recipes WHERE categoryId = :id")
    suspend fun countRecipesUsing(id: Long): Int

    @Delete
    suspend fun delete(category: CategoryEntity)
}
