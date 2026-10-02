package com.bmo00.miga.data.sync

/** Lógica pura (sin Android) del cursor de sincronización de bajada. */
object SyncCursor {

    /**
     * Siguiente cursor tras aplicar cambios hasta [latestRevision]. Si alguna foto falló por un error
     * transitorio ([failedPhotoRevisions]), el cursor se queda justo antes de la primera para que la
     * próxima sincronización la vuelva a pedir; nunca avanza más allá de [latestRevision] ni baja de 0.
     */
    fun next(latestRevision: Long, failedPhotoRevisions: List<Long>): Long {
        val earliestFailure = failedPhotoRevisions.minOrNull() ?: return latestRevision
        return minOf(latestRevision, earliestFailure - 1).coerceAtLeast(0)
    }
}
