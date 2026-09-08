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
    val packVersion: Int? = null,
    /** Última modificación real (nombre o portada); usada por el motor de sincronización para
     *  decidir qué es más reciente al comparar con la copia del servidor. Por defecto igual a
     *  [createdAt] si no se indica (libro recién creado, nunca editado). */
    val updatedAt: Long = createdAt,
    /** Id de la conexión de sincronización (ver SyncConnectionEntity) a la que pertenece este
     *  libro; null = libro local, no sincronizado con ningún servidor. A diferencia de [packId]
     *  (solo lectura), un libro sincronizado es de lectura-escritura normal. */
    val syncConnectionId: Long? = null
)
