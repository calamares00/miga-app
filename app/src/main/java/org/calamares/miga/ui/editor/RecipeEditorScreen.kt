package org.calamares.miga.ui.editor

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.calamares.miga.data.local.PhotoStorage
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
    val availableCategories by viewModel.availableCategories.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val availableUtensils by viewModel.availableUtensils.collectAsState()
    val visionState by viewModel.visionState.collectAsState()
    val aiProgress by viewModel.aiProgress.collectAsState()
    val detectedRecipes by viewModel.detectedRecipes.collectAsState()
    var visionErrorDismissed by remember { mutableStateOf(false) }
    var showVisionErrorDialog by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var pendingEditUri by remember { mutableStateOf<Uri?>(null) }
    var editingPhoto by remember { mutableStateOf<PhotoUi?>(null) }
    val photoSheetState = rememberModalBottomSheetState()
    val clipboardManager = LocalClipboardManager.current
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
        if (!sourceRecipeUrl.isNullOrBlank()) viewModel.startUrlImport(sourceRecipeUrl)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingEditUri = uri
        }
    }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraPath?.let { pendingEditUri = Uri.parse(it) }
        pendingCameraPath = null
    }

    Scaffold(
        topBar = {
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
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

            EditorCard(title = L10n.str(R.string.editor_basics), icon = Icons.Outlined.Info) {
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
                CategoryField(
                    value = viewModel.categoryName.orEmpty(),
                    suggestions = availableCategories,
                    onValueChange = { text -> viewModel.categoryName = text.takeIf { it.isNotBlank() } }
                )
            }

            EditorCard(title = L10n.str(R.string.editor_details), icon = Icons.Outlined.Timer) {
                Section(title = L10n.str(R.string.difficulty)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Difficulty.entries.forEach { d ->
                            FilterChip(selected = viewModel.difficulty == d, onClick = { viewModel.difficulty = d }, label = { Text(d.label) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = viewModel.prepTimeMinutesText,
                        onValueChange = { if (it.all(Char::isDigit)) viewModel.prepTimeMinutesText = it },
                        label = { Text(L10n.str(R.string.prep_min)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = viewModel.cookTimeMinutesText,
                        onValueChange = { if (it.all(Char::isDigit)) viewModel.cookTimeMinutesText = it },
                        label = { Text(L10n.str(R.string.cooking_min)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = viewModel.servings.toString(),
                        onValueChange = { text -> text.toIntOrNull()?.let { viewModel.servings = it.coerceIn(1, 99) } },
                        label = { Text(L10n.str(R.string.servings_2)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            EditorCard(title = null, icon = null) { IngredientsEditor(viewModel) }

            EditorCard(title = null, icon = null) { StepsEditor(viewModel) }

            EditorCard(title = L10n.str(R.string.editor_more), icon = Icons.Outlined.MoreHoriz) {
                Section(title = L10n.str(R.string.utensils_needed)) {
                    ChipMultiSelect(
                        selected = viewModel.selectedUtensils,
                        available = availableUtensils,
                        onToggle = viewModel::toggleUtensil,
                        onAddCustom = viewModel::addCustomUtensil,
                        addDialogTitle = L10n.str(R.string.add_utensil)
                    )
                }
                Section(title = L10n.str(R.string.tags)) {
                    ChipMultiSelect(
                        selected = viewModel.selectedTags,
                        available = availableTags,
                        onToggle = viewModel::toggleTag,
                        onAddCustom = viewModel::addCustomTag,
                        addDialogTitle = L10n.str(R.string.add_tag)
                    )
                }
                RichTextField(
                    value = viewModel.notes,
                    onValueChange = { viewModel.notes = it },
                    label = { Text(L10n.str(R.string.notes_variants)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
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
                TextButton(onClick = { clipboardManager.setText(AnnotatedString(reason)) }) { Text(L10n.str(R.string.copy)) }
            },
            dismissButton = {
                TextButton(onClick = { showVisionErrorDialog = false }) { Text(L10n.str(R.string.close)) }
            }
        )
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            PhotoSourceSheet(
                title = L10n.str(R.string.add_photo),
                onCameraClick = {
                    showPhotoSourceSheet = false
                    val (contentUri, filePath) = PhotoStorage.createCaptureTarget(context)
                    pendingCameraPath = filePath
                    cameraCaptureLauncher.launch(contentUri)
                },
                onGalleryClick = {
                    showPhotoSourceSheet = false
                    photoPicker.launch("image/*")
                }
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(value: String, suggestions: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(L10n.str(R.string.category)) },
            placeholder = { Text(L10n.str(R.string.e_g_desserts_soups_pasta)) },
            modifier = Modifier.fillMaxWidth()
        )
        if (expanded && suggestions.isNotEmpty()) {
            val filtered = suggestions.filter { it.contains(value, ignoreCase = true) && it != value }
            if (filtered.isNotEmpty()) {
                Column {
                    filtered.take(5).forEach { suggestion ->
                        Text(
                            text = suggestion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onValueChange(suggestion); expanded = false }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipMultiSelect(
    selected: List<String>,
    available: List<String>,
    onToggle: (String) -> Unit,
    onAddCustom: (String) -> Unit,
    addDialogTitle: String
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val allOptions = (available + selected).distinct()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        allOptions.forEach { option ->
            FilterChip(selected = option in selected, onClick = { onToggle(option) }, label = { Text(option) })
        }
        FilterChip(
            selected = false,
            onClick = { showAddDialog = true },
            label = { Icon(Icons.Filled.Add, contentDescription = addDialogTitle, modifier = Modifier.size(18.dp)) }
        )
    }

    if (showAddDialog) {
        var newValue by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(addDialogTitle) },
            text = {
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newValue.isNotBlank()) onAddCustom(newValue)
                    showAddDialog = false
                }) { Text(L10n.str(R.string.add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(L10n.str(R.string.cancel)) }
            }
        )
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** Editor section styled like the Settings groups: rounded container and a title with an icon. */
@Composable
private fun EditorCard(title: String?, icon: ImageVector?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
