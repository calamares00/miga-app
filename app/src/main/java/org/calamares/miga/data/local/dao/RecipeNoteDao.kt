package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.calamares.miga.data.local.entity.RecipeNoteEntity

@Dao
interface RecipeNoteDao {

    /** Newest first. */
    @Query("SELECT * FROM recipe_journal WHERE recipeId = :recipeId ORDER BY createdAt DESC, id DESC")
    fun observeForRecipe(recipeId: Long): Flow<List<RecipeNoteEntity>>

    @Query("SELECT * FROM recipe_journal WHERE recipeId = :recipeId ORDER BY createdAt DESC, id DESC")
    suspend fun getForRecipe(recipeId: Long): List<RecipeNoteEntity>

    @Insert
    suspend fun insert(note: RecipeNoteEntity): Long

    @Query("UPDATE recipe_journal SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("DELETE FROM recipe_journal WHERE id = :id")
    suspend fun delete(id: Long)
}
