package com.example.mistreal_mini.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

object ScreenshotHelper {
    // 🖼️ Uses PixelCopy instead of View.draw(Canvas) — the latter reads from the
    // software drawing cache and comes back blank/black for hardware-accelerated
    // content like the map's WebView. PixelCopy reads the real compositor buffer.
    suspend fun captureAndSave(activity: Activity, cropRect: Rect? = null): Uri? {
        val window = activity.window
        val decorView = window.decorView
        val width = decorView.width
        val height = decorView.height
        if (width <= 0 || height <= 0) return null

        val fullBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val copied = suspendCancellableCoroutine<Boolean> { continuation ->
            try {
                PixelCopy.request(window, fullBitmap, { result ->
                    continuation.resume(result == PixelCopy.SUCCESS)
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                Timber.e(e, "PixelCopy request failed")
                continuation.resume(false)
            }
        }

        if (!copied) return null

        val finalBitmap = if (cropRect != null) {
            // Ensure cropRect is within bounds
            val left = cropRect.left.coerceAtLeast(0).coerceAtMost(width - 1)
            val top = cropRect.top.coerceAtLeast(0).coerceAtMost(height - 1)
            val right = cropRect.right.coerceAtMost(width).coerceAtLeast(left + 1)
            val bottom = cropRect.bottom.coerceAtMost(height).coerceAtLeast(top + 1)
            Bitmap.createBitmap(fullBitmap, left, top, right - left, bottom - top)
        } else {
            fullBitmap
        }

        return try {
            val file = File(activity.cacheDir, "screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { outputStream ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save screenshot")
            null
        }
    }
}
