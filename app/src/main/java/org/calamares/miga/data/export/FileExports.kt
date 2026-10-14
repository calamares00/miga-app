package org.calamares.miga.data.export

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

/** Where an export is; see [FileExports]. */
sealed interface FileExportState {
    data object Idle : FileExportState

    /**
     * Writing [title]: [current] of [total] pages (PDF) or photos (Miga file) once known, 0 before.
     */
    data class Running(val title: String, val format: ExportFormat, val current: Int = 0, val total: Int = 0) : FileExportState

    data class Ready(val title: String, val format: ExportFormat, val file: File) : FileExportState

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
                ExportFormat.MIGA_FILE -> RecipeExporter.writeMigaFile(appContext, recipe.name, null, listOf(recipe), singleRecipe = true, onProgress)
                ExportFormat.IMAGE -> RecipeExporter.writeRecipeImage(appContext, recipe, accent)
            }
        }

    /** [loadRecipes] runs inside the export, so loading a large book is part of the progress too. */
    fun exportBook(context: Context, book: RecipeBook, format: ExportFormat, loadRecipes: suspend () -> List<Recipe>): Boolean =
        start(context, book.name, format) { appContext, onProgress ->
            when (format) {
                ExportFormat.PDF -> RecipeExporter.writeBookPdf(appContext, book, loadRecipes(), onProgress)
                ExportFormat.MIGA_FILE -> RecipeExporter.writeMigaFile(appContext, book.name, book, loadRecipes(), singleRecipe = false, onProgress)
                ExportFormat.IMAGE -> error("A book is not exported as an image")
            }
        }

    /** Several recipes of [book] chosen by the user, under [title] ("Selected recipes"). */
    fun exportSelection(context: Context, title: String, book: RecipeBook?, recipes: List<Recipe>, format: ExportFormat): Boolean =
        start(context, title, format) { appContext, onProgress ->
            when (format) {
                ExportFormat.PDF -> RecipeExporter.writeRecipesPdf(appContext, title, recipes, onProgress)
                ExportFormat.MIGA_FILE -> RecipeExporter.writeMigaFile(appContext, title, book, recipes, singleRecipe = false, onProgress)
                ExportFormat.IMAGE -> error("A selection is not exported as an image")
            }
        }

    /** False, without starting anything, when another export is still running. */
    private fun start(
        context: Context,
        title: String,
        format: ExportFormat,
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
                _state.value = FileExportState.Ready(title, format, file)
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
