package com.vsandoram.storagetidy.data

import android.net.Uri

/** Where a scanned file came from, used for grouping and filtering in the UI. */
enum class Source { GALLERY, DOWNLOAD, DOCUMENT }

/**
 * One file found on the device.
 *
 * For media (Gallery / Downloads) [uri] is a MediaStore content URI and
 * [filePath] is null — we delete those through the system delete dialog.
 * For documents found by walking storage, [filePath] is the absolute path and
 * [uri] is a file:// URI used only as a stable key; those are deleted directly
 * (the app holds all-files access for that).
 *
 * [contentHash] is filled in lazily (null until hashed) because hashing every
 * file up front would be slow; we only hash candidates that share a size.
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
    val filePath: String? = null,
    var contentHash: String? = null
) {
    /** True for camera/app screenshots — the single biggest source of gallery
     *  clutter. Detected by the standard Screenshots folder or filename prefix. */
    val isScreenshot: Boolean
        get() = (relativePath?.contains("Screenshots", ignoreCase = true) == true) ||
                displayName.startsWith("Screenshot", ignoreCase = true)

    /** True for PDF / Office documents found by the storage walk. */
    val isDocument: Boolean get() = source == Source.DOCUMENT
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
