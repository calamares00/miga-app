package org.calamares.miga.ui.books

import org.calamares.miga.ui.components.keepScrollInSheet
import org.calamares.miga.L10n
import org.calamares.miga.R
import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import org.calamares.miga.data.model.BookDetails
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.calamares.miga.ui.components.rememberCameraCapture
import org.calamares.miga.ui.components.AdaptiveScaffold
import org.calamares.miga.ui.components.PhotoEditorOverlay
import org.calamares.miga.ui.components.PhotoSourceSheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeBookEditorScreen(
    viewModel: RecipeBookEditorViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    /** After deleting the book: back to the books, not to the book's own (now gone) recipe list. */
    onDeleted: () -> Unit = onSaved
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    var pendingEditUri by remember { mutableStateOf<Uri?>(null) }
    var editingExistingCover by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showRecipePhotos by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    // A new book starts with the keyboard on its name.
    LaunchedEffect(viewModel.isLoading) {
        if (!viewModel.isEditing && !viewModel.isLoading) runCatching { nameFocus.requestFocus() }
    }
    val attemptExit = { if (viewModel.hasUnsavedChanges()) showDiscardDialog = true else onCancel() }
    val photoSheetState = rememberModalBottomSheetState()

    BackHandler(onBack = attemptExit)
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingEditUri = uri
        }
    }
    val cameraCapture = rememberCameraCapture { path -> pendingEditUri = Uri.parse(path) }

    var showLinkDialog by remember { mutableStateOf(false) }
    val availableSyncConnections by viewModel.availableSyncConnections.collectAsStateWithLifecycle()

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

    AdaptiveScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text(if (viewModel.isPack) L10n.str(R.string.installed_pack) else if (viewModel.isEditing) L10n.str(R.string.edit_book) else L10n.str(R.string.new_book)) },
                navigationIcon = {
                    IconButton(onClick = attemptExit) { Icon(Icons.Filled.Close, contentDescription = L10n.str(R.string.cancel)) }
                },
                actions = {
                    if (viewModel.isSaving || viewModel.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                    } else if (viewModel.isPack) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.uninstall_pack), tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        if (viewModel.isEditing) {
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(Icons.Filled.Delete, contentDescription = L10n.str(R.string.delete_book), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { viewModel.save(onSaved) }) { Text(L10n.str(R.string.save)) }
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@AdaptiveScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The cover as the library will show it, updated while typing.
            CoverPreview(
                name = viewModel.name,
                author = viewModel.author,
                photoUri = viewModel.coverPhotoUri,
                coverColor = viewModel.coverColor,
                coverEmoji = viewModel.coverEmoji,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = if (viewModel.isPack) null else {
                    { if (viewModel.coverPhotoUri != null) editingExistingCover = true else showPhotoSourceSheet = true }
                }
            )
            if (!viewModel.isPack) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    AssistChip(
                        onClick = { coverPicker.launch("image/*") },
                        label = { Text(L10n.str(R.string.book_cover_gallery)) },
                        leadingIcon = { Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { cameraCapture.launch() },
                        label = { Text(L10n.str(R.string.book_cover_camera)) },
                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)) }
                    )
                    if (viewModel.recipePhotos.isNotEmpty()) {
                        AssistChip(
                            onClick = { showRecipePhotos = true },
                            label = { Text(L10n.str(R.string.book_cover_from_recipes)) },
                            leadingIcon = { Icon(Icons.Filled.Restaurant, null, Modifier.size(18.dp)) }
                        )
                    }
                    if (viewModel.coverPhotoUri != null) {
                        AssistChip(
                            onClick = { viewModel.coverPhotoUri = null },
                            label = { Text(L10n.str(R.string.book_cover_remove_photo)) },
                            leadingIcon = { Icon(Icons.Filled.HideImage, null, Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it; viewModel.nameError = false },
                label = { Text(L10n.str(R.string.book_name)) },
                placeholder = { Text(L10n.str(R.string.e_g_grandma_desserts)) },
                isError = viewModel.nameError,
                supportingText = { if (viewModel.nameError) Text(L10n.str(R.string.name_required)) },
                readOnly = viewModel.isPack,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
            )
            if (!viewModel.isEditing && viewModel.name.isBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    bookNameSuggestions().forEach { suggestion ->
                        SuggestionChip(onClick = { viewModel.name = suggestion; viewModel.nameError = false }, label = { Text(suggestion) })
                    }
                }
            }

            if (!viewModel.isPack) {
                OutlinedTextField(
                    value = viewModel.author,
                    onValueChange = { viewModel.author = it.take(BookDetails.MAX_AUTHOR) },
                    label = { Text(L10n.str(R.string.book_author)) },
                    placeholder = { Text(L10n.str(R.string.book_author_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.description,
                    onValueChange = { viewModel.description = it.take(BookDetails.MAX_DESCRIPTION) },
                    label = { Text(L10n.str(R.string.book_dedication)) },
                    placeholder = { Text(L10n.str(R.string.book_dedication_hint)) },
                    supportingText = { Text(L10n.str(R.string.book_dedication_help)) },
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                if (viewModel.coverPhotoUri == null) {
                    CoverStylePicker(
                        name = viewModel.name,
                        coverColor = viewModel.coverColor,
                        coverEmoji = viewModel.coverEmoji,
                        onColor = { viewModel.coverColor = it },
                        onEmoji = { viewModel.coverEmoji = it }
                    )
                }
            }

            if (viewModel.isPack) {
                Text(
                    L10n.str(R.string.book_installed_pack_read_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (viewModel.isEditing) {
                val connectionId = viewModel.syncConnectionId
                val connectionLabel = availableSyncConnections.firstOrNull { it.id == connectionId }?.label
                if (connectionId == null) {
                    if (availableSyncConnections.isNotEmpty()) {
                        OutlinedButton(onClick = { showLinkDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.str(R.string.sync_2))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            L10n.str(R.string.synced_x, connectionLabel ?: L10n.str(R.string.connection_removed)),
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
                                Text(L10n.str(R.string.sync_now))
                            }
                        }
                        TextButton(onClick = { viewModel.unlinkFromSyncConnection() }, modifier = Modifier.fillMaxWidth()) {
                            Text(L10n.str(R.string.stop_syncing))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (viewModel.isPack) L10n.str(R.string.uninstall_pack) else L10n.str(R.string.delete_book_2)) },
            text = {
                Text(
                    if (viewModel.isPack) {
                        L10n.str(R.string.sure_want_uninstall_x_recipes, viewModel.name)
                    } else {
                        L10n.str(R.string.sure_want_delete_x_cant, viewModel.name)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onDeleted = onDeleted)
                }) { Text(if (viewModel.isPack) L10n.str(R.string.uninstall) else L10n.str(R.string.delete_2), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(L10n.str(R.string.cancel)) }
            }
        )
    }

    if (showRecipePhotos) {
        ModalBottomSheet(onDismissRequest = { showRecipePhotos = false }) {
            RecipePhotoPicker(viewModel.recipePhotos) { uri ->
                showRecipePhotos = false
                pendingEditUri = Uri.parse(uri)
            }
        }
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }, sheetState = photoSheetState) {
            Column(modifier = Modifier.keepScrollInSheet()) {
                PhotoSourceSheet(
                    title = L10n.str(R.string.add_cover),
                    onCameraClick = {
                        showPhotoSourceSheet = false
                        cameraCapture.launch()
                    },
                    onGalleryClick = {
                        showPhotoSourceSheet = false
                        coverPicker.launch("image/*")
                    }
                )
            }
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
            title = { Text(L10n.str(R.string.sync_2)) },
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
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text(L10n.str(R.string.cancel)) } }
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
