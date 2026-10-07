package org.calamares.miga.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Recipe detail that turns like the pages of a book: swiping left or right opens the next or
 * previous recipe of [recipeIds] (the order of the list it was opened from), and stops at the first
 * and the last one. Swiping is off while cooking mode or the photo viewer is open.
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
    var fullScreen by remember { mutableStateOf(false) }
    HorizontalPager(
        state = pagerState,
        userScrollEnabled = !fullScreen && recipeIds.size > 1,
        key = { page -> recipeIds[page] }
    ) { page ->
        val recipeId = recipeIds[page]
        val active = pagerState.settledPage == page
        RecipeDetailScreen(
            viewModel = viewModelFor(recipeId),
            onBack = onBack,
            onEdit = { onEdit(recipeId) },
            active = active,
            onFullScreenChange = { open -> if (active) fullScreen = open }
        )
    }
}
