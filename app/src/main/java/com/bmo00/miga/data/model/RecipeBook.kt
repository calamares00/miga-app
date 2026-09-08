package com.bmo00.miga.data.model

data class RecipeBook(
    val id: Long = 0L,
    val uid: String,
    val name: String,
    val coverPhotoUri: String?,
    /** Id del pack instalado (ver PacksCatalogClient); null = libro propio del usuario, editable. */
    val packId: String? = null,
    val packVersion: Int? = null,
    /** Id de la conexión de sincronización (ver SyncConnection) a la que pertenece este libro;
     *  null = libro local, no sincronizado con ningún servidor. */
    val syncConnectionId: Long? = null
) {
    /** Un libro-pack es de solo lectura: no se puede renombrar, cambiar portada ni añadir/quitar recetas. */
    val isPack: Boolean get() = packId != null

    /** Un libro sincronizado es de lectura-escritura normal (a diferencia de un pack); solo se
     *  distingue por una insignia visual y por lo que hace el motor de sincronización. */
    val isSynced: Boolean get() = syncConnectionId != null
}

data class RecipeBookSummary(
    val id: Long,
    val name: String,
    val coverPhotoUri: String?,
    val recipeCount: Int,
    val packId: String? = null,
    val packVersion: Int? = null,
    val syncConnectionId: Long? = null
) {
    val isPack: Boolean get() = packId != null
    val isSynced: Boolean get() = syncConnectionId != null
}

data class RecipeBookDraft(
    val id: Long = 0L,
    /** Solo se rellena al importar, para conservar el uid del libro exportado; null = generar uno nuevo. */
    val uid: String? = null,
    val name: String,
    val coverPhotoUri: String?
)

fun RecipeBook.toDraft() = RecipeBookDraft(id = id, uid = uid, name = name, coverPhotoUri = coverPhotoUri)

fun emptyRecipeBookDraft() = RecipeBookDraft(id = 0L, name = "", coverPhotoUri = null)
