package com.example.mistreal_mini.util.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.mistreal_mini.ui.camera.ar.ArSticker
import com.example.mistreal_mini.ui.camera.ar.PreparedSwapTarget
import com.example.mistreal_mini.ui.camera.ar.StickerAnchor
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume

/**
 * Bakes the AR camera's sticker/FaceSwap filter into a *recorded* video
 * file — the gap [com.example.mistreal_mini.ui.camera.ArFilterCameraScreen]
 * flagged ("filters not saved to video yet"). Built on Media3 Transformer's
 * [OverlayEffect]/[BitmapOverlay] — already a dependency, already proven in
 * this codebase by [com.example.mistreal_mini.util.MediaEditorUtil.trimVideo]
 * — rather than hand-written EGL/MediaCodec: Media3 owns the real
 * decode → GL-composite → encode → mux pipeline and audio passthrough
 * internally, so this only has to hand back a transparent bitmap per frame
 * with whatever should be drawn on top.
 *
 * Face detection runs on a *separate* decode pass
 * ([MediaMetadataRetriever], sampled every [DETECTION_STEP_US] rather than
 * truly every frame — there's no supported way to read Transformer's own
 * internal decoded frames directly, and detecting on literally every frame
 * would make export very slow). Between samples, the last detection result
 * is reused, so fast head motion will visibly lag the real face for a few
 * frames. This whole file is the least-verified thing built this session —
 * no device, no real recorded clip, to confirm against; a wrong assumption
 * about frame dimensions/rotation or Media3's default overlay scaling would
 * show up as a misaligned or garbled overlay, not a crash.
 */
object VideoFilterBaker {

    private const val DETECTION_STEP_US = 100_000L // ~10fps face tracking, not true per-frame

