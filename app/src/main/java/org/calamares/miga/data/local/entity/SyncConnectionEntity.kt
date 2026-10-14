package org.calamares.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Connection to a namespace of a self-hosted sync server (see miga-server). There can be several at
 * once, possibly on different servers; every synced book references one by [id].
 */
@Entity(tableName = "sync_connections")
data class SyncConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Name chosen by the user to recognise the connection in Settings (for example "Home"). */
    val label: String,
    val serverUrl: String,
    val namespaceId: String,
    /** Namespace access token, stored encrypted with TokenCipher. */
    val accessToken: String,
    /** Sync cursor: last namespace revision already applied locally. */
    val lastSyncedRevision: Long = 0,
    val lastSyncedAt: Long? = null,
    val lastSyncError: String? = null,
    /** Whether this connection also shares the shopping list (at most one connection does). */
    val syncShopping: Boolean = false,
    /** Whether the full list was already downloaded after enabling [syncShopping]. */
    val shoppingPulled: Boolean = false,
    val createdAt: Long
)
