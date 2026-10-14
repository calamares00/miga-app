package org.calamares.miga.data.repository

import org.calamares.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * What the repository needs to know about the user to write to the shopping list: the list
 * currently in use ([listUid]) and the name used to sign changes on a shared list ([author], empty
 * for none). Both come from the settings DataStore, which the repository does not access directly.
 */
class ShoppingContext(val listUid: Flow<String>, val author: Flow<String>) {
    companion object {
        val Default = ShoppingContext(flowOf(DEFAULT_SHOPPING_LIST_UID), flowOf(""))
    }
}
