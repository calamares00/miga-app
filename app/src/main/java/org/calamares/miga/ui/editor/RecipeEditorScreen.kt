package org.calamares.miga.ui.editor

import org.calamares.miga.ui.components.rememberCopyToClipboard
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Checkbox
import org.calamares.miga.data.tips.TipCategory
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.AssistChip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import org.calamares.miga.data.model.PhotoCredit
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.calamares.miga.ui.components.keepScrollInSheet
import org.calamares.miga.data.model.ContentKind
import org.calamares.miga.data.model.RecipeCategories
import org.calamares.miga.data.content.ContentCleanup
import org.calamares.miga.ui.components.rememberCameraCapture
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.AiProgressView
import androidx.compose.foundation.layout.widthIn
import org.calamares.miga.ui.components.RichTextField
import org.calamares.miga.ui.components.ErrorMessage
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Info
import org.calamares.miga.data.support.ErrorDetail
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.ui.components.AiContentNotice
import android.net.Uri
import android.widget.Toast
import org.calamares.miga.ui.components.CountryPickerDialog
import org.calamares.miga.data.model.RecipeOrigin
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Surface
import org.calamares.miga.data.vision.NewLabels
import androidx.compose.material.icons.filled.NewLabel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.calamares.miga.data.model.Difficulty
import org.calamares.miga.ui.components.PhotoEditorOverlay
import org.calamares.miga.ui.components.PhotoSourceSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditorScreen(
    viewModel: RecipeEditorViewModel,
    sourcePhotoUris: List<String> = emptyList(),
    sourceDishName: String? = null,
    sourceDishDescription: String = "",
    sourceDishOrigin: String? = null,
    sourceRecipeUrl: String? = null,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val availableCategories by viewModel.availableCategories.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val availableUtensils by viewModel.availableUtensils.collectAsStateWithLifecycle()
    val popularTags by viewModel.popularTags.collectAsStateWithLifecycle()
    val popularUtensils by viewModel.popularUtensils.collectAsStateWithLifecycle()
    val visionState by viewModel.visionState.collectAsStateWithLifecycle()
    val aiProgress by viewModel.aiProgress.collectAsStateWithLifecycle()
    val detectedRecipes by viewModel.detectedRecipes.collectAsStateWithLifecycle()
    var visionErrorDismissed by remember { mutableStateOf(false) }
    var showVisionErrorDialog by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var pendingEditUri by remember { mutableStateOf<Uri?>(null) }
    var editingPhoto by remember { mutableStateOf<PhotoUi?>(null) }
    val photoSheetState = rememberBottomSheetState(SheetValue.Hidden)
    val copyToClipboard = rememberCopyToClipboard()
    var showDiscardDialog by remember { mutableStateOf(false) }
    val attemptExit = { if (viewModel.hasUnsavedChanges()) showDiscardDialog = true else onCancel() }

    BackHandler(onBack = attemptExit)

    LaunchedEffect(sourcePhotoUris) {
        if (sourcePhotoUris.isNotEmpty()) viewModel.startVisionExtraction(context, sourcePhotoUris.map { Uri.parse(it) })
    }
    LaunchedEffect(sourceDishName) {
        if (!sourceDishName.isNullOrBlank()) viewModel.startDishGeneration(sourceDishName, sourceDishDescription, sourceDishOrigin)
    }
    LaunchedEffect(sourceRecipeUrl) {
        if (!sourceRecipeUrl.isNullOrBlank()) viewModel.startUrlImport(context, sourceRecipeUrl)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingEditUri = uri
        }
    }
    val cameraCapture = rememberCameraCapture { path -> pendingEditUri = Uri.parse(path) }

    val formScroll = rememberScrollState()
    val tabScope = rememberCoroutineScope()
    /** Where each part of the form starts (see EditorSection), to jump to it from the tabs. */
    val sectionTops = remember { mutableStateMapOf<Int, Int>() }
    val currentSection by remember {
        derivedStateOf {
            if (formScroll.value >= formScroll.maxValue && formScroll.maxValue > 0) EDITOR_SECTIONS - 1
            else sectionTops.filterValues { it <= formScroll.value + 48 }.keys.maxOrNull() ?: 0
        }
    }
    fun editorSections() = listOf(
        L10n.str(R.string.editor_basics),
        L10n.str(R.string.editor_tab_count_x, L10n.str(R.string.ingredients), viewModel.ingredientGroups.sumOf { group -> group.ingredients.count { it.name.isNotBlank() } }),
        L10n.str(R.string.editor_tab_count_x, L10n.str(R.string.editor_tab_steps), viewModel.stepGroups.sumOf { group -> group.steps.count { it.text.isNotBlank() } }),
        L10n.str(R.string.editor_more)
    )
    fun Modifier.section(index: Int) = onGloballyPositioned { sectionTops[index] = it.positionInParent().y.roundToInt() }

    AdaptiveScaffold(
        topBar = {
          Column {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (viewModel.isEditing) L10n.str(R.string.edit_recipe) else L10n.str(R.string.new_recipe)) },
                navigationIcon = {
                    IconButton(onClick = attemptExit) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel)) }
                },
                actions = {
                    if (viewModel.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                    } else {
                        Button(onClick = { viewModel.save(onSaved) }, modifier = Modifier.padding(end = 8.dp)) { Text(L10n.str(R.string.save)) }
                    }
                }
            )
            if (!viewModel.isLoading) {
                // Jumps to each part of the form; the tab follows the part on screen.
                PrimaryScrollableTabRow(
                    selectedTabIndex = currentSection,
                    containerColor = MaterialTheme.colorScheme.background,
                    edgePadding = 8.dp
                ) {
                    editorSections().forEachIndexed { index, title ->
                        Tab(
                            selected = index == currentSection,
                            onClick = { sectionTops[index]?.let { top -> tabScope.launch { formScroll.animateScrollTo(top) } } },
                            text = { Text(title, maxLines = 1) }
                        )
                    }
                }
            }
          }
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@AdaptiveScaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(formScroll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            if (visionState is VisionState.Error && !visionErrorDismissed) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    ErrorMessage(
                        reason = (visionState as VisionState.Error).reason,
                        modifier = Modifier.weight(1f),
                        onRetry = { viewModel.retryAi() }
                    )
                    IconButton(onClick = { visionErrorDismissed = true }) {
                        Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.dismiss_notice))
                    }
                }
            }

            if (visionState is VisionState.Loaded) {
                viewModel.webPolish?.let { polish ->
                    WebPolishNotice(polish.changes, onUndo = viewModel::undoWebPolish)
                }
                if (viewModel.webPolishError != null) {
                    Text(
                        L10n.str(R.string.web_polish_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (visionState is VisionState.Loaded && viewModel.filledByAi) {
                AiContentNotice(
                    feature = L10n.str(R.string.ai_generated_recipe),
                    content = {
                        buildString {
                            appendLine(viewModel.name)
                            viewModel.ingredientGroups.flatMap { it.ingredients }.forEach { appendLine("- ${it.quantity} ${it.unit} ${it.name}".trim()) }
                            viewModel.stepGroups.flatMap { it.steps }.forEachIndexed { i, step -> appendLine("${i + 1}. ${step.text}") }
                        }
                    }
                )
                val toCreate = viewModel.newLabelsToCreate()
                if (!toCreate.isEmpty) NewLabelsNotice(toCreate)
            }

            EditorCard(title = L10n.str(R.string.editor_basics), icon = Icons.Outlined.Info, modifier = Modifier.section(0)) {
                PhotosRow(
                    viewModel = viewModel,
                    onAddPhoto = { showPhotoSourceSheet = true },
                    onEditPhoto = { editingPhoto = it }
                )
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = { viewModel.name = it; viewModel.nameError = false },
                    label = { Text(L10n.str(R.string.recipe_name)) },
                    isError = viewModel.nameError,
                    supportingText = { if (viewModel.nameError) Text(L10n.str(R.string.name_required)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    CategoryField(
                        value = viewModel.categoryName.orEmpty(),
                        suggestions = availableCategories,
                        onValueChange = { text -> viewModel.categoryName = text.takeIf { it.isNotBlank() } },
                        modifier = Modifier.weight(1f)
                    )
                    OriginField(
                        value = viewModel.origin,
                        countryCode = viewModel.originCountry,
                        onValueChange = viewModel::updateOrigin,
                        onPickCountry = viewModel::pickOriginCountry,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            EditorCard(title = L10n.str(R.string.editor_details), icon = Icons.Outlined.Timer) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = viewModel.prepTimeMinutesText,
                        onValueChange = { if (it.all(Char::isDigit)) viewModel.prepTimeMinutesText = it },
                        label = { Text(L10n.str(R.string.editor_prep_label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        suffix = { Text(L10n.str(R.string.editor_minutes_suffix)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = viewModel.cookTimeMinutesText,
                        onValueChange = { if (it.all(Char::isDigit)) viewModel.cookTimeMinutesText = it },
                        label = { Text(L10n.str(R.string.editor_cook_label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        suffix = { Text(L10n.str(R.string.editor_minutes_suffix)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = viewModel.servings.toString(),
                        onValueChange = { text -> text.toIntOrNull()?.let { viewModel.servings = it.coerceIn(1, 99) } },
                        label = { Text(L10n.str(R.string.servings_2), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Difficulty.entries.forEach { d ->
                        FilterChip(
                            selected = viewModel.difficulty == d,
                            onClick = { viewModel.difficulty = d },
                            label = { Text(d.label) },
                            modifier = Modifier.semantics { contentDescription = L10n.str(R.string.difficulty) + ": " + d.label }
                        )
                    }
                }
            }

            EditorCard(title = null, icon = null, modifier = Modifier.section(1)) { IngredientsEditor(viewModel) }

            EditorCard(title = null, icon = null, modifier = Modifier.section(2)) { StepsEditor(viewModel) }

            EditorCard(title = L10n.str(R.string.editor_more), icon = Icons.Outlined.MoreHoriz, modifier = Modifier.section(3)) {
                Section(title = L10n.str(R.string.utensils_needed)) {
                    ChipMultiSelect(
                        selected = viewModel.selectedUtensils,
                        available = availableUtensils,
                        popular = popularUtensils,
                        onToggle = viewModel::toggleUtensil,
                        onAddCustom = viewModel::addCustomUtensil,
                        label = L10n.str(R.string.add_utensil),
                        kind = ContentKind.EQUIPMENT
                    )
                }
                Section(title = L10n.str(R.string.tags)) {
                    ChipMultiSelect(
                        selected = viewModel.selectedTags,
                        available = availableTags,
                        popular = popularTags,
                        onToggle = viewModel::toggleTag,
                        onAddCustom = viewModel::addCustomTag,
                        label = L10n.str(R.string.add_tag),
                        kind = ContentKind.TAG
                    )
                }
                val (notesText, photoCredit) = remember(viewModel.notes) { PhotoCredit.split(viewModel.notes) }
                RichTextField(
                    value = notesText,
                    onValueChange = { viewModel.notes = PhotoCredit.join(it, photoCredit) },
                    label = { Text(L10n.str(R.string.notes_variants)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                photoCredit?.let { credit ->
                    Text(PhotoCredit.plain(credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = viewModel.source,
                    onValueChange = { viewModel.source = it },
                    label = { Text(L10n.str(R.string.source_book_website_etc)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.isFavorite = !viewModel.isFavorite },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(L10n.str(R.string.mark_favourite), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.isFavorite, onCheckedChange = { viewModel.isFavorite = it })
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }

        if (visionState is VisionState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    AiProgressView(
                        progress = aiProgress,
                        fallback = L10n.str(R.string.generating_recipe_ai),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp).widthIn(max = 320.dp),
                        spinnerSize = 32.dp
                    )
                }
            }
        }
        }
    }

    detectedRecipes?.let { detected ->
        DetectedRecipesDialog(
            names = detected.recipes.map { it.name },
            together = detected.together,
            canSaveSeparately = viewModel.canSaveSeparately,
            onJoin = { viewModel.importDetectedTogether(it) },
            onOneEach = { selected ->
                viewModel.importDetectedSeparately(selected) { count, created ->
                    val message = (listOf(L10n.str(R.string.detected_saved_x, count)) + created).joinToString("\n")
                    Toast.makeText(context, message, if (created.isEmpty()) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                    onCancel()
                }
            },
            onCancel = onCancel
        )
    }

    // Tips given besides the recipe: the user picks which ones go to the tips library.
    val extraTips by viewModel.extraTips.collectAsStateWithLifecycle()
    val extraOffer = extraTips
    if (extraOffer != null && visionState !is VisionState.Loading) {
        var chosen by remember(extraOffer) { mutableStateOf(extraOffer.tips.indices.toSet()) }
        AlertDialog(
            onDismissRequest = viewModel::dismissExtraTips,
            icon = { Text("💡", style = MaterialTheme.typography.headlineMedium) },
            title = { Text(L10n.str(R.string.extra_tips_title, extraOffer.tips.size)) },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(L10n.str(R.string.extra_tips_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    extraOffer.tips.forEachIndexed { index, tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                                .clickable { chosen = if (index in chosen) chosen - index else chosen + index }
                        ) {
                            Checkbox(checked = index in chosen, onCheckedChange = { chosen = if (it) chosen + index else chosen - index })
                            Column(Modifier.padding(top = 10.dp, end = 4.dp)) {
                                Text(tip.title, style = MaterialTheme.typography.titleSmall)
                                Text(tip.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = chosen.isNotEmpty(), onClick = {
                    viewModel.saveExtraTips(chosen) { count ->
                        Toast.makeText(context, L10n.str(R.string.extra_tips_saved, count), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(L10n.str(R.string.extra_tips_save)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissExtraTips) { Text(L10n.str(R.string.extra_tips_skip)) } }
        )
    }

    // A cooking tip found instead of a recipe: offered for the tips library, then the editor closes.
    (visionState as? VisionState.TipFound)?.let { found ->
        val category = runCatching { TipCategory.valueOf(found.tip.category.uppercase()) }.getOrDefault(TipCategory.KITCHEN)
        AlertDialog(
            onDismissRequest = viewModel::dismissFoundTip,
            icon = { Text(category.emoji, style = MaterialTheme.typography.headlineMedium) },
            title = { Text(L10n.str(R.string.tip_found_title)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(L10n.str(R.string.tip_found_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(found.tip.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                    Text(found.tip.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text(category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addFoundTip {
                        Toast.makeText(context, L10n.str(R.string.tip_found_added), Toast.LENGTH_SHORT).show()
                        onCancel()
                    }
                }) { Text(L10n.str(R.string.tip_found_add)) }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissFoundTip) { Text(L10n.str(R.string.cancel)) } }
        )
    }

    if (showVisionErrorDialog && visionState is VisionState.Error) {
        val fullReason = (visionState as VisionState.Error).reason
        val reason = ErrorDetail.detail(fullReason) ?: ErrorDetail.summary(fullReason)
        AlertDialog(
            onDismissRequest = { showVisionErrorDialog = false },
            title = { Text(L10n.str(R.string.error_details)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(reason, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyToClipboard(reason) }) { Text(L10n.str(R.string.copy)) }
            },
            dismissButton = {
                TextButton(onClick = { showVisionErrorDialog = false }) { Text(L10n.str(R.string.close)) }
            }
        )
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            Column(modifier = Modifier.keepScrollInSheet()) {
                PhotoSourceSheet(
                    title = L10n.str(R.string.add_photo),
                    onCameraClick = {
                        showPhotoSourceSheet = false
                        cameraCapture.launch()
                    },
                    onGalleryClick = {
                        showPhotoSourceSheet = false
                        photoPicker.launch("image/*")
                    }
                )
            }
        }
    }

    val editingExistingPhoto = editingPhoto
    val editSourceUri = pendingEditUri ?: editingExistingPhoto?.let { Uri.parse(it.uri) }
    if (editSourceUri != null) {
        PhotoEditorOverlay(
            sourceUri = editSourceUri,
            onSave = { path ->
                if (editingExistingPhoto != null) {
                    viewModel.updatePhotoUri(editingExistingPhoto, path)
                    editingPhoto = null
                } else {
                    viewModel.addPhoto(path)
                    pendingEditUri = null
                }
            },
            onCancel = {
                pendingEditUri = null
                editingPhoto = null
            }
        )
    }

    viewModel.duplicate?.let { existing ->
        AlertDialog(
            onDismissRequest = { viewModel.duplicate = null },
            title = { Text(L10n.str(R.string.duplicate_recipe_title)) },
            text = { Text(L10n.str(R.string.duplicate_recipe_body_x_x, existing.name, existing.recipeBookName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.save(onSaved, allowDuplicate = true) }) { Text(L10n.str(R.string.save_anyway)) }
            },
            dismissButton = { TextButton(onClick = { viewModel.duplicate = null }) { Text(L10n.str(R.string.keep_editing)) } }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(L10n.str(R.string.discard_changes)) },
            text = { Text(L10n.str(R.string.changes_lost)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onCancel()
                }) { Text(L10n.str(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(L10n.str(R.string.keep_editing)) }
            }
        )
    }
}

@Composable
private fun PhotosRow(viewModel: RecipeEditorViewModel, onAddPhoto: () -> Unit, onEditPhoto: (PhotoUi) -> Unit) {
    Section(title = L10n.str(R.string.photos)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.photos, key = { it.uri }) { photo ->
                Box(modifier = Modifier.size(88.dp)) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = L10n.str(R.string.edit_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEditPhoto(photo) }
                    )
                    IconButton(
                        onClick = { viewModel.removePhoto(photo) },
                        modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = L10n.str(R.string.remove_photo),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setCoverPhoto(photo) },
                        modifier = Modifier.size(24.dp).align(Alignment.BottomStart)
                    ) {
                        Icon(
                            if (photo.isCover) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (photo.isCover) L10n.str(R.string.cover_photo) else L10n.str(R.string.set_cover),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        )
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onAddPhoto),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AddAPhoto, contentDescription = L10n.str(R.string.add_photo))
                }
            }
        }
    }
}

/**
 * Where the recipe comes from: free text ("México", "Córdoba", "cocina tailandesa") with the flag
 * of its country, guessed from the text or picked from the list by tapping the flag.
 */
@Composable
private fun OriginField(
    value: String,
    countryCode: String?,
    onValueChange: (String) -> Unit,
    onPickCountry: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(L10n.str(R.string.origin)) },
        placeholder = { Text(L10n.str(R.string.origin_hint)) },
        singleLine = true,
        leadingIcon = {
            IconButton(onClick = { picking = true }) {
                val flag = RecipeOrigin.flag(countryCode)
                if (flag != null) {
                    Text(flag, style = MaterialTheme.typography.titleLarge)
                } else {
                    Icon(Icons.Filled.Public, contentDescription = L10n.str(R.string.origin_country))
                }
            }
        },
        supportingText = {
            Text(
                countryCode?.let { RecipeOrigin.countryName(it) } ?: L10n.str(R.string.origin_tap_flag),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier
    )
    if (picking) {
        CountryPickerDialog(
            selected = countryCode,
            onPick = { code -> onPickCountry(code); picking = false },
            onDismiss = { picking = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(value: String, suggestions: List<String>, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val language = L10n.locale().language
    // "postre" when "Postres" exists, or "Tartas" when "Repostería" does: offered instead of
    // creating a near duplicate.
    val similar = remember(value, suggestions) {
        ContentCleanup.similarExisting(ContentKind.CATEGORY, value, suggestions, language)
            ?: RecipeCategories.builtInFor(value)?.name(language)?.takeIf { name -> name != value && suggestions.any { it.equals(name, ignoreCase = true) } }
    }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(L10n.str(R.string.category)) },
            placeholder = { Text(L10n.str(R.string.e_g_desserts_soups_pasta)) },
            supportingText = similar?.let { name ->
                {
                    Text(
                        L10n.str(R.string.did_you_mean_x, name),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onValueChange(name); expanded = false }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (expanded && suggestions.isNotEmpty()) {
            val filtered = suggestions.filter { it.contains(value, ignoreCase = true) && it != value }
            if (filtered.isNotEmpty()) {
                Column {
                    filtered.take(5).forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValueChange(suggestion); expanded = false }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(suggestion)
                            RecipeCategories.description(suggestion, language)?.let { description ->
                                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Picks several utensils or tags without listing every one that exists: the chosen ones are shown
 * as removable chips, and a field below suggests matches while typing ([popular] ones, the most
 * used, when it is focused and empty). Several can be typed at once separated by commas, and a
 * name that does not exist yet is created.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChipMultiSelect(
    selected: List<String>,
    available: List<String>,
    popular: List<String>,
    onToggle: (String) -> Unit,
    onAddCustom: (String) -> Unit,
    label: String,
    kind: ContentKind
) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    /** The field only shows after tapping "Add", so the chips take little room. */
    var adding by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Not remembered: [selected] is a state list that changes without changing identity, and
    // filtering a few hundred names on each recomposition is cheap.
    val allOptions = (available + selected).distinct()
    val fragment = text.trim()

    /** Each name as an existing one when it matches ignoring case, otherwise as a new one. */
    fun commit(names: List<String>) {
        names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.forEach { name ->
            val existing = allOptions.firstOrNull { it.equals(name, ignoreCase = true) }
            if (existing == null) onAddCustom(name) else if (existing !in selected) onToggle(existing)
        }
    }

    val matches = if (fragment.isEmpty()) {
        // On an empty field: the most used, or the first ones while nothing is used yet.
        if (focused) (popular + available).distinct().filter { it !in selected }.take(MAX_SUGGESTIONS) else emptyList()
    } else {
        available.filter { it !in selected && it.contains(fragment, ignoreCase = true) }
            .sortedWith(compareBy({ !it.startsWith(fragment, ignoreCase = true) }, { it.lowercase() }))
            .take(MAX_SUGGESTIONS)
    }
    // "bol grnade" when "Bol grande" exists: offered instead of creating a near duplicate.
    val similar = if (fragment.isEmpty()) null else ContentCleanup.similarExisting(kind, fragment, allOptions, L10n.locale().language)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.forEach { option ->
                InputChip(
                    selected = true,
                    onClick = { onToggle(option) },
                    label = { Text(option) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.remove_x, option), modifier = Modifier.size(18.dp))
                    }
                )
            }
            if (!adding) {
                AssistChip(
                    onClick = { adding = true },
                    label = { Text(L10n.str(R.string.add)) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.semantics { contentDescription = label }
                )
            }
        }
        if (adding) LaunchedEffect(Unit) { focusRequester.requestFocus() }
        if (adding) OutlinedTextField(
            value = text,
            onValueChange = { value ->
                if (value.any { it == ',' || it == ';' || it == '\n' }) {
                    val parts = value.split(',', ';', '\n')
                    commit(parts.dropLast(1))
                    text = parts.last().trimStart()
                } else {
                    text = value
                }
            },
            label = { Text(label) },
            placeholder = { Text(L10n.str(R.string.separate_with_commas)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(listOf(text)); text = "" }),
            trailingIcon = {
                if (fragment.isNotEmpty()) {
                    IconButton(onClick = { commit(listOf(text)); text = "" }) {
                        Icon(Icons.Filled.Add, contentDescription = L10n.str(R.string.add))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { state ->
                // Leaving an empty field hides it again.
                if (focused && !state.isFocused && text.isBlank()) adding = false
                focused = state.isFocused
            }
        )
        if (matches.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matches.forEach { option ->
                    SuggestionChip(onClick = { commit(listOf(option)); text = "" }, label = { Text(option) })
                }
            }
        }
        if (similar != null && similar !in selected && similar !in matches) {
            Text(
                L10n.str(R.string.did_you_mean_x, similar),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { commit(listOf(similar)); text = "" }
            )
        }
    }
}

private const val MAX_SUGGESTIONS = 8

/** Parts of the form the tabs jump to: basics, ingredients, steps, more. */
private const val EDITOR_SECTIONS = 4

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** Editor section styled like the Settings groups: rounded container and a title with an icon. */
@Composable
private fun EditorCard(title: String?, icon: ImageVector?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        content()
    }
}

/**
 * After an AI fill: the category and equipment that do not exist yet and will be created on saving
 * (the AI is asked to reuse the existing ones, so these are the ones where none fitted).
 */
@Composable
private fun NewLabelsNotice(labels: NewLabels) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.NewLabel, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(L10n.str(R.string.new_labels_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                labels.category?.let {
                    Text(L10n.str(R.string.new_labels_category_x, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                if (labels.equipment.isNotEmpty()) {
                    Text(
                        L10n.str(R.string.new_labels_equipment_x, labels.equipment.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/** A recipe read from a web page and then improved by the assistant: what changed, and undo. */
@Composable
private fun WebPolishNotice(changes: List<String>, onUndo: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(L10n.str(R.string.web_polish_title), style = MaterialTheme.typography.titleSmall)
            changes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = onUndo) { Text(L10n.str(R.string.web_polish_undo)) }
        }
    }
}
