package org.calamares.miga.ui.detail

import org.calamares.miga.ui.components.sheetContentHeight
import androidx.compose.foundation.layout.heightIn
import org.calamares.miga.ui.components.keepScrollInSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.allergens.Allergen
import org.calamares.miga.data.allergens.AllergenLevel
import org.calamares.miga.data.allergens.RecipeAllergens

/** Red for "contains", amber for "may contain": the usual colours of allergen charts. */
internal val ALLERGEN_RED = Color(0xFFC62828)
internal val ALLERGEN_AMBER = Color(0xFFD98200)

private fun levelColor(level: AllergenLevel): Color? = when (level) {
    AllergenLevel.CONTAINS -> ALLERGEN_RED
    AllergenLevel.MAY_CONTAIN -> ALLERGEN_AMBER
    AllergenLevel.FREE -> null
}

internal fun levelLabel(level: AllergenLevel): String = L10n.str(
    when (level) {
        AllergenLevel.CONTAINS -> R.string.allergen_level_contains
        AllergenLevel.MAY_CONTAIN -> R.string.allergen_level_may_contain
        AllergenLevel.FREE -> R.string.allergen_level_free
    }
)

/** An allergen's icon in a circle: coloured ring when present, faded when not. */
@Composable
internal fun AllergenBadge(allergen: Allergen, level: AllergenLevel, size: Int = 32) {
    val color = levelColor(level)
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color?.copy(alpha = 0.13f) ?: MaterialTheme.colorScheme.surfaceVariant)
                .then(if (color != null) Modifier.border(2.dp, color, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(allergen.iconRes),
                contentDescription = null,
                tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size * 0.58f).dp).alpha(if (color == null) 0.35f else 1f)
            )
        }
        if (level == AllergenLevel.MAY_CONTAIN) {
            Text(
                "!",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopEnd).size((size * 0.4f).dp).clip(CircleShape).background(ALLERGEN_AMBER)
            )
        }
    }
}

/**
 * What coeliacs need to know, under the allergen badges: "No ingredients with gluten", or
 * "Suitable for coeliacs if you buy gluten-free: stock cube". Never "gluten-free" or "suitable
 * for coeliacs" alone: those are legal claims a home recipe cannot make (Regulation (EU) No
 * 828/2014).
 */
internal fun glutenNote(allergens: RecipeAllergens): String {
    val causes = allergens.causes[Allergen.GLUTEN].orEmpty()
    return when (allergens.level(Allergen.GLUTEN)) {
        AllergenLevel.FREE -> L10n.str(R.string.gluten_free_ingredients)
        AllergenLevel.MAY_CONTAIN -> L10n.str(R.string.coeliac_suitable_if_x, causes.joinToString(", ") { it.ingredient })
        AllergenLevel.CONTAINS -> L10n.str(
            R.string.coeliac_not_suitable_x,
            causes.filter { it.level == AllergenLevel.CONTAINS }.joinToString(", ") { it.ingredient }
        )
    }
}

/**
 * One row under the key facts: the allergens this recipe has and, under them, the note for
 * coeliacs; a tap opens the details.
 */
@Composable
internal fun AllergenLine(allergens: RecipeAllergens, onClick: () -> Unit) {
    val present = allergens.present
    val note = glutenNote(allergens)
    val glutenLevel = allergens.level(Allergen.GLUTEN)
    val description = if (present.isEmpty()) L10n.str(R.string.allergens_none)
    else present.joinToString(", ") { "${it.label}: ${levelLabel(allergens.level(it)).lowercase(L10n.locale())}" }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = L10n.str(R.string.allergens_open), role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = L10n.str(R.string.allergens_title) + ". " + description + ". " + note }
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.str(R.string.allergens_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 12.dp))
                    if (present.isEmpty()) {
                        Text(L10n.str(R.string.allergens_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            present.forEach { AllergenBadge(it, allergens.level(it), 28) }
                        }
                    }
                }
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (glutenLevel) {
                        AllergenLevel.FREE -> MaterialTheme.colorScheme.primary
                        AllergenLevel.MAY_CONTAIN -> ALLERGEN_AMBER
                        AllergenLevel.CONTAINS -> ALLERGEN_RED
                    }
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

