package org.calamares.miga.data.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.calamares.miga.data.io.LimitedInputStream
import org.calamares.miga.data.io.ReadLimits

/**
 * A Miga file opened from another app (a book shared by WhatsApp, Telegram, the mail or the file
 * manager), waiting for the books screen to offer importing it.
 */
sealed interface IncomingBookFile {
    /** Copied to the app's cache: the other app's permission to read it does not last. */
    data class Ready(val file: File) : IncomingBookFile
    data object Unreadable : IncomingBookFile
}

/**
 * Mailbox between MainActivity, which receives "open with" (VIEW) and "share to" (SEND) of files,
 * and the books screen, which asks whether to import them. Kept until consumed, like [ShoppingIntents].
 */
object BookFileIntents {

    /** Types other apps give a ".miga" file: a ZIP inside, but often reported as a generic file. */
    private val FILE_TYPES = setOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "application/x-miga")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _event = MutableStateFlow<IncomingBookFile?>(null)
    val event: StateFlow<IncomingBookFile?> = _event

    /** Returns true when [intent] carries a file for Miga, which is then copied in the background. */
    fun handle(context: Context, intent: Intent?): Boolean {
        intent ?: return false
        val uri: Uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return false
        val type = intent.type ?: context.contentResolver.getType(uri)
        if (type != null && type !in FILE_TYPES) return false
        val appContext = context.applicationContext
        scope.launch {
            _event.value = try {
                val dir = File(appContext.cacheDir, "incoming").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "${UUID.randomUUID()}.miga")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    // Same bound as a downloaded pack: a book with its photos, never an endless stream.
                    file.outputStream().use { LimitedInputStream(input, ReadLimits.SHARED_BOOK_BYTES).copyTo(it) }
                } ?: error("No stream")
                IncomingBookFile.Ready(file)
            } catch (e: Exception) {
                IncomingBookFile.Unreadable
            }
        }
        return true
    }

    fun consume() {
        _event.value = null
    }
}
