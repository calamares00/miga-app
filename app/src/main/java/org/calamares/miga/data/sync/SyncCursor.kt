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

    /**
     * Whether to ask the server for the page after the one just applied, which started at [since]
     * and left the cursor at [cursor]. Not when a photo failed (the cursor stays before it and the
     * same page would come again; the next sync retries), nor when the cursor did not advance.
     */
    fun hasNextPage(hasMore: Boolean, since: Long, cursor: Long, failedPhotoRevisions: List<Long>): Boolean =
        hasMore && failedPhotoRevisions.isEmpty() && cursor > since
}
