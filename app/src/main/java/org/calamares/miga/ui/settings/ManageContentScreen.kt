package org.calamares.miga.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.local.entity.IngredientCategoryEntity
import org.calamares.miga.data.model.ContentItem
import org.calamares.miga.data.model.ContentKind
import org.calamares.miga.data.model.IngredientCatalogItem
import org.calamares.miga.data.model.RecipeFilter
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.ui.components.ButtonContent

class ManageContentViewModel(private val repository: RecipeRepository, val kind: ContentKind) : ViewModel() {
    /** Null until loaded, so the empty state does not flash. */
    val items: StateFlow<List<ContentItem>?> = repository.observeContent(kind)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** For ingredients: each one's shopping category, by ingredient id. */
    val ingredientCategories: StateFlow<Map<Long, IngredientCatalogItem>> =
        (if (kind == ContentKind.INGREDIENT) repository.observeIngredientCatalogWithCategoryById() else flowOf(emptyMap()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val categoryChoices: StateFlow<List<IngredientCategoryEntity>> =
        (if (kind == ContentKind.INGREDIENT) repository.observeIngredientCategories() else flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String) {
        viewModelScope.launch { repository.addContent(kind, name) }
    }

    fun rename(item: ContentItem, newName: String, updateRecipes: Boolean) {
        viewModelScope.launch { repository.renameContent(kind, item.id, newName, updateRecipes) }
    }

    fun delete(ids: Collection<Long>) {
        viewModelScope.launch { repository.deleteContent(kind, ids) }
    }

    fun merge(ids: Collection<Long>, intoId: Long) {
        viewModelScope.launch { repository.mergeContent(kind, ids, intoId) }
    }

    fun changeIngredientCategory(ingredientId: Long, categoryId: Long?) {
        viewModelScope.launch { repository.changeIngredientCategory(ingredientId, categoryId) }
    }
}

private enum class ContentFilter { ALL, UNUSED, MINE }

private enum class ContentSort { NAME, USAGE }

/**
 * One list of Settings > Manage content (categories, equipment, tags, ingredients or ingredient
 * categories): search, "unused" and "mine" filters, sort by name or use, the recipes using an
 * entry, renames that merge into an existing entry, deleting with the option to move its recipes
 * to another entry, and a long press to select several to merge or delete.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ManageContentScreen(
    viewModel: ManageContentViewModel,
    onBack: () -> Unit,
    onOpenRecipes: (title: String, filter: RecipeFilter) -> Unit
) {
    val kind = viewModel.kind
    val loaded by viewModel.items.collectAsState()
    val all = loaded.orEmpty()
    val ingredientCategories by viewModel.ingredientCategories.collectAsState()
    val categoryChoices by viewModel.categoryChoices.collectAsState()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ContentFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(ContentSort.NAME) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ContentItem?>(null) }
    var deleting by remember { mutableStateOf<List<ContentItem>?>(null) }
    var merging by remember { mutableStateOf<List<ContentItem>?>(null) }
    var categoryFor by remember { mutableStateOf<ContentItem?>(null) }

    val shown = all
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .filter {
            when (filter) {
                ContentFilter.ALL -> true
                ContentFilter.UNUSED -> it.usage == 0
                ContentFilter.MINE -> !it.isDefault
            }
        }
        .let { list -> if (sort == ContentSort.USAGE) list.sortedByDescending { it.usage } else list }
    val selecting = selected.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    when {
                        selecting -> Text(L10n.str(R.string.n_selected, selected.size))
                        searching -> OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(L10n.str(R.string.search)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> Text(kind.title)
                    }
                },
                navigationIcon = {
                    when {
                        selecting -> IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel)) }
                        searching -> IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.close)) }
                        else -> IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = L10n.str(R.string.back)) }
                    }
                },
                actions = {
                    if (selecting) {
                        if (selected.size >= 2) {
                            IconButton(onClick = { merging = all.filter { it.id in selected } }) {
                                Icon(Icons.Filled.CallMerge, contentDescription = L10n.str(R.string.content_merge))
                            }
                        }
                        IconButton(onClick = { deleting = all.filter { it.id in selected } }) {
                            Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.delete))
                        }
                    } else {
                        if (!searching) {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, contentDescription = L10n.str(R.string.search)) }
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Filled.Sort, contentDescription = L10n.str(R.string.content_sort)) }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(L10n.str(R.string.content_sort_name)) },
                                    leadingIcon = { RadioButton(selected = sort == ContentSort.NAME, onClick = null) },
                                    onClick = { sort = ContentSort.NAME; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(L10n.str(R.string.content_sort_usage)) },
                                    leadingIcon = { RadioButton(selected = sort == ContentSort.USAGE, onClick = null) },
                                    onClick = { sort = ContentSort.USAGE; showSortMenu = false }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(onClick = { showAdd = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(L10n.str(R.string.add)) })
            }
        }
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(selected = filter == ContentFilter.ALL, onClick = { filter = ContentFilter.ALL }, label = { Text(L10n.str(R.string.content_filter_all, all.size)) })
                    }
                    item {
                        FilterChip(
                            selected = filter == ContentFilter.UNUSED,
                            onClick = { filter = ContentFilter.UNUSED },
                            label = { Text(L10n.str(R.string.content_filter_unused, all.count { it.usage == 0 })) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = filter == ContentFilter.MINE,
                            onClick = { filter = ContentFilter.MINE },
                            label = { Text(L10n.str(R.string.content_filter_mine, all.count { !it.isDefault })) }
                        )
                    }
                }
            }
            if (all.any { it.isDefault }) {
                item(key = "defaultsHint") {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        DefaultBadge()
                        Text(
                            L10n.str(R.string.default_entry_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            if (loaded != null && shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        L10n.str(R.string.there_no_items_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            }
            items(shown, key = { it.id }) { item ->
                ContentEntryRow(
                    item = item,
                    selected = item.id in selected,
                    selecting = selecting,
                    ingredientCategory = if (kind == ContentKind.INGREDIENT) ingredientCategories[item.id]?.categoryName ?: L10n.str(R.string.uncategorized) else null,
                    onClick = {
                        if (selecting) selected = selected.toggle(item.id) else renaming = item
                    },
                    onLongClick = { selected = selected.toggle(item.id) },
                    onUsageClick = recipesFilter(kind, item.name)?.takeIf { item.usage > 0 && !selecting }?.let { recipeFilter ->
                        { onOpenRecipes(item.name, recipeFilter) }
                    },
                    onCategoryClick = { categoryFor = item },
                    onRename = { renaming = item },
                    onDelete = { deleting = listOf(item) }
                )
                HorizontalDivider()
            }
        }
    }

    if (showAdd) {
        NameDialog(
            title = L10n.str(R.string.add),
            initialValue = "",
            existing = all,
            current = null,
            onConfirm = { name, _ -> viewModel.add(name); showAdd = false },
            onDismiss = { showAdd = false }
        )
    }

    renaming?.let { item ->
        NameDialog(
            title = L10n.str(R.string.rename),
            initialValue = item.name,
            existing = all,
            current = item,
            onConfirm = { name, updateRecipes -> viewModel.rename(item, name, updateRecipes); renaming = null },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { items ->
        DeleteDialog(
            kind = kind,
            items = items,
            others = all.filter { other -> items.none { it.id == other.id } },
            onDelete = { viewModel.delete(items.map { it.id }); deleting = null; selected = emptySet() },
            onMove = { target -> viewModel.merge(items.map { it.id }, target.id); deleting = null; selected = emptySet() },
            onDismiss = { deleting = null }
        )
    }

    merging?.let { items ->
        MergeDialog(
            kind = kind,
            items = items,
            onMerge = { target -> viewModel.merge(items.map { it.id }, target.id); merging = null; selected = emptySet() },
            onDismiss = { merging = null }
        )
    }

    categoryFor?.let { item ->
        val currentCategory = ingredientCategories[item.id]?.categoryId
        AlertDialog(
            onDismissRequest = { categoryFor = null },
            title = { Text(L10n.str(R.string.category_x, item.name)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ChoiceRow(L10n.str(R.string.uncategorized), currentCategory == null) {
                        viewModel.changeIngredientCategory(item.id, null); categoryFor = null
                    }
                    categoryChoices.forEach { category ->
                        ChoiceRow(category.name, currentCategory == category.id) {
                            viewModel.changeIngredientCategory(item.id, category.id); categoryFor = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { categoryFor = null }) { Text(L10n.str(R.string.close)) } }
        )
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

/** The search filter listing the recipes that use [name]; null for kinds not used by recipes. */
private fun recipesFilter(kind: ContentKind, name: String): RecipeFilter? = when (kind) {
    ContentKind.CATEGORY -> RecipeFilter(categoryNames = setOf(name))
    ContentKind.EQUIPMENT -> RecipeFilter(utensils = setOf(name))
    ContentKind.TAG -> RecipeFilter(tags = setOf(name))
    ContentKind.INGREDIENT -> RecipeFilter(ingredients = setOf(name))
    ContentKind.INGREDIENT_CATEGORY -> null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentEntryRow(
    item: ContentItem,
    selected: Boolean,
    selecting: Boolean,
    ingredientCategory: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUsageClick: (() -> Unit)?,
    onCategoryClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.isDefault) DefaultBadge(modifier = Modifier.padding(start = 8.dp))
            }
            Text(
                text = if (item.usage == 0) L10n.str(R.string.content_unused) else item.kind.usageLabel(item.usage),
                style = MaterialTheme.typography.bodyMedium,
                color = if (onUsageClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onUsageClick != null) Modifier.clickable(onClick = onUsageClick).padding(vertical = 2.dp) else Modifier
            )
            if (ingredientCategory != null) {
                AssistChip(onClick = onCategoryClick, label = { Text(ingredientCategory) }, enabled = !selecting)
            }
        }
        if (!selecting) {
            IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = L10n.str(R.string.rename)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.delete)) }
        }
    }
}

