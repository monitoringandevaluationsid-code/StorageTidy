package com.vsandoram.storagetidy.data

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds VISUALLY similar photos — not just byte-identical ones. It computes a
 * perceptual fingerprint (a 64-bit "dHash") of each image from a tiny thumbnail,
 * then groups images whose fingerprints are close (small Hamming distance). This
 * catches re-compressed copies (e.g. the WhatsApp version of a photo), resized
 * copies, and the same shot saved twice, which exact hashing misses.
 *
 * Only still images are considered; videos and documents are left to the exact
 * duplicate finder.
 */
class SimilarFinder(private val context: Context) {

    /** Max fingerprint difference (out of 64 bits) to still count as "similar".
     *  Lower = stricter. 10 is a good balance between catching re-compressions
     *  and avoiding false matches. */
    private val threshold = 10

    suspend fun find(
        items: List<MediaItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val images = items.filter {
            it.source == Source.GALLERY && it.mimeType?.startsWith("image/") == true
        }
        val total = images.size
        var done = 0

        val fingerprints = ArrayList<Pair<MediaItem, Long>>(images.size)
        for (item in images) {
            val h = fingerprint(item)
            done++
            onProgress(done, total)
            if (h != null) fingerprints.add(item to h)
        }

        // Greedy clustering: each unused image seeds a group of everything within
        // the threshold. O(n^2) but the per-pair cost is a single XOR + bit count.
        val used = BooleanArray(fingerprints.size)
        val groups = ArrayList<DuplicateGroup>()
        for (i in fingerprints.indices) {
            if (used[i]) continue
            used[i] = true
            val members = ArrayList<MediaItem>()
            members.add(fingerprints[i].first)
            for (j in i + 1 until fingerprints.size) {
                if (used[j]) continue
                if (hamming(fingerprints[i].second, fingerprints[j].second) <= threshold) {
                    used[j] = true
                    members.add(fingerprints[j].first)
                }
            }
            if (members.size > 1) {
                // Newest first → keep the most recent, older copies are reclaimable.
                val ordered = members.sortedByDescending { it.dateAddedSeconds }
                groups.add(DuplicateGroup(hash = "sim-${fingerprints[i].second}", items = ordered))
            }
        }
        groups.sortedByDescending { it.reclaimableBytes }
    }

    private fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** dHash: compare each pixel to its right neighbour on a 9x8 greyscale image,
     *  giving 8x8 = 64 bits. */
    private fun fingerprint(item: MediaItem): Long? = try {
        val thumb = loadThumb(item) ?: return null
        val small = Bitmap.createScaledBitmap(thumb, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(small.getPixel(x, y))
                val right = luminance(small.getPixel(x + 1, y))
                if (left < right) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (small != thumb) small.recycle()
        thumb.recycle()
        hash
    } catch (_: Exception) {
        null
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun loadThumb(item: MediaItem): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            context.contentResolver.loadThumbnail(item.uri, Size(32, 32), null)
        else null
    } catch (_: Exception) {
        null
    }
}
