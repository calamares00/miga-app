package org.calamares.miga.ui.books

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.ui.components.PhotoEditorOverlay
import org.calamares.miga.ui.components.PhotoSourceSheet

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
    var showDiscardDialog by remember { mutableStateOf(false) }
    val attemptExit = { if (viewModel.hasUnsavedChanges()) showDiscardDialog = true else onCancel() }
    val photoSheetState = rememberModalBottomSheetState()

    BackHandler(onBack = attemptExit)
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingEditUri = uri
        }
    }
    val cameraCaptureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraPath?.let { pendingEditUri = Uri.parse(it) }
        pendingCameraPath = null
    }

    var showLinkDialog by remember { mutableStateOf(false) }
    val availableSyncConnections by viewModel.availableSyncConnections.collectAsState()

    LaunchedEffect(viewModel.deleteError) {
        viewModel.deleteError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.deleteError = null
        }
    }

    LaunchedEffect(viewModel.syncNowError) {
        viewModel.syncNowError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.syncNowError = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (viewModel.isPack) L10n.str(R.string.pack_instalado) else if (viewModel.isEditing) L10n.str(R.string.editar_libro) else L10n.str(R.string.nuevo_libro)) },
                navigationIcon = {
                    IconButton(onClick = attemptExit) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancelar)) }
                },
                actions = {
                    if (viewModel.isSaving || viewModel.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                    } else if (viewModel.isPack) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.desinstalar_pack), tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        if (viewModel.isEditing) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.borrar_libro), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { viewModel.save(onSaved) }) { Text(L10n.str(R.string.guardar)) }
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
                    .let { base ->
                        if (viewModel.isPack) base else base.clickable {
                            if (viewModel.coverPhotoUri != null) {
                                editingExistingCover = true
                            } else {
                                showPhotoSourceSheet = true
                            }
                        }
                    }
            ) {
                val cover = viewModel.coverPhotoUri
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = L10n.str(R.string.portada),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!viewModel.isPack) {
                        IconButton(
                            onClick = { showPhotoSourceSheet = true },
                            modifier = Modifier.size(28.dp).align(Alignment.BottomEnd).padding(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.AddAPhoto,
                                contentDescription = L10n.str(R.string.cambiar_portada),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                    .padding(4.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.AddAPhoto,
                        contentDescription = L10n.str(R.string.anadir_portada),
                        modifier = Modifier.size(40.dp).align(Alignment.Center)
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it; viewModel.nameError = false },
                label = { Text(L10n.str(R.string.nombre_libro)) },
                placeholder = { Text(L10n.str(R.string.ej_josi_helen)) },
                isError = viewModel.nameError,
                supportingText = { if (viewModel.nameError) Text(L10n.str(R.string.nombre_es_obligatorio)) },
                readOnly = viewModel.isPack,
                modifier = Modifier.fillMaxWidth()
            )

            if (viewModel.isPack) {
                Text(
                    L10n.str(R.string.este_libro_es_pack_instalado),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (viewModel.isEditing) {
                val connectionId = viewModel.syncConnectionId
                val connectionLabel = availableSyncConnections.firstOrNull { it.id == connectionId }?.label
                if (connectionId == null) {
                    if (availableSyncConnections.isNotEmpty()) {
                        OutlinedButton(onClick = { showLinkDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.str(R.string.sincronizar))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            L10n.str(R.string.sincronizado_x, connectionLabel ?: L10n.str(R.string.conexion_eliminada)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { viewModel.syncNow(context) },
                            enabled = !viewModel.isSyncingNow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (viewModel.isSyncingNow) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Text(L10n.str(R.string.sincronizar_ahora))
                            }
                        }
                        TextButton(onClick = { viewModel.unlinkFromSyncConnection() }, modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.str(R.string.dejar_sincronizar))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (viewModel.isPack) L10n.str(R.string.desinstalar_pack) else L10n.str(R.string.eliminar_libro)) },
            text = {
                Text(
                    if (viewModel.isPack) {
                        L10n.str(R.string.seguro_quieres_desinstalar_x_borraran, viewModel.name)
                    } else {
                        L10n.str(R.string.seguro_quieres_eliminar_x_esta, viewModel.name)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onDeleted = onSaved)
                }) { Text(if (viewModel.isPack) L10n.str(R.string.desinstalar) else L10n.str(R.string.eliminar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(L10n.str(R.string.cancelar)) }
            }
        )
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            PhotoSourceSheet(
                title = L10n.str(R.string.anadir_portada),
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

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text(L10n.str(R.string.sincronizar)) },
            text = {
                Column {
                    availableSyncConnections.forEach { connection ->
                        TextButton(
                            onClick = {
                                viewModel.linkToSyncConnection(connection.id)
                                showLinkDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(connection.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text(L10n.str(R.string.cancelar)) } }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(L10n.str(R.string.descartar_cambios)) },
            text = { Text(L10n.str(R.string.perderan_cambios_has_hecho)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onCancel()
                }) { Text(L10n.str(R.string.descartar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(L10n.str(R.string.seguir_editando)) }
            }
        )
    }
}
