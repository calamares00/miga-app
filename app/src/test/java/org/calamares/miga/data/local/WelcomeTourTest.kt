package org.calamares.miga.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Who sees the welcome tour when the app starts. */
class WelcomeTourTest {

    @Test
    fun `new installs and people who saw an older tour see it, once`() {
        assertFalse("first start", welcomeTourDone(null, null))
        assertFalse("saw the tour before 1.0.49 (no version saved)", welcomeTourDone(true, null))
        assertFalse("saw an older version", welcomeTourDone(true, WELCOME_TOUR_VERSION - 1))
        assertTrue("saw this one", welcomeTourDone(true, WELCOME_TOUR_VERSION))
        assertFalse("asked to see it again (Settings > Help)", welcomeTourDone(false, WELCOME_TOUR_VERSION))
    }
}
