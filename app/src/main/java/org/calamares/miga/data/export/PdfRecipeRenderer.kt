package org.calamares.miga.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.UNCATEGORIZED_CATEGORY_LABEL
import org.calamares.miga.data.model.formatIngredientText
import java.io.File
import kotlin.math.min

// Same layout as the PDF generator of miga-server (PdfBox): keep sizes, margins and section order
// in sync so a PDF looks the same whether it is exported from the app or from the server UI.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val CONTENT_TOP = MARGIN + 20f
private const val CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN - 20f
private val CONTENT_WIDTH = (PAGE_WIDTH - 2 * MARGIN).toInt()

private const val MAX_HERO_IMAGE_HEIGHT = 260f
private const val THUMB_SIZE = 110f
private const val THUMBS_PER_ROW = 4
private const val THUMB_GAP = 10f
private const val COVER_IMAGE_BOX = 300f

private const val ACCENT_COLOR = 0xFFC1633D.toInt() // Terracotta, same as ui/theme/Color.kt
private const val MUTED_COLOR = 0xFF6B6055.toInt()
private const val RULE_COLOR = 0xFFE0D8CE.toInt()

private class Paints {
    val coverTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f; isFakeBoldText = true }
    val coverSubtitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; color = MUTED_COLOR; textAlign = Paint.Align.CENTER }
    val tocTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; isFakeBoldText = true }
    val tocCategory = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; isFakeBoldText = true; color = ACCENT_COLOR }
    val tocEntry = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.5f }
    val tocPageNum = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12.5f; color = MUTED_COLOR; textAlign = Paint.Align.RIGHT }
    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true }
    val meta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = MUTED_COLOR }
    val header = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true }
    val subHeader = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; isFakeBoldText = true; color = ACCENT_COLOR }
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f }
    val runningHeader = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = MUTED_COLOR }
    val footer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = MUTED_COLOR; textAlign = Paint.Align.CENTER }
    val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RULE_COLOR; strokeWidth = 0.75f }
}

/**
 * A unit of content to lay out. Every item can measure its own height without a Canvas, so the
 * page of every item is known (see [PdfRecipeRenderer.layoutItems]) before anything is drawn.
 * Images only keep their uri and size: bitmaps are decoded right before drawing and are not kept
 * by the items, so exporting a large book does not hold every photo in memory at once. They are
 * not recycled explicitly because the page canvas may still reference them until finishPage().
 */
private sealed interface Item {
    val spacingBefore: Float

    data class Text(val text: String, val paint: TextPaint, override val spacingBefore: Float, val forceNewPage: Boolean = false) : Item
    data class Image(val uri: String, val width: Int, val height: Int, override val spacingBefore: Float) : Item
    data class ThumbRow(val uris: List<String>, override val spacingBefore: Float) : Item
    data class TocCategory(val name: String, override val spacingBefore: Float) : Item
    data class TocRecipe(val name: String, val page: Int, override val spacingBefore: Float) : Item
}

private class PlacedItem(val item: Item, val page: Int, val y: Float)

/** Renders a recipe, or a whole book with cover and table of contents, as an A4 PDF. */
object PdfRecipeRenderer {

    fun render(recipe: Recipe): PdfDocument {
        val paints = Paints()
        val items = recipeItems(recipe, paints, forceNewPageForTitle = false)
        val placed = layoutItems(items, startPage = 1, paints)
        val totalPages = placed.maxOfOrNull { it.page } ?: 1
        val document = PdfDocument()
        val byPage = placed.groupBy { it.page }
        for (pageNum in 1..totalPages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            val canvas = page.canvas
            drawHeaderFooter(canvas, paints, recipe.name, pageNum, totalPages)
            byPage[pageNum]?.forEach { drawItem(canvas, it.item, it.y, paints) }
            document.finishPage(page)
        }
        return document
    }

