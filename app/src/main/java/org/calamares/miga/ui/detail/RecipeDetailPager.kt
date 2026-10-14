package org.calamares.miga.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.ui.components.PhotoViewer

/** A full-screen layer drawn over the recipe pager. */
private sealed interface DetailOverlay {
    data class CookMode(val recipe: Recipe, val ttsVoiceName: String?) : DetailOverlay
    data class Photos(val recipe: Recipe, val index: Int) : DetailOverlay
}

/**
 * Recipe detail that turns like the pages of a book: swiping left or right opens the next or
 * previous recipe of [recipeIds] (the order of the list it was opened from), and stops at the first
 * and the last one. Cooking mode and the photo viewer are drawn here, over the whole pager, rather
 * than inside one of its pages, and swiping is off while either is open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipeDetailPager(
    recipeIds: List<Long>,
    initialRecipeId: Long,
    viewModelFor: @Composable (Long) -> RecipeDetailViewModel,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = recipeIds.indexOf(initialRecipeId).coerceAtLeast(0)) { recipeIds.size }
    var overlay by remember { mutableStateOf<DetailOverlay?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = overlay == null && recipeIds.size > 1,
            key = { page -> recipeIds[page] }
        ) { page ->
            val recipeId = recipeIds[page]
            RecipeDetailScreen(
                viewModel = viewModelFor(recipeId),
                onBack = onBack,
                onEdit = { onEdit(recipeId) },
                active = pagerState.settledPage == page,
                onOpenCookMode = { recipe, voice -> overlay = DetailOverlay.CookMode(recipe, voice) },
                onOpenPhoto = { recipe, index -> overlay = DetailOverlay.Photos(recipe, index) }
            )
        }
        when (val current = overlay) {
            is DetailOverlay.CookMode -> CookModeOverlay(
                recipe = current.recipe,
                ttsVoiceName = current.ttsVoiceName,
                onClose = { overlay = null }
            )
            is DetailOverlay.Photos -> PhotoViewer(
                photos = current.recipe.viewablePhotos(),
                initialIndex = current.index,
                contentDescription = current.recipe.name,
                onClose = { overlay = null }
            )
            null -> Unit
        }
    }
}
