package org.calamares.miga.data.follow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.local.AppDatabase
import org.calamares.miga.data.local.entity.FollowedSourceEntity
import org.calamares.miga.data.local.entity.SavedPostEntity
import org.calamares.miga.data.local.entity.SourcePostEntity
import org.calamares.miga.data.search.PageFetchResult
import org.calamares.miga.data.search.RecipeUrlFetcher
import org.calamares.miga.data.search.SocialPost
import org.calamares.miga.data.search.SocialPostRecipe
import org.calamares.miga.data.search.YouTubeRecipe
import org.calamares.miga.data.support.AppDispatchers

/** A channel or profile followed in Favourites, with how many of its posts are new. */
data class FollowedSource(
    val id: Long,
    val network: SocialNetwork,
    val handle: String?,
    val name: String,
    val avatarUrl: String?,
    val url: String,
    val newCount: Int,
    val checkedAt: Long?
)

/** A post shown in Favourites: from a followed source's feed or saved by the user. */
data class FavoritePost(
    val network: SocialNetwork,
    val postKey: String,
    val url: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val kind: PostKind,
    val author: String?,
    val authorUrl: String?,
    /** Publication time (0 when unknown, as for some saved posts). */
    val publishedAt: Long,
    val sourceId: Long? = null,
    val isNew: Boolean = false,
    val isSaved: Boolean = false,
    /** The recipe imported from it, when there is one. */
    val imported: ImportedRecipe? = null,
    /** Its text has the recipe written as such: importing needs no assistant. */
    val readsWithoutAssistant: Boolean = false,
    /** Nothing in it is about cooking (see CookingFilter): feeds leave it out unless asked. */
    val offTopic: Boolean = false
) {
    val id: String get() = "${network.name}:$postKey"
}

data class ImportedRecipe(val recipeId: Long, val bookName: String)

sealed interface FollowResult {
    data class Followed(val sourceId: Long) : FollowResult
    data class AlreadyFollowing(val sourceId: Long) : FollowResult
    data class Error(val reason: String) : FollowResult
}

/**
 * YouTube channels and Instagram profiles followed in Favourites, their latest posts (downloaded
 * when asked, kept to show offline) and the videos and reels saved to cook later. Whether a post
 * was imported is worked out from the recipes' sources, which name the post (see SourceText).
 */
class FollowRepository(db: AppDatabase) {

    private val dao = db.followDao()

    fun observeSources(): Flow<List<FollowedSource>> = combine(dao.observeSources(), dao.observeAllPosts()) { sources, posts ->
        val bySource = posts.groupBy { it.sourceId }
        sources.map { source ->
            FollowedSource(
                id = source.id,
                network = SocialNetwork.valueOf(source.network),
                handle = source.handle,
                name = source.name,
                avatarUrl = source.avatarUrl,
                url = source.url,
                newCount = bySource[source.id].orEmpty().count { it.publishedAt > source.lastOpenedAt },
                checkedAt = source.checkedAt
            )
        }.sortedWith(compareByDescending<FollowedSource> { it.newCount > 0 }.thenBy { it.name.lowercase() })
    }

    fun observeSource(id: Long): Flow<FollowedSource?> = observeSources().map { list -> list.firstOrNull { it.id == id } }

    /** Posts of followed sources (all of them, or [sourceId]'s), newest first, with their state. */
    fun observeFeed(sourceId: Long? = null): Flow<List<FavoritePost>> = combine(
        dao.observeSources(),
        if (sourceId == null) dao.observeAllPosts() else dao.observePosts(sourceId),
        dao.observeSavedPosts(),
        observeImported()
    ) { sources, posts, saved, imported ->
        val sourceById = sources.associateBy { it.id }
        val savedKeys = saved.map { "${it.network}:${it.postKey}" }.toSet()
        posts.mapNotNull { post ->
            val source = sourceById[post.sourceId] ?: return@mapNotNull null
            val network = SocialNetwork.valueOf(source.network)
            val isSaved = "${source.network}:${post.postKey}" in savedKeys
            val importedRecipe = imported["${source.network}:${post.postKey}"]
            val reads = readsWithoutAssistant(post.title, post.description, source.handle ?: source.name)
            FavoritePost(
                network = network,
                postKey = post.postKey,
                url = post.url,
                title = post.title,
                description = post.description,
                thumbnailUrl = post.thumbnailUrl,
                kind = PostKind.valueOf(post.kind),
                author = source.handle ?: source.name,
                authorUrl = source.url,
                publishedAt = post.publishedAt,
                sourceId = source.id,
                isNew = post.publishedAt > source.lastOpenedAt,
                isSaved = isSaved,
                imported = importedRecipe,
                readsWithoutAssistant = reads,
                // What the user saved or imported stays, and so does a post that has a recipe in it.
                offTopic = !isSaved && importedRecipe == null && !reads && CookingFilter.isOffTopic(post.title, post.description)
            )
        }
    }

