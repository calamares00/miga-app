package org.calamares.miga.data.support

import org.calamares.miga.L10n
import org.calamares.miga.R
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Mensajes de error con dos partes: un resumen corto para el usuario y, opcionalmente, el detalle
 * técnico (respuesta del modelo, código HTTP...) que solo se muestra con "Ver detalle". Las dos
 * partes viajan en el mismo String, separadas por [SEPARATOR], para no cambiar los tipos de
 * resultado de todos los clientes.
 */
object ErrorDetail {
    private const val SEPARATOR = "\u001F"

    fun withDetail(summary: String, detail: String?): String =
        if (detail.isNullOrBlank()) summary else summary + SEPARATOR + detail

    fun summary(reason: String): String = reason.substringBefore(SEPARATOR)

    fun detail(reason: String): String? = reason.substringAfter(SEPARATOR, "").takeIf { it.isNotBlank() }
}

/** Errores de los proveedores de IA traducidos a mensajes comprensibles. */
object AiErrors {

    /** La IA respondió, pero no con el JSON esperado. */
    fun badResponse(error: Throwable, modelText: String): String {
        val technical = error.message?.substringBefore("\nJSON input:") ?: error::class.simpleName.orEmpty()
        return ErrorDetail.withDetail(L10n.str(R.string.ai_error_bad_response), "$technical\n\n$modelText")
    }

    /** Respuesta HTTP no correcta de [provider] (Gemini, Claude...). */
    fun http(provider: String, code: Int, providerMessage: String?): String {
        val summary = when (code) {
            400 -> L10n.str(R.string.ai_error_bad_request, provider)
            401, 403 -> L10n.str(R.string.ai_error_key, provider)
            404 -> L10n.str(R.string.ai_error_model, provider)
            429 -> L10n.str(R.string.ai_error_quota, provider)
            in 500..599 -> L10n.str(R.string.ai_error_unavailable, provider)
            else -> L10n.str(R.string.ai_error_generic, provider)
        }
        return ErrorDetail.withDetail(summary, "HTTP $code" + (providerMessage?.let { "\n$it" } ?: ""))
    }

    /** Fallo de red o inesperado al llamar a la IA. */
    fun exception(error: Throwable): String {
        val summary = when (error) {
            is UnknownHostException, is SocketTimeoutException -> L10n.str(R.string.ai_error_network)
            is IOException -> L10n.str(R.string.ai_error_network)
            else -> L10n.str(R.string.ai_error_unexpected)
        }
        return ErrorDetail.withDetail(summary, error.toString())
    }
}