/**
 * Name of a new or renamed entry. Says when the name already exists (the rename merges both) and
 * how many recipes will show the new name; for an ingredient, whether to change it in them too.
 */
@Composable
private fun NameDialog(
    title: String,
    initialValue: String,
    existing: List<ContentItem>,
    current: ContentItem?,
    onConfirm: (name: String, updateRecipes: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    var updateRecipes by rememberSaveable { mutableStateOf(true) }
    val trimmed = value.trim()
    val clash = existing.firstOrNull { it.id != current?.id && it.name.equals(trimmed, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                when {
                    clash != null && current != null -> Text(
                        L10n.str(R.string.content_rename_merges_x, clash.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    clash != null -> Text(
                        L10n.str(R.string.content_already_exists_x, clash.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (current != null && current.usage > 0 && current.kind != ContentKind.INGREDIENT_CATEGORY) {
                    if (current.kind == ContentKind.INGREDIENT) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { updateRecipes = !updateRecipes },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = updateRecipes, onCheckedChange = { updateRecipes = it })
                            Text(L10n.str(R.string.content_rename_in_recipes, current.kind.usageLabel(current.usage)), style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text(
                            L10n.str(R.string.content_rename_updates, current.kind.usageLabel(current.usage)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed, updateRecipes) },
                enabled = trimmed.isNotEmpty() && (current != null || clash == null)
            ) { Text(L10n.str(if (clash != null && current != null) R.string.content_merge else R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

/**
 * Deleting entries: when recipes use them, offers to move those recipes to another entry
 * (a merge) instead of leaving them without one.
 */
@Composable
private fun DeleteDialog(
    kind: ContentKind,
    items: List<ContentItem>,
    others: List<ContentItem>,
    onDelete: () -> Unit,
    onMove: (ContentItem) -> Unit,
    onDismiss: () -> Unit
) {
    val usage = items.sumOf { it.usage }
    var target by remember { mutableStateOf<ContentItem?>(null) }
    // Ingredients are text in the recipes: deleting one only removes it from the suggestions.
    val canMove = usage > 0 && kind != ContentKind.INGREDIENT && others.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (items.size == 1) L10n.str(R.string.delete_quoted_x, items.single().name)
                else L10n.str(R.string.content_delete_n, items.size)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        usage == 0 -> L10n.str(R.string.content_delete_unused)
                        kind == ContentKind.INGREDIENT -> L10n.str(R.string.content_delete_ingredient_used)
                        else -> L10n.str(R.string.content_delete_used_x, kind.usageLabel(usage))
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (canMove) {
                    Text(L10n.str(R.string.content_move_to), style = MaterialTheme.typography.titleSmall)
                    Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        others.forEach { other ->
                            ChoiceRow(other.name, target?.id == other.id) { target = if (target?.id == other.id) null else other }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val chosen = target
            if (chosen != null) {
                TextButton(onClick = { onMove(chosen) }) { Text(L10n.str(R.string.content_move_to_x, chosen.name)) }
            } else {
                TextButton(onClick = onDelete) { Text(L10n.str(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

/** Merging the selected entries into the one the user picks (the most used one by default). */
@Composable
private fun MergeDialog(kind: ContentKind, items: List<ContentItem>, onMerge: (ContentItem) -> Unit, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf(items.maxByOrNull { it.usage } ?: items.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.CallMerge, contentDescription = null) },
        title = { Text(L10n.str(R.string.content_merge_n, items.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L10n.str(R.string.content_merge_keep), style = MaterialTheme.typography.bodyMedium)
                Column(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    items.forEach { item ->
                        ChoiceRow("${item.name} · ${kind.usageLabel(item.usage)}", target.id == item.id) { target = item }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMerge(target) }) { ButtonContent(Icons.Filled.CallMerge, L10n.str(R.string.content_merge)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.str(R.string.cancel)) } }
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
