package com.bmo00.miga.crash

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.bmo00.miga.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Informe de fallos totalmente local: si la app crashea, se guarda un informe de texto en el
 * almacenamiento interno del dispositivo. Al reabrir la app se ofrece verlo, copiarlo o
 * compartirlo manualmente (ver RecipeBooksScreen), pero nada se envía a ningún sitio de forma
 * automática — sin SDK de terceros, sin cuenta, sin servidor propio. Ver PRIVACY.md.
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
                // Si ni siquiera se puede escribir el informe, no debe impedir que la app termine
                // con normalidad (o con el manejador anterior, si lo había).
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
            appendLine("Fecha: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Hilo: ${thread.name}")
            appendLine()
            append(Log.getStackTraceString(throwable))
        }
        File(appContext.filesDir, FILE_NAME).writeText(report)
    }

    /** Informe del último fallo no capturado, si lo hay y todavía no se ha descartado. */
    fun pendingReport(): String? {
        if (!::appContext.isInitialized) return null
        val file = File(appContext.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    /** Marca el informe como revisado borrándolo; se llama al cerrarlo, se comparta o no. */
    fun dismiss() {
        if (::appContext.isInitialized) File(appContext.filesDir, FILE_NAME).delete()
    }
}
