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
 * Accent and soft accent of a [ColorTheme], the only thing that changes between colour themes; the
 * rest of the palette (warm neutrals, Sage as secondary) stays fixed.
 */
private data class AccentPalette(val accent: Color, val accentSoft: Color)

private fun accentPaletteFor(colorTheme: ColorTheme): AccentPalette = when (colorTheme) {
    ColorTheme.TERRACOTTA -> AccentPalette(Terracotta, TerracottaSoft)
    ColorTheme.BLUE -> AccentPalette(Blue, BlueSoft)
    ColorTheme.GREEN -> AccentPalette(Green, GreenSoft)
    ColorTheme.PURPLE -> AccentPalette(Purple, PurpleSoft)
    ColorTheme.PINK -> AccentPalette(Pink, PinkSoft)
    ColorTheme.ORANGE -> AccentPalette(Orange, OrangeSoft)
    ColorTheme.TEAL -> AccentPalette(Teal, TealSoft)
}

private fun lightColorSchemeFor(colorTheme: ColorTheme): ColorScheme {
    val (accent, accentSoft) = accentPaletteFor(colorTheme)
    return lightColorScheme(
        primary = accent,
        onPrimary = CreamElevated,
        primaryContainer = accentSoft,
        onPrimaryContainer = Charcoal,
        secondary = Sage,
        onSecondary = CreamElevated,
        background = Cream,
        onBackground = Charcoal,
        surface = CreamElevated,
        onSurface = Charcoal,
        surfaceVariant = accentSoft,
        onSurfaceVariant = CharcoalSoft,
        outline = Divider,
        error = Error
    )
}

private fun darkColorSchemeFor(colorTheme: ColorTheme): ColorScheme {
    val accent = accentPaletteFor(colorTheme).accent
    return darkColorScheme(
        primary = accent,
        onPrimary = NightBackground,
        primaryContainer = accent,
        onPrimaryContainer = NightBackground,
        secondary = Sage,
        onSecondary = NightBackground,
        background = NightBackground,
        onBackground = NightOnSurface,
        surface = NightSurface,
        onSurface = NightOnSurface,
        surfaceVariant = NightSurface,
        onSurfaceVariant = NightOnSurfaceSoft,
        outline = NightDivider,
        error = Error
    )
}

@Composable
fun MigaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.TERRACOTTA,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorSchemeFor(colorTheme) else lightColorSchemeFor(colorTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MigaTypography,
        content = content
    )
}
