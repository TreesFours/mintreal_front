package com.example.mistreal_mini.ui.camera.ar

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.example.mistreal_mini.util.ar.FaceSwapCompositor
import com.example.mistreal_mini.util.ar.FaceTrackResult
import com.example.mistreal_mini.util.ar.FrameMapper

/**
 * Draws the AI FaceSwap warp (if a target is prepared) and then
 * stickers/tints on top of that, over the live camera
 * [PreviewView][androidx.camera.view.PreviewView], positioned from the
 * latest [FaceTrackResult]. Both are plain Canvas overlays, not GL — the
 * swap works because it's drawn fully opaque, in the same screen position
 * as the live tracked face, which visually occludes the real face
 * underneath without needing pixel-level access to the camera feed itself.
 *
 * Coordinate mapping is shared with the captured-still baking step via
 * [FrameMapper] — unverified against a real device, see that class's doc.
 */
@Composable
fun ArStickerOverlay(
    faceTrack: FaceTrackResult?,
    sticker: ArSticker,
    isFrontCamera: Boolean,
    preparedSwapTarget: PreparedSwapTarget? = null,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val track = faceTrack
        val mesh = track?.mesh
        if (preparedSwapTarget != null && track != null && mesh != null) {
            val swapMapper = FrameMapper(track, size.width, size.height, isFrontCamera)
            fun mapAll(points: List<PointF>) = points.map { point ->
                val (x, y) = swapMapper.map(point)
                PointF(x, y)
            }
            FaceSwapCompositor.drawSwap(
                canvas = drawContext.canvas.nativeCanvas,
                sourceBitmap = preparedSwapTarget.bitmap,
                sourcePoints = preparedSwapTarget.points,
                destPoints = mapAll(mesh.allPoints),
                triangles = preparedSwapTarget.triangles,
                destOvalPoints = mapAll(mesh.faceOvalPoints)
            )
        }

        // Tint is independent of anchor now — a holiday filter can wash the
        // whole frame AND pin an emoji above the face in the same pass.
        sticker.tintColor?.let { drawRect(color = it) }

        if (sticker.anchor == StickerAnchor.FULL_SCREEN_TINT || sticker.anchor == StickerAnchor.FACE_SMOOTH) {
            return@Canvas // FACE_SMOOTH has no live-preview effect — see its doc on StickerAnchor
        }
        if (track == null) return@Canvas
        val mapped = FrameMapper(track, size.width, size.height, isFrontCamera)

        when (sticker.anchor) {
            StickerAnchor.ABOVE_FACE -> {
                val box = track.boundingBox
                val (x, y) = mapped.map(PointF(box.exactCenterX(), box.top.toFloat()))
                val faceWidthOnScreen = mapped.scaleLength(box.width().toFloat())
                drawEmoji(this, sticker.emoji, Offset(x, y), faceWidthOnScreen * 0.9f)
            }
            StickerAnchor.EYES -> {
                val left = track.leftEye
                val right = track.rightEye
                if (left != null && right != null) {
                    val (x, y) = mapped.map(PointF((left.x + right.x) / 2f, (left.y + right.y) / 2f))
                    val eyeSpan = mapped.scaleLength(kotlin.math.hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble()).toFloat())
                    drawEmoji(this, sticker.emoji, Offset(x, y), eyeSpan * 2.2f)
                }
            }
            StickerAnchor.NOSE_TIP -> {
                track.noseBase?.let { nose ->
                    val (x, y) = mapped.map(nose)
                    val faceWidthOnScreen = mapped.scaleLength(track.boundingBox.width().toFloat())
                    drawEmoji(this, sticker.emoji, Offset(x, y), faceWidthOnScreen * 0.4f)
                }
            }
            StickerAnchor.MOUTH -> {
                val left = track.mouthLeft
                val right = track.mouthRight
                if (left != null && right != null) {
                    val (x, y) = mapped.map(PointF((left.x + right.x) / 2f, (left.y + right.y) / 2f))
                    val mouthSpan = mapped.scaleLength(kotlin.math.hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble()).toFloat())
                    drawEmoji(this, sticker.emoji, Offset(x, y), mouthSpan * 2.4f)
                }
            }
            StickerAnchor.BEHIND_HEAD -> {
                val box = track.boundingBox
                val faceWidthOnScreen = mapped.scaleLength(box.width().toFloat())
                val (x, y) = mapped.map(PointF(box.exactCenterX() - box.width() * 0.6f, box.exactCenterY() - box.height() * 0.3f))
                drawEmoji(this, sticker.emoji, Offset(x, y), faceWidthOnScreen * 1.6f)
            }
            StickerAnchor.FULL_SCREEN_TINT, StickerAnchor.FACE_SMOOTH -> {} // handled above
        }
    }
}

private fun drawEmoji(scope: DrawScope, emoji: String, center: Offset, sizePx: Float) {
    if (sizePx <= 0f || sizePx.isNaN()) return
    scope.drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            textSize = sizePx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val metrics = paint.fontMetrics
        val baselineOffset = -(metrics.ascent + metrics.descent) / 2f
        drawText(emoji, center.x, center.y + baselineOffset, paint)
    }
}
