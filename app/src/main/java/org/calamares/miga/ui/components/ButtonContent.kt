package org.calamares.miga.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

/**
 * The content of every button with an icon: Material's icon size and gap and the text on one
 * line, so all buttons look alike instead of each screen choosing its own spacing.
 */
@Composable
fun ButtonContent(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
