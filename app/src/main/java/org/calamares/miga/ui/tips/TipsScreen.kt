package org.calamares.miga.ui.tips

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.tips.CookingTip
import org.calamares.miga.data.tips.TipCategory
import org.calamares.miga.data.tips.searchTips
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.EmptyState

/** A page of the tips screen: every tip, the saved ones or one category. */
internal sealed class TipFilter(val key: String) {
    data object All : TipFilter("all")
    data object Saved : TipFilter("saved")
    data class Category(val category: TipCategory) : TipFilter(category.name)

    val label: String
        get() = when (this) {
            All -> L10n.str(R.string.tips_all)
            Saved -> L10n.str(R.string.tips_saved)
            is Category -> category.label
        }

    fun apply(tips: List<CookingTip>, saved: Set<String>): List<CookingTip> = when (this) {
        All -> tips
        Saved -> tips.filter { it.id in saved }
        is Category -> tips.filter { it.category == category }
    }

    companion object {
        val entries: List<TipFilter> = listOf(All, Saved) + TipCategory.entries.map { Category(it) }
    }
}

/**
 * Cooking tips bundled with the app: a search box, chips for every tip, the saved ones and each
 * category (a horizontal swipe moves between them, as in the books screen), and expandable cards.
 * [initialTipId] opens on that tip's category with the tip expanded (the tip of the day, a tip
 * from a recipe).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TipsScreen(viewModel: TipsViewModel, initialTipId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val tips = viewModel.tips
    val saved by viewModel.savedIds.collectAsStateWithLifecycle()
    val tipOfTheDayEnabled by viewModel.tipOfTheDayEnabled.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(listOfNotNull(initialTipId)) }
    var showMenu by remember { mutableStateOf(false) }
    val initialTip = tips.firstOrNull { it.id == initialTipId }
    val initialPage = initialTip?.let { tip -> TipFilter.entries.indexOfFirst { it is TipFilter.Category && it.category == tip.category } } ?: 0
    val startPage = initialPage.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startPage) { TipFilter.entries.size }
    var selectedPage by rememberSaveable { mutableStateOf(startPage) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { selectedPage = it } }
    LaunchedEffect(selectedPage) { if (pager.settledPage != selectedPage) pager.animateScrollToPage(selectedPage) }
    val searching = query.isNotBlank()

    val toggle: (String) -> Unit = { id -> expanded = if (id in expanded) expanded - id else expanded + id }
    // On a category's own page its name on every card would only repeat the chip.
    val card: @Composable (CookingTip, Boolean) -> Unit = { tip, onCategoryPage ->
        TipCard(
            tip = tip,
            showCategory = !onCategoryPage,
            expanded = tip.id in expanded,
            saved = tip.id in saved,
            onToggle = { toggle(tip.id) },
            onSavedChange = { viewModel.setSaved(tip.id, it) },
            onShare = { shareTip(context, tip) }
        )
    }

    AdaptiveScaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(L10n.str(R.string.cooking_tips)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = L10n.str(R.string.more_actions))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(L10n.str(R.string.tip_of_the_day_show)) },
                                leadingIcon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null) },
                                trailingIcon = { Checkbox(checked = tipOfTheDayEnabled, onCheckedChange = null) },
                                onClick = { showMenu = false; viewModel.setTipOfTheDayEnabled(!tipOfTheDayEnabled) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(L10n.str(R.string.tips_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.clear_search)) }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            )
            if (searching) {
                val found = remember(tips, query) { searchTips(tips, query) }
                if (found.isEmpty()) {
                    Text(
                        L10n.str(R.string.tips_search_empty, query.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                } else {
                    TipList(found) { tip -> card(tip, false) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TipFilter.entries.forEachIndexed { index, filter ->
                        FilterChip(
                            selected = index == selectedPage,
                            onClick = { selectedPage = index },
                            label = { Text("${filter.label} (${filter.apply(tips, saved).size})") }
                        )
                    }
                }
                HorizontalPager(
                    state = pager,
                    verticalAlignment = Alignment.Top,
                    key = { page -> TipFilter.entries[page].key },
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val filter = TipFilter.entries[page]
                    val shown = filter.apply(tips, saved)
                    if (shown.isEmpty() && filter == TipFilter.Saved) {
                        EmptyState(
                            icon = Icons.Outlined.StarBorder,
                            title = L10n.str(R.string.tips_saved_empty_title),
                            body = L10n.str(R.string.tips_saved_empty_body),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // The tip it was opened on starts in view.
                        val start = if (page == startPage) shown.indexOfFirst { it.id == initialTipId }.coerceAtLeast(0) else 0
                        TipList(shown, firstVisible = start) { tip -> card(tip, filter is TipFilter.Category) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TipList(tips: List<CookingTip>, firstVisible: Int = 0, card: @Composable (CookingTip) -> Unit) {
    LazyColumn(
        state = rememberLazyListState(initialFirstVisibleItemIndex = firstVisible),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tips, key = { it.id }) { tip -> card(tip) }
    }
}

/**
 * A tip as a card: its category's emoji, the title and, once opened, the whole text with buttons
 * to save and share it. Closed, it shows the start of the text so it can be skimmed.
 */
