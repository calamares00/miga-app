package com.bmo00.miga.data.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.bmo00.miga.data.model.Recipe

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private val CONTENT_WIDTH = (PAGE_WIDTH - 2 * MARGIN).toInt()

private class Block(val text: String, val paint: TextPaint, val spacingBefore: Float, val forceNewPage: Boolean = false)

private class Paints {
    val bookTitle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; isFakeBoldText = true }
    val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true }
    val meta = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = 0xFF6B6055.toInt() }
    val header = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; isFakeBoldText = true }
    val subHeader = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; isFakeBoldText = true; color = 0xFFC1633D.toInt() }
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f }
}

object PdfRecipeRenderer {

    fun render(recipe: Recipe): PdfDocument = buildDocument(recipeBlocks(recipe, Paints(), forceNewPageForTitle = false))

    fun renderBook(bookName: String, recipes: List<Recipe>): PdfDocument {
        val paints = Paints()
        val blocks = mutableListOf<Block>()
        blocks += Block(bookName, paints.bookTitle, 0f)
        recipes.forEach { recipe -> blocks += recipeBlocks(recipe, paints, forceNewPageForTitle = true) }
        return buildDocument(blocks)
    }

    private fun recipeBlocks(recipe: Recipe, paints: Paints, forceNewPageForTitle: Boolean): List<Block> {
        val blocks = mutableListOf<Block>()
        blocks += Block(recipe.name, paints.title, if (forceNewPageForTitle) 24f else 0f, forceNewPage = forceNewPageForTitle)

        val meta = buildString {
            append(recipe.difficulty.label)
            recipe.categoryName?.let { append(" · ").append(it) }
            recipe.totalTimeMinutes?.let { append(" · ").append(it).append(" min") }
            append(" · Raciones: ").append(recipe.servings)
        }
        blocks += Block(meta, paints.meta, 6f)

        if (recipe.utensils.isNotEmpty()) {
            blocks += Block("Utensilios: " + recipe.utensils.joinToString(", "), paints.body, 10f)
        }
        if (recipe.tags.isNotEmpty()) {
            blocks += Block("Etiquetas: " + recipe.tags.joinToString(", "), paints.body, 4f)
        }

        blocks += Block("Ingredientes", paints.header, 18f)
        recipe.ingredientGroups.forEach { group ->
            if (group.ingredients.isNotEmpty()) {
                if (group.name != null) blocks += Block(group.name, paints.subHeader, 10f)
                group.ingredients.forEach { ingredient ->
                    val qty = ingredient.quantity?.let { q -> if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString() }
                    val line = "•  " + listOfNotNull(qty, ingredient.unit).joinToString(" ") + (if (qty != null) " " else "") + ingredient.name
                    blocks += Block(line.replace("  ", " "), paints.body, 4f)
                }
            }
        }

        blocks += Block("Preparación", paints.header, 18f)
        recipe.stepGroups.forEach { group ->
            if (group.instructions.isNotEmpty()) {
                if (group.name != null) blocks += Block(group.name, paints.subHeader, 10f)
                group.instructions.forEachIndexed { index, instruction ->
                    blocks += Block("${index + 1}. $instruction", paints.body, 6f)
                }
            }
        }

        if (recipe.notes.isNotBlank()) {
            blocks += Block("Notas", paints.header, 18f)
            blocks += Block(recipe.notes, paints.body, 4f)
        }
        if (recipe.source.isNotBlank()) {
            blocks += Block("Origen: " + recipe.source, paints.body, 10f)
        }

        return blocks
    }

    private fun buildDocument(blocks: List<Block>): PdfDocument {
        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
        }

        blocks.forEach { block ->
            val layout = StaticLayout.Builder
                .obtain(block.text, 0, block.text.length, block.paint, CONTENT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(1f, 1.15f)
                .build()

            val neededHeight = block.spacingBefore + layout.height
            val mustBreak = y > MARGIN && (block.forceNewPage || y + neededHeight > PAGE_HEIGHT - MARGIN)
            if (mustBreak) {
                newPage()
            }
            y += block.spacingBefore

            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()

            y += layout.height
        }

        document.finishPage(page)
        return document
    }
}
