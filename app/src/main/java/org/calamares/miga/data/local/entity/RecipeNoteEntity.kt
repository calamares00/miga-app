package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A dated personal note on a recipe ("next time, less salt"), unlike the recipe's own notes, which
 * are part of the recipe. Kept on the device and in backups and Miga files; not synced.
 */
@Entity(
    tableName = "recipe_journal",
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
data class RecipeNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val text: String,
    val createdAt: Long
)
