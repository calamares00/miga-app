package org.calamares.miga.ui.settings

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
    APPEARANCE("appearance", "Apariencia", Icons.Filled.Palette),
    SECURITY("security", "Seguridad", Icons.Filled.Fingerprint),
    CONTENT("content", "Gestionar contenido", Icons.Filled.Tune),
    BACKUP("backup", "Copia de seguridad", Icons.Filled.Backup),
    AI("ai", "Importar con IA", Icons.Filled.AutoAwesome),
    VOICE("voice", "Voz y dictado", Icons.Filled.Mic),
    PACKS("packs", "Packs de recetas", Icons.Filled.Storefront),
    SYNC("sync", "Sincronización", Icons.Filled.Sync);

    companion object {
        /** La sección con ese [id]; si no existe (ruta corrupta), la primera. */
        fun fromId(id: String?): SettingsSection = entries.firstOrNull { it.id == id } ?: APPEARANCE
    }
}
