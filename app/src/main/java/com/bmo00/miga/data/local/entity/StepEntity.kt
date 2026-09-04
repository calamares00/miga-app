package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steps",
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
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Null groupName = paso de la receta principal. Un nombre agrupa pasos de una sub-receta (p.ej. "Salsa de tomate"). */
    val groupName: String?,
    /** Orden dentro de su grupo, empezando en 1. */
    val position: Int,
    val instruction: String
)
