package com.bmo00.miga.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Una conexión a un namespace de un servidor de sincronización self-hosted (ver miga-server).
 *  La app puede tener varias a la vez (p. ej. "Casa" y "Cuadrilla de amigos", posiblemente en
 *  servidores distintos); cada libro sincronizado referencia una de estas filas por [id]. */
@Entity(tableName = "sync_connections")
data class SyncConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nombre elegido por el usuario para reconocer la conexión en Ajustes (p. ej. "Casa"). */
    val label: String,
    val serverUrl: String,
    val namespaceId: String,
    /** Token de acceso al namespace (BYOK, igual de sensible que las API key de Gemini/Anthropic
     *  ya guardadas hoy en DataStore sin cifrado adicional - misma limitación conocida). */
    val accessToken: String,
    /** Cursor de sincronización: última revisión del namespace ya aplicada localmente. */
    val lastSyncedRevision: Long = 0,
    val lastSyncedAt: Long? = null,
    val lastSyncError: String? = null,
    val createdAt: Long
)
