package org.calamares.miga.ui.common

/**
 * MIME types accepted by the file pickers when importing a backup (plain .json or .zip with
 * photos). Many content providers (shares from other apps, downloads...) report "text/plain" or
 * "octet-stream" instead of the right type, especially when the extension is not registered in
 * MimeTypeMap, and a strict filter would hide the file.
 */
val BACKUP_MIME_TYPES = arrayOf(
    "application/json",
    "application/zip",
    "application/x-zip-compressed",
    "text/plain",
    "application/octet-stream",
    "*/*"
)
