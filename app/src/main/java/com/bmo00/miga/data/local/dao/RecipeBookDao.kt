package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bmo00.miga.data.local.entity.RecipeBookEntity
import kotlinx.coroutines.flow.Flow

data class RecipeBookWithCount(
    @Embedded val book: RecipeBookEntity,
    val recipeCount: Int
)

@Dao
interface RecipeBookDao {

    @Query(
        """
        SELECT b.*, (SELECT COUNT(*) FROM recipes r WHERE r.recipeBookId = b.id) AS recipeCount
        FROM recipe_books b
        ORDER BY b.name ASC
        """
    )
    fun observeAllWithCounts(): Flow<List<RecipeBookWithCount>>

    @Query("SELECT * FROM recipe_books WHERE id = :id")
    fun observeOne(id: Long): Flow<RecipeBookEntity?>

    @Query("SELECT * FROM recipe_books WHERE id = :id")
    suspend fun getOnce(id: Long): RecipeBookEntity?

    @Query("SELECT * FROM recipe_books WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): RecipeBookEntity?

    @Query("SELECT COUNT(*) FROM recipes WHERE recipeBookId = :bookId")
    suspend fun countRecipes(bookId: Long): Int

    @Insert
    suspend fun insert(book: RecipeBookEntity): Long

    @Update
    suspend fun update(book: RecipeBookEntity)

    @Query("DELETE FROM recipe_books WHERE id = :id")
    suspend fun delete(id: Long)
}
