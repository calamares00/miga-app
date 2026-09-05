package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bmo00.miga.data.local.entity.IngredientEntity
import com.bmo00.miga.data.local.entity.RecipeEntity
import com.bmo00.miga.data.local.entity.RecipePhotoEntity
import com.bmo00.miga.data.local.entity.RecipeTagCrossRef
import com.bmo00.miga.data.local.entity.RecipeUtensilCrossRef
import com.bmo00.miga.data.local.entity.RecipeWithDetails
import com.bmo00.miga.data.local.entity.StepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun observeAllWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE recipeBookId = :bookId ORDER BY name ASC")
    fun observeAllWithDetailsForBook(bookId: Long): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes")
    suspend fun getAllWithDetailsOnce(): List<RecipeWithDetails>

    @Transaction
    @Query("SELECT * FROM recipes WHERE recipeBookId = :bookId ORDER BY name ASC")
    suspend fun getAllWithDetailsForBookOnce(bookId: Long): List<RecipeWithDetails>

    @Query("UPDATE recipes SET recipeBookId = :newBookId WHERE id = :id")
    suspend fun updateRecipeBook(id: Long, newBookId: Long)

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipe(id: Long)

    @Insert
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: Long)

    @Insert
    suspend fun insertSteps(items: List<StepEntity>)

    @Query("DELETE FROM steps WHERE recipeId = :recipeId")
    suspend fun deleteSteps(recipeId: Long)

    @Insert
    suspend fun insertPhotos(items: List<RecipePhotoEntity>)

    @Query("DELETE FROM recipe_photos WHERE recipeId = :recipeId")
    suspend fun deletePhotos(recipeId: Long)

    @Insert
    suspend fun insertTagCrossRefs(items: List<RecipeTagCrossRef>)

    @Query("DELETE FROM recipe_tag_cross_ref WHERE recipeId = :recipeId")
    suspend fun deleteTagCrossRefs(recipeId: Long)

    @Insert
    suspend fun insertUtensilCrossRefs(items: List<RecipeUtensilCrossRef>)

    @Query("DELETE FROM recipe_utensil_cross_ref WHERE recipeId = :recipeId")
    suspend fun deleteUtensilCrossRefs(recipeId: Long)

    @Query("UPDATE recipes SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE recipes SET timesCooked = timesCooked + 1 WHERE id = :id")
    suspend fun incrementTimesCooked(id: Long)

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeOnce(id: Long): RecipeEntity?

    @Query(
        "UPDATE recipes SET healthColor = :color, healthDescription = :description, " +
            "healthFingerprint = :fingerprint, healthAnalyzedAt = :analyzedAt WHERE id = :id"
    )
    suspend fun updateHealthRating(id: Long, color: String?, description: String?, fingerprint: String?, analyzedAt: Long?)
}
