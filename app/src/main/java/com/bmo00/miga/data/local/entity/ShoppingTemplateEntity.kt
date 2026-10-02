package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Plantilla de lista de la compra del usuario. [body] guarda un artículo por línea (ver ShoppingListShareCodec.toLines). */
@Entity(tableName = "shopping_templates")
data class ShoppingTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    val createdAt: Long
)
