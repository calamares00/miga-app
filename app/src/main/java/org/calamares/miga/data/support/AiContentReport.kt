package org.calamares.miga.data.support

import java.net.URLEncoder

/** Motivos para reportar un contenido generado con IA (política de contenido generado con IA de Google Play). */
enum class AiReportReason(val label: String) {
    OFFENSIVE("Ofensivo o inapropiado"),
    DANGEROUS("Peligroso: alimentos, alergias o salud"),
    WRONG("Incorrecto o sin sentido"),
    OTHER("Otro motivo")
}

/**
 * Construye el reporte de un contenido generado con IA. Miga no tiene servidor propio, así que el
 * reporte se envía al desarrollador por correo ([SUPPORT_EMAIL]). Lógica pura, sin Android.
 */
object AiContentReport {
    /** Correo de soporte (reportes de IA, "Informar de un problema" y contacto de la ficha de Play). */
    const val SUPPORT_EMAIL = "miga@calamares.org"
    private const val MAX_CONTENT_CHARS = 1500

    fun subject(feature: String): String = "Reporte de contenido IA - $feature"

    fun body(feature: String, reason: AiReportReason, comment: String, content: String, appVersion: String): String = buildString {
        appendLine("Función: $feature")
        appendLine("Motivo: ${reason.label}")
        if (comment.isNotBlank()) appendLine("Comentario: ${comment.trim()}")
        appendLine("Versión de Miga: $appVersion")
        appendLine()
        appendLine("Contenido generado:")
        append(content.trim().take(MAX_CONTENT_CHARS))
        if (content.trim().length > MAX_CONTENT_CHARS) append("…")
    }

    /** Enlace mailto al correo de soporte con asunto y cuerpo ya escritos. */
    fun targetUrl(subject: String, body: String): String =
        "mailto:$SUPPORT_EMAIL?subject=${encode(subject)}&body=${encode(body)}"

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
