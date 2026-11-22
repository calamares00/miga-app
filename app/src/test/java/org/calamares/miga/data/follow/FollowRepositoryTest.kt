package org.calamares.miga.data.follow

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.calamares.miga.MigaApp
import org.calamares.miga.data.local.entity.FollowedSourceEntity
import org.calamares.miga.data.local.entity.SourcePostEntity
import org.calamares.miga.data.model.RecipeBookDraft
import org.calamares.miga.data.model.emptyRecipeDraft
import org.calamares.miga.ui.list.TestMigaApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Followed sources, their posts, saved posts and which were imported (no network). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestMigaApp::class)
class FollowRepositoryTest {

    private val app: MigaApp = ApplicationProvider.getApplicationContext()
    private val dao = app.database.followDao()
    private val follows = app.repository.follows

    @Test
    fun `new posts, saved posts and imported ones`() = runBlocking {
        val sourceId = dao.insertSource(
            FollowedSourceEntity(network = "INSTAGRAM", key = "cocina", handle = "@cocina", name = "Cocina", avatarUrl = null,
                url = "https://www.instagram.com/cocina/", addedAt = 0, lastOpenedAt = 1500, checkedAt = 0)
        )
        dao.replacePosts(sourceId, listOf(
            SourcePostEntity(sourceId = sourceId, postKey = "Old1", url = "https://www.instagram.com/p/Old1/", title = "Pesto", description = "", thumbnailUrl = null, publishedAt = 1000, kind = "PHOTO"),
            SourcePostEntity(sourceId = sourceId, postKey = "New2", url = "https://www.instagram.com/reel/New2/", title = "Carbonara", description = "", thumbnailUrl = null, publishedAt = 2000, kind = "REEL")
        ))
        assertEquals(1, follows.observeSources().first().single().newCount)

        // A new post not about cooking is hidden and not counted.
        dao.insertPosts(listOf(
            SourcePostEntity(sourceId = sourceId, postKey = "Trip3", url = "https://www.instagram.com/p/Trip3/", title = "Mis amigas del colegio en el pueblo más bonito del mundo",
                description = "", thumbnailUrl = null, publishedAt = 3000, kind = "PHOTO")
        ))
        assertEquals(1, follows.observeSources().first().single().newCount)
        assertTrue(follows.observeFeed().first().single { it.postKey == "Trip3" }.offTopic)
        dao.deletePosts(sourceId)
        dao.replacePosts(sourceId, listOf(
            SourcePostEntity(sourceId = sourceId, postKey = "Old1", url = "https://www.instagram.com/p/Old1/", title = "Pesto", description = "", thumbnailUrl = null, publishedAt = 1000, kind = "PHOTO"),
            SourcePostEntity(sourceId = sourceId, postKey = "New2", url = "https://www.instagram.com/reel/New2/", title = "Carbonara", description = "", thumbnailUrl = null, publishedAt = 2000, kind = "REEL")
        ))

        val feed = follows.observeFeed().first()
        assertEquals(listOf("New2", "Old1"), feed.map { it.postKey })
        assertTrue(feed[0].isNew)
        assertFalse(feed[1].isNew)

        follows.save(feed[0])
        assertTrue(follows.observeFeed().first()[0].isSaved)
        assertEquals("New2", follows.observeSavedPosts().first().single().postKey)

        // A recipe imported from the reel names it in its source.
        val bookId = app.repository.books.saveRecipeBook(RecipeBookDraft(name = "Pasta", coverPhotoUri = null))
        app.repository.saveRecipe(
            emptyRecipeDraft(bookId).copy(name = "Carbonara", source = "[@cocina](https://www.instagram.com/cocina/) - [reel](https://www.instagram.com/cocina/reel/New2/)")
        )
        val imported = follows.observeSavedPosts().first().single().imported!!
        assertEquals("Pasta", imported.bookName)
        assertNull(follows.observeFeed().first()[1].imported)

        follows.markOpened(sourceId)
        assertEquals(0, follows.observeSources().first().single().newCount)
        follows.unfollow(sourceId)
        assertTrue(follows.observeFeed().first().isEmpty())
        assertEquals("a saved reel stays after unfollowing", 1, follows.observeSavedPosts().first().size)
    }

    @Test
    fun `a recipe in two followed sections shows once`() = runBlocking {
        val ids = listOf("recetas", "postres").map { section ->
            dao.insertSource(
                FollowedSourceEntity(network = "WEB", key = "https://cocina.example/$section/feed/", handle = "cocina.example", name = "Cocina", avatarUrl = null,
                    url = "https://cocina.example/$section/", addedAt = 0, lastOpenedAt = 0, checkedAt = 0)
            )
        }
        ids.forEach { id ->
            dao.replacePosts(id, listOf(
                SourcePostEntity(sourceId = id, postKey = "https://cocina.example/flan", url = "https://cocina.example/flan/", title = "Flan de huevo",
                    description = "", thumbnailUrl = null, publishedAt = 1000, kind = "ARTICLE")
            ))
        }
        assertEquals(1, follows.observeFeed().first().size)
        assertEquals(1, follows.observeFeed(ids[1]).first().size)
    }
}