    suspend fun bake(
        context: Context,
        sourceUri: Uri,
        faceTracker: FaceTracker,
        sticker: ArSticker,
        isFrontCamera: Boolean,
        preparedSwapTarget: PreparedSwapTarget?
    ): Uri? = withContext(Dispatchers.Main) {
        if (preparedSwapTarget == null && sticker.id == "normal") {
            return@withContext sourceUri // nothing to bake — skip the whole re-encode
        }

        val retriever = MediaMetadataRetriever()
        val firstFrame = try {
            retriever.setDataSource(context, sourceUri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            Timber.e(e, "VideoFilterBaker couldn't open %s", sourceUri)
            retriever.release()
            return@withContext null
        }
        if (firstFrame == null) {
            retriever.release()
            return@withContext null
        }
        // Derived from a real decoded (already-rotated) frame, not video
        // metadata, since METADATA_KEY_VIDEO_WIDTH/HEIGHT can report
        // pre-rotation buffer dimensions that disagree with what
        // getFrameAtTime() and FaceTracker actually hand back.
        val overlayWidth = firstFrame.width
        val overlayHeight = firstFrame.height
        val overlayBitmap = Bitmap.createBitmap(overlayWidth, overlayHeight, Bitmap.Config.ARGB_8888)
        val overlayCanvas = Canvas(overlayBitmap)

        var lastDetectionUs = -DETECTION_STEP_US * 2
        var lastResult: FaceTrackResult? = null

        val overlay = object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long): Bitmap {
                if (presentationTimeUs - lastDetectionUs >= DETECTION_STEP_US) {
                    lastDetectionUs = presentationTimeUs
                    lastResult = try {
                        val frame = retriever.getFrameAtTime(presentationTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        frame?.let { faceTracker.detectStillBlocking(it) }
                    } catch (e: Exception) {
                        Timber.w(e, "Per-frame face detection failed at %d us", presentationTimeUs)
                        null
                    }
                }
                overlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                renderOverlayFrame(overlayCanvas, overlayWidth, overlayHeight, lastResult, sticker, isFrontCamera, preparedSwapTarget)
                return overlayBitmap
            }
        }

        val outputFile = File(context.cacheDir, "ar_video_baked_${System.currentTimeMillis()}.mp4")
        val mediaItem = MediaItem.Builder().setUri(sourceUri).build()
        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(ImmutableList.of(overlay)))))
            .build()

        val result = suspendCancellableCoroutine<Uri?> { continuation ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) {
                            continuation.resume(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outputFile))
                        }
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Timber.e(exportException, "VideoFilterBaker export failed")
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
                .build()

            transformer.start(editedMediaItem, outputFile.absolutePath)
            continuation.invokeOnCancellation { transformer.cancel() }
        }

        retriever.release()
        result
    }

    private fun renderOverlayFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        track: FaceTrackResult?,
        sticker: ArSticker,
        isFrontCamera: Boolean,
        preparedSwapTarget: PreparedSwapTarget?
    ) {
        sticker.tintColor?.let { canvas.drawColor(it.toArgb()) }
        if (track == null) return

        val mesh = track.mesh
        if (preparedSwapTarget != null && mesh != null) {
            val mapper = FrameMapper(track, width.toFloat(), height.toFloat(), isFrontCamera)
            fun mapAll(points: List<PointF>) = points.map { p -> val (x, y) = mapper.map(p); PointF(x, y) }
            FaceSwapCompositor.drawSwap(
                canvas = canvas,
                sourceBitmap = preparedSwapTarget.bitmap,
                sourcePoints = preparedSwapTarget.points,
                destPoints = mapAll(mesh.allPoints),
                triangles = preparedSwapTarget.triangles,
                destOvalPoints = mapAll(mesh.faceOvalPoints)
            )
        }

        if (sticker.anchor == StickerAnchor.FULL_SCREEN_TINT || sticker.anchor == StickerAnchor.FACE_SMOOTH) return
        val mapper = FrameMapper(track, width.toFloat(), height.toFloat(), isFrontCamera)
        when (sticker.anchor) {
            StickerAnchor.ABOVE_FACE -> {
                val box = track.boundingBox
                val (x, y) = mapper.map(PointF(box.exactCenterX(), box.top.toFloat()))
                drawEmoji(canvas, sticker.emoji, x, y, mapper.scaleLength(box.width().toFloat()) * 0.9f)
            }
            StickerAnchor.EYES -> {
                val left = track.leftEye
                val right = track.rightEye
                if (left != null && right != null) {
                    val (x, y) = mapper.map(PointF((left.x + right.x) / 2f, (left.y + right.y) / 2f))
                    val eyeSpan = mapper.scaleLength(kotlin.math.hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble()).toFloat())
                    drawEmoji(canvas, sticker.emoji, x, y, eyeSpan * 2.2f)
                }
            }
            StickerAnchor.NOSE_TIP -> {
                track.noseBase?.let { nose ->
                    val (x, y) = mapper.map(nose)
                    drawEmoji(canvas, sticker.emoji, x, y, mapper.scaleLength(track.boundingBox.width().toFloat()) * 0.4f)
                }
            }
            StickerAnchor.MOUTH -> {
                val left = track.mouthLeft
                val right = track.mouthRight
                if (left != null && right != null) {
                    val (x, y) = mapper.map(PointF((left.x + right.x) / 2f, (left.y + right.y) / 2f))
                    val mouthSpan = mapper.scaleLength(kotlin.math.hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble()).toFloat())
                    drawEmoji(canvas, sticker.emoji, x, y, mouthSpan * 2.4f)
                }
            }
            StickerAnchor.BEHIND_HEAD -> {
                val box = track.boundingBox
                val faceWidthOnScreen = mapper.scaleLength(box.width().toFloat())
                val (x, y) = mapper.map(PointF(box.exactCenterX() - box.width() * 0.6f, box.exactCenterY() - box.height() * 0.3f))
                drawEmoji(canvas, sticker.emoji, x, y, faceWidthOnScreen * 1.6f)
            }
            StickerAnchor.FULL_SCREEN_TINT, StickerAnchor.FACE_SMOOTH -> {}
        }
    }

    private fun drawEmoji(canvas: Canvas, emoji: String, x: Float, y: Float, sizePx: Float) {
        if (sizePx <= 0f || sizePx.isNaN()) return
        val paint = Paint().apply {
            textSize = sizePx
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val metrics = paint.fontMetrics
        val baselineOffset = -(metrics.ascent + metrics.descent) / 2f
        canvas.drawText(emoji, x, y + baselineOffset, paint)
    }
}
