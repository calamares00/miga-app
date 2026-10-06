package org.calamares.miga.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.calamares.miga.L10n
import org.calamares.miga.R
import org.calamares.miga.data.local.PhotoStorage
import org.calamares.miga.data.model.HealthColorLevel
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBook
import org.calamares.miga.data.model.RecipePhoto
import org.calamares.miga.data.model.ShoppingListGroup
import org.calamares.miga.data.model.ShoppingStore
import org.calamares.miga.data.model.ShoppingTemplate
import org.calamares.miga.data.model.displayCategoryName
import org.calamares.miga.data.model.formatIngredientText
import org.calamares.miga.data.model.formatQuantity
import org.calamares.miga.data.repository.RecipeRepository
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val MANIFEST = "manifest.json"

sealed interface RecipeImportResult {
    data class Success(val recipe: RecipeExportDto, val photos: List<RecipePhoto> = emptyList()) : RecipeImportResult
    data class Error(val reason: String) : RecipeImportResult
}

sealed interface LibraryImportResult {
    data class Success(val count: Int) : LibraryImportResult
    data class Error(val reason: String) : LibraryImportResult
}

sealed interface LibraryImportParseResult {
    /** A validated backup. Photos are not read yet: they are streamed from [source] on import. */
    data class Success(val dto: LibraryExportDto, val source: Uri, val isZip: Boolean) : LibraryImportParseResult
    data class Error(val reason: String) : LibraryImportParseResult
}

sealed interface PackImportResult {
    data class Success(val bookId: Long) : PackImportResult
    data class Error(val reason: String) : PackImportResult
}

/**
 * Import and export of recipes, books and full backups (JSON or ZIP with photos), plus sharing as
 * text and PDF.
 *
 * ZIP layout: `manifest.json` (a [RecipeExportDto] or a [LibraryExportDto]), book covers in
 * `books/<uid>/` and recipe photos in `recipes/<uid>/`.
 */
