package org.calamares.miga.data.follow

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import org.calamares.miga.data.search.SchemaOrgRecipe
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
    data class AlreadyFollowing(val sourceId: Long, val network: SocialNetwork) : FollowResult

    /** Another section of the website (or the whole site) is followed already: [sourceId], [name]. */
    data class SameSite(val sourceId: Long, val name: String) : FollowResult

    /** Its latest posts are not about cooking (see CookingFilter.isOffTopicSource). */
    data class NotAboutCooking(val name: String) : FollowResult
    data class Error(val reason: String) : FollowResult
}

/** Warnings the user has seen and accepted, so following goes ahead anyway. */
enum class FollowCheck { SAME_SITE, COOKING }

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
                // Posts not about cooking are hidden, so they are not counted as new either.
                newCount = bySource[source.id].orEmpty().count { it.publishedAt > source.lastOpenedAt && !isOffTopic(it.title, it.description) },
                checkedAt = source.checkedAt
            )
        }.sortedWith(compareByDescending<FollowedSource> { it.newCount > 0 }.thenBy { it.name.lowercase() })
    }.smooth()

    fun observeSource(id: Long): Flow<FollowedSource?> = observeSources().map { list -> list.firstOrNull { it.id == id } }

    /** Posts of followed sources (all of them, or [sourceId]'s), newest first, with their state. */
    /**
     * Posts of followed sources (all of them, or [sourceId]'s), newest first, with their state.
     * [extra] adds posts of [sourceId] that are not kept (a website's older recipes, a search).
     */
    fun observeFeed(sourceId: Long? = null, extra: Flow<List<FeedPost>>? = null): Flow<List<FavoritePost>> = combine(
        dao.observeSources(),
        if (sourceId == null) dao.observeAllPosts() else dao.observePosts(sourceId),
        dao.observeSavedPosts(),
        observeImported(),
        extra ?: flowOf(emptyList())
    ) { sources, kept, saved, imported, more ->
        val sourceById = sources.associateBy { it.id }
        val savedKeys = saved.map { "${it.network}:${it.postKey}" }.toSet()
        val keptKeys = kept.map { it.postKey }.toSet()
        val posts = kept + more.filter { it.postKey !in keptKeys }.map { post ->
            SourcePostEntity(
                sourceId = sourceId ?: 0L, postKey = post.postKey, url = post.url, title = post.title, description = post.description,
                thumbnailUrl = post.thumbnailUrl, publishedAt = post.publishedAt, kind = post.kind.name
            )
        }
        posts.mapNotNull { post ->
            val source = sourceById[post.sourceId] ?: return@mapNotNull null
            val network = SocialNetwork.valueOf(source.network)
            val isSaved = "${source.network}:${post.postKey}" in savedKeys
            val importedRecipe = imported["${source.network}:${post.postKey}"]
            val hasRecipe = readsWithoutAssistant(post.title, post.description, source.handle ?: source.name)
            // Recipe websites carry the recipe in the standard format: no assistant needed.
            val reads = hasRecipe || network == SocialNetwork.WEB
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
                offTopic = !isSaved && importedRecipe == null && !hasRecipe && isOffTopic(post.title, post.description)
            )
        // Two followed sections of one website can list the same recipe: show it once.
        }.distinctBy { it.id }
    }.smooth()

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
    }.smooth()

    /** Recipes imported from posts, by "NETWORK:postKey" (taken from the links in their sources). */
    private fun observeImported(): Flow<Map<String, ImportedRecipe>> = dao.observeRecipeSources().map { rows ->
        buildMap {
            rows.forEach { row ->
                LINK.findAll(row.source).forEach { match ->
                    when (val link = SocialFeeds.parse(match.value)) {
                        is SocialLink.YouTubeVideo -> put("YOUTUBE:${link.id}", ImportedRecipe(row.recipeId, row.bookName))
                        is SocialLink.InstagramPost -> put("INSTAGRAM:${link.shortcode}", ImportedRecipe(row.recipeId, row.bookName))
                        // A recipe from a website names its page.
                        else -> put("WEB:${WebFeeds.normalize(match.value)}", ImportedRecipe(row.recipeId, row.bookName))
                    }
                }
            }
        }
    }.flowOn(AppDispatchers.default)

    // What is worked out from a post's text (reading a recipe in it, telling whether it is about
    // cooking) costs regular expressions over the whole text: done once per text and remembered,
    // as the feeds are worked out again on every change of any source.
    private val readsCache = TextCache<Boolean>()
    private val offTopicCache = TextCache<Boolean>()

    private fun readsWithoutAssistant(title: String, description: String, author: String?): Boolean =
        readsCache.get("$author\u0000$title\u0000$description") {
            SocialPostRecipe.parse(SocialPost(listOf(title, description).joinToString("\n\n"), null, author, "", "")) != null
        }

    private fun isOffTopic(title: String, description: String): Boolean =
        offTopicCache.get("$title\u0000$description") { CookingFilter.isOffTopic(title, description) }

    /**
     * Feeds are worked out away from the main thread, and a burst of changes (a refresh writes
     * every source in turn) reaches the screen as one update, so the list does not jump while it
     * is scrolled.
     */
    @OptIn(FlowPreview::class)
    private fun <T> Flow<T>.smooth(): Flow<T> = flowOn(AppDispatchers.default).debounce(BURST_MILLIS).conflate()

    /**
     * Follows the channel, profile or website at [link], downloading its details and latest posts.
     * It asks first (a result the user answers) when the website is followed through another of its
     * sections or when the source is not about cooking, unless [accepted] says it was answered.
     */
    suspend fun follow(link: SocialLink, accepted: Set<FollowCheck> = emptySet()): FollowResult {
        if (link is SocialLink.Website) return followWebsite(link.url, accepted)
        val fetched = when (link) {
            is SocialLink.YouTubeChannel -> fetchYouTube(link.url)
            is SocialLink.InstagramProfile -> fetchInstagram(link.username)
            else -> null
        } ?: return FollowResult.Error(L10n.str(R.string.follow_not_readable))
        val (info, posts) = fetched
        dao.findSource(info.network.name, info.key)?.let { return FollowResult.AlreadyFollowing(it.id, info.network) }
        if (FollowCheck.COOKING !in accepted && CookingFilter.isOffTopicSource(listOfNotNull(info.name, info.handle).joinToString(" "), posts.map { it.title to it.description })) {
            return FollowResult.NotAboutCooking(info.name)
        }
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
        if (source.network == SocialNetwork.WEB.name) {
            val page = webPage(source, 1) ?: return L10n.str(R.string.follow_refresh_failed, source.name)
            dao.updateSourceInfo(sourceId, source.name, source.handle, source.avatarUrl, System.currentTimeMillis())
            dao.replacePosts(sourceId, page.posts.map { it.toEntity(sourceId) })
            return null
        }
        val fetched = when (SocialNetwork.valueOf(source.network)) {
            SocialNetwork.YOUTUBE -> fetchYouTube("https://www.youtube.com/channel/${source.key}")
            SocialNetwork.INSTAGRAM -> fetchInstagram(source.key)
            SocialNetwork.WEB -> null
        } ?: return L10n.str(R.string.follow_refresh_failed, source.name)
        val (info, posts) = fetched
        dao.updateSourceInfo(sourceId, info.name, info.handle ?: source.handle, info.avatarUrl ?: source.avatarUrl, System.currentTimeMillis())
        dao.replacePosts(sourceId, posts.map { it.toEntity(sourceId) })
        return null
    }

    /** Refreshes the sources not checked for [maxAgeMillis]; returns how many could not be read. */
    suspend fun refreshStale(maxAgeMillis: Long = STALE_AFTER_MILLIS): Int {
        val now = System.currentTimeMillis()
        val stale = dao.observeSources().first().filter { (it.checkedAt ?: 0L) < now - maxAgeMillis }
        // A few at a time: much faster than one after another, without flooding the network.
        val gate = Semaphore(PARALLEL_REFRESHES)
        return coroutineScope {
            stale.map { source -> async { gate.withPermit { refresh(source.id) } } }.awaitAll().count { it != null }
        }
    }

    /** What is stored about a source (a website's WordPress API, say). */
    suspend fun storedSource(sourceId: Long): FollowedSourceEntity? = dao.getSource(sourceId)

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
        is SocialLink.Website -> {
            val page = RecipeUrlFetcher.fetchPage(link.url) as? PageFetchResult.Success
            page?.let {
                val recipe = withContext(AppDispatchers.default) { SchemaOrgRecipe.parse(it.html, it.url) }
                val site = WebFeeds.readSite(it.html, it.url)
                val title = recipe?.recipe?.name ?: Regex("""<meta\s+property="og:title"\s+content="([^"]*)"""").find(it.html)?.groupValues?.get(1)
                    ?.let(RecipeUrlFetcher::decodeEntities) ?: return@let null
                FavoritePost(
                    SocialNetwork.WEB, WebFeeds.normalize(it.url), it.url, title, "",
                    recipe?.imageUrl ?: Regex("""<meta\s+property="og:image"\s+content="([^"]*)"""").find(it.html)?.groupValues?.get(1),
                    PostKind.ARTICLE, site.name, WebFeeds.originOf(it.url), 0L, readsWithoutAssistant = true
                )
            }
        }
        else -> null
    }

    // --- Recipe websites ---

    /** Recipes of a website, a page at a time; [next] is the page to ask for to get more. */
    data class WebPage(val posts: List<FeedPost>, val next: Int, val hasMore: Boolean)

    /** A followed website on the host of one of [urls] ("www." aside), or null. */
    suspend fun followedSite(vararg urls: String): FollowedSourceEntity? {
        val hosts = urls.map { WebFeeds.hostOf(it) }.filter { it.isNotEmpty() }.toSet()
        return dao.observeSources().first().firstOrNull { it.network == SocialNetwork.WEB.name && WebFeeds.hostOf(it.url) in hosts }
    }

    private suspend fun followWebsite(pageUrl: String, accepted: Set<FollowCheck>): FollowResult {
        val page = RecipeUrlFetcher.fetchPage(pageUrl) as? PageFetchResult.Success ?: return FollowResult.Error(L10n.str(R.string.follow_not_readable))
        val site = withContext(AppDispatchers.default) { WebFeeds.readSite(page.html, page.url) }
        // The address pasted first, then the one it led to (recetasderechupete.com goes to abc.es,
        // but keeps its feed and API at home).
        val origins = listOf(WebFeeds.originOf(pageUrl), WebFeeds.originOf(page.url)).distinct()
        val feed = WebFeeds.chooseFeed(site.feeds, page.url) ?: origins.flatMap { origin -> WebFeeds.COMMON_FEEDS.map { origin + it } }.firstOrNull { url ->
            (RecipeUrlFetcher.fetchPage(url) as? PageFetchResult.Success)?.let { WebFeeds.readFeed(it.html, url).isNotEmpty() } == true
        }
        // WordPress: a section is followed through its category when it has one with posts; else
        // through its feed, a page at a time.
        val api = origins.map { WebFeeds.wpApiOf(it) }.firstOrNull { api ->
            (RecipeUrlFetcher.fetchPage(WebFeeds.wpPostsUrl(api, null, 1).replace("per_page=${WebFeeds.PAGE_SIZE}", "per_page=1")) as? PageFetchResult.Success)
                ?.let { WebFeeds.readWpPosts(it.html) } != null
        }
        val slug = WebFeeds.slugOf(pageUrl)
        val category = when {
            api == null -> null
            slug == null -> null
            else -> (RecipeUrlFetcher.fetchPage(WebFeeds.wpCategoryUrl(api, slug)) as? PageFetchResult.Success)?.let { WebFeeds.readWpCategory(it.html) }
                ?: FEED_PAGES.takeIf { feed != null }
        }
        if (feed == null && api == null) return FollowResult.Error(L10n.str(R.string.follow_web_no_feed))
        val key = feed ?: (api + (category?.let { "?category=$it" } ?: ""))
        dao.findSource(SocialNetwork.WEB.name, key)?.let { return FollowResult.AlreadyFollowing(it.id, SocialNetwork.WEB) }
        // Two sections of one website share recipes: say which is followed and let the user choose.
        if (FollowCheck.SAME_SITE !in accepted) followedSite(pageUrl, page.url)?.let { return FollowResult.SameSite(it.id, it.name) }
        val now = System.currentTimeMillis()
        val entity = FollowedSourceEntity(
            network = SocialNetwork.WEB.name, key = key, handle = WebFeeds.hostOf(page.url), name = site.name,
            avatarUrl = site.iconUrl, url = page.url, addedAt = now, lastOpenedAt = now, checkedAt = now,
            wpApi = api, wpCategory = category
        )
        val first = webPage(entity, 1) ?: return FollowResult.Error(L10n.str(R.string.follow_not_readable))
        if (first.posts.isEmpty()) return FollowResult.Error(L10n.str(R.string.follow_web_no_feed))
        // A recipe's address usually says it is one ("/recetas/…"): it counts with its title.
        if (FollowCheck.COOKING !in accepted &&
            CookingFilter.isOffTopicSource("${site.name} ${WebFeeds.hostOf(page.url)}", first.posts.map { it.title to "${it.description} ${it.url}" })
        ) {
            return FollowResult.NotAboutCooking(site.name)
        }
        val id = dao.insertSource(entity)
        dao.replacePosts(id, first.posts.map { it.toEntity(id) })
        return FollowResult.Followed(id)
    }

    /** More recipes of a followed website, from page [from]; null when they cannot be read. */
    suspend fun moreFromWebsite(sourceId: Long, from: Int, known: Set<String> = emptySet()): WebPage? =
        dao.getSource(sourceId)?.let { webPage(it, from, known) }

    /** Recipes of a followed WordPress website that match [query]; null when it cannot search. */
    suspend fun searchWebsite(sourceId: Long, query: String): List<FeedPost>? {
        val source = dao.getSource(sourceId) ?: return null
        val api = source.wpApi ?: return null
        val category = source.wpCategory?.takeIf { it > 0 }
        val body = (RecipeUrlFetcher.fetchPage(WebFeeds.wpPostsUrl(api, category, 1, query)) as? PageFetchResult.Success)?.html ?: return null
        val posts = WebFeeds.readWpPosts(body) ?: return null
        return WebFeeds.toFeedPosts(posts, wpMedia(api, posts.map { it.mediaId }))
    }

    /**
     * A page of a website's recipes: through WordPress's API ([WebFeeds.PAGE_SIZE] at a time) or
     * through its feed, reading as many of its pages as it takes to get as many recipes.
     */
    private suspend fun webPage(source: FollowedSourceEntity, from: Int, known: Set<String> = emptySet()): WebPage? {
        val api = source.wpApi
        if (api != null && source.wpCategory != FEED_PAGES) {
            val body = (RecipeUrlFetcher.fetchPage(WebFeeds.wpPostsUrl(api, source.wpCategory, from)) as? PageFetchResult.Success)?.html
            // WordPress answers past the last page with an error: there is no more.
            val posts = body?.let { WebFeeds.readWpPosts(it) } ?: return if (from > 1) WebPage(emptyList(), from, false) else null
            return WebPage(WebFeeds.toFeedPosts(posts, wpMedia(api, posts.map { it.mediaId })), from + 1, posts.size >= WebFeeds.PAGE_SIZE)
        }
        val feed = source.key.takeIf { it.startsWith("http") && "wp-json" !in it } ?: return null
        val collected = LinkedHashMap<String, FeedPost>()
        var page = from
        var more = true
        // Recipes already shown ([known]) are skipped, so a page brings new ones; a feed that does
        // not go by pages repeats them all, and the caller notices nothing new came.
        while (collected.size < WebFeeds.PAGE_SIZE && page < from + MAX_FEED_PAGES) {
            val body = (RecipeUrlFetcher.fetchPage(WebFeeds.pagedFeedUrl(feed, page)) as? PageFetchResult.Success)?.html
            val posts = body?.let { withContext(AppDispatchers.default) { WebFeeds.readFeed(it, feed) } }.orEmpty()
            page++
            // Past the last page WordPress answers with an error.
            if (posts.isEmpty()) {
                more = false
                break
            }
            posts.filter { it.postKey !in collected && it.postKey !in known }.forEach { collected[it.postKey] = it }
        }
        if (collected.isEmpty() && from == 1) return null
        val withPictures = if (api != null) pictures(api, collected.values.toList()) else collected.values.toList()
        return WebPage(withPictures, page, more && collected.isNotEmpty())
    }

    /** Feeds without pictures (some WordPress sites): their posts' pictures, through the API. */
    private suspend fun pictures(api: String, posts: List<FeedPost>): List<FeedPost> {
        val missing = posts.filter { it.thumbnailUrl == null }
        if (missing.isEmpty()) return posts
        val body = (RecipeUrlFetcher.fetchPage(WebFeeds.wpPostsBySlugUrl(api, missing.map { it.url })) as? PageFetchResult.Success)?.html ?: return posts
        val byLink = WebFeeds.readWpPosts(body).orEmpty().associate { WebFeeds.normalize(it.link) to it.mediaId }
        val media = wpMedia(api, byLink.values.toList())
        return posts.map { post -> if (post.thumbnailUrl != null) post else post.copy(thumbnailUrl = byLink[post.postKey]?.let { media[it] }) }
    }

    private suspend fun wpMedia(api: String, ids: List<Long>): Map<Long, String> {
        val wanted = ids.filter { it > 0 }.distinct()
        if (wanted.isEmpty()) return emptyMap()
        val body = (RecipeUrlFetcher.fetchPage(WebFeeds.wpMediaUrl(api, wanted)) as? PageFetchResult.Success)?.html ?: return emptyMap()
        return withContext(AppDispatchers.default) { WebFeeds.readWpMedia(body) }
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

        /** A website section followed through its feed's pages (see FollowedSourceEntity.wpCategory). */
        const val FEED_PAGES = -1L

        /** Feed pages read at most for one page of recipes. */
        private const val MAX_FEED_PAGES = 6

        /** Changes closer together than this reach the screen as one (see smooth). */
        private const val BURST_MILLIS = 150L

        /** Sources downloaded at the same time when refreshing. */
        private const val PARALLEL_REFRESHES = 4

        /** Feeds are downloaded again when Favourites opens, at most once an hour. */
        const val STALE_AFTER_MILLIS = 60 * 60 * 1000L
    }
}
