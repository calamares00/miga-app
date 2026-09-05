package com.bmo00.miga.ui.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bmo00.miga.data.local.SettingsRepository
import com.bmo00.miga.data.model.Difficulty
import com.bmo00.miga.data.model.RecipeDraft
import com.bmo00.miga.data.repository.RecipeRepository
import com.bmo00.miga.data.vision.RecipeVisionResult
import com.bmo00.miga.data.vision.RecipeVisionResultDto
import com.bmo00.miga.data.vision.visionClientFor
import com.bmo00.miga.ui.navigation.Destinations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VisionState {
    data object Idle : VisionState
    data object Loading : VisionState
    data object Loaded : VisionState
    data class Error(val reason: String) : VisionState
}

class RecipeEditorViewModel(
    private val repository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
    private val recipeId: Long,
    private val bookId: Long
) : ViewModel() {

    val isEditing: Boolean = recipeId != Destinations.NEW_RECIPE_ID

    var isLoading by mutableStateOf(isEditing)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var nameError by mutableStateOf(false)

    var name by mutableStateOf("")
    var categoryName by mutableStateOf<String?>(null)
    var difficulty by mutableStateOf(Difficulty.MEDIA)
    var prepTimeMinutesText by mutableStateOf("")
    var cookTimeMinutesText by mutableStateOf("")
    var servings by mutableIntStateOf(4)
    var notes by mutableStateOf("")
    var source by mutableStateOf("")
    var isFavorite by mutableStateOf(false)

    val photos = mutableStateListOf<PhotoUi>()
    val ingredientGroups = mutableStateListOf<IngredientGroupUi>().apply { add(IngredientGroupUi()) }
    val stepGroups = mutableStateListOf<StepGroupUi>().apply { add(StepGroupUi()) }
    val selectedTags = mutableStateListOf<String>()
    val selectedUtensils = mutableStateListOf<String>()

    val availableCategories: StateFlow<List<String>> = repository.observeCategories()
        .map { it.map { c -> c.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableTags: StateFlow<List<String>> = repository.observeTags()
        .map { it.map { t -> t.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableUtensils: StateFlow<List<String>> = repository.observeUtensils()
        .map { it.map { u -> u.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableIngredientNames: StateFlow<List<String>> = repository.observeIngredientNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _visionState = MutableStateFlow<VisionState>(VisionState.Idle)
    val visionState: StateFlow<VisionState> = _visionState
    private var visionStarted = false

    /** Reconoce una receta a partir de una foto (cámara o galería) y precarga este formulario con el resultado. */
    fun startVisionExtraction(context: Context, photoUri: Uri) {
        if (isEditing || visionStarted) return
        visionStarted = true
        viewModelScope.launch {
            _visionState.value = VisionState.Loading
            val apiKey = settingsRepository.observeGeminiApiKey().first()
            if (apiKey.isBlank()) {
                _visionState.value = VisionState.Error("Configura una API key de Gemini en Ajustes")
                return@launch
            }
            val bytes = context.contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
            if (bytes == null) {
                _visionState.value = VisionState.Error("No se pudo leer la foto")
                return@launch
            }
            val mimeType = context.contentResolver.getType(photoUri) ?: "image/jpeg"
            val provider = settingsRepository.observeVisionProvider().first()
            val model = settingsRepository.observeGeminiModel().first()
            when (val result = visionClientFor(provider).extractRecipe(bytes, mimeType, apiKey, model)) {
                is RecipeVisionResult.Success -> {
                    applyVisionResult(result.recipe)
                    _visionState.value = VisionState.Loaded
                }
                is RecipeVisionResult.Error -> _visionState.value = VisionState.Error(result.reason)
            }
        }
    }

    private fun applyVisionResult(recipe: RecipeVisionResultDto) {
        // Algunos libros titulan la sección con varias categorías juntas separadas por coma
        // (p.ej. "Arroz, legumbres, patatas y pasta"); solo la primera se usa como categoría de
        // la receta (el modelo de datos admite una sola) y el resto se añaden como etiquetas.
        val categoryParts = recipe.categoryName?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        name = recipe.name
        categoryName = categoryParts.firstOrNull()
        difficulty = runCatching { Difficulty.valueOf(recipe.difficulty) }.getOrDefault(Difficulty.MEDIA)
        prepTimeMinutesText = recipe.prepTimeMinutes?.toString().orEmpty()
        cookTimeMinutesText = recipe.cookTimeMinutes?.toString().orEmpty()
        servings = recipe.servings.coerceIn(1, 99)
        notes = recipe.notes
        source = recipe.source
        ingredientGroups.clear(); ingredientGroups.addAll(recipe.ingredientGroups.map { it.toUi() }.ifEmpty { listOf(IngredientGroupUi()) })
        stepGroups.clear(); stepGroups.addAll(recipe.stepGroups.map { it.toUi() }.ifEmpty { listOf(StepGroupUi()) })
        selectedTags.clear(); selectedTags.addAll((recipe.tags + categoryParts.drop(1)).distinct())
        selectedUtensils.clear(); selectedUtensils.addAll(recipe.utensils)
    }

    init {
        if (isEditing) {
            viewModelScope.launch {
                repository.observeRecipe(recipeId).first()?.let { recipe ->
                    name = recipe.name
                    categoryName = recipe.categoryName
                    difficulty = recipe.difficulty
                    prepTimeMinutesText = recipe.prepTimeMinutes?.toString().orEmpty()
                    cookTimeMinutesText = recipe.cookTimeMinutes?.toString().orEmpty()
                    servings = recipe.servings
                    notes = recipe.notes
                    source = recipe.source
                    isFavorite = recipe.isFavorite
                    photos.clear(); photos.addAll(recipe.photos.map { it.toUi() })
                    ingredientGroups.clear(); ingredientGroups.addAll(recipe.ingredientGroups.map { it.toUi() }.ifEmpty { listOf(IngredientGroupUi()) })
                    stepGroups.clear(); stepGroups.addAll(recipe.stepGroups.map { it.toUi() }.ifEmpty { listOf(StepGroupUi()) })
                    selectedTags.clear(); selectedTags.addAll(recipe.tags)
                    selectedUtensils.clear(); selectedUtensils.addAll(recipe.utensils)
                }
                isLoading = false
            }
        }
    }

    // --- Ingredientes ---
    fun addIngredientRow(groupIndex: Int) {
        ingredientGroups.getOrNull(groupIndex)?.ingredients?.add(IngredientRowUi())
    }

    fun removeIngredientRow(groupIndex: Int, rowIndex: Int) {
        ingredientGroups.getOrNull(groupIndex)?.ingredients?.removeAt(rowIndex)
    }

    fun addIngredientSubGroup() {
        ingredientGroups.add(IngredientGroupUi(name = "Nueva sub-receta", ingredients = mutableListOf(IngredientRowUi())))
    }

    fun removeIngredientGroup(groupIndex: Int) {
        if (ingredientGroups.size > 1) ingredientGroups.removeAt(groupIndex)
    }

    // --- Pasos ---
    fun addStepRow(groupIndex: Int) {
        stepGroups.getOrNull(groupIndex)?.steps?.add(StepRowUi())
    }

    fun removeStepRow(groupIndex: Int, rowIndex: Int) {
        stepGroups.getOrNull(groupIndex)?.steps?.removeAt(rowIndex)
    }

    fun addStepSubGroup() {
        stepGroups.add(StepGroupUi(name = "Nueva sub-receta", steps = mutableListOf(StepRowUi())))
    }

    fun removeStepGroup(groupIndex: Int) {
        if (stepGroups.size > 1) stepGroups.removeAt(groupIndex)
    }

    // --- Fotos ---
    fun addPhoto(uri: String) {
        photos.add(PhotoUi(uri, isCover = photos.isEmpty()))
    }

    fun removePhoto(photo: PhotoUi) {
        val wasCover = photo.isCover
        photos.remove(photo)
        if (wasCover && photos.isNotEmpty()) photos[0].isCover = true
    }

    fun setCoverPhoto(photo: PhotoUi) {
        photos.forEach { it.isCover = it === photo }
    }

    // --- Tags / utensilios ---
    fun toggleTag(name: String) {
        if (selectedTags.contains(name)) selectedTags.remove(name) else selectedTags.add(name)
    }

    fun toggleUtensil(name: String) {
        if (selectedUtensils.contains(name)) selectedUtensils.remove(name) else selectedUtensils.add(name)
    }

    fun addCustomTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !selectedTags.contains(trimmed)) selectedTags.add(trimmed)
    }

    fun addCustomUtensil(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && !selectedUtensils.contains(trimmed)) selectedUtensils.add(trimmed)
    }

    fun save(onSaved: (Long) -> Unit) {
        if (name.isBlank()) {
            nameError = true
            return
        }
        viewModelScope.launch {
            isSaving = true
            val draft = RecipeDraft(
                id = if (isEditing) recipeId else 0L,
                recipeBookId = bookId,
                name = name,
                categoryName = categoryName,
                difficulty = difficulty,
                prepTimeMinutes = prepTimeMinutesText.toIntOrNull(),
                cookTimeMinutes = cookTimeMinutesText.toIntOrNull(),
                servings = servings,
                notes = notes,
                source = source,
                isFavorite = isFavorite,
                photos = photos.map { it.toDomain() },
                ingredientGroups = ingredientGroups.map { it.toDomain() },
                stepGroups = stepGroups.map { it.toDomain() },
                tagNames = selectedTags.toList(),
                utensilNames = selectedUtensils.toList()
            )
            val id = repository.saveRecipe(draft)
            isSaving = false
            onSaved(id)
        }
    }
}
