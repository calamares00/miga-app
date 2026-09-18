package com.bmo00.miga.data.model

/**
 * Tema de color (acento) de la app, independiente del modo claro/oscuro ([ThemeMode]). Los colores
 * concretos de cada uno viven en `ui/theme/Theme.kt` (esta capa se mantiene sin dependencias de
 * Compose, igual que [ThemeMode]); solo el neutro de fondo/superficie se mantiene fijo entre temas,
 * lo que cambia es el acento (botones, chips seleccionados, superficie resaltada...).
 */
enum class ColorTheme(val label: String) {
    TERRACOTTA("Terracota (por defecto)"),
    BLUE("Azul"),
    GREEN("Verde"),
    PURPLE("Morado"),
    PINK("Rosa"),
    ORANGE("Naranja"),
    TEAL("Turquesa")
}
