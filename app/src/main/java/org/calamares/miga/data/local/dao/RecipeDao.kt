package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.calamares.miga.data.local.entity.IngredientEntity
import org.calamares.miga.data.local.entity.RecipeEntity
import org.calamares.miga.data.local.entity.RecipePhotoEntity
import org.calamares.miga.data.local.entity.RecipeTagCrossRef
import org.calamares.miga.data.local.entity.RecipeUtensilCrossRef
import org.calamares.miga.data.local.entity.RecipeWithDetails
import org.calamares.miga.data.local.entity.StepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE ASC")
    fun observeAllWithDetails(): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE recipeBookId = :bookId ORDER BY name COLLATE NOCASE ASC")
    fun observeAllWithDetailsForBook(bookId: Long): Flow<List<RecipeWithDetails>>

    @Transaction
    @Query("SELECT * FROM recipes")
    suspend fun getAllWithDetailsOnce(): List<RecipeWithDetails>

    @Transaction
    @Query("SELECT * FROM recipes WHERE recipeBookId = :bookId ORDER BY name COLLATE NOCASE ASC")
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

    /**
     * Read before deleting and reinserting a recipe's photos on save, so photos that already
     * existed (same uri) keep their uid.
     */
    @Query("SELECT * FROM recipe_photos WHERE recipeId = :recipeId")
    suspend fun getPhotosOnce(recipeId: Long): List<RecipePhotoEntity>

    /** A cover photo of the user's (the newest), to preview the photo frames in Settings. */
    @Query("SELECT uri FROM recipe_photos ORDER BY isCover DESC, id DESC LIMIT 1")
    fun observeSamplePhotoUri(): Flow<String?>

    @Query("SELECT * FROM recipe_photos WHERE uid = :uid LIMIT 1")
    suspend fun findPhotoByUid(uid: String): RecipePhotoEntity?

    @Query("DELETE FROM recipe_photos WHERE uid = :uid")
    suspend fun deletePhotoByUid(uid: String)

    @Query("UPDATE recipe_photos SET isCover = :isCover, position = :position WHERE uid = :uid")
    suspend fun updatePhotoMetaByUid(uid: String, isCover: Boolean, position: Int)

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

    @Query("UPDATE recipes SET rating = :rating WHERE id = :id")
    suspend fun setRating(id: Long, rating: Int?)

    @Query("UPDATE recipes SET timesCooked = timesCooked + 1 WHERE id = :id")
    suspend fun incrementTimesCooked(id: Long)

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeOnce(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE uid = :uid LIMIT 1")
    suspend fun findByUid(uid: String): RecipeEntity?

    @Query("DELETE FROM recipes WHERE recipeBookId = :bookId AND uid NOT IN (:keepUids)")
    suspend fun deleteRecipesNotInUidSet(bookId: Long, keepUids: List<String>)

    /**
     * Gives a uid to photos created before sync existed (the v7 -> v8 migration added the column as
     * NULL); without it a photo cannot be uploaded. Idempotent.
     */
    @Query("UPDATE recipe_photos SET uid = lower(hex(randomblob(16))) WHERE uid IS NULL")
    suspend fun backfillPhotoUids()

    @Query("DELETE FROM recipes WHERE recipeBookId = :bookId")
    suspend fun deleteAllForBook(bookId: Long)

    @Query(
        "UPDATE recipes SET healthColor = :color, healthDescription = :description, " +
            "healthFingerprint = :fingerprint, healthAnalyzedAt = :analyzedAt WHERE id = :id"
    )
    suspend fun updateHealthRating(id: Long, color: String?, description: String?, fingerprint: String?, analyzedAt: Long?)

    @Query(
        "UPDATE recipes SET nutritionCalories = :calories, nutritionProteinGrams = :protein, " +
            "nutritionCarbsGrams = :carbs, nutritionFatGrams = :fat, nutritionFingerprint = :fingerprint, " +
            "nutritionAnalyzedAt = :analyzedAt WHERE id = :id"
    )
    suspend fun updateNutritionInfo(
        id: Long,
        calories: Int?,
        protein: Double?,
        carbs: Double?,
        fat: Double?,
        fingerprint: String?,
        analyzedAt: Long?
    )
}
