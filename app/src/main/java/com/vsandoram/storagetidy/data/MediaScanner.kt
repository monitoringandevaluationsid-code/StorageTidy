package com.vsandoram.storagetidy.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the device's media index via MediaStore. This is the sanctioned,
 * permission-light way to enumerate the Gallery and Downloads on modern Android
 * — no "All files access" required, and deletes still route through the system
 * confirmation dialog.
 */
class MediaScanner(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** Scan images + videos (the Gallery) and the Downloads collection. */
    suspend fun scanAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        buildList {
            addAll(queryImages())
            addAll(queryVideos())
            addAll(queryDownloads())
        }
    }

    private fun queryImages(): List<MediaItem> =
        query(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            source = Source.GALLERY
        )

    private fun queryVideos(): List<MediaItem> =
        query(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            source = Source.GALLERY
        )

    private fun queryDownloads(): List<MediaItem> =
        query(
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            source = Source.DOWNLOAD
        )

    private fun query(collection: android.net.Uri, source: Source): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val out = ArrayList<MediaItem>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            // RELATIVE_PATH exists on API 29+; getColumnIndex returns -1 if absent.
            val pathCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val size = c.getLong(sizeCol)
                if (size <= 0L) continue   // skip zero-byte / placeholder rows
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = c.getString(nameCol) ?: "unnamed",
                    sizeBytes = size,
                    dateAddedSeconds = c.getLong(dateCol),
                    source = source,
                    mimeType = c.getString(mimeCol),
                    relativePath = if (pathCol >= 0) c.getString(pathCol) else null
                )
            }
        }
        return out
    }
}
