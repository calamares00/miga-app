package com.bmo00.miga.ui.books

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bmo00.miga.data.local.PhotoStorage
import com.bmo00.miga.ui.components.PhotoEditorOverlay
import com.bmo00.miga.ui.components.PhotoSourceSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBookEditorScreen(
    viewModel: RecipeBookEditorViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var pendingEditUri by remember { mutableStateOf<Uri?>(null) }
    var editingExistingCover by remember { mutableStateOf(false) }
    val photoSheetState = rememberModalBottomSheetState()
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingEditUri = uri
        }
    }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraPath?.let { pendingEditUri = Uri.parse(it) }
        pendingCameraPath = null
    }

    LaunchedEffect(viewModel.deleteError) {
        viewModel.deleteError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.deleteError = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Editar libro" else "Nuevo libro") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancelar") }
                },
                actions = {
                    if (viewModel.isSaving || viewModel.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                    } else {
                        if (viewModel.isEditing) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Borrar libro", tint = MaterialTheme.colorScheme.error)
                            }
                        }
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

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        if (viewModel.coverPhotoUri != null) {
                            editingExistingCover = true
                        } else {
                            showPhotoSourceSheet = true
                        }
                    }
            ) {
                val cover = viewModel.coverPhotoUri
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = "Portada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { showPhotoSourceSheet = true },
                        modifier = Modifier.size(28.dp).align(Alignment.BottomEnd).padding(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.AddAPhoto,
                            contentDescription = "Cambiar portada",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                .padding(4.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = "Añadir portada",
                        modifier = Modifier.size(40.dp).align(Alignment.Center)
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it; viewModel.nameError = false },
                label = { Text("Nombre del libro") },
                placeholder = { Text("Ej. Josi, Helen...") },
                isError = viewModel.nameError,
                supportingText = { if (viewModel.nameError) Text("El nombre es obligatorio") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar libro") },
            text = { Text("¿Seguro que quieres eliminar \"${viewModel.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onDeleted = onSaved)
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            PhotoSourceSheet(
                title = "Añadir portada",
                onCameraClick = {
                    showPhotoSourceSheet = false
                    val (contentUri, filePath) = PhotoStorage.createCaptureTarget(context)
                    pendingCameraPath = filePath
                    cameraCaptureLauncher.launch(contentUri)
                },
                onGalleryClick = {
                    showPhotoSourceSheet = false
                    coverPicker.launch("image/*")
                }
            )
        }
    }

    val editSourceUri = pendingEditUri
        ?: (if (editingExistingCover) viewModel.coverPhotoUri?.let { Uri.parse(it) } else null)
    if (editSourceUri != null) {
        PhotoEditorOverlay(
            sourceUri = editSourceUri,
            onSave = { path ->
                viewModel.coverPhotoUri = path
                pendingEditUri = null
                editingExistingCover = false
            },
            onCancel = {
                pendingEditUri = null
                editingExistingCover = false
            }
        )
    }
}
