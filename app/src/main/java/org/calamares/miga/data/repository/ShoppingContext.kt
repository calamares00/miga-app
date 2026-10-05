package org.calamares.miga.data.repository

import org.calamares.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Lo que el repositorio necesita saber del usuario para escribir en la lista de la compra: en qué
 * lista está trabajando ahora ([listUid]) y con qué nombre firma sus cambios en una lista
 * compartida ([author], vacío = sin firma). Vienen de los ajustes (DataStore), que el repositorio
 * no conoce directamente.
 */
class ShoppingContext(val listUid: Flow<String>, val author: Flow<String>) {
    companion object {
        val Default = ShoppingContext(flowOf(DEFAULT_SHOPPING_LIST_UID), flowOf(""))
    }
}
