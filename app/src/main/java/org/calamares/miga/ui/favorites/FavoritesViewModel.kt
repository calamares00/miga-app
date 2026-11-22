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
import org.calamares.miga.data.follow.FollowRepository
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

    val feed: StateFlow<List<FavoritePost>> = follows.observeFeed()
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
            if (failed > 0 && !onlyStale) _message.value = L10n.str(R.string.follow_some_failed, failed)
        }
    }

    /** What a link pasted in the "Add" sheet is. */
    fun inspect(text: String) {
        val url = Regex("""https?://\S+""").find(text)?.value
        val link = url?.let { SocialFeeds.parse(it) }
        _addState.value = when {
            text.isBlank() -> AddState.Idle
            link != null -> AddState.Found(link)
            else -> AddState.NotSupported(L10n.str(R.string.favorites_add_not_supported))
        }
    }

    fun resetAdd() {
        _addState.value = AddState.Idle
    }

    fun follow(link: SocialLink) {
        viewModelScope.launch {
            _addState.value = AddState.Working
            when (val result = follows.follow(link)) {
                is FollowResult.Followed -> {
                    _addState.value = AddState.Idle
                    _openSource.value = result.sourceId
                }
                is FollowResult.AlreadyFollowing -> {
                    _addState.value = AddState.Idle
                    _message.value = L10n.str(R.string.follow_already)
                    _openSource.value = result.sourceId
                }
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

    val posts: StateFlow<List<FavoritePost>> = follows.observeFeed(sourceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val books: StateFlow<List<RecipeBookSummary>> = repository.books.observeRecipeBooks().map { list -> list.filterNot { it.isPack } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val grid = MutableStateFlow(false)

    init {
        viewModelScope.launch {
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
