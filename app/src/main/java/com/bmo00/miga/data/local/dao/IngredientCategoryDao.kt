package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bmo00.miga.data.local.entity.IngredientCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientCategoryDao {
    @Query("SELECT * FROM ingredient_categories ORDER BY name ASC")
    fun observeAll(): Flow<List<IngredientCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: IngredientCategoryEntity): Long

    @Query("SELECT * FROM ingredient_categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): IngredientCategoryEntity?

    @Query("SELECT * FROM ingredient_categories WHERE id = :id")
    suspend fun getOnce(id: Long): IngredientCategoryEntity?

    @Update
    suspend fun update(category: IngredientCategoryEntity)

    @Query("SELECT COUNT(*) FROM ingredient_catalog WHERE categoryId = :id")
    suspend fun countIngredientsUsing(id: Long): Int

    @Delete
    suspend fun delete(category: IngredientCategoryEntity)
}
