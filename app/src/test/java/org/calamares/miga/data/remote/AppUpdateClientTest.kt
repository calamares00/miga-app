package org.calamares.miga.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateClientTest {

    @Test
    fun `higher patch, minor or major is newer`() {
        assertTrue(AppUpdateClient.isNewer("1.0.6", "1.0.5"))
        assertTrue(AppUpdateClient.isNewer("1.1.0", "1.0.9"))
        assertTrue(AppUpdateClient.isNewer("2.0", "1.9.9"))
    }

    @Test
    fun `numbers are compared as numbers, not text`() {
        assertTrue(AppUpdateClient.isNewer("1.0.10", "1.0.9"))
        assertFalse(AppUpdateClient.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun `same or older version is not newer`() {
        assertFalse(AppUpdateClient.isNewer("1.0.5", "1.0.5"))
        assertFalse(AppUpdateClient.isNewer("1.0", "1.0.0"))
        assertFalse(AppUpdateClient.isNewer("v1.0.4", "1.0.5"))
    }
}
