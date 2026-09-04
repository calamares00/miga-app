package com.bmo00.miga.ui.common

/**
 * MIME types aceptados por los selectores de archivo para importar una copia de seguridad
 * (.json plano o .zip con fotos). Muchos proveedores de contenido (compartir desde otra app,
 * descargas...) no informan el MIME "correcto" para estos archivos —sobre todo si el
 * dispositivo no tiene esa extensión registrada en su MimeTypeMap— y devuelven "text/plain" u
 * "octet-stream" en su lugar, lo que hacía que el archivo ni siquiera apareciera en el selector
 * con un filtro estricto.
 */
val BACKUP_MIME_TYPES = arrayOf(
    "application/json",
    "application/zip",
    "application/x-zip-compressed",
    "text/plain",
    "application/octet-stream",
    "*/*"
)
