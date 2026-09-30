package org.calamares.miga.data.model

/** Una conexión configurada en Ajustes a un namespace de un servidor de sincronización
 *  self-hosted (ver miga-server). Puede haber varias a la vez. */
data class SyncConnection(
    val id: Long,
    val label: String,
    val serverUrl: String,
    val namespaceId: String,
    val accessToken: String,
    val lastSyncedRevision: Long,
    val lastSyncedAt: Long?,
    val lastSyncError: String?,
    /** Esta conexión comparte también la lista de la compra con las demás apps del namespace. */
    val syncShopping: Boolean = false,
    val shoppingPulled: Boolean = false
)
