package org.calamares.miga.data.export

import org.calamares.miga.data.remote.ShareUploadResult
import org.calamares.miga.data.remote.ShareLinkClient
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calamares.miga.L10n
import org.calamares.miga.MigaApp
import kotlinx.coroutines.flow.first
import org.calamares.miga.R
import org.calamares.miga.data.ai.AiKeepAlive
import org.calamares.miga.data.model.Recipe
import org.calamares.miga.data.model.RecipeBook
import java.io.File

/** What an export produces. */
enum class ExportFormat(val mimeType: String) {
    /** To read, print or send: see [RecipeExporter.writeBookPdf]. */
    PDF("application/pdf"),

    /** A ZIP with the photos to import into Miga later: see [RecipeExporter.writeMigaFile]. */
    MIGA_FILE("application/zip"),

    /** A picture of one recipe for social networks: see [RecipeImageCard]. */
    IMAGE("image/jpeg")
}

/** The options of the "Share" sheet (see ShareSheet), the same for books, recipes and selections. */
enum class ShareAction {
    /** A ".miga" file for someone else, merged on their phone. */
    SEND_FILE,

    /** The same, encrypted on a link that lasts 7 days. */
    LINK,
    PDF,

    /** A picture for social networks: one recipe only. */
    IMAGE,

    /** Plain text: one recipe only. */
    TEXT,

    /** A Miga file (.zip) to keep or import later, with favourites and ratings. */
    SAVE_COPY
}

/** What a link holds, for the message sent with it. */
enum class SharedKind { BOOK, BOOKS, RECIPE }

/** Where an export is; see [FileExports]. */
sealed interface FileExportState {
    data object Idle : FileExportState

    /**
     * Writing [title]: [current] of [total] pages (PDF) or photos (Miga file) once known, 0 before.
     */
    data class Running(
        val title: String,
        val format: ExportFormat,
        val current: Int = 0,
        val total: Int = 0,
        /** Sending a book link: [current] is then the percentage uploaded. */
        val uploading: Boolean = false
    ) : FileExportState

    /** A link ready to send ("Create link"): opens the share sheet with [url] at once. */
    data class LinkReady(val title: String, val url: String, val expiresAt: Long, val kind: SharedKind = SharedKind.BOOK) : FileExportState

    /** "Your name" is empty: asked before sharing [title], so whoever receives it sees who sent it. */
    data class NeedsName(val title: String) : FileExportState

    /** [autoShare]: "Share book" opens the share sheet as soon as the file is ready. */
    data class Ready(val title: String, val format: ExportFormat, val file: File, val autoShare: Boolean = false) : FileExportState

    data class Failed(val title: String, val message: String) : FileExportState
}

/**
 * Exports recipes, selections and books (as PDF or as a Miga file) outside of any screen, so
 * leaving the screen (or the app) does not cancel a long book export.
 *
 * While it runs, the ongoing notification shows the progress (see [AiKeepAlive.hold], which also
 * keeps the app alive in the background) and the app shows it in a dialog. When the file is ready
 * the app offers to save it to the user's files or share it, and a notification says so if the user
 * has left the app. One export at a time.
 */
object FileExports {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow<FileExportState>(FileExportState.Idle)
    val state: StateFlow<FileExportState> = _state

    val isRunning: Boolean get() = _state.value is FileExportState.Running

    /** [accent] colours the image (the current theme's primary colour); unused by the other formats. */
    fun exportRecipe(context: Context, recipe: Recipe, format: ExportFormat, accent: Int = 0xFF2F7A57.toInt()): Boolean =
        start(context, recipe.name, format) { appContext, onProgress ->
            when (format) {
                ExportFormat.PDF -> RecipeExporter.writeRecipePdf(appContext, recipe, onProgress)
                ExportFormat.MIGA_FILE -> RecipeExporter.writeMigaFile(appContext, recipe.name, emptyList(), listOf(recipe), singleRecipe = true, onProgress)
                ExportFormat.IMAGE -> RecipeExporter.writeRecipeImage(appContext, recipe, accent)
            }
        }

