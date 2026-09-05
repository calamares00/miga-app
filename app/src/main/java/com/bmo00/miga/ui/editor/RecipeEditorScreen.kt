package com.bmo00.miga.ui.editor

import android.net.Uri
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
import com.bmo00.miga.data.local.PhotoStorage
import com.bmo00.miga.data.model.Difficulty
import com.bmo00.miga.ui.components.PhotoEditorOverlay
import com.bmo00.miga.ui.components.PhotoSourceSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeEditorScreen(
    viewModel: RecipeEditorViewModel,
    sourcePhotoUri: String? = null,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val availableCategories by viewModel.availableCategories.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val availableUtensils by viewModel.availableUtensils.collectAsState()
    val visionState by viewModel.visionState.collectAsState()
    var visionErrorDismissed by remember { mutableStateOf(false) }
    var showVisionErrorDialog by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var pendingEditUri by remember { mutableStateOf<Uri?>(null) }
    val photoSheetState = rememberModalBottomSheetState()
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(sourcePhotoUri) {
        sourcePhotoUri?.let { viewModel.startVisionExtraction(context, Uri.parse(it)) }
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
                title = { Text(if (viewModel.isEditing) "Editar receta" else "Nueva receta") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancelar") }
                },
                actions = {
                    if (viewModel.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                    } else {
                        TextButton(onClick = { viewModel.save(onSaved) }) { Text("Guardar") }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            if (visionState is VisionState.Error && !visionErrorDismissed) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "No se pudo leer la foto con IA: ${(visionState as VisionState.Error).reason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showVisionErrorDialog = true }
                    )
                    IconButton(onClick = { visionErrorDismissed = true }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar aviso")
                    }
                }
            }

            PhotosRow(viewModel = viewModel, onAddPhoto = { showPhotoSourceSheet = true })

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it; viewModel.nameError = false },
                label = { Text("Nombre de la receta") },
                isError = viewModel.nameError,
                supportingText = { if (viewModel.nameError) Text("El nombre es obligatorio") },
                modifier = Modifier.fillMaxWidth()
            )

            CategoryField(
                value = viewModel.categoryName.orEmpty(),
                suggestions = availableCategories,
                onValueChange = { text -> viewModel.categoryName = text.takeIf { it.isNotBlank() } }
            )

            Section(title = "Dificultad") {
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
                    label = { Text("Prep. (min)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.cookTimeMinutesText,
                    onValueChange = { if (it.all(Char::isDigit)) viewModel.cookTimeMinutesText = it },
                    label = { Text("Cocción (min)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = viewModel.servings.toString(),
                    onValueChange = { text -> text.toIntOrNull()?.let { viewModel.servings = it.coerceIn(1, 99) } },
                    label = { Text("Raciones") },
                    modifier = Modifier.weight(1f)
                )
            }

            Section(title = "Utensilios necesarios") {
                ChipMultiSelect(
                    selected = viewModel.selectedUtensils,
                    available = availableUtensils,
                    onToggle = viewModel::toggleUtensil,
                    onAddCustom = viewModel::addCustomUtensil,
                    addDialogTitle = "Añadir utensilio"
                )
            }

            Section(title = "Etiquetas") {
                ChipMultiSelect(
                    selected = viewModel.selectedTags,
                    available = availableTags,
                    onToggle = viewModel::toggleTag,
                    onAddCustom = viewModel::addCustomTag,
                    addDialogTitle = "Añadir etiqueta"
                )
            }

            HorizontalDivider()
            IngredientsEditor(viewModel)

            HorizontalDivider()
            StepsEditor(viewModel)

            HorizontalDivider()

            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                label = { Text("Notas / variantes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            OutlinedTextField(
                value = viewModel.source,
                onValueChange = { viewModel.source = it },
                label = { Text("Origen (libro, web, etc.)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = viewModel.isFavorite, onCheckedChange = { viewModel.isFavorite = it })
                Text("Marcar como favorita")
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            strokeWidth = 4.dp
                        )
                        Text(
                            "Leyendo la foto con IA...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
        }
    }

    if (showVisionErrorDialog && visionState is VisionState.Error) {
        val reason = (visionState as VisionState.Error).reason
        AlertDialog(
            onDismissRequest = { showVisionErrorDialog = false },
            title = { Text("Detalle del error") },
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
                TextButton(onClick = { clipboardManager.setText(AnnotatedString(reason)) }) { Text("Copiar") }
            },
            dismissButton = {
                TextButton(onClick = { showVisionErrorDialog = false }) { Text("Cerrar") }
            }
        )
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            PhotoSourceSheet(
                title = "Añadir foto",
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

    pendingEditUri?.let { uri ->
        PhotoEditorOverlay(
            sourceUri = uri,
            aspectRatio = 1f,
            onSave = { path ->
                viewModel.addPhoto(path)
                pendingEditUri = null
            },
            onCancel = { pendingEditUri = null }
        )
    }
}

@Composable
private fun PhotosRow(viewModel: RecipeEditorViewModel, onAddPhoto: () -> Unit) {
    Section(title = "Fotos") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.photos, key = { it.uri }) { photo ->
                Box(modifier = Modifier.size(88.dp)) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.setCoverPhoto(photo) }
                    )
                    IconButton(
                        onClick = { viewModel.removePhoto(photo) },
                        modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Quitar foto",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                        )
                    }
                    if (photo.isCover) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Foto de portada",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.align(Alignment.BottomStart).size(18.dp)
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
                    Icon(Icons.Filled.AddAPhoto, contentDescription = "Añadir foto")
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
            label = { Text("Categoría") },
            placeholder = { Text("Ej. Postres, Cremas, Pastas...") },
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
                }) { Text("Añadir") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") }
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
