package org.calamares.miga.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.carbs.CarbStatus
import org.calamares.miga.data.carbs.RecipeCarbs
import org.calamares.miga.data.model.readableQuantity
import org.calamares.miga.ui.components.keepScrollInSheet
import org.calamares.miga.ui.components.sheetContentHeight
import kotlin.math.roundToInt

/** "3½ raciones" (to the nearest half) for [grams] of carbohydrate, or "35 g" when [portion] is 0. */
internal fun carbAmount(grams: Double, portion: Int): String {
    if (portion <= 0) return L10n.str(R.string.carb_grams_x, grams.roundToInt())
    val halves = Math.round(grams / portion * 2) / 2.0
    return when {
        halves == 0.0 -> L10n.str(R.string.carb_portion_under_half)
        halves <= 1.0 -> L10n.str(R.string.carb_portion_one_x, readableQuantity(halves))
        else -> L10n.str(R.string.carb_portions_x, readableQuantity(halves))
    }
}

/**
 * The carbohydrates per serving under the allergens, for those who count them (Settings). The
 * second line says the grams and, when some ingredients could not be counted, how many.
 */
@Composable
internal fun CarbLine(carbs: RecipeCarbs, portion: Int, onClick: () -> Unit) {
    val perServing = carbs.perServing ?: carbs.total
    val headline = if (carbs.perServing != null) L10n.str(R.string.carb_per_serving_x, carbAmount(perServing, portion))
    else L10n.str(R.string.carb_whole_recipe_x, carbAmount(perServing, portion))
    val detail = listOfNotNull(
        L10n.str(R.string.carb_grams_x, perServing.roundToInt()).takeIf { portion > 0 },
        when (carbs.missing.size) {
            0 -> null
            1 -> L10n.str(R.string.carb_missing_one)
            else -> L10n.str(R.string.carb_missing_x, carbs.missing.size)
        }
    ).joinToString(" · ")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = L10n.str(R.string.carb_open), role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = L10n.str(R.string.carb_title) + ". " + headline + ". " + detail }
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Grain, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(L10n.str(R.string.carb_title), style = MaterialTheme.typography.titleSmall)
                Text("≈ $headline", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (carbs.missing.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else ALLERGEN_AMBER
                    )
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

/** What each ingredient brings, what could not be counted and where the figures come from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarbSheet(carbs: RecipeCarbs, portion: Int, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.keepScrollInSheet()) {
            CarbSheetContent(carbs, portion)
        }
    }
}

@Composable
internal fun CarbSheetContent(carbs: RecipeCarbs, portion: Int) {
    val counted = carbs.items.filter { it.status == CarbStatus.COUNTED && !it.optional }.sortedByDescending { it.grams }
    val free = carbs.items.filter { it.status == CarbStatus.FREE && !it.optional }
    val optional = carbs.items.filter { it.optional && it.status == CarbStatus.COUNTED }
    Column(
        modifier = Modifier.heightIn(max = sheetContentHeight(0.9f)).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(L10n.str(R.string.carb_title), style = MaterialTheme.typography.titleLarge)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                carbs.perServing?.let { perServing ->
                    Text(
                        "≈ " + L10n.str(R.string.carb_per_serving_x, carbAmount(perServing, portion)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (portion > 0) {
                        Text(L10n.str(R.string.carb_grams_x, perServing.roundToInt()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Text(
                    L10n.str(R.string.carb_whole_recipe_x, carbAmount(carbs.total, portion)) +
                        (if (portion > 0) " (${L10n.str(R.string.carb_grams_x, carbs.total.roundToInt())})" else "") +
                        (carbs.servings?.takeIf { it > 0 }?.let { " · " + L10n.str(R.string.carb_for_x_people, it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (carbs.missing.isNotEmpty()) {
            Section(L10n.str(R.string.carb_missing_title), ALLERGEN_AMBER) {
                carbs.missing.forEach { item ->
                    ItemRow(
                        item.name,
                        L10n.str(if (item.status == CarbStatus.NO_AMOUNT) R.string.carb_no_amount else R.string.carb_unknown_food)
                    )
                }
                Text(L10n.str(R.string.carb_missing_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (counted.isNotEmpty()) {
            Section(L10n.str(R.string.carb_by_ingredient), MaterialTheme.colorScheme.primary) {
                counted.forEach { item ->
                    ItemRow(item.name, carbAmount(item.grams, portion) + if (portion > 0) " · " + L10n.str(R.string.carb_grams_x, item.grams.roundToInt()) else "")
                }
            }
        }

        if (optional.isNotEmpty()) {
            Section(L10n.str(R.string.carb_optional_title), MaterialTheme.colorScheme.primary) {
                optional.forEach { item -> ItemRow(item.name, "+ " + L10n.str(R.string.carb_grams_x, item.grams.roundToInt())) }
            }
        }

        if (free.isNotEmpty()) {
            Section(L10n.str(R.string.carb_free_title), MaterialTheme.colorScheme.primary) {
                Text(free.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider()
        Text(L10n.str(R.string.carb_sources), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(L10n.str(R.string.carb_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String, color: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = color)
        content()
    }
}

@Composable
private fun ItemRow(name: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
