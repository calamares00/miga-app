package org.calamares.miga.ui.detail

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ExperimentalMaterial3Api
import org.calamares.miga.data.health.ingredientWarning
import org.calamares.miga.data.model.SourceText
import org.calamares.miga.data.health.IngredientWarning
import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.allergens.AllergenLevel
import org.calamares.miga.ui.components.FormattedText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.ContentCopy
import android.widget.Toast
import android.os.Build
import android.content.ClipboardManager
import android.content.ClipData
import org.calamares.miga.data.model.formatIngredientText
import org.calamares.miga.data.model.Ingredient
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

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
internal fun StarRatingRow(rating: Int?, onRatingChange: (Int) -> Unit, small: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (star in 1..MAX_RATING_STARS) {
            val filled = rating != null && star <= rating
            IconButton(onClick = { onRatingChange(star) }, modifier = Modifier.size(if (small) 30.dp else 36.dp)) {
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = L10n.str(R.string.stars_x, star),
                    tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (small) 18.dp else 24.dp)
                )
            }
        }
    }
}

/** The small separator between category, origin and rating. */
@Composable
internal fun Dot() {
    Text("  ·  ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One of the recipe's key facts; [description] replaces the text for screen readers. */
internal class Fact(val icon: ImageVector, val text: String, val description: String?)

/** The key facts side by side in one strip. */
@Composable
internal fun FactsStrip(facts: List<Fact>) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            facts.forEach { fact ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics(mergeDescendants = true) { fact.description?.let { contentDescription = it } }
                ) {
                    Icon(fact.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(fact.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/**
 * One ingredient as written ("50 g de panceta"), the amount in the theme colour so it still
 * stands out, with a warning when an allergen depends on the product. Tap ticks it; a long press
 * offers a substitute.
 */
/** "Puede llevar gluten y apio según el producto: mira la etiqueta" / "Lleva huevo". */
internal fun warningText(warning: IngredientWarning): String {
    val names = warning.allergens.map { it.label.lowercase(L10n.locale()) }
    val list = if (names.size > 1) names.dropLast(1).joinToString(", ") + L10n.str(R.string.list_and) + names.last() else names.single()
    return if (warning.level == AllergenLevel.CONTAINS) L10n.str(R.string.ingredient_contains_x, list)
    else L10n.str(R.string.ingredient_may_contain_x, list)
}

/** The ⚠ next to an ingredient; a tap says which allergens and why. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllergenWarningIcon(warning: IngredientWarning) {
    val message = warningText(warning)
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(message) } },
        state = state
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = message,
            tint = if (warning.level == AllergenLevel.CONTAINS) ALLERGEN_RED else ALLERGEN_AMBER,
            modifier = Modifier
                .padding(start = 2.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = message) { scope.launch { state.show() } }
                .padding(4.dp)
                .size(16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IngredientLine(
    ingredient: Ingredient,
    scale: Double,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onSubstitute: (() -> Unit)?,
    /** Allergens to warn about (see HealthPrefs.ingredientWarnings): null for all. */
    watchedAllergens: Set<Allergen>? = null
) {
    val text = formatIngredientText(ingredient.name, ingredient.quantity, ingredient.unit, scale, forReading = true) +
        if (ingredient.optional) " " + L10n.str(R.string.ingredient_optional_suffix) else ""
    val warning = remember(ingredient.name, watchedAllergens) { ingredientWarning(ingredient.name, watchedAllergens) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Checkbox,
                onLongClickLabel = onSubstitute?.let { L10n.str(R.string.substitute_x, ingredient.name) },
                onClick = { onCheckedChange(!checked) },
                onLongClick = onSubstitute
            )
            .semantics { stateDescription = if (checked) L10n.str(R.string.selected) else L10n.str(R.string.not_selected) }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 2.dp).size(18.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false)
        )
        warning?.let { AllergenWarningIcon(it) }
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

/**
 * Where the recipe comes from: web addresses and links ("[@user](profile) - [reel](post)") open
 * in the browser, and a button copies the address of the post (or the whole text when there is
 * none).
 */
@Composable
internal fun SourceLine(source: String) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormattedText(
            source,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = {
            val text = SourceText.mainLink(source) ?: source.trim()
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(L10n.str(R.string.source), text))
            // Android 13 and later show their own notice.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, L10n.str(R.string.source_copied), Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = L10n.str(R.string.copy_source))
        }
    }
}