    /**
     * Renders a whole book: a cover, a table of contents grouped by category with the real page of
     * every recipe, and the recipes in the same order.
     *
     * The table of contents needs the page where each recipe starts, which is only known after
     * laying out everything before it. So it is laid out three times: first with dummy numbers to
     * learn how many pages it takes (its height does not depend on the numbers), then the content
     * is laid out after it to find each recipe's first page, and finally the table is laid out
     * again with the real numbers.
     */
    fun renderBook(bookName: String, coverPhotoUri: String?, recipes: List<Recipe>): PdfDocument {
        val paints = Paints()
        val ordered = groupedByCategory(recipes)

        val tocDry = layoutItems(buildTocItems(ordered, paints) { 0 }, startPage = 1, paints)
        val tocPageCount = tocDry.maxOfOrNull { it.page } ?: 1
        val contentStartPage = 2 + tocPageCount

        val recipeItemLists = ordered.map { recipe -> recipe to recipeItems(recipe, paints, forceNewPageForTitle = true) }
        val contentPlaced = layoutItems(recipeItemLists.flatMap { it.second }, startPage = contentStartPage, paints)
        val startPageByRecipe = mutableMapOf<String, Int>()
        var cursor = 0
        recipeItemLists.forEach { (recipe, items) ->
            startPageByRecipe[recipe.uid] = contentPlaced.getOrNull(cursor)?.page ?: contentStartPage
            cursor += items.size
        }
        val totalPages = contentPlaced.maxOfOrNull { it.page } ?: (contentStartPage - 1)

        val tocPlaced = layoutItems(buildTocItems(ordered, paints) { recipe -> startPageByRecipe.getValue(recipe.uid) }, startPage = 1, paints)

        val document = PdfDocument()

        val coverPage = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val coverBitmap = coverPhotoUri?.let { loadScaledBitmap(it, COVER_IMAGE_BOX.toInt() * 2) }
        drawCoverPage(coverPage.canvas, paints, bookName, coverBitmap, ordered.size)
        document.finishPage(coverPage)

        val tocByLocalPage = tocPlaced.groupBy { it.page }
        for (localPage in 1..tocPageCount) {
            val absolutePage = 1 + localPage
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, absolutePage).create())
            val canvas = page.canvas
            drawHeaderFooter(canvas, paints, null, absolutePage, totalPages)
            tocByLocalPage[localPage]?.forEach { drawItem(canvas, it.item, it.y, paints) }
            document.finishPage(page)
        }

        val contentByPage = contentPlaced.groupBy { it.page }
        for (pageNum in contentStartPage..totalPages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            val canvas = page.canvas
            drawHeaderFooter(canvas, paints, bookName, pageNum, totalPages)
            contentByPage[pageNum]?.forEach { drawItem(canvas, it.item, it.y, paints) }
            document.finishPage(page)
        }

        return document
    }

    /** Recipes sorted by category (alphabetically, uncategorized last) and then by name. */
    private fun groupedByCategory(recipes: List<Recipe>): List<Recipe> {
        val uncategorized = UNCATEGORIZED_CATEGORY_LABEL
        return recipes.groupBy { it.categoryName?.takeIf { name -> name.isNotBlank() } ?: uncategorized }
            .toSortedMap(compareBy { if (it == uncategorized) "￿" else it.lowercase() })
            .flatMap { (_, group) -> group.sortedBy { it.name.lowercase() } }
    }

    private fun buildTocItems(ordered: List<Recipe>, paints: Paints, pageNumberFor: (Recipe) -> Int): List<Item> {
        val items = mutableListOf<Item>()
        items += Item.Text(L10n.str(R.string.contents), paints.tocTitle, 0f)
        var lastCategory: String? = null
        ordered.forEach { recipe ->
            val category = recipe.categoryName?.takeIf { it.isNotBlank() } ?: UNCATEGORIZED_CATEGORY_LABEL
            if (category != lastCategory) {
                items += Item.TocCategory(category, if (lastCategory == null) 20f else 16f)
                lastCategory = category
            }
            items += Item.TocRecipe(recipe.name, pageNumberFor(recipe), 8f)
        }
        return items
    }

    private fun recipeItems(recipe: Recipe, paints: Paints, forceNewPageForTitle: Boolean): List<Item> {
        val items = mutableListOf<Item>()
        items += Item.Text(recipe.name, paints.title, if (forceNewPageForTitle) 16f else 0f, forceNewPage = forceNewPageForTitle)

        val meta = buildString {
            append(recipe.difficulty.label)
            recipe.categoryName?.let { append(" · ").append(it) }
            recipe.totalTimeMinutes?.let { append(" · ").append(it).append(" min") }
            append(L10n.str(R.string.servings_x, recipe.servings))
        }
        items += Item.Text(meta, paints.meta, 6f)

        val coverUri = recipe.coverPhotoUri
        if (coverUri != null) {
            imageSize(coverUri)?.let { (width, height) -> items += Item.Image(coverUri, width, height, 12f) }
        }
        val extraUris = recipe.photos.map { it.uri }.filter { it != coverUri && imageSize(it) != null }
        extraUris.chunked(THUMBS_PER_ROW).forEachIndexed { index, row ->
            items += Item.ThumbRow(row, if (index == 0) 10f else THUMB_GAP)
        }

        if (recipe.utensils.isNotEmpty()) {
            items += Item.Text(L10n.str(R.string.utensils) + ": " + recipe.utensils.joinToString(", "), paints.body, 10f)
        }
        if (recipe.tags.isNotEmpty()) {
            items += Item.Text(L10n.str(R.string.tags) + ": " + recipe.tags.joinToString(", "), paints.body, 4f)
        }

        items += Item.Text(L10n.str(R.string.ingredients), paints.header, 18f)
        recipe.ingredientGroups.forEach { group ->
            if (group.ingredients.isNotEmpty()) {
                if (group.name != null) items += Item.Text(group.name, paints.subHeader, 10f)
                group.ingredients.forEach { ingredient ->
                    val line = "• " + formatIngredientText(ingredient.name, ingredient.quantity, ingredient.unit)
                    items += Item.Text(line, paints.body, 4f)
                }
            }
        }

        items += Item.Text(L10n.str(R.string.method), paints.header, 18f)
        recipe.stepGroups.forEach { group ->
            if (group.instructions.isNotEmpty()) {
                if (group.name != null) items += Item.Text(group.name, paints.subHeader, 10f)
                group.instructions.forEachIndexed { index, instruction ->
                    items += Item.Text("${index + 1}. $instruction", paints.body, 6f)
                }
            }
        }

        if (recipe.notes.isNotBlank()) {
            items += Item.Text(L10n.str(R.string.notes), paints.header, 18f)
            items += Item.Text(recipe.notes, paints.body, 4f)
        }
        if (recipe.source.isNotBlank()) {
            items += Item.Text(L10n.str(R.string.source) + ": " + recipe.source, paints.body, 10f)
        }

        return items
    }

    /**
     * Assigns a page and a vertical position to every item without touching a Canvas; used both to
     * measure (table of contents) and for the final drawing.
     */
    private fun layoutItems(items: List<Item>, startPage: Int, paints: Paints): List<PlacedItem> {
        val placed = mutableListOf<PlacedItem>()
        var page = startPage
        var y = CONTENT_TOP
        items.forEach { item ->
            val height = measuredHeight(item, paints)
            val needed = item.spacingBefore + height
            val mustBreak = y > CONTENT_TOP && ((item is Item.Text && item.forceNewPage) || y + needed > CONTENT_BOTTOM)
            if (mustBreak) {
                page++
                y = CONTENT_TOP
            }
            y += item.spacingBefore
            placed += PlacedItem(item, page, y)
            y += height
        }
        return placed
    }

    private fun measuredHeight(item: Item, paints: Paints): Float = when (item) {
        is Item.Text -> textLayout(item.text, item.paint).height.toFloat()
        is Item.Image -> imageDrawSize(item.width, item.height).second
        is Item.ThumbRow -> THUMB_SIZE
        is Item.TocCategory -> singleLineHeight(paints.tocCategory)
        is Item.TocRecipe -> singleLineHeight(paints.tocEntry)
    }

    private fun singleLineHeight(paint: TextPaint): Float = (paint.descent() - paint.ascent()) * 1.2f

    private fun textLayout(text: String, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1f, 1.15f)
            .build()

    /** Size of a full-width image, capped at [MAX_HERO_IMAGE_HEIGHT] while keeping its aspect ratio. */
    private fun imageDrawSize(width: Int, height: Int): Pair<Float, Float> {
        val naturalHeight = CONTENT_WIDTH.toFloat() * height / width
        val drawHeight = min(naturalHeight, MAX_HERO_IMAGE_HEIGHT)
        val drawWidth = if (drawHeight < naturalHeight) drawHeight * width / height else CONTENT_WIDTH.toFloat()
        return drawWidth to drawHeight
    }

    private fun drawItem(canvas: Canvas, item: Item, y: Float, paints: Paints) {
        when (item) {
            is Item.Text -> {
                val layout = textLayout(item.text, item.paint)
                canvas.save()
                canvas.translate(MARGIN, y)
                layout.draw(canvas)
                canvas.restore()
            }
            is Item.Image -> {
                val bitmap = loadScaledBitmap(item.uri, CONTENT_WIDTH * 2) ?: return
                val (w, h) = imageDrawSize(item.width, item.height)
                val left = MARGIN + (CONTENT_WIDTH - w) / 2f
                canvas.drawBitmap(bitmap, null, RectF(left, y, left + w, y + h), null)
            }
            is Item.ThumbRow -> {
                var x = MARGIN
                item.uris.forEach { uri ->
                    loadScaledBitmap(uri, (THUMB_SIZE * 2).toInt())?.let { bitmap ->
                        canvas.drawBitmap(centerCropSquare(bitmap, THUMB_SIZE.toInt()), x, y, null)
                    }
                    x += THUMB_SIZE + THUMB_GAP
                }
            }
            is Item.TocCategory -> {
                canvas.drawText(item.name.uppercase(), MARGIN, y - paints.tocCategory.ascent(), paints.tocCategory)
            }
            is Item.TocRecipe -> drawTocRow(canvas, item.name, item.page, y, paints)
        }
    }

    /** One table-of-contents line: name, dot leaders and the page number aligned to the right. */
    private fun drawTocRow(canvas: Canvas, name: String, pageNumber: Int, y: Float, paints: Paints) {
        val baseline = y - paints.tocEntry.ascent()
        val pageText = pageNumber.toString()
        val numWidth = paints.tocPageNum.measureText(pageText)
        val availableForName = CONTENT_WIDTH - numWidth - 16f
        val ellipsized = TextUtils.ellipsize(name, paints.tocEntry, availableForName, TextUtils.TruncateAt.END).toString()
        canvas.drawText(ellipsized, MARGIN, baseline, paints.tocEntry)
        val nameWidth = paints.tocEntry.measureText(ellipsized)
        val dotsStart = MARGIN + nameWidth + 4f
        val dotsEnd = MARGIN + availableForName - 4f
        if (dotsEnd > dotsStart) {
            val dotWidth = paints.tocEntry.measureText(". ")
            var dx = dotsStart
            while (dx < dotsEnd) {
                canvas.drawText(".", dx, baseline, paints.tocEntry)
                dx += dotWidth
            }
        }
        canvas.drawText(pageText, MARGIN + CONTENT_WIDTH, baseline, paints.tocPageNum)
    }

    private fun drawCoverPage(canvas: Canvas, paints: Paints, bookName: String, coverBitmap: Bitmap?, recipeCount: Int) {
        var y = 140f
        if (coverBitmap != null) {
            val scale = min(COVER_IMAGE_BOX / coverBitmap.width, COVER_IMAGE_BOX / coverBitmap.height)
            val w = coverBitmap.width * scale
            val h = coverBitmap.height * scale
            val left = (PAGE_WIDTH - w) / 2f
            canvas.drawBitmap(coverBitmap, null, RectF(left, y, left + w, y + h), null)
            y += h + 30f
        }
        val titleLayout = StaticLayout.Builder
            .obtain(bookName, 0, bookName.length, paints.coverTitle, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(MARGIN, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 12f
        val subtitle = if (recipeCount == 1) L10n.str(R.string.recipe_count_one) else L10n.str(R.string.recipe_count_many, recipeCount)
        canvas.drawText(subtitle, PAGE_WIDTH / 2f, y, paints.coverSubtitle)
    }

    private fun drawHeaderFooter(canvas: Canvas, paints: Paints, headerText: String?, pageNum: Int, totalPages: Int) {
        if (headerText != null) {
            canvas.drawText(headerText, MARGIN, MARGIN + 6f, paints.runningHeader)
            canvas.drawLine(MARGIN, MARGIN + 12f, PAGE_WIDTH - MARGIN, MARGIN + 12f, paints.rule)
        }
        canvas.drawText(L10n.str(R.string.miga_page_x_x, pageNum, totalPages), PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN + 14f, paints.footer)
    }

    private fun filePath(uri: String): String? =
        runCatching { Uri.parse(uri).path }.getOrNull()?.takeIf { File(it).exists() }

    /** Pixel size of a stored photo without decoding it, or null when it cannot be read. */
    private fun imageSize(uri: String): Pair<Int, Int>? {
        val path = filePath(uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth to bounds.outHeight else null
    }

    /** Decodes a stored photo ("file://...") subsampled to roughly [targetWidth] to save memory. */
    private fun loadScaledBitmap(uri: String, targetWidth: Int): Bitmap? {
        val path = filePath(uri) ?: return null
        val (width, _) = imageSize(uri) ?: return null
        var sampleSize = 1
        while (width / (sampleSize * 2) >= targetWidth) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    private fun centerCropSquare(bitmap: Bitmap, size: Int): Bitmap {
        val srcSize = min(bitmap.width, bitmap.height)
        val left = (bitmap.width - srcSize) / 2
        val top = (bitmap.height - srcSize) / 2
        val cropped = Bitmap.createBitmap(bitmap, left, top, srcSize, srcSize)
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        if (cropped !== bitmap && cropped !== scaled) cropped.recycle()
        return scaled
    }
}
