package org.calamares.miga.ui.components

import org.calamares.miga.L10n
import org.calamares.miga.R
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
 * Content of the photo source picker (camera or gallery), meant to go inside a `ModalBottomSheet`.
 * Used both to create a recipe from a photo and to add any other photo (to a recipe or as a book
 * cover).
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
            Text(L10n.str(R.string.take_photo), modifier = Modifier.padding(start = 16.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onGalleryClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(L10n.str(R.string.choose_gallery), modifier = Modifier.padding(start = 16.dp))
        }
    }
}