@Composable
internal fun TipCard(
    tip: CookingTip,
    expanded: Boolean,
    saved: Boolean,
    onToggle: () -> Unit,
    onSavedChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    showCategory: Boolean = true
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "tipArrow")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.clickable(onClick = onToggle).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = if (expanded) 4.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(tip.category)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (showCategory) {
                        Text(tip.category.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(tip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (saved) {
                    Icon(Icons.Filled.Star, contentDescription = L10n.str(R.string.tips_saved), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp).rotate(rotation)
                )
            }
            if (!expanded) {
                Text(
                    tip.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 54.dp, end = 8.dp, top = 6.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 54.dp, end = 8.dp, top = 8.dp)) {
                    Text(tip.body, style = MaterialTheme.typography.bodyLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { onSavedChange(!saved) }) {
                            Icon(
                                if (saved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = L10n.str(if (saved) R.string.tip_unsave else R.string.tip_save),
                                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = L10n.str(R.string.share), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryBadge(category: TipCategory) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(category.emoji, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The tip of the day at the top of the books screen: tap to read it in the tips screen, or hide
 * it until tomorrow.
 */
@Composable
fun TipOfTheDayCard(tip: CookingTip, onOpen: () -> Unit, onHide: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onOpen).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("💡", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 2.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    L10n.str(R.string.tip_of_the_day).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Text(tip.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    tip.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onHide) {
                Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.hide_until_tomorrow), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

/** A row in a recipe that says how many tips help with it; tapping it shows them. */
@Composable
fun RecipeTipsRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💡", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                if (count == 1) L10n.str(R.string.recipe_tips_one) else L10n.str(R.string.recipe_tips_x, count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.rotate(-90f))
        }
    }
}

/** The tips of a recipe, open, with a way to the full list. Meant for a bottom sheet. */
@Composable
fun RecipeTipsContent(
    tips: List<CookingTip>,
    saved: Set<String>,
    onSavedChange: (String, Boolean) -> Unit,
    onSeeAll: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(L10n.str(R.string.tips_for_recipe), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
        tips.forEach { tip ->
            var open by rememberSaveable(tip.id) { mutableStateOf(true) }
            TipCard(
                tip = tip,
                expanded = open,
                saved = tip.id in saved,
                onToggle = { open = !open },
                onSavedChange = { onSavedChange(tip.id, it) },
                onShare = { shareTip(context, tip) }
            )
        }
        TextButton(onClick = onSeeAll, modifier = Modifier.align(Alignment.End)) { Text(L10n.str(R.string.see_all_tips)) }
    }
}

internal fun shareTip(context: Context, tip: CookingTip) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, tip.title)
        putExtra(Intent.EXTRA_TEXT, "💡 ${tip.title}\n\n${tip.body}\n\n— Miga")
    }
    runCatching { context.startActivity(Intent.createChooser(intent, L10n.str(R.string.share))) }
}
