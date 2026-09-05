package com.bmo00.miga.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Contenido del selector de origen de una foto (cámara o galería), pensado para ir dentro de un
 * `ModalBottomSheet`. Se usa tanto para añadir una receta a partir de una foto como para añadir
 * cualquier otra foto (de una receta o de la portada de un libro).
 */
@Composable
fun PhotoSourceSheet(title: String, onCameraClick: () -> Unit, onGalleryClick: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCameraClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Hacer foto", modifier = Modifier.padding(start = 16.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGalleryClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Elegir de galería", modifier = Modifier.padding(start = 16.dp))
        }
    }
}
