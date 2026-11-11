package org.calamares.miga.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.stats.LibraryStats
import org.calamares.miga.data.stats.LibraryStatsCalculator
import org.calamares.miga.data.stats.StorageUsage
import org.calamares.miga.data.stats.StorageUsageScanner
import org.calamares.miga.data.support.AppDispatchers

/** Name of Coil's disk cache folder inside the cache directory. */
private const val IMAGE_CACHE_DIR = "image_cache"

data class StatsUiState(
    val isLoading: Boolean = true,
    val stats: LibraryStats? = null,
    /** Null while the app's files are being measured. */
    val storage: StorageUsage? = null,
    /** True while the cache or unused photos are being deleted. */
    val storageBusy: Boolean = false
)

/**
 * Statistics of the recipe library, computed in memory from every recipe and book, and the space
 * the app takes on the device. The data model has no cooking history (only the times cooked
 * counter), so there are rankings but no streaks.
 */
class StatsViewModel(
    private val repository: RecipeRepository,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState

    init {
        viewModelScope.launch {
            val recipes = repository.getAllRecipesOnce()
            val books = repository.books.getAllRecipeBooksOnce()
            val stats = withContext(AppDispatchers.default) {
                LibraryStatsCalculator.compute(
                    recipes = recipes,
                    books = books,
                    now = System.currentTimeMillis(),
                    uncategorized = L10n.str(R.string.uncategorized),
                    others = L10n.str(R.string.stats_others)
                )
            }
            _uiState.update { it.copy(isLoading = false, stats = stats) }
            measureStorage()
        }
    }

    /** File names of the photos used by a recipe or a book cover. */
    private suspend fun referencedPhotoNames(): Set<String> {
        val recipes = repository.getAllRecipesOnce()
        val books = repository.books.getAllRecipeBooksOnce()
        return StorageUsageScanner.referencedNames(recipes.flatMap { recipe -> recipe.photos.map { it.uri } } + books.map { it.coverPhotoUri })
    }

    private suspend fun measureStorage() {
        val referenced = referencedPhotoNames()
        val usage = withContext(AppDispatchers.io) { StorageUsageScanner.scan(appContext, referenced, System.currentTimeMillis()) }
        _uiState.update { it.copy(storage = usage, storageBusy = false) }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearCache(onDone: (String) -> Unit) {
        if (_uiState.value.storageBusy) return
        _uiState.update { it.copy(storageBusy = true) }
        viewModelScope.launch {
            withContext(AppDispatchers.io) {
                val loader = appContext.imageLoader
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
                StorageUsageScanner.clearCache(appContext, keep = setOf(IMAGE_CACHE_DIR))
            }
            measureStorage()
            onDone(L10n.str(R.string.stats_cache_cleared))
        }
    }

    fun deleteUnusedPhotos(onDone: (String) -> Unit) {
        if (_uiState.value.storageBusy) return
        _uiState.update { it.copy(storageBusy = true) }
        viewModelScope.launch {
            // Read again right before deleting, so a photo added meanwhile is never removed.
            val referenced = referencedPhotoNames()
            val deleted = withContext(AppDispatchers.io) {
                StorageUsageScanner.deleteUnusedPhotos(appContext, referenced, System.currentTimeMillis())
            }
            measureStorage()
            onDone(L10n.str(R.string.stats_unused_photos_deleted_x, deleted))
        }
    }
}
