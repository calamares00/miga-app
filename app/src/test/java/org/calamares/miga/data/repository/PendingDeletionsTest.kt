package org.calamares.miga.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deletions with "Undo": hidden at once, deleted after the window unless undone. */
class PendingDeletionsTest {

    private val pending = PendingDeletions(CoroutineScope(SupervisorJob() + Dispatchers.Default), windowMillis = 200)

    @Test
    fun `items are hidden at once and deleted when the window is over`() = runBlocking {
        var deleted = false
        val offer = pending.schedule("Deleted", recipeIds = setOf(1L, 2L)) { deleted = true }
        assertEquals(setOf(1L, 2L), pending.recipeIds.value)
        assertEquals(offer, pending.offer.value)
        delay(600)
        assertTrue(deleted)
        assertTrue(pending.recipeIds.value.isEmpty())
        assertNull(pending.offer.value)
    }

    @Test
    fun `undo brings the items back and nothing is deleted`() = runBlocking {
        var deleted = false
        val offer = pending.schedule("Deleted", bookIds = setOf(7L)) { deleted = true }
        pending.offered(offer)
        assertNull(pending.offer.value)
        assertEquals(setOf(7L), pending.bookIds.value)
        pending.undo(offer)
        assertTrue(pending.bookIds.value.isEmpty())
        delay(600)
        assertFalse(deleted)
    }

    @Test
    fun `two deletions are independent`() = runBlocking {
        val first = pending.schedule("One", recipeIds = setOf(1L)) {}
        pending.schedule("Two", recipeIds = setOf(2L)) {}
        pending.undo(first)
        assertEquals(setOf(2L), pending.recipeIds.value)
    }
}
