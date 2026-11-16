package org.calamares.miga.ui.detail

import org.calamares.miga.ui.theme.recipePhotoFrame
import org.calamares.miga.ui.components.ErrorMessage
import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.formatQuantity
import org.calamares.miga.ui.theme.HealthAmberContainer
import org.calamares.miga.ui.theme.HealthAmberOn
import org.calamares.miga.ui.theme.HealthGreenContainer
import org.calamares.miga.ui.theme.HealthGreenOn
import org.calamares.miga.ui.theme.HealthRedContainer
import org.calamares.miga.ui.theme.HealthRedOn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun HealthBlock(recipe: Recipe, healthState: HealthState, onRetry: () -> Unit) {
    when (healthState) {
        HealthState.Loading -> LoadingRow()
        HealthState.NotConfigured -> NoticeText(L10n.str(R.string.health_needs_assistant))
        HealthState.PackPending -> NoticeText(L10n.str(R.string.health_pack_pending))
        is HealthState.Error -> ErrorMessage(healthState.reason, onRetry = onRetry)
        HealthState.Loaded -> recipe.healthRating?.let { rating ->
            val (containerColor, contentColor, label) = when (rating.color) {
                HealthColorLevel.GREEN -> Triple(HealthGreenContainer, HealthGreenOn, L10n.str(R.string.healthy))
                HealthColorLevel.YELLOW -> Triple(HealthAmberContainer, HealthAmberOn, L10n.str(R.string.moderate))
                HealthColorLevel.RED -> Triple(HealthRedContainer, HealthRedOn, L10n.str(R.string.not_very_healthy))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(label) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = containerColor, labelColor = contentColor)
                )
                Text(rating.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        HealthState.Idle -> Unit
    }
}

@Composable
internal fun NutritionBlock(recipe: Recipe, nutritionState: NutritionState, onRetry: () -> Unit) {
    when (nutritionState) {
        NutritionState.Loading -> LoadingRow()
        NutritionState.NotConfigured -> NoticeText(L10n.str(R.string.health_needs_assistant))
        NutritionState.PackPending -> NoticeText(L10n.str(R.string.health_pack_pending))
        is NutritionState.Error -> ErrorMessage(nutritionState.reason, onRetry = onRetry)
        NutritionState.Loaded -> recipe.nutritionInfo?.let { nutrition ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L10n.str(R.string.nutrition_per_serving), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    NutritionStat(value = "${nutrition.caloriesPerServing}", label = "kcal")
                    NutritionStat(value = formatQuantity(nutrition.proteinGrams), label = L10n.str(R.string.protein_g))
                    NutritionStat(value = formatQuantity(nutrition.carbsGrams), label = L10n.str(R.string.carbs_g))
                    NutritionStat(value = formatQuantity(nutrition.fatGrams), label = L10n.str(R.string.fat_g))
                }
            }
        }
        NutritionState.Idle -> Unit
    }
}

@Composable
internal fun NoticeText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun LoadingRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            L10n.str(R.string.analysing_ai),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/** Full-bleed header photo(s), swipeable when there are several, with a bottom gradient. */
/** The recipe photos in display order: the cover first. */
internal fun Recipe.viewablePhotos(): List<String> =
    photos.sortedByDescending { it.isCover }.map { it.uri }.ifEmpty { listOfNotNull(coverPhotoUri) }

/** Lets scrolling left over at the first or last photo reach the parent pager. */
internal object PassThroughScrollConnection : NestedScrollConnection

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoHeader(recipe: Recipe, onPhotoClick: (Int) -> Unit) {
    val photos = remember(recipe.photos, recipe.coverPhotoUri) { recipe.viewablePhotos() }
    val pagerState = rememberPagerState(pageCount = { photos.size })
    Box(modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).background(MaterialTheme.colorScheme.surfaceVariant)) {
        HorizontalPager(
            state = pagerState,
            // With a single photo there is nothing to swipe here, so the gesture reaches the pager
            // that turns to the next recipe; with several, it does once the last photo is reached.
            userScrollEnabled = photos.size > 1,
            pageNestedScrollConnection = PassThroughScrollConnection,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            AsyncImage(
                model = photos[page],
                contentDescription = recipe.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().recipePhotoFrame().clickable { onPhotoClick(page) }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
        )
        if (photos.size > 1) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                photos.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (index == pagerState.currentPage) 1f else 0.6f))
                    )
                }
            }
        }
    }
}

@Composable
internal fun FactTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun GroupTitle(name: String) {
    Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

/** Rounded container like the Settings groups. */
@Composable
internal fun RoundedGroup(contentPadding: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(contentPadding)
    ) { content() }
}

internal const val MAX_RATING_STARS = 5

@Composable
internal fun StarRatingRow(rating: Int?, onRatingChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..MAX_RATING_STARS) {
            val filled = rating != null && star <= rating
            IconButton(onClick = { onRatingChange(star) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = L10n.str(R.string.stars_x, star),
                    tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ServingsStepper(servings: Int, onStep: (Int) -> Unit) {
    Row(
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Disabled at the limits, so it is clear why nothing changes.
        IconButton(onClick = { onStep(-1) }, enabled = servings > MIN_SERVINGS) {
            Icon(Icons.Filled.Remove, contentDescription = L10n.str(R.string.fewer_servings))
        }
        Text("$servings", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onStep(1) }, enabled = servings < MAX_SERVINGS) {
            Icon(Icons.Filled.Add, contentDescription = L10n.str(R.string.more_servings))
        }
    }
}

internal const val MIN_SERVINGS = 1
internal const val MAX_SERVINGS = 99

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
internal fun NutritionStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