    /** [loadRecipes] runs inside the export, so loading a large book is part of the progress too. */
    fun exportBook(context: Context, book: RecipeBook, format: ExportFormat, loadRecipes: suspend () -> List<Recipe>): Boolean =
        start(context, book.name, format) { appContext, onProgress ->
            when (format) {
                ExportFormat.PDF -> RecipeExporter.writeBookPdf(appContext, book, loadRecipes(), onProgress)
                ExportFormat.MIGA_FILE -> RecipeExporter.writeMigaFile(appContext, book.name, listOf(book), loadRecipes(), singleRecipe = false, onProgress)
                ExportFormat.IMAGE -> error("A book is not exported as an image")
            }
        }

    /**
     * "Send to someone": writes [books] (one or several) as a ".miga" file for someone else (see
     * RecipeExporter.writeMigaFile with forSharing) and opens the share sheet as soon as it is
     * ready. Signed with "Your name", asked first when it is empty (see [NeedsName]).
     */
    fun shareBooks(context: Context, books: List<RecipeBook>, loadRecipes: suspend () -> List<Recipe>): Boolean {
        val title = booksTitle(books)
        return withSenderName(context, title) { sender ->
            start(context, title, ExportFormat.MIGA_FILE, autoShare = true) { appContext, onProgress ->
                RecipeExporter.writeMigaFile(appContext, title, books, loadRecipes(), singleRecipe = false, onPhoto = onProgress, forSharing = true, sharedBy = sender)
            }
        }
    }

    /** One book by its name; several as "3 books". */
    private fun booksTitle(books: List<RecipeBook>): String =
        books.singleOrNull()?.name ?: L10n.str(R.string.n_books, books.size)

    /** "Share recipe": one recipe as a ".miga" file, imported into a book chosen by whoever receives it. */
    fun shareRecipe(context: Context, recipe: Recipe): Boolean =
        withSenderName(context, recipe.name) { sender ->
            start(context, recipe.name, ExportFormat.MIGA_FILE, autoShare = true) { appContext, onProgress ->
                RecipeExporter.writeMigaFile(appContext, recipe.name, emptyList(), listOf(recipe), singleRecipe = true, onPhoto = onProgress, forSharing = true, sharedBy = sender)
            }
        }

    /**
     * "Create link": writes [books] like [shareBooks], encrypts them with a new key, uploads them
     * to miga-share.calamares.org and offers the link (the key goes only in the link's fragment).
     */
    fun shareBooksLink(context: Context, books: List<RecipeBook>, loadRecipes: suspend () -> List<Recipe>): Boolean {
        val title = booksTitle(books)
        val kind = if (books.size == 1) SharedKind.BOOK else SharedKind.BOOKS
        return withSenderName(context, title) { sender ->
            startLink(context, title, kind) { appContext, onPhoto ->
                RecipeExporter.writeMigaFile(appContext, title, books, loadRecipes(), singleRecipe = false, onPhoto = onPhoto, forSharing = true, sharedBy = sender)
            }
        }
    }

    /** "Create link" of one recipe, like [shareBooksLink]. */
    fun shareRecipeLink(context: Context, recipe: Recipe): Boolean =
        withSenderName(context, recipe.name) { sender ->
            startLink(context, recipe.name, SharedKind.RECIPE) { appContext, onPhoto ->
                RecipeExporter.writeMigaFile(appContext, recipe.name, emptyList(), listOf(recipe), singleRecipe = true, onPhoto = onPhoto, forSharing = true, sharedBy = sender)
            }
        }

    /** What to share once the sender's name is known (see [withSenderName]). */
    private var pendingShare: ((String?) -> Unit)? = null

    /**
     * Runs [share] with "Your name", or first asks for it ([FileExportState.NeedsName]) when it is
     * empty. False, without doing anything, when an export is running.
     */
    private fun withSenderName(context: Context, title: String, share: (String?) -> Unit): Boolean {
        if (isRunning) return false
        val settings = (context.applicationContext as MigaApp).settingsRepository
        scope.launch {
            val name = settings.observeShoppingAuthor().first().trim()
            if (name.isNotEmpty()) {
                share(name)
            } else {
                pendingShare = share
                _state.value = FileExportState.NeedsName(title)
            }
        }
        return true
    }

