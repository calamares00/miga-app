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

/** Where a PDF export is; see [PdfExports]. */
sealed interface PdfExportState {
    data object Idle : PdfExportState

    /** Rendering [title]; [page] of [totalPages] once the layout is known (0 before). */
    data class Running(val title: String, val page: Int = 0, val totalPages: Int = 0) : PdfExportState

    data class Ready(val title: String, val file: File) : PdfExportState

    data class Failed(val title: String, val message: String) : PdfExportState
}

/**
 * Exports recipes and books as PDF outside of any screen, so leaving the screen (or the app) does
 * not cancel a long book export.
 *
 * While it runs, the ongoing notification shows the progress page by page (see
 * [AiKeepAlive.hold], which also keeps the app alive in the background) and the app shows it in a
 * dialog. When the file is ready the app offers to save it to the user's files or share it, and a
 * notification says so if the user has left the app. One export at a time.
 */
object PdfExports {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _state = MutableStateFlow<PdfExportState>(PdfExportState.Idle)
    val state: StateFlow<PdfExportState> = _state

    val isRunning: Boolean get() = _state.value is PdfExportState.Running

    fun exportRecipe(context: Context, recipe: Recipe): Boolean = start(context, recipe.name) { appContext, onPage ->
        RecipeExporter.writeRecipePdf(appContext, recipe, onPage)
    }

    /** [loadRecipes] runs inside the export, so loading a large book is part of the progress too. */
    fun exportBook(context: Context, book: RecipeBook, loadRecipes: suspend () -> List<Recipe>): Boolean =
        start(context, book.name) { appContext, onPage ->
            RecipeExporter.writeBookPdf(appContext, book, loadRecipes(), onPage)
        }

    /** False, without starting anything, when another export is still running. */
    private fun start(
        context: Context,
        title: String,
        write: suspend (Context, onPage: (Int, Int) -> Unit) -> File
    ): Boolean {
        if (isRunning) return false
        val appContext = context.applicationContext
        _state.value = PdfExportState.Running(title)
        job = scope.launch {
            try {
                val file = AiKeepAlive.hold(L10n.str(R.string.pdf_exporting_x, title), icon = R.drawable.ic_notification_pdf) {
                    status(L10n.str(R.string.pdf_preparing))
                    val coroutineJob = coroutineContext.job
                    write(appContext) { page, total ->
                        // Called from the rendering thread before each page: cancelling stops it here.
                        coroutineJob.ensureActive()
                        _state.value = PdfExportState.Running(title, page, total)
                        progress(page, total)
                        status(L10n.str(R.string.pdf_page_x_of_y, page, total))
                    }
                }
                _state.value = PdfExportState.Ready(title, file)
                AiKeepAlive.announceIfInBackground(L10n.str(R.string.pdf_ready_notification_x, title), icon = R.drawable.ic_notification_pdf)
            } catch (e: CancellationException) {
                _state.value = PdfExportState.Idle
            } catch (e: Throwable) {
                // OutOfMemoryError included: a huge book must fail with a message, not crash the app.
                _state.value = PdfExportState.Failed(title, e.message ?: e::class.simpleName.orEmpty())
            }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = PdfExportState.Idle
    }

    /** Closes the result (ready or failed). The file stays in the cache until the next export or a cache clean-up. */
    fun dismiss() {
        if (!isRunning) _state.value = PdfExportState.Idle
    }

    fun share(context: Context, file: File) {
        RecipeExporter.sharePdf(context, file)
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
