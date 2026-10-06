package org.calamares.miga.ui.settings

import org.calamares.miga.L10n
import org.calamares.miga.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Categorías de Ajustes que abren su propia pantalla (la pantalla principal de Ajustes solo lista
 * estas categorías, al estilo de los ajustes de las apps de Google). [id] es el argumento de la ruta.
 */
enum class SettingsSection(val id: String, val title: String, val icon: ImageVector) {
    APPEARANCE("appearance", L10n.str(R.string.appearance), Icons.Filled.Palette),
    SECURITY("security", L10n.str(R.string.security), Icons.Filled.Fingerprint),
    CONTENT("content", L10n.str(R.string.manage_content), Icons.Filled.Tune),
    BACKUP("backup", L10n.str(R.string.backup), Icons.Filled.Backup),
    AI("ai", L10n.str(R.string.artificial_intelligence), Icons.Filled.AutoAwesome),
    VOICE("voice", L10n.str(R.string.voice_dictation_2), Icons.Filled.Mic),
    PACKS("packs", L10n.str(R.string.recipe_packs), Icons.Filled.Storefront),
    SYNC("sync", L10n.str(R.string.sync), Icons.Filled.Sync);

    companion object {
        /** La sección con ese [id]; si no existe (ruta corrupta), la primera. */
        fun fromId(id: String?): SettingsSection = entries.firstOrNull { it.id == id } ?: APPEARANCE
    }
}
