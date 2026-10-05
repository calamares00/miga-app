package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Plantilla de lista de la compra del usuario. [body] guarda los artículos en JSON (ver ShoppingTemplateCodec; las antiguas, una línea por artículo). */
@Entity(tableName = "shopping_templates")
data class ShoppingTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    val createdAt: Long
)
