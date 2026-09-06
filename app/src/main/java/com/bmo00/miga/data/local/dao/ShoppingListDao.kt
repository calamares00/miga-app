package com.bmo00.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.bmo00.miga.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {

    @Query("SELECT * FROM shopping_list_items ORDER BY normalizedName ASC")
    fun observeAll(): Flow<List<ShoppingListItemEntity>>

    /**
     * Fila fusionable: mismo nombre normalizado + misma unidad (incluye null=null, por eso se usa
     * el operador SQLite "IS", null-safe, en vez de "=") + cantidad no nula (la de la fila y la
     * que se va a sumar se comprueban aparte, en el repositorio).
     */
    @Query("SELECT * FROM shopping_list_items WHERE normalizedName = :normalizedName AND unit IS :unit AND quantity IS NOT NULL LIMIT 1")
    suspend fun findMergeable(normalizedName: String, unit: String?): ShoppingListItemEntity?

    @Insert
    suspend fun insert(item: ShoppingListItemEntity): Long

    @Update
    suspend fun update(item: ShoppingListItemEntity)

    @Query("UPDATE shopping_list_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM shopping_list_items")
    suspend fun clearAll()

    @Query("DELETE FROM shopping_list_items WHERE checked = 1")
    suspend fun clearChecked()
}
