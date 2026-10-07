package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * Accent colour of the app, independent from light/dark mode ([ThemeMode]). The actual colours live
 * in ui/theme/Theme.kt so this layer has no Compose dependency; only the accent changes between
 * themes, backgrounds and surfaces stay neutral.
 */
enum class ColorTheme(val label: String) {
    GARDEN(L10n.str(R.string.garden_default)),
    TERRACOTTA(L10n.str(R.string.terracotta)),
    BLUE(L10n.str(R.string.blue)),
    GREEN(L10n.str(R.string.green)),
    PURPLE(L10n.str(R.string.purple)),
    PINK(L10n.str(R.string.pink)),
    ORANGE(L10n.str(R.string.orange)),
    TEAL(L10n.str(R.string.turquoise));

    companion object {
        /** Used when the user has never chosen a colour. */
        val DEFAULT = GARDEN
    }
}
