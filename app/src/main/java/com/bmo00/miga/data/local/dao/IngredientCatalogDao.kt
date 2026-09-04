package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bmo00.miga.data.local.entity.IngredientCatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientCatalogDao {
    @Query("SELECT * FROM ingredient_catalog ORDER BY name ASC")
    fun observeAll(): Flow<List<IngredientCatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: IngredientCatalogEntity): Long

    @Query("SELECT * FROM ingredient_catalog WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): IngredientCatalogEntity?

    @Query("SELECT * FROM ingredient_catalog WHERE id = :id")
    suspend fun getOnce(id: Long): IngredientCatalogEntity?

    @Update
    suspend fun update(entity: IngredientCatalogEntity)

    @Query("UPDATE ingredient_catalog SET categoryId = :categoryId WHERE id = :id")
    suspend fun updateCategory(id: Long, categoryId: Long?)

    @Delete
    suspend fun delete(entity: IngredientCatalogEntity)
}
