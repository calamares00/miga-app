package org.calamares.miga.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Clean, light neutrals with a hint of green, shared by every colour theme. Terracotta (the logo
 * colour) stays in the palette as the secondary accent.
 */
val Paper = Color(0xFFF7F8F4)
val PaperElevated = Color(0xFFFFFFFF)
val PaperContainerLow = Color(0xFFF2F4EF)
val PaperContainer = Color(0xFFEDF0EA)
val PaperContainerHigh = Color(0xFFE7EAE4)
val PaperContainerHighest = Color(0xFFE1E5DE)
val Ink = Color(0xFF1D2520)
val InkSoft = Color(0xFF5A645E)
val Outline = Color(0xFFC6CCC4)
val Divider = Color(0xFFE1E5DE)
val Terracotta = Color(0xFFC1633D)
val TerracottaSoft = Color(0xFFF5DFD4)
val TerracottaNight = Color(0xFFE08A66)
val Sage = Color(0xFF6E7F68)
val Error = Color(0xFFB3261E)

val NightBackground = Color(0xFF121513)
val NightSurface = Color(0xFF1A1E1B)
val NightContainerLowest = Color(0xFF0D100E)
val NightContainerLow = Color(0xFF171B18)
val NightContainer = Color(0xFF1C211E)
val NightContainerHigh = Color(0xFF252B27)
val NightContainerHighest = Color(0xFF2F3531)
val NightOnSurface = Color(0xFFE5EAE6)
val NightOnSurfaceSoft = Color(0xFFA7B0AA)
val NightOutline = Color(0xFF434B46)
val NightDivider = Color(0xFF2D332F)

/** Default accent: a deep leaf green, and a lighter one that reads well on the dark theme. */
val Garden = Color(0xFF2F7A57)
val GardenSoft = Color(0xFFD3EBDD)
val GardenNight = Color(0xFF6BBF93)

/**
 * Accents of the colour themes available in Settings (see ColorTheme and accentPaletteFor in
 * Theme.kt). Each (accent, soft accent) pair keeps a similar lightness and chroma so they all sit
 * equally well on the shared neutrals; only the accent changes, never the background palette.
 */
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

/**
 * Fixed colours (independent of light/dark theme) for a recipe's health rating. They must read as a
 * recognisable green-to-red scale at a glance, which colorScheme roles do not guarantee (tertiary
 * and secondaryContainer are generated from another seed).
 */
val HealthGreenContainer = Color(0xFFD7ECC8)
val HealthGreenOn = Color(0xFF2E4A20)
val HealthAmberContainer = Color(0xFFF6E2B0)
val HealthAmberOn = Color(0xFF6B4A12)
val HealthRedContainer = Color(0xFFF5D2CC)
val HealthRedOn = Color(0xFF7A241C)
