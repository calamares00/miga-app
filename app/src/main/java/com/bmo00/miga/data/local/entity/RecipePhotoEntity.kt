package com.bmo00.miga.data.local.entity

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
    /** Identificador estable (UUID), usado por el motor de sincronización para subir/bajar/borrar
     *  esta foto de forma individual. Nullable por retrocompatibilidad de la migración (las fotos
     *  ya existentes se rellenan con un uid al migrar); toda foto nueva lo lleva desde el alta. */
    val uid: String? = null
)
