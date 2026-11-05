package com.vsandoram.storagetidy.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

object Format {
    /** Human-readable byte size: 1536 -> "1.5 KB", 5_000_000 -> "4.8 MB". */
    fun bytes(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val group = (ln(size.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = size / 1024.0.pow(group.toDouble())
        return if (group == 0) "$size B"
        else String.format(Locale.US, "%.1f %s", value, units[group])
    }

    /** dateAdded is seconds since epoch. */
    fun date(seconds: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(seconds * 1000))
}
