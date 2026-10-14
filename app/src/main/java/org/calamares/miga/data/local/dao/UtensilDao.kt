package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.calamares.miga.data.local.entity.UtensilEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UtensilDao {
    @Query("SELECT * FROM utensils ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<UtensilEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(utensil: UtensilEntity): Long

    @Query("SELECT * FROM utensils WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): UtensilEntity?

    @Query("SELECT * FROM utensils")
    suspend fun getAllOnce(): List<UtensilEntity>

    /** Points the recipes of [fromId] to [toId]; the ones that already had both keep a single link. */
    @Transaction
    suspend fun moveRecipes(fromId: Long, toId: Long) {
        moveCrossRefs(fromId, toId)
        deleteCrossRefs(fromId)
    }

    @Query("UPDATE OR IGNORE recipe_utensil_cross_ref SET utensilId = :toId WHERE utensilId = :fromId")
    suspend fun moveCrossRefs(fromId: Long, toId: Long)

    @Query("DELETE FROM recipe_utensil_cross_ref WHERE utensilId = :id")
    suspend fun deleteCrossRefs(id: Long)

    @Query("SELECT * FROM utensils WHERE id = :id")
    suspend fun getOnce(id: Long): UtensilEntity?

    @Update
    suspend fun update(utensil: UtensilEntity)

    @Query("SELECT COUNT(*) FROM recipe_utensil_cross_ref WHERE utensilId = :id")
    suspend fun countRecipesUsing(id: Long): Int

    @Delete
    suspend fun delete(utensil: UtensilEntity)
}
