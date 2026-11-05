package com.vsandoram.storagetidy.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds document files (PDF / Word / Excel / PowerPoint / text) anywhere in
 * shared storage by walking the filesystem. This needs all-files access
 * (MANAGE_EXTERNAL_STORAGE); the caller only invokes it once that is granted.
 *
 * MediaStore is not used here because it does not index arbitrary documents
 * outside the Downloads collection — a plain directory walk is what reaches the
 * Documents folder, WhatsApp Documents, and so on.
 */
class DocumentScanner {

    private val extensions = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "txt", "csv", "odt", "ods", "rtf"
    )

    /** Walk [root] (normally the shared-storage root) collecting document files. */
    suspend fun scan(root: File): List<MediaItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<MediaItem>()
        val stack = ArrayDeque<File>()
        if (root.isDirectory) stack.addLast(root)

        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val path = dir.absolutePath
            // Android/data and Android/obb are app-private and not readable even
            // with all-files access — skip them (Android/media IS readable, so
            // WhatsApp documents there are still reached).
            if (path.contains("/Android/data") || path.contains("/Android/obb")) continue

            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory) {
                    stack.addLast(f)
                } else {
                    val ext = f.extension.lowercase()
                    val size = f.length()
                    if (ext in extensions && size > 0) {
                        out += MediaItem(
                            id = f.absolutePath.hashCode().toLong(),
                            uri = Uri.fromFile(f),
                            displayName = f.name,
                            sizeBytes = size,
                            dateAddedSeconds = f.lastModified() / 1000,
                            source = Source.DOCUMENT,
                            mimeType = null,
                            relativePath = f.parent,
                            filePath = f.absolutePath
                        )
                    }
                }
            }
        }
        out
    }
}
