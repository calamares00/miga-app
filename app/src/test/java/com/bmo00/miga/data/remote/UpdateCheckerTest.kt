package com.bmo00.miga.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `a higher minor version is newer`() {
        assertTrue(UpdateChecker.isNewerVersion(current = "1.0.5", latest = "1.1.0"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(UpdateChecker.isNewerVersion(current = "1.2.3", latest = "1.2.3"))
    }

    @Test
    fun `a lower version is not newer`() {
        assertFalse(UpdateChecker.isNewerVersion(current = "2.0.0", latest = "1.9.9"))
    }

    @Test
    fun `a missing patch segment is treated as zero`() {
        assertTrue(UpdateChecker.isNewerVersion(current = "1.0", latest = "1.0.1"))
        assertFalse(UpdateChecker.isNewerVersion(current = "1.0.0", latest = "1.0"))
    }
}
