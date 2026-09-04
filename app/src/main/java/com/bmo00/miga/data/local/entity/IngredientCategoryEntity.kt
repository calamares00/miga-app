package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Categoría del catálogo de ingredientes (Frutas, Verduras...), distinta de las categorías de receta. */
@Entity(tableName = "ingredient_categories")
data class IngredientCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
