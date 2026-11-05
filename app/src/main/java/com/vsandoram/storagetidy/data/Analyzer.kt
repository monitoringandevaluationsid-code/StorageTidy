package com.vsandoram.storagetidy.data

/** The complete result of a scan, ready for the UI to render. */
data class ScanReport(
    val allItems: List<MediaItem>,
    val duplicates: List<DuplicateGroup>,
    val similar: List<DuplicateGroup>,
    val largeFiles: List<MediaItem>,
    val oldFiles: List<MediaItem>,
    val screenshots: List<MediaItem>,
    val documents: List<MediaItem>,
    val documentsAccessGranted: Boolean
) {
    val totalBytes: Long get() = allItems.sumOf { it.sizeBytes }
    val duplicateReclaimableBytes: Long get() = duplicates.sumOf { it.reclaimableBytes }
    val duplicateCount: Int get() = duplicates.sumOf { it.extras.size }
}

/**
 * Turns a flat item list into the three "optimise" views. Thresholds are
 * deliberately conservative so nothing recent or important is suggested by
 * default — the user still confirms every delete.
 */
object Analyzer {

    private const val LARGE_FILE_THRESHOLD = 50L * 1024 * 1024   // 50 MB
    private const val OLD_FILE_DAYS = 180L                       // ~6 months

    fun build(
        items: List<MediaItem>,
        duplicates: List<DuplicateGroup>,
        similar: List<DuplicateGroup>,
        documentsAccessGranted: Boolean
    ): ScanReport {
        val nowSeconds = System.currentTimeMillis() / 1000
        val oldCutoff = nowSeconds - OLD_FILE_DAYS * 24 * 60 * 60

        val large = items
            .filter { it.sizeBytes >= LARGE_FILE_THRESHOLD }
            .sortedByDescending { it.sizeBytes }

        // "Old" covers Downloads and documents — old photos are usually worth
        // keeping, but a six-month-old download or document is almost always stale.
        val old = items
            .filter {
                (it.source == Source.DOWNLOAD || it.source == Source.DOCUMENT) &&
                        it.dateAddedSeconds in 1 until oldCutoff
            }
            .sortedBy { it.dateAddedSeconds }

        val screenshots = items
            .filter { it.isScreenshot }
            .sortedByDescending { it.dateAddedSeconds }

        val documents = items
            .filter { it.isDocument }
            .sortedByDescending { it.sizeBytes }

        return ScanReport(
            allItems = items,
            duplicates = duplicates,
            similar = similar,
            largeFiles = large,
            oldFiles = old,
            screenshots = screenshots,
            documents = documents,
            documentsAccessGranted = documentsAccessGranted
        )
    }
}
