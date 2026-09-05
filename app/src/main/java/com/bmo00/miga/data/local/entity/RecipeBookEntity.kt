package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipe_books")
data class RecipeBookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Identificador estable (UUID) independiente del id local, usado en export/import. */
    val uid: String,
    val name: String,
    val coverPhotoUri: String?,
    val createdAt: Long,
    /** Id del pack instalado (ver PacksCatalogClient); null = libro propio del usuario, editable. */
    val packId: String? = null,
    /** Versión del pack instalada; null si [packId] es null. */
    val packVersion: Int? = null
)
