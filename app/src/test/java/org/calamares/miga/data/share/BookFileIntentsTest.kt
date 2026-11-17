package org.calamares.miga.data.share

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.calamares.miga.MigaApp
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Only files are taken as shared books: other links opened in Miga are not "a file that could not be opened". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class BookFileIntentsTest {

    private val app: MigaApp = ApplicationProvider.getApplicationContext()

    @Test
    fun `app links are not files`() {
        listOf("miga://openrouter?code=abc&state=xyz", "https://example.com/recipe").forEach { link ->
            assertFalse(link, BookFileIntents.handle(app, Intent(Intent.ACTION_VIEW, Uri.parse(link))))
        }
    }
}
