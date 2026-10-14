package org.calamares.miga.data.export

import org.calamares.miga.data.model.displayText
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
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeOrigin
import org.calamares.miga.data.model.RichText
import org.calamares.miga.data.model.formatIngredientText
import java.io.File
import kotlin.math.max

private const val WIDTH = 1080
private const val PADDING = 72f
private val CONTENT_WIDTH = (WIDTH - 2 * PADDING).toInt()
private const val PHOTO_HEIGHT = 720
private const val BAND_HEIGHT = 260
private const val FOOTER_HEIGHT = 130f

/** A very long recipe is cut here ("…") rather than producing an image phones refuse to show. */
private const val MAX_HEIGHT = 9000

private const val PAPER = 0xFFF7F8F4.toInt()
private const val INK = 0xFF1D2520.toInt()
private const val INK_SOFT = 0xFF5A645E.toInt()
private const val DIVIDER = 0xFFE1E5DE.toInt()

/**
 * Draws a whole recipe as one tall image to share on social networks or messaging apps: the cover
 * photo (or a band in the accent colour), the name, the key facts, every ingredient and every step,
 * and a small Miga signature. The height follows the content, so nothing is cut; it is laid out
 * first and drawn afterwards on a plain Canvas, so it looks the same whatever the screen.
 */
object RecipeImageCard {

    /** Something to draw at a vertical offset, with its height known before drawing. */
    private class Block(val height: Float, val spacingBefore: Float, val draw: (Canvas, Float) -> Unit)

    fun render(context: Context, recipe: Recipe, accent: Int): Bitmap {
        val photo = recipe.coverPhotoUri?.let { loadCropped(it, WIDTH, PHOTO_HEIGHT) }
        val headerHeight = if (photo != null) PHOTO_HEIGHT + 24f else BAND_HEIGHT + 56f
        val blocks = contentBlocks(recipe, accent)

        val contentHeight = blocks.sumOf { (it.spacingBefore + it.height).toDouble() }.toFloat()
        val fullHeight = headerHeight + contentHeight + 40f + FOOTER_HEIGHT
        val height = fullHeight.coerceAtMost(MAX_HEIGHT.toFloat()).toInt()
        val truncated = fullHeight > MAX_HEIGHT

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)

