package org.calamares.miga.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.calamares.miga.data.support.AppDispatchers
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.follow.FavoritePost
import org.calamares.miga.data.follow.FeedPost
import org.calamares.miga.data.follow.FollowRepository
import org.calamares.miga.data.follow.FollowCheck
import org.calamares.miga.data.follow.FollowResult
import org.calamares.miga.data.follow.SocialFeeds
import org.calamares.miga.data.follow.FollowedSource
import org.calamares.miga.data.follow.SocialLink
import org.calamares.miga.data.follow.SocialNetwork
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBookSummary
import org.calamares.miga.data.repository.RecipeRepository

/** The views of the Favourites tab. */
enum class FavoritesFilter { ALL, RECIPES, CHANNELS, POSTS }

/** What the "Add" sheet found in a pasted link. */
sealed interface AddState {
    data object Idle : AddState
    data object Working : AddState
    data class Found(val link: SocialLink) : AddState
    data class NotSupported(val text: String) : AddState

    /**
     * Following [link] needs an answer to [question] ([check]); [followedId] is the source already
     * followed on the same website, to open it instead.
     */
    data class Ask(val link: SocialLink, val question: String, val check: FollowCheck, val accepted: Set<FollowCheck>, val followedId: Long? = null) : AddState
}

/**
 * Favourites: saved recipes, followed YouTube channels and Instagram profiles with their latest
 * posts, and saved videos and reels. Feeds are downloaded again when the tab opens, at most once an
 * hour, and on demand.
 */
class FavoritesViewModel(private val repository: RecipeRepository) : ViewModel() {

    private val follows = repository.follows

    private val _filter = MutableStateFlow(FavoritesFilter.ALL)
    val filter: StateFlow<FavoritesFilter> = _filter.asStateFlow()

    /** YouTube or Instagram only, in the channels and posts views; null for both. */
    private val _network = MutableStateFlow<SocialNetwork?>(null)
    val network: StateFlow<SocialNetwork?> = _network.asStateFlow()

    private val _onlyNotImported = MutableStateFlow(false)
    val onlyNotImported: StateFlow<Boolean> = _onlyNotImported.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _addState = MutableStateFlow<AddState>(AddState.Idle)
    val addState: StateFlow<AddState> = _addState.asStateFlow()

    /** Set after following from the "Add" sheet: the screen opens the new source. */
    private val _openSource = MutableStateFlow<Long?>(null)
    val openSource: StateFlow<Long?> = _openSource.asStateFlow()

