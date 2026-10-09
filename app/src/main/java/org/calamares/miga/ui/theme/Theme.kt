package org.calamares.miga.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.calamares.miga.data.model.ColorTheme

/**
 * Accent of a [ColorTheme] and its soft variant, the only thing that changes between colour
 * themes. [night] is the accent used in dark mode, lighter when the base one would be too dark.
 */
private data class AccentPalette(val accent: Color, val accentSoft: Color, val night: Color = accent)

private fun accentPaletteFor(colorTheme: ColorTheme): AccentPalette = when (colorTheme) {
    ColorTheme.GARDEN -> AccentPalette(Garden, GardenSoft, GardenNight)
    ColorTheme.TERRACOTTA -> AccentPalette(Terracotta, TerracottaSoft, TerracottaNight)
    ColorTheme.BLUE -> AccentPalette(Blue, BlueSoft)
    ColorTheme.GREEN -> AccentPalette(Green, GreenSoft)
    ColorTheme.PURPLE -> AccentPalette(Purple, PurpleSoft)
    ColorTheme.PINK -> AccentPalette(Pink, PinkSoft)
    ColorTheme.ORANGE -> AccentPalette(Orange, OrangeSoft)
    ColorTheme.TEAL -> AccentPalette(Teal, TealSoft)
}

/** Accent shown on the colour swatches in Settings. */
fun accentColorFor(colorTheme: ColorTheme): Color = accentPaletteFor(colorTheme).accent

/** Soft variant of the accent, shown with it on the colour swatches in Settings. */
fun accentSoftColorFor(colorTheme: ColorTheme): Color = accentPaletteFor(colorTheme).accentSoft

// Every role is set explicitly: the Material defaults for the ones left out (secondaryContainer,
// surface containers...) are lilac-tinted and clash with the palette.
private fun lightColorSchemeFor(colorTheme: ColorTheme): ColorScheme {
    val (accent, accentSoft) = accentPaletteFor(colorTheme)
    // With the terracotta theme the secondary accent would repeat the primary one.
    val second = if (colorTheme == ColorTheme.TERRACOTTA) Garden else Terracotta
    val secondSoft = if (colorTheme == ColorTheme.TERRACOTTA) GardenSoft else TerracottaSoft
    return lightColorScheme(
        primary = accent,
        onPrimary = PaperElevated,
        primaryContainer = accentSoft,
        onPrimaryContainer = Ink,
        inversePrimary = accentSoft,
        secondary = Sage,
        onSecondary = PaperElevated,
        secondaryContainer = accentSoft,
        onSecondaryContainer = Ink,
        tertiary = second,
        onTertiary = PaperElevated,
        tertiaryContainer = secondSoft,
        onTertiaryContainer = Ink,
        background = Paper,
        onBackground = Ink,
        surface = PaperElevated,
        onSurface = Ink,
        surfaceVariant = PaperContainerHigh,
        onSurfaceVariant = InkSoft,
        surfaceTint = accent,
        surfaceBright = PaperElevated,
        surfaceDim = PaperContainerHighest,
        surfaceContainerLowest = PaperElevated,
        surfaceContainerLow = PaperContainerLow,
        surfaceContainer = PaperContainer,
        surfaceContainerHigh = PaperContainerHigh,
        surfaceContainerHighest = PaperContainerHighest,
        outline = Outline,
        outlineVariant = Divider,
        error = Error
    )
}

private fun darkColorSchemeFor(colorTheme: ColorTheme): ColorScheme {
    val accent = accentPaletteFor(colorTheme).night
    val second = if (colorTheme == ColorTheme.TERRACOTTA) GardenNight else TerracottaNight
    return darkColorScheme(
        primary = accent,
        onPrimary = NightBackground,
        primaryContainer = accent,
        onPrimaryContainer = NightBackground,
        inversePrimary = accent,
        secondary = Sage,
        onSecondary = NightBackground,
        secondaryContainer = NightContainerHighest,
        onSecondaryContainer = NightOnSurface,
        tertiary = second,
        onTertiary = NightBackground,
        tertiaryContainer = NightContainerHigh,
        onTertiaryContainer = NightOnSurface,
        background = NightBackground,
        onBackground = NightOnSurface,
        surface = NightSurface,
        onSurface = NightOnSurface,
        surfaceVariant = NightContainerHigh,
        onSurfaceVariant = NightOnSurfaceSoft,
        surfaceTint = accent,
        surfaceBright = NightContainerHighest,
        surfaceDim = NightBackground,
        surfaceContainerLowest = NightContainerLowest,
        surfaceContainerLow = NightContainerLow,
        surfaceContainer = NightContainer,
        surfaceContainerHigh = NightContainerHigh,
        surfaceContainerHighest = NightContainerHighest,
        outline = NightOutline,
        outlineVariant = NightDivider,
        error = Error
    )
}

@Composable
fun MigaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorSchemeFor(colorTheme) else lightColorSchemeFor(colorTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MigaTypography,
        shapes = MigaShapes,
        content = content
    )
}
