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

/** Estado de la búsqueda de productos en Open Food Facts. */
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

    /** Artículos pendientes de cada lista (uid -> cantidad), para mostrarlos en las pestañas de listas. */
    val listCounts: StateFlow<Map<String, Int>> = repository.observeShoppingListPendingCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _searchState = MutableStateFlow<ProductSearchState>(ProductSearchState.Idle)
    val searchState: StateFlow<ProductSearchState> = _searchState

    /** Busca productos por nombre en Open Food Facts (solo al enviar la búsqueda, no en cada tecla). */
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

    /** Añade a la lista actual un producto elegido en la búsqueda (con su foto y su ficha). */
    fun addSearchedProduct(product: ScannedProduct) {
        viewModelScope.launch { repository.addScannedShoppingProduct(product.name, product.imageUrl, product.info) }
    }

    val selectedListUid: StateFlow<String> = settingsRepository.observeShoppingListUid()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_SHOPPING_LIST_UID)

    /** Nombre con el que se firman los cambios en una lista compartida (vacío = sin firma). */
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

    /** Sincroniza ahora la conexión que comparte las listas (si hay una); la pantalla lo repite mientras está visible. */
    suspend fun syncSharedListsOnce(context: Context) {
        val connectionId = repository.getShoppingSyncConnectionId() ?: return
        syncEngine.syncConnection(context, connectionId)
    }

    /**
     * Avisos de "Ana añadió 2 artículos" cuando llegan artículos nuevos de otra persona (por sync) a la
     * lista que se está viendo. La primera carga de cada lista no avisa (todo sería "nuevo").
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

    /** La tienda elegida (null si no hay ninguna o fue borrada). */
    val selectedStore: StateFlow<ShoppingStore?> = combine(stores, selectedStoreId) { list, id -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Nombres de las categorías de ingredientes, para completar el orden de pasillos al editar una tienda. */
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
     * Casilla del catálogo táctil: si [name] ya está pendiente en la lista lo quita (con tombstone,
     * como cualquier borrado) y si no lo añade sin cantidad.
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

    /** Busca [barcode] en Open Food Facts y, si existe, lo añade con su foto; [onResult] recibe el mensaje a mostrar. */
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

    /** Guarda la lista actual (con fotos y fichas de los productos) como plantilla nueva. */
    fun saveTemplate(name: String) {
        val items = groups.value.flatMap { it.items }.map { TemplateItem(it.name, it.quantity, it.unit, it.imageUrl, it.productInfo) }
        if (items.isEmpty() || name.isBlank()) return
        viewModelScope.launch { repository.saveShoppingTemplate(name, items) }
    }

    /** Crea una plantilla vacía (o con [first]) y devuelve su id por [onCreated]. */
    fun createTemplate(name: String, first: TemplateItem? = null, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveShoppingTemplate(name, listOfNotNull(first))?.let(onCreated)
        }
    }

    fun addToTemplate(templateId: Long, item: TemplateItem) {
        viewModelScope.launch { repository.updateShoppingTemplateItems(templateId) { ShoppingTemplateCodec.upsert(it, item) } }
    }

    /** Añade a la plantilla lo escrito a mano ("2 kg tomates, leche"); devuelve cuántos artículos. */
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

    /** Busca el código en Open Food Facts y lo guarda en la plantilla; [onResult] recibe el mensaje a mostrar. */
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

    /** [onDeleted] se llama una vez quitado, para ofrecer "Deshacer" con [restoreItem]. */
    fun deleteItem(item: ShoppingListItem, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteShoppingListItem(item.id)
            onDeleted()
        }
    }

    fun restoreItem(item: ShoppingListItem) {
        viewModelScope.launch { repository.restoreShoppingListItem(item) }
    }

    /** Interpreta [text] (uno o varios artículos, con cantidad/unidad) y los añade; devuelve cuántos. */
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

    /** Añade una sugerencia; lo ya escrito ([typed]) manda sobre lo recordado del historial. */
    fun addSuggestion(suggestion: ShoppingSuggestion, typed: ParsedShoppingEntry?) {
        val quantity = typed?.quantity ?: suggestion.quantity
        val unit = typed?.unit ?: if (typed?.quantity == null) suggestion.unit else null
        addParsedEntries(listOf(ParsedShoppingEntry(suggestion.name, quantity, unit)))
    }

    /** Artículos pendientes (sin marcar), tal cual se comparten por QR. */
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

/** Producto de Open Food Facts como artículo de plantilla (con foto y ficha). */
fun ScannedProduct.toTemplateItem(): TemplateItem = TemplateItem(name = name, imageUrl = imageUrl, info = info)

/** Artículo de la lista con producto de Open Food Facts como artículo de plantilla. */
fun ShoppingListItem.toTemplateItem(): TemplateItem = TemplateItem(name, quantity, unit, imageUrl, productInfo)
