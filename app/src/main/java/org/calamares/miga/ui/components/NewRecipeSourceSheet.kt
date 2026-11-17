package org.calamares.miga.ui.components

import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
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
 * Content of the "new recipe" picker, meant to go inside a `ModalBottomSheet`: write it by hand,
 * read it from photos (which opens PhotoSourceSheet to choose camera or gallery; one or several
 * recipes), from a web page, from a file exported by Miga, or ask the assistant for one.
 */
@Composable
fun NewRecipeSourceSheet(
    onManualClick: () -> Unit,
    onFileClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onSearchDishClick: () -> Unit,
    onUrlClick: () -> Unit,
    aiEnabled: Boolean = true
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(L10n.str(R.string.new_recipe), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        NewRecipeSourceRow(Icons.Filled.Edit, L10n.str(R.string.new_recipe_by_hand), L10n.str(R.string.new_recipe_by_hand_desc), onManualClick)
        // Photos and suggestions need the assistant, so they are hidden when it is off. A web page
        // does not when it carries a schema.org recipe, as most recipe sites do.
        if (aiEnabled) {
            NewRecipeSourceRow(Icons.Filled.AddAPhoto, L10n.str(R.string.new_recipe_from_photos), L10n.str(R.string.new_recipe_from_photos_desc), onPhotoClick)
        }
        NewRecipeSourceRow(Icons.Filled.Link, L10n.str(R.string.new_recipe_from_web), L10n.str(R.string.new_recipe_from_web_desc), onUrlClick)
        NewRecipeSourceRow(Icons.Filled.UploadFile, L10n.str(R.string.new_recipe_from_file), L10n.str(R.string.new_recipe_from_file_desc), onFileClick)
        if (aiEnabled) {
            NewRecipeSourceRow(Icons.Filled.AutoAwesome, L10n.str(R.string.find_recipe_ai), L10n.str(R.string.new_recipe_suggest_desc), onSearchDishClick)
        }
    }
}

@Composable
private fun NewRecipeSourceRow(icon: ImageVector, label: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
