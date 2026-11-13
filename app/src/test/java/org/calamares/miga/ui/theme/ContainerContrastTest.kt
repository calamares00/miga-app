package org.calamares.miga.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.calamares.miga.data.model.ColorTheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text on the container colours must stay readable in every colour theme, light and dark: a
 * selected pack card is primaryContainer with onPrimaryContainer text (its secondary lines at 78 %),
 * where the grey onSurfaceVariant used before measured about 1:1 in the dark themes.
 */
class ContainerContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05
        val l2 = b.luminance() + 0.05
        return (maxOf(l1, l2) / minOf(l1, l2)).toDouble()
    }

    @Test
    fun `text on primaryContainer is readable in every theme`() {
        ColorTheme.entries.forEach { theme ->
            listOf("light" to lightColorSchemeFor(theme), "dark" to darkColorSchemeFor(theme)).forEach { (mode, scheme) ->
                val background = scheme.primaryContainer
                val main = contrast(scheme.onPrimaryContainer, background)
                val secondary = contrast(scheme.onPrimaryContainer.copy(alpha = 0.78f).compositeOver(background), background)
                assertTrue("$theme $mode: main text $main", main >= 4.5)
                assertTrue("$theme $mode: secondary text $secondary", secondary >= 3.0)
                // Buttons (primary): 3:1 at least, the same as Material's own palettes for labels.
                assertTrue("$theme $mode: button text", contrast(scheme.onPrimary, scheme.primary) >= 3.0)
            }
        }
    }
}
