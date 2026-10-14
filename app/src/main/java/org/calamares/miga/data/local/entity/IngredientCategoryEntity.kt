package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Category of the ingredient catalogue (Fruit, Vegetables...), separate from recipe categories. */
@Entity(tableName = "ingredient_categories")
data class IngredientCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
