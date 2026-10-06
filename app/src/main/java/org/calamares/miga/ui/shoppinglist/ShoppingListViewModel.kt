package org.calamares.miga.ui.shoppinglist

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.calamares.miga.data.export.RecipeExporter
import org.calamares.miga.data.local.SettingsRepository
import org.calamares.miga.data.model.Ingredient
import org.calamares.miga.data.model.IngredientCatalogItem
import org.calamares.miga.data.model.ParsedShoppingEntry
import org.calamares.miga.data.model.ShoppingEntryParser
import org.calamares.miga.data.model.ShoppingListGroup
import org.calamares.miga.data.model.DEFAULT_SHOPPING_LIST_UID
import org.calamares.miga.data.model.ShoppingListInfo
import org.calamares.miga.data.model.ShoppingListItem
import org.calamares.miga.data.model.ShoppingStore
import org.calamares.miga.data.model.ShoppingSuggestion
import org.calamares.miga.data.model.ShoppingTemplate
import org.calamares.miga.data.model.ShoppingTemplateCodec
import org.calamares.miga.data.model.TemplateItem
import org.calamares.miga.data.remote.OpenFoodFactsClient
import org.calamares.miga.data.remote.ProductLookupResult
import org.calamares.miga.data.remote.ProductSearchResult
import org.calamares.miga.data.remote.ScannedProduct
import org.calamares.miga.data.repository.RecipeRepository
import org.calamares.miga.data.sync.SyncEngine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the Open Food Facts product search. */
sealed interface ProductSearchState {
    data object Idle : ProductSearchState
    data object Loading : ProductSearchState
    data class Results(val products: List<ScannedProduct>) : ProductSearchState
    data class Error(val reason: String) : ProductSearchState
}

class ShoppingListViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val groups: StateFlow<List<ShoppingListGroup>> = repository.observeShoppingList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<ShoppingSuggestion>> = repository.observeShoppingHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val catalogNames: StateFlow<List<String>> = repository.observeIngredientNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val catalog: StateFlow<List<IngredientCatalogItem>> = repository.observeIngredientCatalogWithCategory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<ShoppingTemplate>> = repository.observeShoppingTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val imagesEnabled: StateFlow<Boolean> = settingsRepository.observeShoppingImagesEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val syncEngine = SyncEngine(repository)

    val lists: StateFlow<List<ShoppingListInfo>> = repository.observeShoppingLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(ShoppingListInfo(DEFAULT_SHOPPING_LIST_UID, L10n.str(R.string.shopping))))

    /** Pending items per list (uid to count), shown on the list tabs. */
    val listCounts: StateFlow<Map<String, Int>> = repository.observeShoppingListPendingCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _searchState = MutableStateFlow<ProductSearchState>(ProductSearchState.Idle)
    val searchState: StateFlow<ProductSearchState> = _searchState

    /** Searches products by name in Open Food Facts (only on submit, not on every keystroke). */
    fun searchProducts(query: String, spainOnly: Boolean) {
        if (query.trim().length < 2) {
            _searchState.value = ProductSearchState.Idle
            return
        }
        viewModelScope.launch {
            _searchState.value = ProductSearchState.Loading
            _searchState.value = when (val result = OpenFoodFactsClient.search(query, spainOnly)) {
                is ProductSearchResult.Success -> ProductSearchState.Results(result.products)
                is ProductSearchResult.Error -> ProductSearchState.Error(result.reason)
            }
        }
    }

    fun clearSearch() {
        _searchState.value = ProductSearchState.Idle
    }

    /**
     * Adds a product picked from the search to the current list, with its photo and product sheet.
     */
    fun addSearchedProduct(product: ScannedProduct) {
        viewModelScope.launch { repository.addScannedShoppingProduct(product.name, product.imageUrl, product.info) }
    }

    val selectedListUid: StateFlow<String> = settingsRepository.observeShoppingListUid()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_SHOPPING_LIST_UID)

    /** Name used to sign changes on a shared list (empty for none). */
    val author: StateFlow<String> = settingsRepository.observeShoppingAuthor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun selectList(uid: String) {
        viewModelScope.launch { settingsRepository.setShoppingListUid(uid) }
    }

    fun createList(name: String) {
        viewModelScope.launch {
            repository.createShoppingList(name)?.let { settingsRepository.setShoppingListUid(it) }
        }
    }

    fun renameList(uid: String, name: String) {
        viewModelScope.launch { repository.renameShoppingList(uid, name) }
    }

    fun deleteList(uid: String) {
        viewModelScope.launch {
            if (selectedListUid.value == uid) settingsRepository.setShoppingListUid(DEFAULT_SHOPPING_LIST_UID)
            repository.deleteShoppingList(uid)
        }
    }

    fun setAuthor(name: String) {
        viewModelScope.launch { settingsRepository.setShoppingAuthor(name) }
    }

    /** Syncs the connection that shares the lists, if any. The screen repeats it while visible. */
    suspend fun syncSharedListsOnce(context: Context) {
        val connectionId = repository.getShoppingSyncConnectionId() ?: return
        syncEngine.syncConnection(context, connectionId)
    }

    /**
     * "Ana added 2 items" notices when new items from someone else arrive (via sync) in the list
     * being viewed. The first load of each list does not notify, since everything would be new.
     */
    private val _remoteAdditions = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val remoteAdditions: SharedFlow<String> = _remoteAdditions.asSharedFlow()
    private var announcedListUid: String? = null
    private var knownItemUids: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            repository.observeShoppingListSnapshots()
                .collect { (listUid, snapshot) ->
                    val items = snapshot.flatMap { it.items }
                    val me = settingsRepository.observeShoppingAuthor().first()
                    val uids = items.map { it.uid }.toSet()
                    if (announcedListUid == listUid) {
                        val others = items.filter { it.uid !in knownItemUids && !it.addedBy.isNullOrBlank() && !it.addedBy.equals(me, ignoreCase = true) }
                        if (others.isNotEmpty()) {
                            val who = others.map { it.addedBy!! }.distinct()
                            val names = if (who.size == 1) who.single() else L10n.str(R.string.several_people)
                            _remoteAdditions.tryEmit(L10n.str(R.string.x_added_x, names, if (others.size == 1) L10n.str(R.string.n_1_item) else L10n.str(R.string.x_items, others.size)))
                        }
                    }
                    announcedListUid = listUid
                    knownItemUids = uids
                }
        }
    }

    val stores: StateFlow<List<ShoppingStore>> = repository.observeShoppingStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedStoreId: StateFlow<Long> = settingsRepository.observeShoppingStoreId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    /** The selected store (null when there is none or it was deleted). */
    val selectedStore: StateFlow<ShoppingStore?> = combine(stores, selectedStoreId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Ingredient category names, used to complete the aisle order when editing a store. */
    val ingredientCategoryNames: StateFlow<List<String>> = repository.observeIngredientCategories()
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectStore(id: Long) {
        viewModelScope.launch { settingsRepository.setShoppingStoreId(id) }
    }

    fun saveStore(store: ShoppingStore, select: Boolean) {
        viewModelScope.launch {
            val id = repository.saveShoppingStore(store)
            if (select && id != 0L) settingsRepository.setShoppingStoreId(id)
        }
    }

    fun deleteStore(id: Long) {
        viewModelScope.launch {
            repository.deleteShoppingStore(id)
            if (selectedStoreId.value == id) settingsRepository.setShoppingStoreId(0L)
        }
    }

    fun setImagesEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShoppingImagesEnabled(enabled) }
    }

    /**
     * Tappable catalogue checkbox: if [name] is already pending it is removed (with a tombstone,
     * like any deletion), otherwise it is added without a quantity.
     */
    fun toggleCatalogItem(name: String) {
        val key = name.trim().lowercase()
        val existing = groups.value.flatMap { it.items }.firstOrNull { !it.checked && it.name.trim().lowercase() == key }
        if (existing != null) {
            viewModelScope.launch { repository.deleteShoppingListItem(existing.id) }
        } else {
            addParsedEntries(listOf(ParsedShoppingEntry(name.trim(), null, null)))
        }
    }

    /**
     * Looks up [barcode] in Open Food Facts and, if found, adds it with its photo. [onResult]
     * receives the message to show.
     */
    fun addScannedProduct(barcode: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = OpenFoodFactsClient.lookup(barcode)) {
                is ProductLookupResult.Found -> {
                    repository.addScannedShoppingProduct(result.product.name, result.product.imageUrl, result.product.info)
                    onResult(L10n.str(R.string.added_x, result.product.name))
                }
                ProductLookupResult.NotFound -> onResult(L10n.str(R.string.product_not_found_open_food_2, barcode))
                is ProductLookupResult.Error -> onResult(L10n.str(R.string.couldnt_look_up_product_x, result.reason))
            }
        }
    }

    /** Saves the current list (with product photos and sheets) as a new template. */
    fun saveTemplate(name: String) {
        val items = groups.value.flatMap { it.items }.map { TemplateItem(it.name, it.quantity, it.unit, it.imageUrl, it.productInfo) }
        if (items.isEmpty() || name.isBlank()) return
        viewModelScope.launch { repository.saveShoppingTemplate(name, items) }
    }

    /** Creates an empty template (or one holding [first]) and passes its id to [onCreated]. */
    fun createTemplate(name: String, first: TemplateItem? = null, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveShoppingTemplate(name, listOfNotNull(first))?.let(onCreated)
        }
    }

    fun addToTemplate(templateId: Long, item: TemplateItem) {
        viewModelScope.launch { repository.updateShoppingTemplateItems(templateId) { ShoppingTemplateCodec.upsert(it, item) } }
    }

    /**
     * Adds typed text ("2 kg tomatoes, milk") to the template and returns the number of items
     * added.
     */
    fun addTextToTemplate(templateId: Long, text: String): Int {
        val entries = ShoppingEntryParser.parse(text)
        if (entries.isEmpty()) return 0
        viewModelScope.launch {
            repository.updateShoppingTemplateItems(templateId) { current ->
                entries.fold(current) { acc, entry -> ShoppingTemplateCodec.upsert(acc, TemplateItem.of(entry)) }
            }
        }
        return entries.size
    }

    fun removeFromTemplate(templateId: Long, index: Int) {
        viewModelScope.launch {
            repository.updateShoppingTemplateItems(templateId) { current ->
                if (index in current.indices) current.filterIndexed { i, _ -> i != index } else current
            }
        }
    }

    fun renameTemplate(templateId: Long, name: String) {
        viewModelScope.launch { repository.renameShoppingTemplate(templateId, name) }
    }

    /**
     * Looks up the barcode in Open Food Facts and saves it to the template. [onResult] receives the
     * message to show.
     */
    fun addScannedProductToTemplate(templateId: Long, barcode: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = OpenFoodFactsClient.lookup(barcode)) {
                is ProductLookupResult.Found -> {
                    addToTemplate(templateId, result.product.toTemplateItem())
                    onResult(L10n.str(R.string.added_template_x, result.product.name))
                }
                ProductLookupResult.NotFound -> onResult(L10n.str(R.string.product_not_found_open_food, barcode))
                is ProductLookupResult.Error -> onResult(L10n.str(R.string.couldnt_look_up_product_x, result.reason))
            }
        }
    }

    fun applyTemplate(template: ShoppingTemplate) {
        viewModelScope.launch { repository.applyShoppingTemplate(template.items) }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { repository.deleteShoppingTemplate(id) }
    }

    fun setChecked(id: Long, checked: Boolean) {
        viewModelScope.launch { repository.setShoppingListItemChecked(id, checked) }
    }

    /** [onDeleted] is called once removed, to offer "Undo" through [restoreItem]. */
    fun deleteItem(item: ShoppingListItem, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteShoppingListItem(item.id)
            onDeleted()
        }
    }

    fun restoreItem(item: ShoppingListItem) {
        viewModelScope.launch { repository.restoreShoppingListItem(item) }
    }

    /**
     * Parses [text] (one or more items, with quantity and unit), adds them and returns how many.
     */
    fun addEntries(text: String, splitOnY: Boolean = false): Int {
        val entries = ShoppingEntryParser.parse(text, splitOnY)
        addParsedEntries(entries)
        return entries.size
    }

    fun addParsedEntries(entries: List<ParsedShoppingEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            repository.addShoppingListEntries(entries.map { Ingredient(it.name, it.quantity, it.unit) })
        }
    }

    /**
     * Adds a suggestion. What the user typed ([typed]) takes precedence over what the history
     * remembers.
     */
    fun addSuggestion(suggestion: ShoppingSuggestion, typed: ParsedShoppingEntry?) {
        val quantity = typed?.quantity ?: suggestion.quantity
        val unit = typed?.unit ?: if (typed?.quantity == null) suggestion.unit else null
        addParsedEntries(listOf(ParsedShoppingEntry(suggestion.name, quantity, unit)))
    }

    /** Pending (unchecked) items, as shared by QR code. */
    fun pendingEntries(): List<ParsedShoppingEntry> =
        groups.value.flatMap { it.items }.filter { !it.checked }.map { ParsedShoppingEntry(it.name, it.quantity, it.unit) }

    fun clearAll() {
        viewModelScope.launch { repository.clearShoppingList() }
    }

    fun clearChecked() {
        viewModelScope.launch { repository.clearCheckedShoppingListItems() }
    }

    fun share(context: Context) {
        RecipeExporter.shareShoppingListAsText(context, groups.value)
    }
}

/** Open Food Facts product as a template item (with photo and product sheet). */
fun ScannedProduct.toTemplateItem(): TemplateItem = TemplateItem(name = name, imageUrl = imageUrl, info = info)

/** List item with an Open Food Facts product as a template item. */
fun ShoppingListItem.toTemplateItem(): TemplateItem = TemplateItem(name, quantity, unit, imageUrl, productInfo)
