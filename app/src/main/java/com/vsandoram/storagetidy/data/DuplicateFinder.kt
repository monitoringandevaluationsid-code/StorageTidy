package com.vsandoram.storagetidy.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Finds EXACT duplicates: byte-for-byte identical files. Strategy is two-pass so
 * we never hash more than we must:
 *   1. Group by file size. A size that appears only once cannot have a duplicate,
 *      so it is discarded without ever being read.
 *   2. Within each size bucket, hash file contents (SHA-256) and group by hash.
 *
 * Keeper rule: within a duplicate group we keep the MOST RECENT copy (largest
 * dateAdded) and mark the older copies as reclaimable.
 */
class DuplicateFinder(private val context: Context) {

    suspend fun find(
        items: List<MediaItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {

        // Pass 1 — bucket by size, keep only sizes with 2+ files.
        val bySize = items.groupBy { it.sizeBytes }.filterValues { it.size > 1 }
        val candidates = bySize.values.flatten()
        val total = candidates.size
        var done = 0

        // Pass 2 — hash candidates, group by hash.
        val byHash = HashMap<String, MutableList<MediaItem>>()
        for (item in candidates) {
            val hash = hashOf(item)
            done++
            onProgress(done, total)
            if (hash == null) continue
            item.contentHash = hash
            byHash.getOrPut(hash) { mutableListOf() }.add(item)
        }

        byHash.values
            .filter { it.size > 1 }
            .map { group ->
                // Newest first → the most recent copy is the keeper, older ones
                // are the reclaimable extras.
                val ordered = group.sortedByDescending { it.dateAddedSeconds }
                DuplicateGroup(hash = ordered.first().contentHash!!, items = ordered)
            }
            .sortedByDescending { it.reclaimableBytes } // biggest wins at the top
    }

    private fun hashOf(item: MediaItem): String? = try {
        // Documents carry an absolute path and are read directly; media items are
        // read through the content resolver.
        val input = if (item.filePath != null) java.io.FileInputStream(item.filePath)
                    else context.contentResolver.openInputStream(item.uri)
        input?.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buf)
                if (read < 0) break
                digest.update(buf, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    } catch (_: Exception) {
        null // unreadable file (revoked, deleted mid-scan) — just skip it
    }
}