    val sources: StateFlow<List<FollowedSource>> = follows.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Posts of the followed sources; the ones not about cooking are left out. */
    val feed: StateFlow<List<FavoritePost>> = follows.observeFeed().map { list -> list.filterNot { it.offTopic } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val saved: StateFlow<List<FavoritePost>> = follows.observeSavedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteRecipes: StateFlow<List<Recipe>> = repository.observeAllRecipes().map { list -> list.filter { it.isFavorite } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Books a recipe can be imported into (packs are read-only). */
    val books: StateFlow<List<RecipeBookSummary>> = repository.books.observeRecipeBooks().map { list -> list.filterNot { it.isPack } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleSources: StateFlow<List<FollowedSource>> = combine(sources, _network) { list, network ->
        list.filter { network == null || it.network == network }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visibleSaved: StateFlow<List<FavoritePost>> = combine(saved, _network, _onlyNotImported) { list, network, notImported ->
        list.filter { (network == null || it.network == network) && (!notImported || it.imported == null) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh(onlyStale = true)
    }

    fun setFilter(filter: FavoritesFilter) {
        _filter.value = filter
    }

    fun setNetwork(network: SocialNetwork?) {
        _network.value = network
    }

    fun setOnlyNotImported(only: Boolean) {
        _onlyNotImported.value = only
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun consumeOpenSource() {
        _openSource.value = null
    }

    /** Downloads the feeds again: all of them, or only those not checked in the last hour. */
    fun refresh(onlyStale: Boolean = false) {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val failed = follows.refreshStale(if (onlyStale) FollowRepository.STALE_AFTER_MILLIS else 0L)
            _refreshing.value = false
            if (failed > 0 && !onlyStale) _message.value = L10n.plural(R.plurals.follow_some_failed, failed)
        }
    }

    /** What a link pasted in the "Add" sheet is. */
    fun inspect(text: String) {
        val url = Regex("""https?://\S+""").find(text)?.value
        // Any other web page is a recipe website to follow, or a recipe to import or save.
        val link = url?.let { SocialFeeds.parse(it) ?: SocialLink.Website(it) }
        _addState.value = when {
            text.isBlank() -> AddState.Idle
            link != null -> AddState.Found(link)
            else -> AddState.NotSupported(L10n.str(R.string.favorites_add_not_supported))
        }
    }

    /** "Follow anyway" after a warning. */
    fun followAnyway(ask: AddState.Ask) = follow(ask.link, ask.accepted + ask.check)

    /** Opens the source already followed on the same website instead of following another. */
    fun openFollowed(ask: AddState.Ask) {
        _addState.value = AddState.Idle
        _openSource.value = ask.followedId
    }

    fun resetAdd() {
        _addState.value = AddState.Idle
    }

    /** Follows [link]; [accepted] are the warnings the user has already answered with "Follow anyway". */
    fun follow(link: SocialLink, accepted: Set<FollowCheck> = emptySet()) {
        viewModelScope.launch {
            _addState.value = AddState.Working
            when (val result = follows.follow(link, accepted)) {
                is FollowResult.Followed -> {
                    _addState.value = AddState.Idle
                    _openSource.value = result.sourceId
                }
                is FollowResult.AlreadyFollowing -> {
                    _addState.value = AddState.Idle
                    _message.value = L10n.str(if (result.network == SocialNetwork.WEB) R.string.follow_already_web else R.string.follow_already)
                    _openSource.value = result.sourceId
                }
                is FollowResult.SameSite -> _addState.value =
                    AddState.Ask(link, L10n.str(R.string.follow_same_site, result.name), FollowCheck.SAME_SITE, accepted, result.sourceId)
                is FollowResult.NotAboutCooking -> _addState.value =
                    AddState.Ask(link, L10n.str(R.string.follow_not_cooking, result.name), FollowCheck.COOKING, accepted)
                is FollowResult.Error -> {
                    _addState.value = AddState.Found(link)
                    _message.value = result.reason
                }
            }
        }
    }

    /** Saves the video or reel at [link] (its title and thumbnail are downloaded first). */
    fun saveLink(link: SocialLink) {
        viewModelScope.launch {
            _addState.value = AddState.Working
            val post = follows.postFromLink(link)
            if (post == null) {
                _addState.value = AddState.Found(link)
                _message.value = L10n.str(R.string.follow_not_readable)
                return@launch
            }
            follows.save(post)
            _addState.value = AddState.Idle
            _filter.value = FavoritesFilter.POSTS
            _message.value = L10n.str(R.string.favorites_post_saved)
        }
    }

    fun toggleSaved(post: FavoritePost) {
        viewModelScope.launch {
            if (post.isSaved) {
                follows.unsave(post)
                _message.value = L10n.str(R.string.favorites_post_removed)
            } else {
                follows.save(post)
                _message.value = L10n.str(R.string.favorites_post_saved)
            }
        }
    }
}

/** One channel's or profile's view: its latest posts, refreshed when opened if they are old. */
class SourceViewModel(private val repository: RecipeRepository, val sourceId: Long) : ViewModel() {

    private val follows = repository.follows

    val source: StateFlow<FollowedSource?> = follows.observeSource(sourceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** A website's older recipes loaded with "More recipes", or the results of a search in it. */
    private val extra = MutableStateFlow<List<FeedPost>>(emptyList())

    private val allPosts: StateFlow<List<FavoritePost>> = follows.observeFeed(sourceId, extra)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var nextPage = 0
    private val _canLoadMore = MutableStateFlow(false)
    /** A website with more recipes than those shown. */
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    /** The website can be searched (WordPress). */
    val canSearch = MutableStateFlow(false)

    /** The search shown, or null for the latest recipes. */
    private val _query = MutableStateFlow<String?>(null)
    val query: StateFlow<String?> = _query.asStateFlow()
    private var latest: List<FeedPost> = emptyList()

    /** Posts not about cooking are left out until the user asks to see them. */
    val showOffTopic = MutableStateFlow(false)

    val posts: StateFlow<List<FavoritePost>> = combine(allPosts, showOffTopic, _query, extra) { list, all, query, more ->
        // A search shows only what it found.
        val shown = if (query != null) more.map { it.postKey }.toSet().let { found -> list.filter { it.postKey in found } } else list
        if (all) shown else shown.filterNot { it.offTopic }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** How many posts are left out. */
    val hiddenCount: StateFlow<Int> = combine(allPosts, showOffTopic) { list, all -> if (all) 0 else list.count { it.offTopic } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val books: StateFlow<List<RecipeBookSummary>> = repository.books.observeRecipeBooks().map { list -> list.filterNot { it.isPack } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val grid = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val stored = follows.storedSource(sourceId)
            if (stored?.network == SocialNetwork.WEB.name) {
                canSearch.value = stored.wpApi != null
                nextPage = 2
                _canLoadMore.value = true
            }
            val checked = follows.observeSource(sourceId).firstOrNull()?.checkedAt
            if ((checked ?: 0L) < System.currentTimeMillis() - FollowRepository.STALE_AFTER_MILLIS) refresh()
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _message.value = follows.refresh(sourceId)
            _refreshing.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /** The website's next recipes (see FollowRepository.moreFromWebsite). */
    fun loadMore() {
        if (_loadingMore.value || !_canLoadMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            val known = (allPosts.value.map { it.postKey } + extra.value.map { it.postKey }).toSet()
            val page = follows.moreFromWebsite(sourceId, nextPage, known)
            if (page == null) {
                _message.value = L10n.str(R.string.favorites_more_failed)
            } else {
                val fresh = page.posts.filter { it.postKey !in known }
                extra.value = extra.value + fresh
                nextPage = page.next
                // A feed that does not go by pages gives the same recipes again: there are no more.
                _canLoadMore.value = page.hasMore && fresh.isNotEmpty()
            }
            _loadingMore.value = false
        }
    }

    /** Searches the website (blank goes back to its latest recipes). */
    fun search(text: String) {
        val query = text.trim()
        if (query.isEmpty()) {
            if (_query.value != null) {
                _query.value = null
                extra.value = latest
            }
            return
        }
        viewModelScope.launch {
            _refreshing.value = true
            val results = follows.searchWebsite(sourceId, query)
            _refreshing.value = false
            if (results == null) {
                _message.value = L10n.str(R.string.favorites_more_failed)
                return@launch
            }
            if (_query.value == null) latest = extra.value
            _query.value = query
            extra.value = results
        }
    }

    /**
     * Leaving the view: its posts stop being new. Not in [viewModelScope], which is cancelled as
     * the view closes.
     */
    fun markOpened() {
        CoroutineScope(AppDispatchers.io).launch { follows.markOpened(sourceId) }
    }

    fun unfollow(onDone: () -> Unit) {
        viewModelScope.launch {
            follows.unfollow(sourceId)
            onDone()
        }
    }

    fun toggleSaved(post: FavoritePost) {
        viewModelScope.launch { if (post.isSaved) follows.unsave(post) else follows.save(post) }
    }
}
