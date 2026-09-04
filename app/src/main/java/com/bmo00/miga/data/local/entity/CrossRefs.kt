package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "recipe_tag_cross_ref",
    primaryKeys = ["recipeId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("recipeId"), Index("tagId")]
)
data class RecipeTagCrossRef(
    val recipeId: Long,
    val tagId: Long
)

@Entity(
    tableName = "recipe_utensil_cross_ref",
    primaryKeys = ["recipeId", "utensilId"],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UtensilEntity::class, parentColumns = ["id"], childColumns = ["utensilId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("recipeId"), Index("utensilId")]
)
data class RecipeUtensilCrossRef(
    val recipeId: Long,
    val utensilId: Long
)