    /** Answer to [FileExportState.NeedsName]: saves [name] as "Your name" (when given) and shares. */
    fun continueWithName(context: Context, name: String?) {
        val share = pendingShare ?: return
        pendingShare = null
        _state.value = FileExportState.Idle
        val settings = (context.applicationContext as MigaApp).settingsRepository
        val trimmed = name?.trim()?.take(60)?.ifEmpty { null }
        scope.launch {
            if (trimmed != null) settings.setShoppingAuthor(trimmed)
            share(trimmed)
        }
    }

    private fun startLink(
        context: Context,
        title: String,
        kind: SharedKind,
        write: suspend (Context, onPhoto: (Int, Int) -> Unit) -> File
    ) {
        if (isRunning) return
        val appContext = context.applicationContext
        val format = ExportFormat.MIGA_FILE
        _state.value = FileExportState.Running(title, format)
        val notificationTitle = L10n.str(R.string.share_link_preparing_x, title)
        val icon = R.drawable.ic_notification_export
        job = scope.launch {
            try {
                val result = AiKeepAlive.hold(notificationTitle, icon = icon) {
                    status(L10n.str(R.string.pdf_preparing))
                    val coroutineJob = coroutineContext.job
                    val file = write(appContext) { current, total ->
                        coroutineJob.ensureActive()
                        _state.value = FileExportState.Running(title, format, current, total)
                        progress(current, total)
                        status(progressText(format, current, total))
                    }
                    val encrypted = File(file.parentFile, file.name + ".enc")
                    val key = withContext(Dispatchers.IO) { ShareLinkClient.encrypt(file, encrypted) }
                    try {
                        ShareLinkClient.upload(encrypted, key) { sent, total ->
                            coroutineJob.ensureActive()
                            val percent = (sent * 100 / total.coerceAtLeast(1)).toInt()
                            _state.value = FileExportState.Running(title, format, percent, 100, uploading = true)
                            progress(percent, 100)
                            status(L10n.str(R.string.share_link_uploading_x, percent))
                        }
                    } finally {
                        encrypted.delete()
                        file.delete()
                    }
                }
                _state.value = when (result) {
                    is ShareUploadResult.Success -> FileExportState.LinkReady(title, result.link.url, result.expiresAt, kind)
                    is ShareUploadResult.Error -> FileExportState.Failed(
                        title,
                        L10n.str(
                            when (result.code) {
                                413 -> R.string.share_link_too_large
                                429 -> R.string.share_link_too_many
                                else -> R.string.share_link_failed
                            }
                        )
                    )
                }
            } catch (e: CancellationException) {
                _state.value = FileExportState.Idle
            } catch (e: Throwable) {
                _state.value = FileExportState.Failed(title, e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }

    /**
     * Runs a "Share" option for one recipe. False, without doing anything, when another export is
     * running. [accent] colours the picture.
     */
    fun shareRecipeAs(context: Context, recipe: Recipe, action: ShareAction, accent: Int): Boolean = when (action) {
        ShareAction.SEND_FILE -> shareRecipe(context, recipe)
        ShareAction.LINK -> shareRecipeLink(context, recipe)
        ShareAction.PDF -> exportRecipe(context, recipe, ExportFormat.PDF)
        ShareAction.IMAGE -> exportRecipe(context, recipe, ExportFormat.IMAGE, accent)
        ShareAction.TEXT -> { RecipeExporter.shareAsText(context, recipe); true }
        ShareAction.SAVE_COPY -> exportRecipe(context, recipe, ExportFormat.MIGA_FILE)
    }

    /**
     * Runs a "Share" option for one or several books ([loadRecipes] loads all their recipes). PDF and
     * the copy are for one book; a picture or text, never.
     */
    fun shareBooksAs(context: Context, books: List<RecipeBook>, action: ShareAction, loadRecipes: suspend () -> List<Recipe>): Boolean =
        when (action) {
            ShareAction.SEND_FILE -> shareBooks(context, books, loadRecipes)
            ShareAction.LINK -> shareBooksLink(context, books, loadRecipes)
            ShareAction.PDF -> exportBook(context, books.single(), ExportFormat.PDF, loadRecipes)
            ShareAction.SAVE_COPY -> exportBook(context, books.single(), ExportFormat.MIGA_FILE, loadRecipes)
            ShareAction.IMAGE, ShareAction.TEXT -> error("A book is shared as a file, a link, a PDF or a copy")
        }

    /** Several recipes of [book] chosen by the user, under [title] ("Selected recipes"). */
    fun exportSelection(context: Context, title: String, book: RecipeBook?, recipes: List<Recipe>, format: ExportFormat): Boolean =
        start(context, title, format) { appContext, onProgress ->
            when (format) {
                ExportFormat.PDF -> RecipeExporter.writeRecipesPdf(appContext, title, recipes, onProgress)
                ExportFormat.MIGA_FILE -> RecipeExporter.writeMigaFile(appContext, title, listOfNotNull(book), recipes, singleRecipe = false, onProgress)
                ExportFormat.IMAGE -> error("A selection is not exported as an image")
            }
        }

    /** False, without starting anything, when another export is still running. */
    private fun start(
        context: Context,
        title: String,
        format: ExportFormat,
        autoShare: Boolean = false,
        write: suspend (Context, onProgress: (Int, Int) -> Unit) -> File
    ): Boolean {
        if (isRunning) return false
        val appContext = context.applicationContext
        _state.value = FileExportState.Running(title, format)
        val notificationTitle = when (format) {
            ExportFormat.PDF -> L10n.str(R.string.pdf_exporting_x, title)
            ExportFormat.MIGA_FILE, ExportFormat.IMAGE -> L10n.str(R.string.miga_file_exporting_x, title)
        }
        val icon = when (format) {
            ExportFormat.PDF -> R.drawable.ic_notification_pdf
            ExportFormat.MIGA_FILE, ExportFormat.IMAGE -> R.drawable.ic_notification_export
        }
        job = scope.launch {
            try {
                val file = AiKeepAlive.hold(notificationTitle, icon = icon) {
                    status(L10n.str(R.string.pdf_preparing))
                    val coroutineJob = coroutineContext.job
                    write(appContext) { current, total ->
                        // Called from the writing thread before each page or photo: cancelling stops it here.
                        coroutineJob.ensureActive()
                        _state.value = FileExportState.Running(title, format, current, total)
                        progress(current, total)
                        status(progressText(format, current, total))
                    }
                }
                _state.value = FileExportState.Ready(title, format, file, autoShare)
                AiKeepAlive.announceIfInBackground(L10n.str(R.string.export_ready_notification_x, title), icon = icon)
            } catch (e: CancellationException) {
                _state.value = FileExportState.Idle
            } catch (e: Throwable) {
                // OutOfMemoryError included: a huge book must fail with a message, not crash the app.
                _state.value = FileExportState.Failed(title, e.message ?: e::class.simpleName.orEmpty())
            }
        }
        return true
    }

    /** "Page 3 of 40" or "Photo 3 of 40". */
    fun progressText(format: ExportFormat, current: Int, total: Int): String = when (format) {
        ExportFormat.PDF -> L10n.str(R.string.pdf_page_x_of_y, current, total)
        ExportFormat.MIGA_FILE, ExportFormat.IMAGE -> L10n.str(R.string.export_photo_x_of_y, current, total)
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = FileExportState.Idle
    }

    /** Closes the result (ready or failed). The file stays in the cache until the next export or a cache clean-up. */
    fun dismiss() {
        if (!isRunning) _state.value = FileExportState.Idle
    }

    /** Sends [link] as text (WhatsApp, Telegram, mail…), with when it expires. */
    fun shareLink(context: Context, link: FileExportState.LinkReady) {
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, L10n.locale()).format(java.util.Date(link.expiresAt))
        val message = when (link.kind) {
            SharedKind.BOOK -> R.string.share_link_message_x_x_x
            SharedKind.BOOKS -> R.string.share_books_link_message_x_x_x
            SharedKind.RECIPE -> R.string.share_recipe_link_message_x_x_x
        }
        val text = L10n.str(message, link.title, link.url, date)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, L10n.str(R.string.share_link)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(context: Context, ready: FileExportState.Ready) {
        RecipeExporter.shareExported(context, ready.file, ready.format.mimeType)
    }

    /** Copies the exported [file] to [destination], picked by the user with the system file picker. */
    suspend fun saveTo(context: Context, file: File, destination: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(destination)?.use { output ->
                file.inputStream().use { it.copyTo(output) }
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            coroutineContext.ensureActive()
            false
        }
    }
}
