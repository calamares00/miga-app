package com.bmo00.miga.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bmo00.miga.data.local.PhotoStorage
import com.bmo00.miga.data.model.HealthColorLevel
import com.bmo00.miga.data.model.Recipe
import com.bmo00.miga.data.model.RecipeBook
import com.bmo00.miga.data.model.RecipePhoto
import com.bmo00.miga.data.repository.RecipeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed interface RecipeImportResult {
    data class Success(val recipe: RecipeExportDto, val photos: List<RecipePhoto> = emptyList()) : RecipeImportResult
    data class Error(val reason: String) : RecipeImportResult
}

sealed interface LibraryImportResult {
    data class Success(val count: Int) : LibraryImportResult
    data class Error(val reason: String) : LibraryImportResult
}

object RecipeExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        // Sin esto, un campo cuyo valor coincide con su default (p.ej. "version" recién creado)
        // no se escribiría en el JSON de salida, y el importador lo trataría como si faltase.
        encodeDefaults = true
    }

    /**
     * Migraciones del JSON de una receta individual, indexadas por versión de origen: el
     * elemento en la posición N transforma un JSON en versión N a versión N+1. Un archivo sin
     * clave "version" (todo lo exportado antes de que existiera este mecanismo) se trata como
     * versión 0.
     */
    private val recipeMigrations: List<(JsonObject) -> JsonObject> = listOf(
        { obj -> obj }, // v0 -> v1: el "esquema v0" ya tenía los mismos campos, no-op.
        { obj -> addUidIfMissing(obj) }, // v1 -> v2: añade "uid" (las fotos ya tienen valor por defecto).
        { obj -> obj } // v2 -> v3: "health" es opcional con default null, no hace falta generar nada.
    )

    /** Igual que [recipeMigrations] pero para la copia de seguridad completa ([LibraryExportDto]). */
    private val libraryMigrations: List<(JsonObject) -> JsonObject> = listOf(
        { obj -> obj }, // v0 -> v1
        { obj ->
            // v1 -> v2: cada receta anidada en "recipes" también necesita su propio "uid".
            val recipesArray = obj["recipes"] as? JsonArray ?: JsonArray(emptyList())
            val migratedRecipes = JsonArray(recipesArray.map { addUidIfMissing(it.jsonObject) })
            JsonObject(obj + ("recipes" to migratedRecipes))
        },
        { obj -> obj } // v2 -> v3: "health" es opcional con default null, no hace falta generar nada.
    )

    private fun addUidIfMissing(obj: JsonObject): JsonObject =
        if (obj.containsKey("uid")) obj else JsonObject(obj + ("uid" to JsonPrimitive(UUID.randomUUID().toString())))

    private fun migrateJson(rawJson: String, migrations: List<(JsonObject) -> JsonObject>, currentVersion: Int): JsonObject {
        var obj = json.parseToJsonElement(rawJson).jsonObject
        var version = (obj["version"] as? JsonPrimitive)?.intOrNull ?: 0
        while (version < currentVersion) {
            obj = migrations.getOrElse(version) { { o: JsonObject -> o } }(obj)
            version++
        }
        return JsonObject(obj + ("version" to JsonPrimitive(version)))
    }

    fun shareAsText(context: Context, recipe: Recipe) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, recipe.name)
            putExtra(Intent.EXTRA_TEXT, formatRecipeAsText(recipe))
        }
        context.startActivity(Intent.createChooser(intent, "Compartir receta"))
    }

    /** Exporta una receta: ZIP con sus fotos si tiene alguna, si no un .json plano como hasta ahora. */
    fun shareRecipe(context: Context, recipe: Recipe) {
        val content = json.encodeToString(recipe.toExportDto())
        if (recipe.photos.isEmpty()) {
            val file = writeExportFile(context, sanitizeFileName(recipe.name) + ".json", content)
            shareFile(context, file, "application/json")
        } else {
            val photoSources = recipe.photos.mapIndexed { index, photo -> "recipes/${recipe.uid}/$index.jpg" to photo.uri }
            val file = writeZipFile(context, sanitizeFileName(recipe.name) + ".zip", content, photoSources)
            shareFile(context, file, "application/zip")
        }
    }

    fun shareAsPdf(context: Context, recipe: Recipe) {
        val document = PdfRecipeRenderer.render(recipe)
        val file = File(exportsDir(context), sanitizeFileName(recipe.name) + ".pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        shareFile(context, file, "application/pdf")
    }

    /** Exporta un libro completo: ZIP si el libro o alguna receta tienen foto, si no un .json plano. */
    fun shareBook(context: Context, book: RecipeBook, recipes: List<Recipe>) {
        shareRecipes(context, book.name, book, recipes)
    }

    fun shareBookAsPdf(context: Context, book: RecipeBook, recipes: List<Recipe>) {
        val document = PdfRecipeRenderer.renderBook(book.name, recipes)
        val file = File(exportsDir(context), sanitizeFileName(book.name) + ".pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        shareFile(context, file, "application/pdf")
    }

    /** Comparte un subconjunto arbitrario de recetas (selección múltiple), con la portada de [book] si se indica. */
    fun shareRecipes(context: Context, fileName: String, book: RecipeBook?, recipes: List<Recipe>) {
        val dto = LibraryExportDto(
            exportedAt = System.currentTimeMillis(),
            books = listOfNotNull(book?.let { bookExportDto(it) }),
            recipes = recipes.map { it.toExportDto() }
        )
        val content = json.encodeToString(dto)
        val hasPhotos = (book?.coverPhotoUri != null) || recipes.any { it.photos.isNotEmpty() }
        if (!hasPhotos) {
            val file = writeExportFile(context, sanitizeFileName(fileName) + ".json", content)
            shareFile(context, file, "application/json")
        } else {
            val photoSources = photoSourcesFor(book, recipes)
            val file = writeZipFile(context, sanitizeFileName(fileName) + ".zip", content, photoSources)
            shareFile(context, file, "application/zip")
        }
    }

    /** Copia de seguridad de toda la app: siempre en ZIP (con o sin fotos) para no tener que decidir el formato al elegir dónde guardarla. */
    suspend fun exportLibrary(context: Context, destination: Uri, books: List<RecipeBook>, recipes: List<Recipe>) = withContext(Dispatchers.IO) {
        val dto = LibraryExportDto(
            exportedAt = System.currentTimeMillis(),
            books = books.map { bookExportDto(it) },
            recipes = recipes.map { it.toExportDto() }
        )
        val content = json.encodeToString(dto)
        val photoSources = books.flatMap { book ->
            book.coverPhotoUri?.let { listOf("books/${book.uid}/cover.jpg" to it) } ?: emptyList()
        } + recipes.flatMap { recipe -> recipe.photos.mapIndexed { index, photo -> "recipes/${recipe.uid}/$index.jpg" to photo.uri } }
        context.contentResolver.openOutputStream(destination)?.use { output ->
            writeZipToStream(context, output, content, photoSources)
        }
    }

    /** Importa una receta individual exportada (ver [shareRecipe]): .json plano o .zip con fotos, migrando esquemas antiguos. */
    suspend fun importRecipe(context: Context, source: Uri): RecipeImportResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: return@withContext RecipeImportResult.Error("No se pudo abrir el archivo")
            val isZip = isZip(bytes)
            val entries = if (isZip) readZipEntries(bytes) else emptyMap<String, ByteArray>()
            val manifestText = if (isZip) {
                entries["manifest.json"]?.toString(Charsets.UTF_8)
                    ?: return@withContext RecipeImportResult.Error("El archivo ZIP no contiene manifest.json")
            } else {
                bytes.toString(Charsets.UTF_8)
            }
            val migrated = migrateJson(manifestText, recipeMigrations, CURRENT_RECIPE_SCHEMA_VERSION)
            val dto = json.decodeFromJsonElement(RecipeExportDto.serializer(), migrated)
            val photos = resolvePhotos(context, dto.uid, dto.photos, entries)
            RecipeImportResult.Success(dto, photos)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecipeImportResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
        }
    }

    /** Importa una copia de seguridad (.json plano o .zip con fotos), migrando esquemas antiguos. */
    suspend fun importLibrary(context: Context, source: Uri, repository: RecipeRepository): LibraryImportResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: return@withContext LibraryImportResult.Error("No se pudo abrir el archivo")
            val isZip = isZip(bytes)
            val entries = if (isZip) readZipEntries(bytes) else emptyMap<String, ByteArray>()
            val manifestText = if (isZip) {
                entries["manifest.json"]?.toString(Charsets.UTF_8)
                    ?: return@withContext LibraryImportResult.Error("El archivo ZIP no contiene manifest.json")
            } else {
                bytes.toString(Charsets.UTF_8)
            }
            val migrated = migrateJson(manifestText, libraryMigrations, CURRENT_LIBRARY_SCHEMA_VERSION)
            val dto = json.decodeFromJsonElement(LibraryExportDto.serializer(), migrated)
            val bookIdsByName = mutableMapOf<String, Long>()
            dto.recipes.forEach { recipeDto ->
                val bookId = bookIdsByName.getOrPut(recipeDto.recipeBookName) {
                    val bookMeta = dto.books.find { it.name == recipeDto.recipeBookName }
                    val coverUri = bookMeta?.coverPhotoFileName?.let { fileName ->
                        entries["books/${bookMeta.uid}/$fileName"]?.let { PhotoStorage.copyBytesToInternalStorage(context, it) }
                    }
                    repository.getOrCreateRecipeBookIdByName(recipeDto.recipeBookName, bookMeta?.uid, coverUri)
                }
                val photos = resolvePhotos(context, recipeDto.uid, recipeDto.photos, entries)
                val recipeId = repository.saveRecipe(recipeDto.toDraft(bookId, photos))
                applyHealthFromImport(repository, recipeId, recipeDto.health)
            }
            LibraryImportResult.Success(dto.recipes.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LibraryImportResult.Error(e.message ?: e::class.simpleName ?: "Error desconocido")
        }
    }

    private fun bookExportDto(book: RecipeBook) = BookExportDto(
        uid = book.uid,
        name = book.name,
        coverPhotoFileName = if (book.coverPhotoUri != null) "cover.jpg" else null
    )

    private fun photoSourcesFor(book: RecipeBook?, recipes: List<Recipe>): List<Pair<String, String>> {
        val bookCover = book?.coverPhotoUri?.let { listOf("books/${book.uid}/cover.jpg" to it) } ?: emptyList()
        val recipePhotos = recipes.flatMap { recipe -> recipe.photos.mapIndexed { index, photo -> "recipes/${recipe.uid}/$index.jpg" to photo.uri } }
        return bookCover + recipePhotos
    }

    private fun resolvePhotos(context: Context, recipeUid: String, photoDtos: List<PhotoExportDto>, entries: Map<String, ByteArray>): List<RecipePhoto> =
        photoDtos.mapNotNull { photoDto ->
            val bytes = entries["recipes/$recipeUid/${photoDto.fileName}"] ?: return@mapNotNull null
            val uri = PhotoStorage.copyBytesToInternalStorage(context, bytes) ?: return@mapNotNull null
            RecipePhoto(uri = uri, isCover = photoDto.isCover)
        }

    /** Aplica la valoración de salud embebida en una receta importada, si tenía alguna. */
    suspend fun applyHealthFromImport(repository: RecipeRepository, recipeId: Long, health: RecipeHealthDto?) {
        if (health == null) return
        val colorLevel = runCatching { HealthColorLevel.valueOf(health.colorLevel) }.getOrDefault(HealthColorLevel.YELLOW)
        repository.saveHealthRating(recipeId, colorLevel, health.description, health.fingerprint, health.analyzedAt)
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun writeZipToStream(context: Context, output: OutputStream, manifestJson: String, photoSources: List<Pair<String, String>>) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifestJson.toByteArray())
            zip.closeEntry()
            photoSources.forEach { (path, sourceUri) ->
                context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                    zip.putNextEntry(ZipEntry(path))
                    input.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }

    private fun writeZipFile(context: Context, fileName: String, manifestJson: String, photoSources: List<Pair<String, String>>): File {
        val file = File(exportsDir(context), fileName)
        file.outputStream().use { output -> writeZipToStream(context, output, manifestJson, photoSources) }
        return file
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
