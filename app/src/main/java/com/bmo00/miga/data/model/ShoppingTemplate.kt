package com.bmo00.miga.data.model

/** Plantilla de lista de la compra: un nombre y sus artículos; [id] > 0 si es del usuario (guardada en Room). */
data class ShoppingTemplate(
    val id: Long,
    val name: String,
    val entries: List<ParsedShoppingEntry>
)