    fun observeSavedPosts(): Flow<List<FavoritePost>> = combine(dao.observeSavedPosts(), observeImported()) { saved, imported ->
        saved.map { post ->
            FavoritePost(
                network = SocialNetwork.valueOf(post.network),
                postKey = post.postKey,
                url = post.url,
                title = post.title,
                description = post.description,
                thumbnailUrl = post.thumbnailUrl,
                kind = PostKind.valueOf(post.kind),
                author = post.author,
                authorUrl = post.authorUrl,
                publishedAt = 0L,
                isSaved = true,
                imported = imported["${post.network}:${post.postKey}"],
                readsWithoutAssistant = readsWithoutAssistant(post.title, post.description, post.author)
            )
        }
    }

    /** Recipes imported from posts, by "NETWORK:postKey" (taken from the links in their sources). */
    private fun observeImported(): Flow<Map<String, ImportedRecipe>> = dao.observeRecipeSources().map { rows ->
        buildMap {
            rows.forEach { row ->
                LINK.findAll(row.source).forEach { match ->
                    when (val link = SocialFeeds.parse(match.value)) {
                        is SocialLink.YouTubeVideo -> put("YOUTUBE:${link.id}", ImportedRecipe(row.recipeId, row.bookName))
                        is SocialLink.InstagramPost -> put("INSTAGRAM:${link.shortcode}", ImportedRecipe(row.recipeId, row.bookName))
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun readsWithoutAssistant(title: String, description: String, author: String?): Boolean =
        SocialPostRecipe.parse(SocialPost(listOf(title, description).joinToString("\n\n"), null, author, "", "")) != null

    /** Follows the channel or profile at [link], downloading its details and latest posts. */
    suspend fun follow(link: SocialLink): FollowResult {
        val fetched = when (link) {
            is SocialLink.YouTubeChannel -> fetchYouTube(link.url)
            is SocialLink.InstagramProfile -> fetchInstagram(link.username)
            else -> null
        } ?: return FollowResult.Error(L10n.str(R.string.follow_not_readable))
        val (info, posts) = fetched
        dao.findSource(info.network.name, info.key)?.let { return FollowResult.AlreadyFollowing(it.id) }
        val now = System.currentTimeMillis()
        val id = dao.insertSource(
            FollowedSourceEntity(
                network = info.network.name, key = info.key, handle = info.handle, name = info.name,
                avatarUrl = info.avatarUrl, url = info.url, addedAt = now, lastOpenedAt = now, checkedAt = now
            )
        )
        dao.replacePosts(id, posts.map { it.toEntity(id) })
        return FollowResult.Followed(id)
    }

    /** Downloads a source's latest posts again; returns an error to show, or null. */
    suspend fun refresh(sourceId: Long): String? {
        val source = dao.getSource(sourceId) ?: return null
        val fetched = when (SocialNetwork.valueOf(source.network)) {
            SocialNetwork.YOUTUBE -> fetchYouTube("https://www.youtube.com/channel/${source.key}")
            SocialNetwork.INSTAGRAM -> fetchInstagram(source.key)
        } ?: return L10n.str(R.string.follow_refresh_failed, source.name)
        val (info, posts) = fetched
        dao.updateSourceInfo(sourceId, info.name, info.handle ?: source.handle, info.avatarUrl ?: source.avatarUrl, System.currentTimeMillis())
        dao.replacePosts(sourceId, posts.map { it.toEntity(sourceId) })
        return null
    }

    /** Refreshes the sources not checked for [maxAgeMillis]; returns how many could not be read. */
    suspend fun refreshStale(maxAgeMillis: Long = STALE_AFTER_MILLIS): Int {
        val now = System.currentTimeMillis()
        val sources = dao.observeSources().first()
        return sources.filter { (it.checkedAt ?: 0L) < now - maxAgeMillis }.count { refresh(it.id) != null }
    }

    /** The user looked at a source: its posts stop being new. */
    suspend fun markOpened(sourceId: Long) = dao.markOpened(sourceId, System.currentTimeMillis())

    suspend fun unfollow(sourceId: Long) = dao.deleteSource(sourceId)

    suspend fun save(post: FavoritePost) {
        dao.insertSavedPost(
            SavedPostEntity(
                network = post.network.name, postKey = post.postKey, url = post.url, title = post.title,
                description = post.description, thumbnailUrl = post.thumbnailUrl, author = post.author,
                authorUrl = post.authorUrl, kind = post.kind.name, savedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun unsave(post: FavoritePost) = dao.deleteSavedPost(post.network.name, post.postKey)

    /** A video or reel from its link, to save it; null when its page cannot be read. */
    suspend fun postFromLink(link: SocialLink): FavoritePost? = when (link) {
        is SocialLink.YouTubeVideo -> {
            val page = RecipeUrlFetcher.fetchPage(link.url, RecipeUrlFetcher.PLAIN_USER_AGENT) as? PageFetchResult.Success
            val video = page?.let { withContext(AppDispatchers.default) { YouTubeRecipe.read(it.html, link.id) } }
                ?: (RecipeUrlFetcher.fetchPage(YouTubeRecipe.oEmbedUrl(link.id)) as? PageFetchResult.Success)?.let { YouTubeRecipe.readOEmbed(it.html, link.id) }
            video?.let {
                FavoritePost(
                    SocialNetwork.YOUTUBE, it.id, it.url, it.title, it.description.orEmpty(), it.thumbnailUrl,
                    PostKind.VIDEO, it.handle ?: it.author, it.channelUrl, 0L
                )
            }
        }
        is SocialLink.InstagramPost -> {
            val page = RecipeUrlFetcher.fetchPage(link.url) as? PageFetchResult.Success
            val embed = SocialPostRecipe.instagramEmbedUrl(link.url)?.let { url ->
                (RecipeUrlFetcher.fetchPage(url) as? PageFetchResult.Success)?.let { SocialPostRecipe.readInstagramEmbed(it.html) }
            }
            SocialPostRecipe.withEmbed(page?.let { SocialPostRecipe.read(it.html, link.url) }, embed, link.url)?.let { post ->
                val (title, text) = SocialFeeds.titleAndText(post.text)
                FavoritePost(
                    SocialNetwork.INSTAGRAM, link.shortcode, post.url, title, text, post.imageUrl,
                    if (SocialPostRecipe.isReel(post.url) || embed?.videoUrl != null) PostKind.REEL else PostKind.PHOTO,
                    post.author, post.authorUrl, 0L
                )
            }
        }
        else -> null
    }

    private suspend fun fetchYouTube(channelUrl: String): Pair<SourceInfo, List<FeedPost>>? {
        val page = RecipeUrlFetcher.fetchPage(channelUrl, RecipeUrlFetcher.PLAIN_USER_AGENT) as? PageFetchResult.Success ?: return null
        val info = withContext(AppDispatchers.default) { SocialFeeds.readYouTubeChannel(page.html) } ?: return null
        val feed = RecipeUrlFetcher.fetchPage(SocialFeeds.youTubeFeedUrl(info.key), RecipeUrlFetcher.PLAIN_USER_AGENT) as? PageFetchResult.Success
            ?: return null
        return info to withContext(AppDispatchers.default) { SocialFeeds.readYouTubeFeed(feed.html) }
    }

    private suspend fun fetchInstagram(username: String): Pair<SourceInfo, List<FeedPost>>? {
        val page = RecipeUrlFetcher.fetchPage(SocialFeeds.instagramProfileEmbedUrl(username)) as? PageFetchResult.Success ?: return null
        return withContext(AppDispatchers.default) { SocialFeeds.readInstagramProfile(page.html) }
    }

    private fun FeedPost.toEntity(sourceId: Long) = SourcePostEntity(
        sourceId = sourceId, postKey = postKey, url = url, title = title, description = description,
        thumbnailUrl = thumbnailUrl, publishedAt = publishedAt, kind = kind.name
    )

    companion object {
        private val LINK = Regex("""https?://[^\s<>"()\]]+""")

        /** Feeds are downloaded again when Favourites opens, at most once an hour. */
        const val STALE_AFTER_MILLIS = 60 * 60 * 1000L
    }
}
