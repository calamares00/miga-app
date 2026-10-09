package org.calamares.miga.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import org.calamares.miga.data.model.PhotoFrame

/** The frame chosen in Settings > Appearance, provided at the root of the app. */
val LocalPhotoFrame = staticCompositionLocalOf { PhotoFrame.DEFAULT }

/** Off-white of the photo mats, the same in light and dark mode, like real photo paper. */
private val MatColor = Color(0xFFFAF8F3)

/**
 * Draws the [frame] (by default the user's, see [LocalPhotoFrame]) over a recipe photo. Goes on
 * the image itself, after its size: frames with a mat shrink the photo into a window of the mat
 * (zoomed to fill it, never distorted) and the others draw on top of it. Sizes are proportional,
 * so the same frame looks alike on a thumbnail and on the photo of a recipe.
 */
@Composable
fun Modifier.recipePhotoFrame(frame: PhotoFrame = LocalPhotoFrame.current): Modifier {
    if (frame == PhotoFrame.NONE) return this
    val accent = MaterialTheme.colorScheme.primary
    return drawWithContent { drawFrame(frame, accent) }
}

private fun ContentDrawScope.drawFrame(frame: PhotoFrame, accent: Color) {
    val unit = size.minDimension
    when (frame) {
        PhotoFrame.NONE -> drawContent()
        PhotoFrame.POLAROID -> {
            val side = unit * 0.06f
            drawMatted(Rect(side, side, size.width - side, size.height - unit * 0.2f))
        }
        PhotoFrame.GALLERY -> {
            val mat = unit * 0.08f
            val window = Rect(mat, mat, size.width - mat, size.height - mat)
            drawMatted(window)
            // The thin bevel of a passe-partout around the window.
            val line = maxOf(1.dp.toPx(), unit * 0.006f)
            drawRect(
                color = Color.Black.copy(alpha = 0.18f),
                topLeft = window.topLeft,
                size = window.size,
                style = Stroke(width = line)
            )
        }
        PhotoFrame.FINE_LINE -> {
            drawContent()
            val inset = unit * 0.05f
            val line = maxOf(1.dp.toPx(), unit * 0.008f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - 2 * inset, size.height - 2 * inset),
                cornerRadius = CornerRadius(unit * 0.04f),
                style = Stroke(width = line)
            )
        }
        PhotoFrame.VIGNETTE -> {
            drawContent()
            drawRect(
                Brush.radialGradient(
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.5f),
                    center = center,
                    radius = size.maxDimension * 0.75f
                )
            )
        }
        PhotoFrame.ACCENT -> {
            drawContent()
            val line = maxOf(3.dp.toPx(), unit * 0.035f)
            drawRect(
                color = accent,
                topLeft = Offset(line / 2, line / 2),
                size = Size(size.width - line, size.height - line),
                style = Stroke(width = line)
            )
        }
    }
}

/** Fills the area with the mat and draws the photo zoomed to cover [window], keeping its proportions. */
private fun ContentDrawScope.drawMatted(window: Rect) {
    drawRect(MatColor)
    val zoom = maxOf(window.width / size.width, window.height / size.height)
    clipRect(window.left, window.top, window.right, window.bottom) {
        withTransform({
            translate(window.center.x - center.x, window.center.y - center.y)
            scale(zoom, zoom, pivot = center)
        }) {
            this@drawMatted.drawContent()
        }
    }
}
