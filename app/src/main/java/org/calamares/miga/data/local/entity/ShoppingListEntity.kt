package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Lista de la compra adicional (Casa, Fiesta...); la lista por defecto ("main") no tiene fila.
 * Mismos campos de sincronización que los artículos: [updatedAt] para "última escritura gana",
 * [deletedAt] como tombstone y [syncDirty] para lo que falta por subir.
 */
@Entity(tableName = "shopping_lists", indices = [Index(value = ["uid"], unique = true)])
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val syncDirty: Boolean = false
)
