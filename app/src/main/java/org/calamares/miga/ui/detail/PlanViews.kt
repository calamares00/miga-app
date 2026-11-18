package org.calamares.miga.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.carbs.FoodGroup
import org.calamares.miga.data.carbs.GroupPortions
import org.calamares.miga.data.health.Meal
import org.calamares.miga.data.health.MealFitter
import org.calamares.miga.data.health.PortionPlan
import org.calamares.miga.data.model.readableQuantity
import org.calamares.miga.ui.components.keepScrollInSheet
import org.calamares.miga.ui.components.sheetContentHeight
import kotlin.math.roundToInt

/** "4 harinas · 2 proteicos · 1 verdura": the groups with something in them, in plan order. */
internal fun portionsText(portions: GroupPortions): String =
    FoodGroup.entries.filter { portions[it] >= 0.25 }
        .joinToString(" · ") { "${readableQuantity(Math.round(portions[it] * 2) / 2.0)} ${it.label.lowercase(L10n.locale())}" }

/** What to add to complete a group, in kitchen measures (Clínic method). */
internal fun complementText(group: FoodGroup, portions: Double): String {
    fun n(value: Double) = readableQuantity(value)
    // Weights to the nearest 5 g: nobody weighs 113 g of fish.
    fun g(value: Double) = (Math.round(value / 5) * 5).toInt()
    // Plural from more than 1, as cooks write it ("1½ vasos", "½ vaso").
    fun many(value: Double) = value > 1.0
    return when (group) {
        FoodGroup.STARCHES -> L10n.str(
            if (many(portions / 2)) R.string.complement_starches_many_x else R.string.complement_starches_x,
            g(portions * 20), n(portions / 2)
        )
        FoodGroup.FRUIT -> L10n.str(if (many(portions / 2)) R.string.complement_fruit_many_x else R.string.complement_fruit_x, n(portions / 2))
        FoodGroup.VEGETABLES -> L10n.str(if (many(portions)) R.string.complement_vegetables_many_x else R.string.complement_vegetables_x, n(portions))
        FoodGroup.MILK -> when {
            portions <= 0.5 -> L10n.str(R.string.complement_milk_half)
            many(portions) -> L10n.str(R.string.complement_milk_many_x, n(portions), n(portions * 2))
            else -> L10n.str(R.string.complement_milk_x, n(portions), n(portions * 2))
        }
        FoodGroup.PROTEIN -> L10n.str(R.string.complement_protein_x, g(portions * 50), g(portions * 75))
        FoodGroup.FAT -> L10n.str(if (many(portions)) R.string.complement_fat_many_x else R.string.complement_fat_x, n(portions))
    }
}

/** The recipe in the plan's groups, per serving; a tap fits it to a meal of the plan. */
@Composable
internal fun PlanLine(perServing: GroupPortions, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = L10n.str(R.string.plan_fit_open), role = Role.Button, onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(L10n.str(R.string.plan_line_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    portionsText(perServing).ifEmpty { L10n.str(R.string.plan_line_nothing) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    L10n.str(R.string.plan_fit_action),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanSheet(perServing: GroupPortions, plan: PortionPlan, recipeServings: Int, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.keepScrollInSheet()) {
            PlanSheetContent(perServing, plan, recipeServings)
        }
    }
}

/**
 * Pick a meal of the plan: how much of the recipe to take, what it brings and what to add to
 * complete the meal (or what is over), in kitchen measures.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanSheetContent(perServing: GroupPortions, plan: PortionPlan, recipeServings: Int, initialMeal: Meal? = null) {
    val meals = plan.usedMeals
    val suitable = remember(perServing, plan) { MealFitter.suitableMeals(perServing, plan) }
    val default = initialMeal
        ?: (listOf(Meal.LUNCH, Meal.DINNER) + meals).firstOrNull { it in suitable }
        ?: listOf(Meal.LUNCH, Meal.DINNER).firstOrNull { it in meals } ?: meals.firstOrNull() ?: Meal.LUNCH
    var meal by rememberSaveable { mutableStateOf(default) }
    val fit = remember(perServing, plan, meal) { MealFitter.fit(perServing, plan, meal) }
    Column(
        modifier = Modifier.heightIn(max = sheetContentHeight(0.9f)).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(L10n.str(R.string.plan_fit_title), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            meals.forEach { option ->
                FilterChip(selected = option == meal, onClick = { meal = option }, label = { Text(option.label) })
            }
        }
        Text(
            L10n.str(R.string.plan_meal_is_x, portionsText(plan.meal(meal))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!fit.suitable) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(L10n.str(R.string.plan_not_suitable_x, meal.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        L10n.str(R.string.plan_not_suitable_why_x, fit.mainGroup?.label?.lowercase(L10n.locale()).orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (suitable.isEmpty()) L10n.str(R.string.plan_suits_none)
                        else L10n.str(R.string.plan_suits_x, suitable.joinToString(", ") { it.label.lowercase(L10n.locale()) }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider()
            Text(L10n.str(R.string.plan_fit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        fit.servings < 1.0 -> L10n.str(R.string.plan_take_part_x, readableQuantity(fit.servings))
                        fit.servings == 1.0 -> L10n.str(R.string.plan_take_one)
                        else -> L10n.str(R.string.plan_take_x, readableQuantity(fit.servings))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (recipeServings > 0) {
                    Text(
                        L10n.str(R.string.plan_take_hint_x, recipeServings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    L10n.str(R.string.plan_brings_x, portionsText(fit.fromRecipe).ifEmpty { L10n.str(R.string.plan_line_nothing) }, fit.fromRecipe.kcal.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (fit.missing.isNotEmpty()) {
            Text(L10n.str(R.string.plan_to_complete), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            fit.missing.forEach { (group, portions) ->
                GroupRow(group, portions, complementText(group, portions))
            }
        } else {
            Text(L10n.str(R.string.plan_complete), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }

        if (fit.over.isNotEmpty()) {
            Text(L10n.str(R.string.plan_over), style = MaterialTheme.typography.titleSmall, color = ALLERGEN_AMBER)
            fit.over.forEach { (group, portions) ->
                GroupRow(group, portions, if (group == FoodGroup.FAT) L10n.str(R.string.plan_over_fat) else L10n.str(R.string.plan_over_other))
            }
        }

        if (perServing.cookingOil) {
            Text(L10n.str(R.string.plan_cooking_oil), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (fit.fromRecipe.otherCarbs >= 1) {
            Text(
                L10n.str(R.string.plan_other_carbs_x, fit.fromRecipe.otherCarbs.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = ALLERGEN_AMBER
            )
        }

        HorizontalDivider()
        Text(L10n.str(R.string.plan_fit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupRow(group: FoodGroup, portions: Double, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "${readableQuantity(portions)} ${group.label.lowercase(L10n.locale())}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(120.dp)
        )
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}
