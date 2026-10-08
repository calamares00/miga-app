package org.calamares.miga.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.formatIngredientText
import java.io.File
import kotlin.math.max

// Portrait 4:5, the size Instagram and WhatsApp statuses show without cropping much.
private const val WIDTH = 1080
private const val HEIGHT = 1350
private const val PADDING = 72f
private const val PHOTO_HEIGHT = 600
private const val FOOTER_HEIGHT = 120f

private const val PAPER = 0xFFF7F8F4.toInt()
private const val INK = 0xFF1D2520.toInt()
private const val INK_SOFT = 0xFF5A645E.toInt()
private const val DIVIDER = 0xFFE1E5DE.toInt()

/**
 * Draws a recipe as an image to share on social networks or messaging apps: the cover photo (or a
 * band in the accent colour), the name, the key facts, the ingredients that fit and a small Miga
 * signature. Drawn on a plain Canvas, so it looks the same whatever the screen.
 */
object RecipeImageCard {

    fun render(context: Context, recipe: Recipe, accent: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)

        val photo = recipe.coverPhotoUri?.let { loadCropped(it, WIDTH, PHOTO_HEIGHT) }
        var y: Float
        if (photo != null) {
            canvas.drawBitmap(photo, 0f, 0f, null)
            photo.recycle()
            // Soft fade into the paper so the text below does not start on a hard edge.
            val fade = Paint().apply {
                shader = LinearGradient(0f, PHOTO_HEIGHT - 140f, 0f, PHOTO_HEIGHT.toFloat(), 0x00F7F8F4, PAPER, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, PHOTO_HEIGHT - 140f, WIDTH.toFloat(), PHOTO_HEIGHT.toFloat(), fade)
            y = PHOTO_HEIGHT + 24f
        } else {
            val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, WIDTH.toFloat(), 260f, accent, darker(accent), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), 260f, band)
            y = 260f + 56f
        }

        val contentWidth = (WIDTH - 2 * PADDING).toInt()

        // Category and origin, small and in the accent colour, above the name.
        val kicker = listOfNotNull(
            recipe.categoryName?.takeIf { it.isNotBlank() }?.uppercase(),
            RecipeOrigin.label(recipe.origin, recipe.originCountry)
        ).joinToString("  ·  ")
        if (kicker.isNotEmpty()) {
            val kickerPaint = textPaint(30f, accent, bold = true).apply { letterSpacing = 0.06f }
            y = drawText(canvas, kicker, kickerPaint, PADDING, y, contentWidth, maxLines = 1) + 12f
        }

        y = drawText(canvas, recipe.name, textPaint(68f, INK, bold = true), PADDING, y, contentWidth, maxLines = 3) + 18f

        val facts = buildList {
            recipe.totalTimeMinutes?.let { add("⏱ " + L10n.str(R.string.minutes_short, it)) }
            add("👥 " + recipe.servings)
            add(recipe.difficulty.label)
            recipe.rating?.let { add("★".repeat(it)) }
        }.joinToString("   ·   ")
        y = drawText(canvas, facts, textPaint(34f, INK_SOFT), PADDING, y, contentWidth, maxLines = 1) + 30f

        canvas.drawRect(PADDING, y, WIDTH - PADDING, y + 3f, Paint().apply { color = DIVIDER })
        y += 36f

        // Ingredients, as many as fit above the signature, then "and N more".
        val ingredients = recipe.ingredientGroups.flatMap { group -> group.ingredients }
        if (ingredients.isNotEmpty()) {
            y = drawText(canvas, L10n.str(R.string.ingredients), textPaint(38f, accent, bold = true), PADDING, y, contentWidth, maxLines = 1) + 14f
            val itemPaint = textPaint(34f, INK)
            val lineHeight = 50f
            val bottom = HEIGHT - FOOTER_HEIGHT - 16f
            val fitting = max(0, ((bottom - y) / lineHeight).toInt())
            val shown = if (ingredients.size <= fitting) ingredients else ingredients.take(max(0, fitting - 1))
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            shown.forEach { ingredient ->
                canvas.drawCircle(PADDING + 8f, y + 22f, 7f, dot)
                drawText(
                    canvas,
                    formatIngredientText(ingredient.name, ingredient.quantity, ingredient.unit),
                    itemPaint,
                    PADDING + 32f,
                    y,
                    contentWidth - 32,
                    maxLines = 1
                )
                y += lineHeight
            }
            if (shown.size < ingredients.size) {
                drawText(
                    canvas,
                    L10n.str(R.string.image_card_more_x, ingredients.size - shown.size),
                    textPaint(32f, INK_SOFT),
                    PADDING + 32f,
                    y,
                    contentWidth - 32,
                    maxLines = 1
                )
            }
        }

        drawFooter(context, canvas, accent)
        return bitmap
    }

    /** "Miga" with the app icon, at the bottom right, over a thin line. */
    private fun drawFooter(context: Context, canvas: Canvas, accent: Int) {
        val top = HEIGHT - FOOTER_HEIGHT
        canvas.drawRect(PADDING, top, WIDTH - PADDING, top + 2f, Paint().apply { color = DIVIDER })
        val label = L10n.str(R.string.image_card_signature)
        val paint = textPaint(32f, INK_SOFT)
        val brand = textPaint(40f, accent, bold = true)
        val baseline = top + FOOTER_HEIGHT / 2 + 14f
        val brandWidth = brand.measureText("Miga")
        val brandX = WIDTH - PADDING - brandWidth
        canvas.drawText("Miga", brandX, baseline, brand)
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.let { icon ->
            val size = 84
            val left = (brandX - size + 4).toInt()
            val iconTop = (top + (FOOTER_HEIGHT - size) / 2).toInt()
            icon.setBounds(left, iconTop, left + size, iconTop + size)
            icon.draw(canvas)
        }
        canvas.drawText(label, PADDING, baseline, paint)
    }

    /** Draws [text] wrapped in [width] and returns the y below it. */
    private fun drawText(canvas: Canvas, text: String, paint: TextPaint, x: Float, y: Float, width: Int, maxLines: Int): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.08f)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun darker(color: Int): Int {
        val factor = 0.72f
        val r = ((color shr 16 and 0xFF) * factor).toInt()
        val g = ((color shr 8 and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** The stored photo scaled and centre-cropped to exactly [width] x [height]. */
    private fun loadCropped(uri: String, width: Int, height: Int): Bitmap? = runCatching {
        val path = Uri.parse(uri).path?.takeIf { File(it).exists() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = max(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        val srcWidth = (width / scale).toInt()
        val srcHeight = (height / scale).toInt()
        val left = (decoded.width - srcWidth) / 2
        val top = (decoded.height - srcHeight) / 2
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(
            decoded,
            Rect(left, top, left + srcWidth, top + srcHeight),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        decoded.recycle()
        result
    }.getOrNull()
}
