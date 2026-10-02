package com.bmo00.miga.data.sync

import org.junit.Assert.assertEquals
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
}
