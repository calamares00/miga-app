package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User shopping list template. [body] stores the items as JSON (see ShoppingTemplateCodec); older
 * templates used one item per line.
 */
@Entity(tableName = "shopping_templates")
data class ShoppingTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    val createdAt: Long
)
