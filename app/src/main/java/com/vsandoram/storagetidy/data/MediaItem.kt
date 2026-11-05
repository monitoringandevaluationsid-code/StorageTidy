package com.vsandoram.storagetidy.data

import android.net.Uri

/** Where a scanned file came from, used for grouping and filtering in the UI. */
enum class Source { GALLERY, DOWNLOAD }

/**
 * One file found on the device. [uri] is a MediaStore content URI — the only
 * handle we use to delete, so we never touch raw file paths or need all-files
 * access. [contentHash] is filled in lazily (null until hashed) because hashing
 * every file up front would be slow; we only hash candidates that share a size.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val dateAddedSeconds: Long,
    val source: Source,
    val mimeType: String?,
    val relativePath: String? = null,
    var contentHash: String? = null
) {
    /** True for camera/app screenshots — the single biggest source of gallery
     *  clutter. Detected by the standard Screenshots folder or filename prefix. */
    val isScreenshot: Boolean
        get() = (relativePath?.contains("Screenshots", ignoreCase = true) == true) ||
                displayName.startsWith("Screenshot", ignoreCase = true)
}

/** A set of files with identical content. The first is kept; the rest are the
 *  reclaimable copies. */
data class DuplicateGroup(
    val hash: String,
    val items: List<MediaItem>
) {
    val keeper: MediaItem get() = items.first()
    val extras: List<MediaItem> get() = items.drop(1)
    val reclaimableBytes: Long get() = extras.sumOf { it.sizeBytes }
}
