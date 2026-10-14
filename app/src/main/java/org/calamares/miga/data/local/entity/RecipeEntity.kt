package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipes",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RecipeBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeBookId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId"), Index("recipeBookId")]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Stable identifier (UUID) independent of the local id, used by export/import and sync. */
    val uid: String,
    val name: String,
    val categoryId: Long?,
    val recipeBookId: Long,
    val difficulty: String,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val servings: Int,
    val notes: String?,
    val source: String?,
    val isFavorite: Boolean,
    val timesCooked: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** Cached health rating (see HealthRating); null when never analysed. */
    val healthColor: String? = null,
    val healthDescription: String? = null,
    /**
     * Fingerprint of the analysed ingredients and steps; when it differs from the current content
     * the rating is stale.
     */
    val healthFingerprint: String? = null,
    val healthAnalyzedAt: Long? = null,
    /** Cached nutrition estimate (see NutritionInfo); null when never analysed. */
    val nutritionCalories: Int? = null,
    val nutritionProteinGrams: Double? = null,
    val nutritionCarbsGrams: Double? = null,
    val nutritionFatGrams: Double? = null,
    /**
     * Fingerprint of the analysed ingredients and steps; when it differs from the current content
     * the estimate is stale.
     */
    val nutritionFingerprint: String? = null,
    val nutritionAnalyzedAt: Long? = null,
    /**
     * Personal rating from 1 to 5 stars; null when not rated. Set from the recipe screen, not from
     * the editor.
     */
    val rating: Int? = null,
    /** Where the recipe comes from, as written by the user or the AI ("México", "Córdoba"). */
    val origin: String? = null,
    /** ISO 3166-1 alpha-2 code of the origin's country ("MX", "ES"), for its flag; null when unknown. */
    val originCountry: String? = null
)