        if (photo != null) {
            canvas.drawBitmap(photo, 0f, 0f, null)
            photo.recycle()
            // Soft fade into the paper so the text below does not start on a hard edge.
            val fade = Paint().apply {
                shader = LinearGradient(0f, PHOTO_HEIGHT - 160f, 0f, PHOTO_HEIGHT.toFloat(), PAPER and 0x00FFFFFF, PAPER, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, PHOTO_HEIGHT - 160f, WIDTH.toFloat(), PHOTO_HEIGHT.toFloat(), fade)
        } else {
            val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, WIDTH.toFloat(), BAND_HEIGHT.toFloat(), accent, darker(accent), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), BAND_HEIGHT.toFloat(), band)
        }

        val contentBottom = height - FOOTER_HEIGHT - 40f
        var y = headerHeight
        for (block in blocks) {
            y += block.spacingBefore
            if (truncated && y + block.height > contentBottom - 60f) {
                drawLayout(canvas, layout("…", textPaint(40f, INK_SOFT), CONTENT_WIDTH), PADDING, y)
                break
            }
            block.draw(canvas, y)
            y += block.height
        }

        drawFooter(context, canvas, accent, height.toFloat())
        return bitmap
    }

    private fun contentBlocks(recipe: Recipe, accent: Int): List<Block> {
        val blocks = mutableListOf<Block>()
        fun text(text: String, paint: TextPaint, spacingBefore: Float, x: Float = PADDING, width: Int = CONTENT_WIDTH, maxLines: Int = Int.MAX_VALUE) {
            val layout = layout(text, paint, width, maxLines)
            blocks += Block(layout.height.toFloat(), spacingBefore) { canvas, y -> drawLayout(canvas, layout, x, y) }
        }
        fun divider(spacingBefore: Float) {
            blocks += Block(3f, spacingBefore) { canvas, y ->
                canvas.drawRect(PADDING, y, WIDTH - PADDING, y + 3f, Paint().apply { color = DIVIDER })
            }
        }

        // Category and origin, small and in the accent colour, above the name.
        val kicker = listOfNotNull(
            recipe.categoryName?.takeIf { it.isNotBlank() }?.uppercase(),
            RecipeOrigin.label(recipe.origin, recipe.originCountry)
        ).joinToString("  ·  ")
        if (kicker.isNotEmpty()) text(kicker, textPaint(30f, accent, bold = true).apply { letterSpacing = 0.06f }, 0f, maxLines = 1)
        text(recipe.name, textPaint(68f, INK, bold = true), if (kicker.isNotEmpty()) 12f else 0f, maxLines = 4)

        val facts = buildList {
            recipe.prepTimeMinutes?.takeIf { it > 0 }?.let { add("🔪 " + L10n.str(R.string.minutes_short, it)) }
            recipe.cookTimeMinutes?.takeIf { it > 0 }?.let { add("🔥 " + L10n.str(R.string.minutes_short, it)) }
            add("👥 " + recipe.servings)
            add(recipe.difficulty.label)
            recipe.rating?.let { add("★".repeat(it)) }
        }.joinToString("   ·   ")
        text(facts, textPaint(34f, INK_SOFT), 18f)

        val ingredientGroups = recipe.ingredientGroups.filter { it.ingredients.isNotEmpty() }
        if (ingredientGroups.isNotEmpty()) {
            divider(30f)
            text(L10n.str(R.string.ingredients), textPaint(42f, accent, bold = true), 30f)
            val itemPaint = textPaint(34f, INK)
            ingredientGroups.forEach { group ->
                group.name?.let { text(it, textPaint(34f, INK, bold = true), 18f) }
                group.ingredients.forEach { ingredient ->
                    val layout = layout(ingredient.displayText(), itemPaint, CONTENT_WIDTH - 36)
                    blocks += Block(layout.height.toFloat(), 10f) { canvas, y ->
                        canvas.drawCircle(PADDING + 9f, y + 24f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
                        drawLayout(canvas, layout, PADDING + 36f, y)
                    }
                }
            }
        }

        val stepGroups = recipe.stepGroups.filter { it.instructions.isNotEmpty() }
        if (stepGroups.isNotEmpty()) {
            divider(36f)
            text(L10n.str(R.string.method), textPaint(42f, accent, bold = true), 30f)
            val stepPaint = textPaint(34f, INK)
            val numberPaint = textPaint(28f, 0xFFFFFFFF.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }
            stepGroups.forEach { group ->
                group.name?.let { text(it, textPaint(34f, INK, bold = true), 20f) }
                group.instructions.forEachIndexed { index, instruction ->
                    val layout = layout(RichText.toPlainText(instruction), stepPaint, CONTENT_WIDTH - 70)
                    blocks += Block(max(layout.height.toFloat(), 46f), 18f) { canvas, y ->
                        canvas.drawCircle(PADDING + 22f, y + 23f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
                        canvas.drawText("${index + 1}", PADDING + 22f, y + 33f, numberPaint)
                        drawLayout(canvas, layout, PADDING + 70f, y)
                    }
                }
            }
        }

        if (recipe.notes.isNotBlank()) {
            divider(36f)
            text(L10n.str(R.string.notes), textPaint(42f, accent, bold = true), 30f)
            text(RichText.toPlainText(recipe.notes), textPaint(32f, INK_SOFT), 14f)
        }
        return blocks
    }

    /** "Miga" with the app icon, at the bottom right, over a thin line. */
    private fun drawFooter(context: Context, canvas: Canvas, accent: Int, height: Float) {
        val top = height - FOOTER_HEIGHT
        canvas.drawRect(PADDING, top, WIDTH - PADDING, top + 2f, Paint().apply { color = DIVIDER })
        val baseline = top + FOOTER_HEIGHT / 2 + 14f
        val brand = textPaint(40f, accent, bold = true)
        val brandX = WIDTH - PADDING - brand.measureText("Miga")
        canvas.drawText("Miga", brandX, baseline, brand)
        // The app icon as the launcher shows it (background and foreground, in the system shape).
        runCatching { context.packageManager.getApplicationIcon(context.packageName) }.getOrNull()?.let { icon ->
            val size = 64
            val left = (brandX - size - 14).toInt()
            val iconTop = (top + (FOOTER_HEIGHT - size) / 2).toInt()
            icon.setBounds(left, iconTop, left + size, iconTop + size)
            icon.draw(canvas)
        }
        canvas.drawText(L10n.str(R.string.image_card_signature), PADDING, baseline, textPaint(32f, INK_SOFT))
    }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int = Int.MAX_VALUE): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.12f)
            .build()

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
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
