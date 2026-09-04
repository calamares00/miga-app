package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipeId")]
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Null groupName = ingrediente de la receta principal. Un nombre agrupa ingredientes de una sub-receta (p.ej. una salsa). */
    val groupName: String?,
    val position: Int,
    val name: String,
    val quantity: Double?,
    val unit: String?
)
