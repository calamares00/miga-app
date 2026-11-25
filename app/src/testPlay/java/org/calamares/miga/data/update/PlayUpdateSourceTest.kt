package org.calamares.miga.data.update

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import kotlinx.coroutines.runBlocking
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class PlayUpdateSourceTest {

    private val manager = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())

    @Test
    fun `an update Play offers is announced by its version code`() = runBlocking {
        manager.setUpdateAvailable(300, AppUpdateType.IMMEDIATE)
        assertEquals(AvailableUpdate(key = "code:300", versionName = null), UpdateSource.check(manager))
    }

    @Test
    fun `nothing when Play has no update`() = runBlocking {
        manager.setUpdateNotAvailable()
        assertNull(UpdateSource.check(manager))
    }
}
