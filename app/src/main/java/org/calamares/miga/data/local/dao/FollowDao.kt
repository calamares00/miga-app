package org.calamares.miga.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.calamares.miga.data.local.entity.FollowedSourceEntity
import org.calamares.miga.data.local.entity.SavedPostEntity
import org.calamares.miga.data.local.entity.SourcePostEntity

@Dao
interface FollowDao {
    @Query("SELECT * FROM followed_sources ORDER BY name COLLATE NOCASE")
    fun observeSources(): Flow<List<FollowedSourceEntity>>

    @Query("SELECT * FROM followed_sources WHERE id = :id")
    fun observeSource(id: Long): Flow<FollowedSourceEntity?>

    @Query("SELECT * FROM followed_sources WHERE id = :id")
    suspend fun getSource(id: Long): FollowedSourceEntity?

    @Query("SELECT * FROM followed_sources WHERE network = :network AND `key` = :key")
    suspend fun findSource(network: String, key: String): FollowedSourceEntity?

    @Insert
    suspend fun insertSource(source: FollowedSourceEntity): Long

    @Query("UPDATE followed_sources SET name = :name, handle = :handle, avatarUrl = :avatarUrl, checkedAt = :checkedAt WHERE id = :id")
    suspend fun updateSourceInfo(id: Long, name: String, handle: String?, avatarUrl: String?, checkedAt: Long)

    @Query("UPDATE followed_sources SET lastOpenedAt = :at WHERE id = :id")
    suspend fun markOpened(id: Long, at: Long)

    @Query("DELETE FROM followed_sources WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("SELECT * FROM source_posts ORDER BY publishedAt DESC")
    fun observeAllPosts(): Flow<List<SourcePostEntity>>

    @Query("SELECT * FROM source_posts WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    fun observePosts(sourceId: Long): Flow<List<SourcePostEntity>>

    @Query("DELETE FROM source_posts WHERE sourceId = :sourceId")
    suspend fun deletePosts(sourceId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<SourcePostEntity>)

    /** The latest posts of a source replace the ones kept before. */
    @Transaction
    suspend fun replacePosts(sourceId: Long, posts: List<SourcePostEntity>) {
        deletePosts(sourceId)
        insertPosts(posts)
    }

    @Query("SELECT * FROM saved_posts ORDER BY savedAt DESC")
    fun observeSavedPosts(): Flow<List<SavedPostEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedPost(post: SavedPostEntity): Long

    @Query("DELETE FROM saved_posts WHERE network = :network AND postKey = :postKey")
    suspend fun deleteSavedPost(network: String, postKey: String)

    /** The source of every recipe that has one, to tell which posts were already imported. */
    @Query("SELECT r.source AS source, r.id AS recipeId, b.name AS bookName FROM recipes r JOIN recipe_books b ON b.id = r.recipeBookId WHERE r.source != ''")
    fun observeRecipeSources(): Flow<List<RecipeSourceRow>>
}

/** A recipe's source with where the recipe is (see FollowDao.observeRecipeSources). */
data class RecipeSourceRow(val source: String, val recipeId: Long, val bookName: String)
