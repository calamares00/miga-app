package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipe_photos",
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
data class RecipePhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val uri: String,
    val position: Int,
    val isCover: Boolean,
    /**
     * Stable identifier (UUID) used by sync to upload, download and delete this photo on its own.
     * Nullable only because of the v7 -> v8 migration; every new photo gets one.
     */
    val uid: String? = null
)
