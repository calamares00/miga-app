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
    TERRACOTTA(L10n.str(R.string.terracota_defecto)),
    BLUE(L10n.str(R.string.azul)),
    GREEN(L10n.str(R.string.verde)),
    PURPLE(L10n.str(R.string.morado)),
    PINK(L10n.str(R.string.rosa)),
    ORANGE(L10n.str(R.string.naranja)),
    TEAL(L10n.str(R.string.turquesa))
}
