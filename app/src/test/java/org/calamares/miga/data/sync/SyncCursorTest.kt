package org.calamares.miga.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncCursorTest {

    @Test
    fun `without failures the cursor goes to the latest revision`() {
        assertEquals(120, SyncCursor.next(120, emptyList()))
    }

    @Test
    fun `a failed photo holds the cursor right before it`() {
        assertEquals(49, SyncCursor.next(120, listOf(80, 50, 90)))
    }

    @Test
    fun `the cursor never exceeds the latest revision nor goes below zero`() {
        assertEquals(10, SyncCursor.next(10, listOf(500)))
        assertEquals(0, SyncCursor.next(10, listOf(0)))
        assertEquals(0, SyncCursor.next(10, listOf(1)))
    }

    @Test
    fun `the next page is asked for only while the server has more and the cursor advances`() {
        assertTrue(SyncCursor.hasNextPage(hasMore = true, since = 0, cursor = 500, failedPhotoRevisions = emptyList()))
        assertFalse(SyncCursor.hasNextPage(hasMore = false, since = 0, cursor = 500, failedPhotoRevisions = emptyList()))
        // A failed photo holds the cursor back: asking again would bring the same page.
        assertFalse(SyncCursor.hasNextPage(hasMore = true, since = 0, cursor = 120, failedPhotoRevisions = listOf(121)))
        assertFalse(SyncCursor.hasNextPage(hasMore = true, since = 500, cursor = 500, failedPhotoRevisions = emptyList()))
    }
}
