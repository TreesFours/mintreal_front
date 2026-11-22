package com.example.mistreal_mini.util.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import androidx.compose.ui.graphics.toArgb
import com.example.mistreal_mini.ui.camera.ar.ArSticker
import com.example.mistreal_mini.ui.camera.ar.StickerAnchor

/**
 * Bakes the sticker/tint the user had selected onto a freshly captured
 * still, so the saved photo matches what the live preview showed — mirrors
 * [com.example.mistreal_mini.ui.camera.ar.ArStickerOverlay]'s math, but
 * drawing into an android.graphics.Canvas instead of a Compose DrawScope.
 *
 * CameraX's ImageCapture, unlike PreviewView, does NOT mirror front-camera
 * stills by default — the saved JPEG is in true (unmirrored) sensor
 * orientation. So stickers are mapped against the UNMIRRORED bitmap
 * (isFrontCamera=false is passed to [FrameMapper] here on purpose, even for
 * a front-camera shot), and only the finished composite is flipped
 * horizontally for front-camera shots — matching what the user actually
 * composed the shot against in the mirrored preview. This is the standard
 * fix other camera apps use for the same mismatch; still unverified on a
 * real device in this environment.
 */
object StickerBitmapRenderer {

    fun bake(source: Bitmap, faceTrack: FaceTrackResult?, sticker: ArSticker, isFrontCamera: Boolean): Bitmap {
        val composite = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(composite)

        // Tint is independent of anchor — a holiday filter's wash applies
        // whether or not there's also an anchored emoji to draw.
        sticker.tintColor?.let { canvas.drawColor(it.toArgb()) }

        val track = faceTrack
        if (track != null) {
            val mapper = FrameMapper(track, composite.width.toFloat(), composite.height.toFloat(), isFrontCamera = false)
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
                StickerAnchor.FACE_SMOOTH -> {
                    val (left, top) = mapper.map(PointF(track.boundingBox.left.toFloat(), track.boundingBox.top.toFloat()))
                    val (right, bottom) = mapper.map(PointF(track.boundingBox.right.toFloat(), track.boundingBox.bottom.toFloat()))
                    FaceSmoother.smooth(composite, android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt()))
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
                StickerAnchor.FULL_SCREEN_TINT -> {}
            }
        }

        return if (isFrontCamera) flipHorizontally(composite) else composite
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

    private fun flipHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
