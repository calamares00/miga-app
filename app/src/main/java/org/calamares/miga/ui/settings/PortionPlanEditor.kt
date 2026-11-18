package org.calamares.miga.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.carbs.FoodGroup
import org.calamares.miga.data.health.Meal
import org.calamares.miga.data.health.PortionPlan
import org.calamares.miga.data.model.readableQuantity
import org.calamares.miga.ui.components.keepScrollInSheet
import org.calamares.miga.ui.components.sheetContentHeight
import kotlin.math.roundToInt

/** The plan in one row of Settings > Health: what it adds up to, and a button to edit it. */
@Composable
internal fun PortionPlanRow(plan: PortionPlan, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(L10n.str(R.string.plan_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (plan.isEmpty) L10n.str(R.string.plan_none)
                else L10n.str(R.string.plan_summary_x, plan.usedMeals.size, plan.kcal.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onEdit) { Text(L10n.str(if (plan.isEmpty) R.string.plan_add else R.string.edit)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortionPlanSheet(plan: PortionPlan, onChange: (PortionPlan) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.keepScrollInSheet()) {
            PortionPlanEditor(plan, onChange)
        }
    }
}

/**
 * The plan as the care team writes it: for each meal, portions of each group, in halves. The
 * total kcal is an approximation from the method's average portions, to check against the plan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PortionPlanEditor(plan: PortionPlan, onChange: (PortionPlan) -> Unit) {
    Column(
        modifier = Modifier.heightIn(max = sheetContentHeight(0.9f)).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.str(R.string.plan_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                L10n.str(R.string.plan_kcal_x, plan.kcal.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(L10n.str(R.string.plan_editor_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Meal.entries.forEach { meal ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(meal.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        val kcal = plan.meal(meal).kcal
                        if (kcal > 0) {
                            Text(L10n.str(R.string.plan_kcal_x, kcal.roundToInt()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), maxItemsInEachRow = 2) {
                        FoodGroup.entries.forEach { group ->
                            PortionStepper(
                                group.label,
                                plan.portions(meal, group),
                                { onChange(plan.with(meal, group, it)) },
                                Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row {
            TextButton(onClick = { onChange(PortionPlan.EXAMPLE_1500) }) { Text(L10n.str(R.string.plan_load_example)) }
            if (!plan.isEmpty) {
                TextButton(onClick = { onChange(PortionPlan()) }) { Text(L10n.str(R.string.plan_clear), color = MaterialTheme.colorScheme.error) }
            }
        }
        Text(L10n.str(R.string.plan_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Harinas  − 4 +", in half portions. */
@Composable
private fun PortionStepper(label: String, value: Double, onChange: (Double) -> Unit, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (value > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onChange((value - 0.5).coerceAtLeast(0.0)) }, enabled = value > 0, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = L10n.str(R.string.plan_less_x, label), modifier = Modifier.size(18.dp))
        }
        Text(
            if (value > 0) readableQuantity(value) else "0",
            style = MaterialTheme.typography.titleSmall,
            color = if (value > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp)
        )
        IconButton(onClick = { onChange(value + 0.5) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Add, contentDescription = L10n.str(R.string.plan_more_x, label), modifier = Modifier.size(18.dp))
        }
    }
}
