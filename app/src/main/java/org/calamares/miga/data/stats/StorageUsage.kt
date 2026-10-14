package org.calamares.miga.data.stats

import android.content.Context
import org.calamares.miga.data.local.AppDatabase
import java.io.File

/**
 * How much space the app takes on the device, by kind: recipe and cover photos (and how many of
 * them are no longer used), the database, the cache and the rest of the app's files.
 */
data class StorageUsage(
    val photosBytes: Long,
    val photosCount: Int,
    val unusedPhotosBytes: Long,
    val unusedPhotosCount: Int,
    val databaseBytes: Long,
    val cacheBytes: Long,
    val otherBytes: Long
) {
    val totalBytes: Long get() = photosBytes + databaseBytes + cacheBytes + otherBytes
}

/** A stored photo file, as needed to decide whether it is still used. */
data class PhotoFileInfo(val name: String, val bytes: Long, val lastModified: Long)

object StorageUsageScanner {

    /**
     * Photos newer than this are never considered unused: one may have just been taken for a recipe
     * that is still being edited and not saved yet.
     */
    const val UNUSED_PHOTO_MIN_AGE_MILLIS = 24L * 60 * 60 * 1000

    private fun photosDir(context: Context) = File(context.filesDir, "photos")

    /** File names of the photos referenced by [uris] (the "file://" uris stored by the app). */
    fun referencedNames(uris: Collection<String?>): Set<String> =
        uris.mapNotNull { uri ->
            uri?.takeIf { it.startsWith("file:") }?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
        }.toSet()

    /** The [photos] no recipe or book cover uses ([referenced] file names) and older than a day. */
    fun unusedPhotos(photos: List<PhotoFileInfo>, referenced: Set<String>, now: Long): List<PhotoFileInfo> =
        photos.filter { it.name !in referenced && now - it.lastModified >= UNUSED_PHOTO_MIN_AGE_MILLIS }

    private fun photoFiles(context: Context): List<File> = photosDir(context).listFiles()?.filter { it.isFile }.orEmpty()

    fun scan(context: Context, referencedNames: Set<String>, now: Long): StorageUsage {
        val photos = photoFiles(context).map { PhotoFileInfo(it.name, it.length(), it.lastModified()) }
        val unused = unusedPhotos(photos, referencedNames, now)
        val photosBytes = photos.sumOf { it.bytes }
        val databaseDir = context.getDatabasePath(AppDatabase.DATABASE_NAME).parentFile
        val databaseBytes = databaseDir?.let(::sizeOf) ?: 0L
        val cacheBytes = sizeOf(context.cacheDir) + (context.externalCacheDir?.let(::sizeOf) ?: 0L)
        val otherBytes = (sizeOf(context.filesDir) - photosBytes).coerceAtLeast(0L) +
            sizeOf(File(context.applicationInfo.dataDir, "shared_prefs"))
        return StorageUsage(
            photosBytes = photosBytes,
            photosCount = photos.size,
            unusedPhotosBytes = unused.sumOf { it.bytes },
            unusedPhotosCount = unused.size,
            databaseBytes = databaseBytes,
            cacheBytes = cacheBytes,
            otherBytes = otherBytes
        )
    }

    /** Deletes the unused photos (see [unusedPhotos]) and returns how many were deleted. */
    fun deleteUnusedPhotos(context: Context, referencedNames: Set<String>, now: Long): Int {
        val files = photoFiles(context)
        val unused = unusedPhotos(files.map { PhotoFileInfo(it.name, it.length(), it.lastModified()) }, referencedNames, now)
            .map { it.name }
            .toSet()
        return files.count { it.name in unused && it.delete() }
    }

    /**
     * Empties the cache except [keep] (the image loader's own cache, which is cleared through the
     * loader so it does not end up out of step with its files).
     */
    fun clearCache(context: Context, keep: Set<String>) {
        listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { dir ->
            dir.listFiles()?.filter { it.name !in keep }?.forEach { it.deleteRecursively() }
        }
    }

    private fun sizeOf(file: File): Long =
        if (file.isFile) file.length() else file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
}
