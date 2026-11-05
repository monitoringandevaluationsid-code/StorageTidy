package com.vsandoram.storagetidy.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a small MediaStore thumbnail for a content URI, off the main thread.
 * Uses ContentResolver.loadThumbnail (API 29+, which matches our minSdk) so it
 * works for both images and videos without an image-loading library.
 *
 * Kept deliberately simple: a per-row produceState with a tiny target size.
 * For very long lists this is fine because LazyColumn only composes visible
 * rows, so only on-screen thumbnails are ever decoded.
 */
@Composable
fun rememberThumbnail(uri: Uri): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, uri) {
        value = loadThumbnail(context, uri)
    }.value
}

private suspend fun loadThumbnail(context: Context, uri: Uri): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(96, 96), null)
            } else null
        } catch (_: Exception) {
            // Non-visual files (PDFs, zips in Downloads) have no thumbnail — that's fine.
            null
        }
    }
