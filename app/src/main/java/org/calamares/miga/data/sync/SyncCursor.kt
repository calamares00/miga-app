package org.calamares.miga.data.sync

/** Pure logic for the download sync cursor. */
object SyncCursor {

    /**
     * Next cursor after applying changes up to [latestRevision]. When a photo failed with a
     * transient error ([failedPhotoRevisions]) the cursor stops just before the first one so the
     * next sync asks for it again. It never goes past [latestRevision] or below 0.
     */
    fun next(latestRevision: Long, failedPhotoRevisions: List<Long>): Long {
        val earliestFailure = failedPhotoRevisions.minOrNull() ?: return latestRevision
        return minOf(latestRevision, earliestFailure - 1).coerceAtLeast(0)
    }
}
