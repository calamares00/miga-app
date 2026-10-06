package org.calamares.miga.data.model

import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * Tema de color (acento) de la app, independiente del modo claro/oscuro ([ThemeMode]). Los colores
 * concretos de cada uno viven en `ui/theme/Theme.kt` (esta capa se mantiene sin dependencias de
 * Compose, igual que [ThemeMode]); solo el neutro de fondo/superficie se mantiene fijo entre temas,
 * lo que cambia es el acento (botones, chips seleccionados, superficie resaltada...).
 */
enum class ColorTheme(val label: String) {
    TERRACOTTA(L10n.str(R.string.terracotta_default)),
    BLUE(L10n.str(R.string.blue)),
    GREEN(L10n.str(R.string.green)),
    PURPLE(L10n.str(R.string.purple)),
    PINK(L10n.str(R.string.pink)),
    ORANGE(L10n.str(R.string.orange)),
    TEAL(L10n.str(R.string.turquoise))
}