object RecipeExporter {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        // Otherwise fields equal to their default (such as "version") would be left out of the file.
        encodeDefaults = true
    }

    /**
     * Migrations of a single recipe JSON, indexed by source version: entry N turns version N into
     * version N + 1. Most steps only added optional fields with a default, so they do nothing.
     */
    private val recipeMigrations: List<(JsonObject) -> JsonObject> = listOf(
        { obj -> obj },
        { obj -> addUidIfMissing(obj) }, // v1 -> v2 added "uid".
        { obj -> obj },
        { obj -> obj },
        { obj -> obj }
    )

    /** Same as [recipeMigrations] for a full backup ([LibraryExportDto]). */
    private val libraryMigrations: List<(JsonObject) -> JsonObject> = listOf(
        { obj -> obj },
        { obj ->
            // v1 -> v2: every nested recipe needs its own "uid".
            val recipesArray = obj["recipes"] as? JsonArray ?: JsonArray(emptyList())
            JsonObject(obj + ("recipes" to JsonArray(recipesArray.map { addUidIfMissing(it.jsonObject) })))
        },
        { obj -> obj },
        { obj -> obj },
        { obj -> obj }
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
        context.startActivity(Intent.createChooser(intent, L10n.str(R.string.share_recipe)))
    }

    fun shareShoppingListAsText(context: Context, groups: List<ShoppingListGroup>) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, L10n.str(R.string.shopping_list_2))
            putExtra(Intent.EXTRA_TEXT, formatShoppingListAsText(groups))
        }
        context.startActivity(Intent.createChooser(intent, L10n.str(R.string.share_list)))
    }

    /** Exports one recipe: a ZIP when it has photos, a plain JSON file otherwise. */
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

    /** Renders and shares a recipe as PDF; suspending because decoding the photos is slow I/O. */
    suspend fun shareAsPdf(context: Context, recipe: Recipe) = withContext(Dispatchers.IO) {
        val document = PdfRecipeRenderer.render(recipe)
        val file = File(exportsDir(context), sanitizeFileName(recipe.name) + ".pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        shareFile(context, file, "application/pdf")
    }

    /** Exports a whole book: a ZIP when the book or any recipe has photos, a plain JSON otherwise. */
    fun shareBook(context: Context, book: RecipeBook, recipes: List<Recipe>) {
        shareRecipes(context, book.name, book, recipes)
    }

    /** Renders and shares a whole book as PDF (cover, contents by category and recipes). */
    suspend fun shareBookAsPdf(context: Context, book: RecipeBook, recipes: List<Recipe>) = withContext(Dispatchers.IO) {
        val document = PdfRecipeRenderer.renderBook(book.name, book.coverPhotoUri, recipes)
        val file = File(exportsDir(context), sanitizeFileName(book.name) + ".pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        shareFile(context, file, "application/pdf")
    }

    /** Shares any set of recipes (multi-selection), including the cover of [book] when given. */
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
            val file = writeZipFile(context, sanitizeFileName(fileName) + ".zip", content, photoSourcesFor(book, recipes))
            shareFile(context, file, "application/zip")
        }
    }

    /** Full backup of the app, always as a ZIP so the format does not depend on the content. */
    suspend fun exportLibrary(
        context: Context,
        destination: Uri,
        books: List<RecipeBook>,
        recipes: List<Recipe>,
        templates: List<ShoppingTemplate> = emptyList(),
        stores: List<ShoppingStore> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val dto = LibraryExportDto(
            exportedAt = System.currentTimeMillis(),
            books = books.map { bookExportDto(it) },
            recipes = recipes.map { it.toExportDto() },
            shoppingTemplates = templates.filter { !it.isPredefined }.map { TemplateBackupDto(it.name, it.items) },
            shoppingStores = stores.map { StoreBackupDto(it.name, it.argb, it.aisleOrder) }
        )
        val content = json.encodeToString(dto)
        val photoSources = books.flatMap { book ->
            book.coverPhotoUri?.let { listOf("books/${book.uid}/cover.jpg" to it) } ?: emptyList()
        } + recipes.flatMap { recipe -> recipe.photos.mapIndexed { index, photo -> "recipes/${recipe.uid}/$index.jpg" to photo.uri } }
        context.contentResolver.openOutputStream(destination)?.use { output ->
            writeZipToStream(context, output, content, photoSources)
        }
    }

    /** Imports a single exported recipe (see [shareRecipe]): plain JSON or ZIP with photos. */
    suspend fun importRecipe(context: Context, source: Uri): RecipeImportResult = withContext(Dispatchers.IO) {
        try {
            val manifest = readManifest(context, source)
                ?: return@withContext RecipeImportResult.Error(L10n.str(R.string.couldnt_open_file))
            val text = manifest.text
                ?: return@withContext RecipeImportResult.Error(L10n.str(R.string.zip_file_doesnt_contain_manifest))
            val dto = json.decodeFromJsonElement(
                RecipeExportDto.serializer(),
                migrateJson(text, recipeMigrations, CURRENT_RECIPE_SCHEMA_VERSION)
            )
            val extracted = if (manifest.isZip) extractZipPhotos(context, source, photoPaths(listOf(dto))) else emptyMap()
            RecipeImportResult.Success(dto, resolvePhotos(dto.uid, dto.photos, extracted))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecipeImportResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.unknown_error))
        }
    }

    /** Reads and validates a backup (JSON or ZIP) without writing anything to the database. */
    suspend fun parseLibraryImport(context: Context, source: Uri): LibraryImportParseResult = withContext(Dispatchers.IO) {
        try {
            val manifest = readManifest(context, source)
                ?: return@withContext LibraryImportParseResult.Error(L10n.str(R.string.couldnt_open_file))
            val text = manifest.text
                ?: return@withContext LibraryImportParseResult.Error(L10n.str(R.string.zip_file_doesnt_contain_manifest))
            val dto = json.decodeFromJsonElement(
                LibraryExportDto.serializer(),
                migrateJson(text, libraryMigrations, CURRENT_LIBRARY_SCHEMA_VERSION)
            )
            LibraryImportParseResult.Success(dto, source, manifest.isZip)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LibraryImportParseResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.file_isnt_valid_backup))
        }
    }

    /** Writes a backup already validated by [parseLibraryImport] into the database. */
    suspend fun importParsedLibrary(
        context: Context,
        parsed: LibraryImportParseResult.Success,
        repository: RecipeRepository
    ): LibraryImportResult = withContext(Dispatchers.IO) {
        try {
            val dto = parsed.dto
            val wantedPaths = photoPaths(dto.recipes) + dto.books.mapNotNull { book ->
                book.coverPhotoFileName?.let { "books/${book.uid}/$it" }
            }
            val extracted = if (parsed.isZip) extractZipPhotos(context, parsed.source, wantedPaths) else emptyMap()
            fun coverOf(book: BookExportDto?): String? =
                book?.coverPhotoFileName?.let { extracted["books/${book.uid}/$it"] }

            val bookIdsByName = mutableMapOf<String, Long>()
            dto.recipes.forEach { recipeDto ->
                val bookId = bookIdsByName.getOrPut(recipeDto.recipeBookName) {
                    val bookMeta = dto.books.find { it.name == recipeDto.recipeBookName }
                    repository.getOrCreateRecipeBookIdByName(recipeDto.recipeBookName, bookMeta?.uid, coverOf(bookMeta))
                }
                val photos = resolvePhotos(recipeDto.uid, recipeDto.photos, extracted)
                val recipeId = repository.saveRecipe(recipeDto.toDraft(bookId, photos))
                applyHealthFromImport(repository, recipeId, recipeDto.health)
                applyNutritionFromImport(repository, recipeId, recipeDto.nutrition)
                if (recipeDto.rating != null) repository.setRating(recipeId, recipeDto.rating)
            }
            // Books without recipes are restored too.
            dto.books.filter { it.name !in bookIdsByName }.forEach { bookMeta ->
                bookIdsByName[bookMeta.name] = repository.getOrCreateRecipeBookIdByName(bookMeta.name, bookMeta.uid, coverOf(bookMeta))
            }
            // Templates and supermarkets are only added when there is none with the same name.
            val existingTemplates = repository.observeShoppingTemplates().first().map { it.name.trim().lowercase() }.toSet()
            dto.shoppingTemplates.filter { it.name.trim().lowercase() !in existingTemplates }.forEach {
                repository.saveShoppingTemplate(it.name, it.items)
            }
            val existingStores = repository.observeShoppingStores().first().map { it.name.trim().lowercase() }.toSet()
            dto.shoppingStores.filter { it.name.trim().lowercase() !in existingStores }.forEach {
                repository.saveShoppingStore(ShoppingStore(0L, it.name, it.argb, it.aisleOrder))
            }
            LibraryImportResult.Success(dto.recipes.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LibraryImportResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.unknown_error))
        }
    }

    /**
     * Installs or updates a pack downloaded from the catalogue (see PacksCatalogClient). Same ZIP
     * format as [shareBook], but the book is identified by [packId] and updated in place by
     * [RecipeRepository.installOrUpdatePack] instead of being matched by name.
     */
    suspend fun importPackFromBytes(
        context: Context,
        zipBytes: ByteArray,
        repository: RecipeRepository,
        packId: String,
        packVersion: Int
    ): PackImportResult = withContext(Dispatchers.IO) {
        try {
            if (!isZip(zipBytes)) return@withContext PackImportResult.Error(L10n.str(R.string.downloaded_file_isnt_valid_zip))
            val manifestText = ZipInputStream(ByteArrayInputStream(zipBytes)).use { readManifestEntry(it) }
                ?: return@withContext PackImportResult.Error(L10n.str(R.string.pack_doesnt_contain_manifest_json))
            val dto = json.decodeFromJsonElement(
                LibraryExportDto.serializer(),
                migrateJson(manifestText, libraryMigrations, CURRENT_LIBRARY_SCHEMA_VERSION)
            )
            val bookMeta = dto.books.firstOrNull()
                ?: return@withContext PackImportResult.Error(L10n.str(R.string.pack_doesnt_include_book_details))
            val coverPath = bookMeta.coverPhotoFileName?.let { "books/${bookMeta.uid}/$it" }
            val extracted = ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                extractEntries(context, zip, photoPaths(dto.recipes) + listOfNotNull(coverPath))
            }
            val bookId = repository.installOrUpdatePack(
                packId = packId,
                packVersion = packVersion,
                bookName = bookMeta.name,
                bookUid = bookMeta.uid,
                bookCoverUri = coverPath?.let { extracted[it] },
                recipes = dto.recipes,
                photosByUid = dto.recipes.associate { it.uid to resolvePhotos(it.uid, it.photos, extracted) }
            )
            PackImportResult.Success(bookId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PackImportResult.Error(e.message ?: e::class.simpleName ?: L10n.str(R.string.unknown_error))
        }
    }

    /** Stores the AI health rating included in an imported recipe, if any. */
    suspend fun applyHealthFromImport(repository: RecipeRepository, recipeId: Long, health: RecipeHealthDto?) {
        if (health == null) return
        val colorLevel = runCatching { HealthColorLevel.valueOf(health.colorLevel) }.getOrDefault(HealthColorLevel.YELLOW)
        repository.saveHealthRating(recipeId, colorLevel, health.description, health.fingerprint, health.analyzedAt)
    }

    /** Stores the AI nutrition estimate included in an imported recipe, if any. */
    suspend fun applyNutritionFromImport(repository: RecipeRepository, recipeId: Long, nutrition: RecipeNutritionDto?) {
        if (nutrition == null) return
        repository.saveNutritionInfo(
            recipeId,
            nutrition.caloriesPerServing,
            nutrition.proteinGrams,
            nutrition.carbsGrams,
            nutrition.fatGrams,
            nutrition.fingerprint,
            nutrition.analyzedAt
        )
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

    private fun photoPaths(recipes: List<RecipeExportDto>): List<String> =
        recipes.flatMap { recipe -> recipe.photos.map { "recipes/${recipe.uid}/${it.fileName}" } }

    /** Maps the photos of a recipe to the files extracted from the ZIP, skipping missing ones. */
    private fun resolvePhotos(recipeUid: String, photoDtos: List<PhotoExportDto>, extracted: Map<String, String>): List<RecipePhoto> =
        photoDtos.mapNotNull { photoDto ->
            extracted["recipes/$recipeUid/${photoDto.fileName}"]?.let { RecipePhoto(uri = it, isCover = photoDto.isCover) }
        }

    private class Manifest(val text: String?, val isZip: Boolean)

    /** Reads the JSON of a plain file, or the manifest of a ZIP, streaming instead of loading the whole file. */
    private fun readManifest(context: Context, source: Uri): Manifest? {
        val input = context.contentResolver.openInputStream(source) ?: return null
        return BufferedInputStream(input).use { buffered ->
            buffered.mark(4)
            val signature = ByteArray(2)
            val read = buffered.read(signature)
            buffered.reset()
            if (read == 2 && isZip(signature)) {
                Manifest(ZipInputStream(buffered).let { readManifestEntry(it) }, isZip = true)
            } else {
                Manifest(buffered.readBytes().toString(Charsets.UTF_8), isZip = false)
            }
        }
    }

    private fun readManifestEntry(zip: ZipInputStream): String? {
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name == MANIFEST) return zip.readBytes().toString(Charsets.UTF_8)
            entry = zip.nextEntry
        }
        return null
    }

    /** Streams the ZIP at [source] and copies the entries in [wanted] to internal storage. */
    private fun extractZipPhotos(context: Context, source: Uri, wanted: Collection<String>): Map<String, String> {
        if (wanted.isEmpty()) return emptyMap()
        val input = context.contentResolver.openInputStream(source) ?: return emptyMap()
        return ZipInputStream(BufferedInputStream(input)).use { extractEntries(context, it, wanted) }
    }

    /**
     * Copies the entries listed in [wanted] to internal storage and returns their new uris by ZIP
     * path. Entry names are only used as map keys, never as file paths, so a malicious ZIP cannot
     * write outside the photos folder.
     */
    private fun extractEntries(context: Context, zip: ZipInputStream, wanted: Collection<String>): Map<String, String> {
        val wantedSet = wanted.toSet()
        val result = mutableMapOf<String, String>()
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            if (!entry.isDirectory && name in wantedSet) {
                PhotoStorage.copyStreamToInternalStorage(context, NonClosingInputStream(zip))?.let { result[name] = it }
            }
            entry = zip.nextEntry
        }
        return result
    }

    /** Lets a ZIP entry be copied with `use {}` helpers without closing the whole ZipInputStream. */
    private class NonClosingInputStream(private val delegate: InputStream) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() = Unit
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun writeZipToStream(context: Context, output: OutputStream, manifestJson: String, photoSources: List<Pair<String, String>>) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifestJson.toByteArray())
            zip.closeEntry()
            photoSources.forEach { (path, sourceUri) ->
                // A photo deleted outside the app must not abort the whole export.
                val input = runCatching { context.contentResolver.openInputStream(Uri.parse(sourceUri)) }.getOrNull()
                input?.use {
                    zip.putNextEntry(ZipEntry(path))
                    it.copyTo(zip)
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
        context.startActivity(Intent.createChooser(intent, L10n.str(R.string.share)))
    }

    /** Keeps letters (accents included), digits, dashes and underscores; spaces become underscores. */
    private fun sanitizeFileName(name: String): String =
        name.trim()
            .replace(Regex("[^\\p{L}\\p{N}_ -]"), "")
            .trim()
            .replace(" ", "_")
            .take(60)
            .ifBlank { "recipe" }

    private fun formatShoppingListAsText(groups: List<ShoppingListGroup>): String = buildString {
        appendLine(L10n.str(R.string.shopping_list))
        groups.forEach { group ->
            appendLine()
            appendLine(displayCategoryName(group.categoryName).uppercase())
            group.items.forEach { item ->
                append(if (item.checked) "[x] " else "[ ] ")
                appendLine(listOfNotNull(item.quantity?.let { formatQuantity(it) }, item.unit, item.name).joinToString(" "))
            }
        }
    }

    private fun formatRecipeAsText(recipe: Recipe): String = buildString {
        appendLine(recipe.name)
        appendLine("—".repeat(recipe.name.length.coerceAtMost(40)))
        append(recipe.difficulty.label)
        recipe.categoryName?.let { append(" · ").append(it) }
        recipe.totalTimeMinutes?.let { append(" · ").append(it).append(" min") }
        appendLine(L10n.str(R.string.servings_x, recipe.servings))
        if (recipe.utensils.isNotEmpty()) appendLine(L10n.str(R.string.utensils) + ": " + recipe.utensils.joinToString(", "))
        appendLine()
        appendLine(L10n.str(R.string.ingredients).uppercase())
        recipe.ingredientGroups.forEach { group ->
            if (group.ingredients.isNotEmpty()) {
                if (group.name != null) appendLine(group.name.uppercase())
                group.ingredients.forEach { appendLine("- " + formatIngredientText(it.name, it.quantity, it.unit)) }
            }
        }
        appendLine()
        appendLine(L10n.str(R.string.method).uppercase())
        recipe.stepGroups.forEach { group ->
            if (group.instructions.isNotEmpty()) {
                if (group.name != null) appendLine(group.name.uppercase())
                group.instructions.forEachIndexed { index, instruction -> appendLine("${index + 1}. $instruction") }
            }
        }
        if (recipe.notes.isNotBlank()) {
            appendLine()
            appendLine(L10n.str(R.string.notes).uppercase())
            appendLine(recipe.notes)
        }
        if (recipe.source.isNotBlank()) {
            appendLine()
            appendLine(L10n.str(R.string.source) + ": " + recipe.source)
        }
    }
}
