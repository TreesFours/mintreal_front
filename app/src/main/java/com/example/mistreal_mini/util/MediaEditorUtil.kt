package com.example.mistreal_mini.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

/**
 * Attachment editing helpers backing the full-size preview editor: image cropping,
 * video trimming (Media3 Transformer re-encode), and per-segment thumbnail
 * extraction for the 6-way video segmentation workflow. Output files follow the
 * same cacheDir + FileProvider convention as [ScreenshotHelper].
 */
object MediaEditorUtil {

    /** Decodes a base64-encoded image (as returned by Gemini/Imagen generation) into a cached file. */
    suspend fun saveBase64Image(context: Context, base64: String, mimeType: String?): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val extension = when {
                    mimeType?.contains("png") == true -> "png"
                    mimeType?.contains("webp") == true -> "webp"
                    else -> "jpg"
                }
                val file = File(context.cacheDir, "ai_generated_${System.currentTimeMillis()}.$extension")
                FileOutputStream(file).use { it.write(bytes) }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } catch (e: Exception) {
                Timber.e(e, "Failed to save AI-generated image")
                null
            }
        }

    private fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap, prefix: String): Uri? {
        return try {
            val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save edited bitmap")
            null
        }
    }

    /** [normalizedRect] fields are 0f..1f fractions of the source image's width/height. */
    suspend fun cropImage(context: Context, sourceUri: Uri, normalizedRect: RectF): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val source = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return@withContext null

                val left = (normalizedRect.left * source.width).toInt().coerceIn(0, source.width - 1)
                val top = (normalizedRect.top * source.height).toInt().coerceIn(0, source.height - 1)
                val right = (normalizedRect.right * source.width).toInt().coerceIn(left + 1, source.width)
                val bottom = (normalizedRect.bottom * source.height).toInt().coerceIn(top + 1, source.height)

                val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
                saveBitmapAndGetUri(context, cropped, "crop")
            } catch (e: Exception) {
                Timber.e(e, "Crop failed")
                null
            }
        }

    /**
     * Rasterizes freehand pencil strokes onto a copy of the FULL source image
     * (not just the tapped region/thumbnail) so the AI sees the markup with
     * real spatial context — "fix the 5th finger here" only makes sense next
     * to the whole hand, not a cropped sliver of it. [strokes] are lists of
     * (x,y) points as 0f..1f fractions of the source image, matching the
     * same convention [cropImage]/[extractImageRegionThumbnail] already use.
     */
    suspend fun rasterizeAnnotation(
        context: Context,
        sourceUri: Uri,
        strokes: List<List<Pair<Float, Float>>>,
        colorArgb: Int,
        strokeWidthFraction: Float = 0.01f
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val source = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@withContext null

            val annotated = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(annotated)
            val paint = android.graphics.Paint().apply {
                color = colorArgb
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = strokeWidthFraction * annotated.width
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                isAntiAlias = true
            }

            strokes.forEach { points ->
                if (points.size < 2) return@forEach
                val path = android.graphics.Path()
                val (startX, startY) = points.first()
                path.moveTo(startX * annotated.width, startY * annotated.height)
                points.drop(1).forEach { (x, y) -> path.lineTo(x * annotated.width, y * annotated.height) }
                canvas.drawPath(path, paint)
            }

            saveBitmapAndGetUri(context, annotated, "annotation")
        } catch (e: Exception) {
            Timber.e(e, "Annotation rasterization failed")
            null
        }
    }

    /**
     * Highlights one detected face's bounding box on a copy of an
     * already-in-memory frame (e.g. from [ChatViewModel.detectFacesInVideo])
     * so a multi-face video can say "swap THIS one" with a visual pointer,
     * the same way [rasterizeAnnotation] gives pencil markup real spatial
     * context instead of a text-only description of "which face."
     * [targetBoxFraction] is 0f..1f of the frame's own dimensions.
     */
    suspend fun rasterizeFaceTargetMarker(
        context: Context,
        frame: Bitmap,
        targetBoxFraction: android.graphics.RectF,
        colorArgb: Int = android.graphics.Color.MAGENTA
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val marked = frame.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(marked)
            val paint = android.graphics.Paint().apply {
                color = colorArgb
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 0.01f * marked.width
                isAntiAlias = true
            }
            canvas.drawRect(
                targetBoxFraction.left * marked.width,
                targetBoxFraction.top * marked.height,
                targetBoxFraction.right * marked.width,
                targetBoxFraction.bottom * marked.height,
                paint
            )
            saveBitmapAndGetUri(context, marked, "face_target")
        } catch (e: Exception) {
            Timber.e(e, "rasterizeFaceTargetMarker failed")
            null
        }
    }

    /**
     * Spatial counterpart to [extractSegmentThumbnail] for images — a 2-column x
     * 3-row grid (6 regions) instead of a time axis, so the same "tap a segment,
     * give it a specific instruction" workflow works for pictures too, not just
     * video. [index] is 0..5, read left-to-right then top-to-bottom.
     */
    suspend fun extractImageRegionThumbnail(context: Context, uri: Uri, index: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val source = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return@withContext null

                val col = index % 2
                val row = index / 2
                val regionWidth = source.width / 2
                val regionHeight = source.height / 3
                val left = (col * regionWidth).coerceIn(0, source.width - 1)
                val top = (row * regionHeight).coerceIn(0, source.height - 1)
                val width = regionWidth.coerceAtMost(source.width - left).coerceAtLeast(1)
                val height = regionHeight.coerceAtMost(source.height - top).coerceAtLeast(1)

                Bitmap.createBitmap(source, left, top, width, height)
            } catch (e: Exception) {
                Timber.e(e, "Failed to extract image region")
                null
            }
        }

    fun getVideoDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Timber.e(e, "Failed to read video duration")
            0L
        } finally {
            retriever.release()
        }
    }

    /** Thumbnail near the midpoint of segment [index] out of [segmentCount] equal time slices. */
    suspend fun extractSegmentThumbnail(
        context: Context,
        uri: Uri,
        index: Int,
        segmentCount: Int,
        durationMs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (durationMs <= 0) return@withContext null
        val segmentDuration = durationMs / segmentCount
        val midpointUs = ((segmentDuration * index) + segmentDuration / 2) * 1000
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(midpointUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract segment thumbnail")
            null
        } finally {
            retriever.release()
        }
    }

    /** Re-encodes [sourceUri] to a new file containing only [startMs, endMs]. */
    suspend fun trimVideo(context: Context, sourceUri: Uri, startMs: Long, endMs: Long): Uri? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val outputFile = File(context.cacheDir, "trim_${System.currentTimeMillis()}.mp4")

                val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(sourceUri)
                    .setClippingConfiguration(clippingConfiguration)
                    .build()

                val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
                                )
                            }
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            Timber.e(exportException, "Video trim failed")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                    .build()

                transformer.start(editedMediaItem, outputFile.absolutePath)

                continuation.invokeOnCancellation { transformer.cancel() }
            }
        }

    /**
     * Muxes [audioUri] onto [videoUri] as a separate audio track via two
     * parallel Transformer sequences (video-only + audio-only), composited
     * together — this is how Media3 combines independently-sourced audio and
     * video rather than needing them pre-muxed in one container already.
     * [replaceExisting] strips the video's own audio first; otherwise the new
     * track plays alongside whatever audio the video already had.
     */
    suspend fun addAudioToVideo(context: Context, videoUri: Uri, audioUri: Uri, replaceExisting: Boolean = true): Uri? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val outputFile = File(context.cacheDir, "voiceover_${System.currentTimeMillis()}.mp4")

                val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
                    .setRemoveAudio(replaceExisting)
                    .build()
                val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(audioUri)).build()

                val composition = androidx.media3.transformer.Composition.Builder(
                    androidx.media3.transformer.EditedMediaItemSequence(videoItem),
                    androidx.media3.transformer.EditedMediaItemSequence(audioItem)
                ).build()

                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile)
                                )
                            }
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            Timber.e(exportException, "Voice-over mux failed")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    })
                    .build()

                transformer.start(composition, outputFile.absolutePath)

                continuation.invokeOnCancellation { transformer.cancel() }
            }
        }
}
