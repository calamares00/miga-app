package com.bmo00.miga.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeBook
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object RecipeExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun shareAsText(context: Context, recipe: Recipe) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, recipe.name)
            putExtra(Intent.EXTRA_TEXT, formatRecipeAsText(recipe))
        }
        context.startActivity(Intent.createChooser(intent, "Compartir receta"))
    }

    fun shareAsJson(context: Context, recipe: Recipe) {
        val content = json.encodeToString(recipe.toExportDto())
        val file = writeExportFile(context, sanitizeFileName(recipe.name) + ".json", content)
        shareFile(context, file, "application/json")
    }

    fun shareAsPdf(context: Context, recipe: Recipe) {
        val document = PdfRecipeRenderer.render(recipe)
        val file = File(exportsDir(context), sanitizeFileName(recipe.name) + ".pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        shareFile(context, file, "application/pdf")
    }

    fun shareBookAsJson(context: Context, book: RecipeBook, recipes: List<Recipe>) {
        val dto = LibraryExportDto(exportedAt = System.currentTimeMillis(), recipes = recipes.map { it.toExportDto() })
        val content = json.encodeToString(dto)
        val file = writeExportFile(context, sanitizeFileName(book.name) + ".json", content)
        shareFile(context, file, "application/json")
    }

    fun shareBookAsPdf(context: Context, book: RecipeBook, recipes: List<Recipe>) {
        val document = PdfRecipeRenderer.renderBook(book.name, recipes)
        val file = File(exportsDir(context), sanitizeFileName(book.name) + ".pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        shareFile(context, file, "application/pdf")
    }

    suspend fun exportLibrary(context: Context, destination: Uri, recipes: List<Recipe>) = withContext(Dispatchers.IO) {
        val dto = LibraryExportDto(
            exportedAt = System.currentTimeMillis(),
            recipes = recipes.map { it.toExportDto() }
        )
        val content = json.encodeToString(dto)
        context.contentResolver.openOutputStream(destination)?.use { output ->
            output.write(content.toByteArray())
        }
    }

    /** Importa una receta individual exportada como JSON (ver [shareAsJson]). Null si no se pudo leer/parsear. */
    suspend fun importRecipe(context: Context, source: Uri): RecipeExportDto? = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
            ?: return@withContext null
        runCatching { json.decodeFromString(RecipeExportDto.serializer(), content) }.getOrNull()
    }

    /** Importa una copia de seguridad JSON. Devuelve el número de recetas importadas. */
    suspend fun importLibrary(context: Context, source: Uri, repository: RecipeRepository): Int = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
            ?: return@withContext 0
        val dto = json.decodeFromString(LibraryExportDto.serializer(), content)
        val bookIdsByName = mutableMapOf<String, Long>()
        dto.recipes.forEach { recipeDto ->
            val bookId = bookIdsByName.getOrPut(recipeDto.recipeBookName) {
                repository.getOrCreateRecipeBookIdByName(recipeDto.recipeBookName)
            }
            repository.saveRecipe(recipeDto.toDraft(bookId))
        }
        dto.recipes.size
    }

    private fun exportsDir(context: Context): File = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun writeExportFile(context: Context, fileName: String, content: String): File {
        val file = File(exportsDir(context), fileName)
        file.writeText(content)
        return file
    }

    private fun shareFile(context: Context, file: File, mimeType: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir receta"))
    }

    private fun sanitizeFileName(name: String): String =
        name.trim().ifBlank { "receta" }.replace(Regex("[^A-Za-z0-9-_ ]"), "").replace(" ", "_").take(60)

    private fun formatRecipeAsText(recipe: Recipe): String = buildString {
        appendLine(recipe.name)
        appendLine("—".repeat(recipe.name.length.coerceAtMost(40)))
        append(recipe.difficulty.label)
        recipe.categoryName?.let { append(" · ").append(it) }
        recipe.totalTimeMinutes?.let { append(" · ").append(it).append(" min") }
        appendLine(" · Raciones: ${recipe.servings}")
        if (recipe.utensils.isNotEmpty()) appendLine("Utensilios: ${recipe.utensils.joinToString(", ")}")
        appendLine()
        appendLine("INGREDIENTES")
        recipe.ingredientGroups.forEach { group ->
            if (group.ingredients.isNotEmpty()) {
                if (group.name != null) appendLine(group.name.uppercase())
                group.ingredients.forEach { ingredient ->
                    val qty = ingredient.quantity?.let { q -> if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString() }
                    appendLine("- " + listOfNotNull(qty, ingredient.unit, ingredient.name).joinToString(" "))
                }
            }
        }
        appendLine()
        appendLine("PREPARACIÓN")
        recipe.stepGroups.forEach { group ->
            if (group.instructions.isNotEmpty()) {
                if (group.name != null) appendLine(group.name.uppercase())
                group.instructions.forEachIndexed { index, instruction -> appendLine("${index + 1}. $instruction") }
            }
        }
        if (recipe.notes.isNotBlank()) {
            appendLine()
            appendLine("NOTAS")
            appendLine(recipe.notes)
        }
        if (recipe.source.isNotBlank()) {
            appendLine()
            appendLine("Origen: ${recipe.source}")
        }
    }
}
