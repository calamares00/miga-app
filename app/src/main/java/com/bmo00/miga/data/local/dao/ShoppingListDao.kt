package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bmo00.miga.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Todas las lecturas de la lista visible excluyen las filas con [ShoppingListItemEntity.deletedAt]
 * (tombstones pendientes de subir al servidor). Toda escritura local marca la fila como
 * "syncDirty" con un updatedAt nuevo; ver RecipeRepository para la lógica de sincronización.
 */
@Dao
interface ShoppingListDao {

    @Query("SELECT * FROM shopping_list_items WHERE deletedAt IS NULL ORDER BY normalizedName ASC")
    fun observeAll(): Flow<List<ShoppingListItemEntity>>

    /**
     * Fila fusionable: mismo nombre normalizado + misma unidad (incluye null=null, por eso se usa
     * el operador SQLite "IS", null-safe, en vez de "=") + cantidad no nula (la de la fila y la
     * que se va a sumar se comprueban aparte, en el repositorio).
     */
    @Query("SELECT * FROM shopping_list_items WHERE deletedAt IS NULL AND normalizedName = :normalizedName AND unit IS :unit AND quantity IS NOT NULL LIMIT 1")
    suspend fun findMergeable(normalizedName: String, unit: String?): ShoppingListItemEntity?

    /** Artículo todavía en la lista (sin tombstone) con ese nombre normalizado, marcado o no. */
    @Query("SELECT * FROM shopping_list_items WHERE deletedAt IS NULL AND normalizedName = :normalizedName LIMIT 1")
    suspend fun findLiveByName(normalizedName: String): ShoppingListItemEntity?

    @Insert
    suspend fun insert(item: ShoppingListItemEntity): Long

    @Update
    suspend fun update(item: ShoppingListItemEntity)

    @Query("UPDATE shopping_list_items SET checked = :checked, updatedAt = :now, syncDirty = 1 WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean, now: Long)

    @Query("UPDATE shopping_list_items SET deletedAt = :now, updatedAt = :now, syncDirty = 1 WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE shopping_list_items SET deletedAt = :now, updatedAt = :now, syncDirty = 1 WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(now: Long)

    @Query("UPDATE shopping_list_items SET deletedAt = :now, updatedAt = :now, syncDirty = 1 WHERE deletedAt IS NULL AND checked = 1")
    suspend fun softDeleteChecked(now: Long)

    // --- Sincronización ---

    @Query("SELECT * FROM shopping_list_items WHERE uid = :uid")
    suspend fun findByUid(uid: String): ShoppingListItemEntity?

    @Query("SELECT * FROM shopping_list_items WHERE syncDirty = 1 ORDER BY updatedAt ASC")
    suspend fun getDirty(): List<ShoppingListItemEntity>

    /** Solo limpia la marca si la fila no ha vuelto a cambiar mientras se subía (mismo updatedAt). */
    @Query("UPDATE shopping_list_items SET syncDirty = 0 WHERE uid = :uid AND updatedAt = :updatedAt")
    suspend fun markSynced(uid: String, updatedAt: Long)

    @Query("DELETE FROM shopping_list_items WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    /** Tombstones ya subidos (o sin sincronización activa): no hace falta conservarlos. */
    @Query("DELETE FROM shopping_list_items WHERE deletedAt IS NOT NULL AND syncDirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM shopping_list_items WHERE deletedAt IS NOT NULL")
    suspend fun purgeAllTombstones()

    @Query("UPDATE shopping_list_items SET syncDirty = 1 WHERE deletedAt IS NULL")
    suspend fun markAllLiveDirty()
}
