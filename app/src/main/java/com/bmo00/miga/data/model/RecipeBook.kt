package com.bmo00.miga.data.model

data class RecipeBook(
    val id: Long = 0L,
    val uid: String,
    val name: String,
    val coverPhotoUri: String?,
    /** Id del pack instalado (ver PacksCatalogClient); null = libro propio del usuario, editable. */
    val packId: String? = null,
    val packVersion: Int? = null
) {
    /** Un libro-pack es de solo lectura: no se puede renombrar, cambiar portada ni añadir/quitar recetas. */
    val isPack: Boolean get() = packId != null
}

data class RecipeBookSummary(
    val id: Long,
    val name: String,
    val coverPhotoUri: String?,
    val recipeCount: Int,
    val packId: String? = null,
    val packVersion: Int? = null
) {
    val isPack: Boolean get() = packId != null
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
