package com.bmo00.miga.data.model

/** Fila del catálogo de ingredientes con el nombre de su categoría ya resuelto (o null = "Sin categoría"). */
data class IngredientCatalogItem(
    val id: Long,
    val name: String,
    val categoryId: Long?,
    val categoryName: String?
)
