package com.bmo00.miga.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta minimalista: neutros cálidos + un único acento terracota.
val Cream = Color(0xFFF6F1EB)
val CreamElevated = Color(0xFFFFFFFF)
val Charcoal = Color(0xFF2B2118)
val CharcoalSoft = Color(0xFF6B6055)
val Terracotta = Color(0xFFC1633D)
val TerracottaSoft = Color(0xFFE9D3C7)
val Sage = Color(0xFF7C8A6E)
val Divider = Color(0xFFE4DCD1)
val Error = Color(0xFFB3261E)

val NightBackground = Color(0xFF1C1712)
val NightSurface = Color(0xFF262019)
val NightOnSurface = Color(0xFFEFE6DB)
val NightOnSurfaceSoft = Color(0xFFB8AC9D)
val NightDivider = Color(0xFF3A3226)

// Acentos de los temas de color seleccionables en Ajustes (ver ColorTheme/accentPaletteFor en
// Theme.kt): cada par (acento, acento suave) sigue la misma relación de luminosidad/croma que
// Terracotta/TerracottaSoft, para que todos encajen igual de bien sobre los neutros cálidos
// (Cream/Charcoal) del resto de la app - solo cambia el acento, no la paleta neutra de fondo.
val Blue = Color(0xFF3B6EA5)
val BlueSoft = Color(0xFFCFE0EF)
val Green = Color(0xFF4C8C5B)
val GreenSoft = Color(0xFFD6EAD9)
val Purple = Color(0xFF7C5AA0)
val PurpleSoft = Color(0xFFE3D7ED)
val Pink = Color(0xFFC25B82)
val PinkSoft = Color(0xFFF1D7E1)
val Orange = Color(0xFFD08A3E)
val OrangeSoft = Color(0xFFF2E0C7)
val Teal = Color(0xFF3E8F94)
val TealSoft = Color(0xFFCFE7E8)

// Colores fijos (no ligados al tema claro/oscuro) para la valoración de salud de una receta:
// deben leerse como un espectro verde -> rojo reconocible de un vistazo, cosa que los roles del
// colorScheme no garantizan (tertiary/secondaryContainer se autogeneran de otra semilla y no
// tienen por qué salir verdes o amarillos).
val HealthGreenContainer = Color(0xFFD7ECC8)
val HealthGreenOn = Color(0xFF2E4A20)
val HealthAmberContainer = Color(0xFFF6E2B0)
val HealthAmberOn = Color(0xFF6B4A12)
val HealthRedContainer = Color(0xFFF5D2CC)
val HealthRedOn = Color(0xFF7A241C)
