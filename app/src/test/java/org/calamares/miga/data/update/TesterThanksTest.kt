package org.calamares.miga.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TesterThanksTest {

    @Test
    fun `testers are those who started before the public version`() {
        assertTrue(TesterThanks.isTester("1.0.58"))
        assertTrue(TesterThanks.isTester("1.0.0"))
        assertFalse(TesterThanks.isTester("1.1.0"))
        assertFalse(TesterThanks.isTester("1.2.3"))
    }

    @Test
    fun `thanks only once Miga is public, and again only when due`() {
        val today = 20_000L
        // Not public yet: the latest stable Release is still a beta-era version, or unknown.
        assertFalse(TesterThanks.isDue(true, "1.0.57", null, today))
        assertFalse(TesterThanks.isDue(true, null, null, today))
        // Public: shown the first time, to testers only.
        assertTrue(TesterThanks.isDue(true, "1.1.0", null, today))
        assertTrue(TesterThanks.isDue(true, "1.2.0", null, today))
        assertFalse(TesterThanks.isDue(false, "1.1.0", null, today))
        // Stayed in the beta: back after the reminder interval.
        assertFalse(TesterThanks.isDue(true, "1.1.0", today + TesterThanks.REMIND_AFTER_DAYS, today))
        assertTrue(TesterThanks.isDue(true, "1.1.0", today, today))
        // Moved to the public version: never again.
        assertFalse(TesterThanks.isDue(true, "1.1.0", TesterThanks.NEVER, today + 1_000))
    }

    @Test
    fun `every message can come up`() {
        val random = Random(7)
        val picked = (1..200).map { TesterThanks.pick(random) }.toSet()
        assertEquals(TesterThanks.MESSAGES.indices.toSet(), picked)
    }
}
