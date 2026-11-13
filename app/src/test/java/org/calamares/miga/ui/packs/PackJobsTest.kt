package org.calamares.miga.ui.packs

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.calamares.miga.L10n
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.entity.RecipeBookEntity
import org.calamares.miga.data.repository.RecipeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Pack batches run in PackJobs, not in a screen: they finish with nobody watching them. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PackJobsTest {

    @Test
    fun `an uninstall batch finishes on its own and reports the result`() = runBlocking {
        val app: Application = ApplicationProvider.getApplicationContext()
        L10n.init(app)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = RecipeRepository(db)
        listOf("arroces", "panaderia").forEach {
            db.recipeBookDao().insert(RecipeBookEntity(uid = it, name = it, coverPhotoUri = null, createdAt = 0L, packId = it))
        }

        PackJobs.uninstallAll(repository, listOf("arroces", "panaderia"))
        // No screen holds it: wait for the job itself.
        withTimeout(10_000) { while (PackJobs.progress.value != null || PackJobs.message.value == null) delay(20) }

        assertNull(repository.books.findRecipeBookByPackId("arroces"))
        assertNull(repository.books.findRecipeBookByPackId("panaderia"))
        assertTrue(PackJobs.message.value!!.contains("2"))
        PackJobs.dismissMessage()
        assertEquals(null, PackJobs.message.value)
        db.close()
    }
}
