package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.bmo00.miga.data.local.entity.UtensilEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UtensilDao {
    @Query("SELECT * FROM utensils ORDER BY name ASC")
    fun observeAll(): Flow<List<UtensilEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(utensil: UtensilEntity): Long

    @Query("SELECT * FROM utensils WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): UtensilEntity?

    @Query("SELECT * FROM utensils WHERE id = :id")
    suspend fun getOnce(id: Long): UtensilEntity?

    @Update
    suspend fun update(utensil: UtensilEntity)

    @Query("SELECT COUNT(*) FROM recipe_utensil_cross_ref WHERE utensilId = :id")
    suspend fun countRecipesUsing(id: Long): Int

    @Delete
    suspend fun delete(utensil: UtensilEntity)
}
