package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Catalogue of ingredient names used so far, for autocompletion in the recipe editor. */
@Entity(
    tableName = "ingredient_catalog",
    foreignKeys = [
        ForeignKey(
            entity = IngredientCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("name", unique = true), Index("categoryId")]
)
data class IngredientCatalogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long? = null
)
