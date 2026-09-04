package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Catálogo de nombres de ingrediente usados, para sugerir autocompletado al editar recetas. */
@Entity(tableName = "ingredient_catalog", indices = [Index("name", unique = true)])
data class IngredientCatalogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