/** The details, in a bottom sheet: every allergen, what brings each one and the coeliac card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AllergenSheet(allergens: RecipeAllergens, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.keepScrollInSheet()) {
            AllergenSheetContent(allergens)
        }
    }
}

@Composable
internal fun AllergenSheetContent(allergens: RecipeAllergens) {
    Column(
        modifier = Modifier.heightIn(max = sheetContentHeight(0.9f)).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(L10n.str(R.string.allergens_sheet_title), style = MaterialTheme.typography.titleLarge)
        Allergen.entries.chunked(5).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { allergen ->
                    val level = allergens.level(allergen)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription = "${allergen.label}: ${levelLabel(level)}"
                        }
                    ) {
                        AllergenBadge(allergen, level, 40)
                        Text(
                            allergen.label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            color = if (level == AllergenLevel.FREE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(ALLERGEN_RED, levelLabel(AllergenLevel.CONTAINS))
            Legend(ALLERGEN_AMBER, levelLabel(AllergenLevel.MAY_CONTAIN))
            Legend(MaterialTheme.colorScheme.outline, levelLabel(AllergenLevel.FREE))
        }
        if (allergens.present.isNotEmpty()) {
            HorizontalDivider()
            Text(L10n.str(R.string.allergens_in_this_recipe), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            allergens.present.forEach { allergen ->
                val level = allergens.level(allergen)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AllergenBadge(allergen, level, 30)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (level == AllergenLevel.CONTAINS) allergen.label
                            else L10n.str(R.string.allergen_may_contain_x, allergen.label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            allergens.causes[allergen].orEmpty().joinToString(", ") { cause ->
                                var text = cause.ingredient
                                // Next to what surely brings it, say which ones only may.
                                if (level == AllergenLevel.CONTAINS && cause.level == AllergenLevel.MAY_CONTAIN) {
                                    text = L10n.str(R.string.allergen_cause_may_x, text)
                                }
                                if (cause.optional) L10n.str(R.string.allergen_cause_optional_x, text) else text
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        CoeliacCard(allergens)
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(L10n.str(R.string.allergens_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What a person with coeliac disease needs to know about this recipe. */
@Composable
private fun CoeliacCard(allergens: RecipeAllergens) {
    val level = allergens.level(Allergen.GLUTEN)
    val causes = allergens.causes[Allergen.GLUTEN].orEmpty()
    val color = when (level) {
        AllergenLevel.CONTAINS -> ALLERGEN_RED
        AllergenLevel.MAY_CONTAIN -> ALLERGEN_AMBER
        AllergenLevel.FREE -> MaterialTheme.colorScheme.primary
    }
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(Allergen.GLUTEN.iconRes), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    L10n.str(
                        when (level) {
                            AllergenLevel.CONTAINS -> R.string.coeliac_not_suitable
                            AllergenLevel.MAY_CONTAIN, AllergenLevel.FREE -> R.string.coeliac_suitable_if
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            when (level) {
                AllergenLevel.CONTAINS -> Bullet(
                    L10n.str(R.string.coeliac_contains_x, causes.filter { it.level == AllergenLevel.CONTAINS }.joinToString(", ") { it.ingredient })
                )
                AllergenLevel.MAY_CONTAIN -> Bullet(L10n.str(R.string.coeliac_buy_certified_x, causes.joinToString(", ") { it.ingredient }))
                AllergenLevel.FREE -> Unit
            }
            // Gluten-free foods that may carry traces (buckwheat, maize flour, dry lentils...).
            allergens.traceCauses[Allergen.GLUTEN]?.takeIf { it.isNotEmpty() && level != AllergenLevel.CONTAINS }?.let { names ->
                Bullet(L10n.str(R.string.coeliac_buy_certified_traces_x, names.joinToString(", ")))
            }
            Bullet(L10n.str(R.string.coeliac_kitchen))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
