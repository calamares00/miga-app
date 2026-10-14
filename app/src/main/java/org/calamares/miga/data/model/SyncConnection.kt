package org.calamares.miga.data.model

/**
 * A connection, configured in Settings, to a namespace on a self-hosted sync server (see
 * miga-server). Several can be active at once.
 */
data class SyncConnection(
    val id: Long,
    val label: String,
    val serverUrl: String,
    val namespaceId: String,
    val accessToken: String,
    val lastSyncedRevision: Long,
    val lastSyncedAt: Long?,
    val lastSyncError: String?,
    /**
     * Whether this connection also shares the shopping list with the other apps in the namespace.
     */
    val syncShopping: Boolean = false,
    val shoppingPulled: Boolean = false
)
