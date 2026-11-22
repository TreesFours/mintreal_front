package com.example.mistreal_mini.util.ar

import android.graphics.PointF

/**
 * Maps a point from a [FaceTrackResult]'s raw ImageAnalysis frame space into
 * an arbitrary target pixel space of size [targetWidth] x [targetHeight],
 * assuming crop-to-fill centering (matching PreviewView's default
 * FILL_CENTER scale type) and mirroring horizontally for the front camera.
 * Shared by the live Compose overlay
 * ([com.example.mistreal_mini.ui.camera.ar.ArStickerOverlay]) and the
 * captured-still baking step ([StickerBitmapRenderer]) so the saved photo
 * matches what was on screen. Unverified against a real device — see the
 * class doc on [FaceTrackResult]'s callers for why.
 */
class FrameMapper(
    track: FaceTrackResult,
    private val targetWidth: Float,
    private val targetHeight: Float,
    private val isFrontCamera: Boolean
) {
    private val rotation = ((track.rotationDegrees % 360) + 360) % 360
    private val rawWidth = track.imageWidth
    private val rawHeight = track.imageHeight
    private val uprightWidth: Int
    private val uprightHeight: Int

    init {
        val swapped = rotation == 90 || rotation == 270
        uprightWidth = if (swapped) rawHeight else rawWidth
        uprightHeight = if (swapped) rawWidth else rawHeight
    }

    private val scale = maxOf(targetWidth / uprightWidth, targetHeight / uprightHeight)
    private val offsetX = (targetWidth - uprightWidth * scale) / 2f
    private val offsetY = (targetHeight - uprightHeight * scale) / 2f

    fun scaleLength(raw: Float) = raw * scale

    /** Returns the mapped (x, y) in target pixel space. */
    fun map(point: PointF): Pair<Float, Float> {
        val (ux, uy) = when (rotation) {
            90 -> (rawHeight - point.y) to point.x
            180 -> (rawWidth - point.x) to (rawHeight - point.y)
            270 -> point.y to (rawWidth - point.x)
            else -> point.x to point.y
        }
        val mirroredUx = if (isFrontCamera) uprightWidth - ux else ux
        return (mirroredUx * scale + offsetX) to (uy * scale + offsetY)
    }
}
