package org.calamares.miga.crash

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import org.calamares.miga.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Fully local crash reporting.
 *
 * When the app crashes a plain text report is written to internal storage. On the next start the
 * user can view, copy or share it manually (see RecipeBooksScreen); nothing is ever sent
 * automatically. No third-party SDK, account or server is involved (see PRIVACY.md).
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private lateinit var appContext: Context

    fun install(context: Context) {
        appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(thread, throwable)
            } catch (e: Exception) {
                // Failing to write the report must not prevent the app from terminating normally.
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun writeReport(thread: Thread, throwable: Throwable) {
        val report = buildString {
            appendLine("Miga ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(Log.getStackTraceString(throwable))
        }
        File(appContext.filesDir, FILE_NAME).writeText(report)
    }

    /** Report of the last uncaught crash, if there is one that has not been dismissed yet. */
    fun pendingReport(): String? {
        if (!::appContext.isInitialized) return null
        val file = File(appContext.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    /** Marks the report as reviewed by deleting it; called when it is closed, shared or not. */
    fun dismiss() {
        if (::appContext.isInitialized) File(appContext.filesDir, FILE_NAME).delete()
    }
}
