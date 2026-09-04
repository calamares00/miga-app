package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_books")
data class RecipeBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverPhotoUri: String?,
    val createdAt: Long
)
