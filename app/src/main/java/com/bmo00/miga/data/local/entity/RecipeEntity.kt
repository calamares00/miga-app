package com.bmo00.miga.data.local.entity

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
    /** Identificador estable (UUID) independiente del id local, usado en export/import. */
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
    val updatedAt: Long
)
