package com.bmo00.miga.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Contenido del selector de cómo dar de alta una receta nueva, pensado para ir dentro de un
 * `ModalBottomSheet`: a mano, desde un archivo ya exportado (JSON/ZIP, sustituye al antiguo
 * "Importar receta" del menú del libro) o a partir de una foto (sustituye a "Añadir con foto";
 * a su vez abre PhotoSourceSheet para elegir cámara o galería).
 */
@Composable
fun NewRecipeSourceSheet(
    onManualClick: () -> Unit,
    onFileClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onBulkPhotoClick: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Nueva receta", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        NewRecipeSourceRow(icon = Icons.Filled.Edit, label = "Manual", onClick = onManualClick)
        NewRecipeSourceRow(icon = Icons.Filled.UploadFile, label = "Desde archivo (JSON/ZIP)", onClick = onFileClick)
        NewRecipeSourceRow(icon = Icons.Filled.AddAPhoto, label = "Desde imagen", onClick = onPhotoClick)
        NewRecipeSourceRow(icon = Icons.Filled.Collections, label = "Varias recetas desde imágenes", onClick = onBulkPhotoClick)
    }
}

@Composable
private fun NewRecipeSourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}
