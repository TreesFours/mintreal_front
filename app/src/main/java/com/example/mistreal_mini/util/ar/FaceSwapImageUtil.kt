package com.example.mistreal_mini.util.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Instant, local, free face swap on a static image — the same triangulated
 * warp [com.example.mistreal_mini.ui.camera.ArFilterCameraScreen] already
 * does live, now reachable from the chat attachment editor too, instead of
 * that screen's only face-swap option being a Gemini AI-edit round trip.
 * Reducing "two separate face-swap systems" to one shared mechanism plus
 * an optional AI-quality alternative, rather than two disconnected ones —
 * this doesn't replace the Gemini AI-edit path (still useful for bigger
 * asks like "also change the background"), it just means a *pure* face
 * swap no longer has to go through an AI call at all.
 */
object FaceSwapImageUtil {

    /**
     * Returns a new file's [Uri] with [referenceFaceUri]'s face swapped onto
     * whichever face [sourceUri] detects, or null if either image has no
     * detectable face. Runs on the calling coroutine's dispatcher — callers
     * should already be off the main thread (this does real decode/detect
     * work, same cost class as [com.example.mistreal_mini.ui.camera.ar.ArCameraViewModel]'s
     * swap-target preparation).
     */
    suspend fun swapFaceInImage(context: Context, sourceUri: Uri, referenceFaceUri: Uri, faceTracker: FaceTracker): Uri? {
        return try {
            val sourceBitmap = loadBitmap(context, sourceUri) ?: return null
            val referenceBitmap = loadBitmap(context, referenceFaceUri) ?: return null

            val referenceMesh = faceTracker.detectStill(referenceBitmap)?.mesh ?: run {
                Timber.w("FaceSwapImageUtil: no detectable face in reference image")
                return null
            }
            val sourceMesh = faceTracker.detectStill(sourceBitmap)?.mesh ?: run {
                Timber.w("FaceSwapImageUtil: no detectable face in source image")
                return null
            }

            val triangles = DelaunayTriangulator.triangulate(referenceMesh.allPoints)
            val destColor = FaceSwapCompositor.averageColor(sourceBitmap, sourceMesh.faceOvalPoints)
            val srcColor = FaceSwapCompositor.averageColor(referenceBitmap, referenceMesh.faceOvalPoints)

            val composite = sourceBitmap.copy(Bitmap.Config.ARGB_8888, true)
            FaceSwapCompositor.drawSwap(
                canvas = Canvas(composite),
                sourceBitmap = referenceBitmap,
                sourcePoints = referenceMesh.allPoints,
                destPoints = sourceMesh.allPoints,
                triangles = triangles,
                destOvalPoints = sourceMesh.faceOvalPoints,
                colorGain = FaceSwapCompositor.colorGainFrom(destColor, srcColor)
            )

            val outFile = File(context.cacheDir, "quick_faceswap_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out -> composite.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (e: Exception) {
            Timber.e(e, "FaceSwapImageUtil.swapFaceInImage failed")
            null
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}
