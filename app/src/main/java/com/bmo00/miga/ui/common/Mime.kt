package com.bmo00.miga.ui.common

/**
 * MIME types aceptados por los selectores de archivo para importar JSON. Muchos proveedores de
 * contenido (compartir desde otra app, descargas...) no informan "application/json" para un
 * archivo .json —sobre todo si el dispositivo no tiene esa extensión registrada en su
 * MimeTypeMap— y devuelven "text/plain" u "octet-stream" en su lugar, lo que hacía que el
 * archivo ni siquiera apareciera en el selector con un filtro estricto.
 */
val JSON_MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream",
    "*/*"
)
