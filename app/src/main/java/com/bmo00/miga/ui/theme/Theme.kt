package com.bmo00.miga.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Terracotta,
    onPrimary = CreamElevated,
    primaryContainer = TerracottaSoft,
    onPrimaryContainer = Charcoal,
    secondary = Sage,
    onSecondary = CreamElevated,
    background = Cream,
    onBackground = Charcoal,
    surface = CreamElevated,
    onSurface = Charcoal,
    surfaceVariant = TerracottaSoft,
    onSurfaceVariant = CharcoalSoft,
    outline = Divider,
    error = Error
)

private val DarkColors = darkColorScheme(
    primary = Terracotta,
    onPrimary = NightBackground,
    primaryContainer = Terracotta,
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

@Composable
fun RecetarioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RecetarioTypography,
        content = content
    )
}
