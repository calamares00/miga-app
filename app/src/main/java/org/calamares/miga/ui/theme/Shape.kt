package org.calamares.miga.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * App shapes. extraSmall is the shape of every text field (and of menus and snackbars): rounded
 * corners, the same for search boxes, forms and message boxes, instead of Material's default
 * almost square 4 dp. No screen sets its own text field shape; they all take it from here.
 */
val MigaShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp)
)
