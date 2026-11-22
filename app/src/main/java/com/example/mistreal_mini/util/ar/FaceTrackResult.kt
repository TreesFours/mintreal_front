package com.example.mistreal_mini.util.ar

import android.graphics.PointF
import android.graphics.Rect

/**
 * One frame's worth of ML Kit face tracking, in the ImageAnalysis frame's
 * own pixel coordinate space (not yet mapped into the Compose preview's
 * displayed size — [com.example.mistreal_mini.ui.camera.ar.ArStickerOverlay]
 * owns that mapping, since it needs the PreviewView's actual rendered size
 * to do it, which this class deliberately doesn't know about).
 */
data class FaceTrackResult(
    val boundingBox: Rect,
    val leftEye: PointF?,
    val rightEye: PointF?,
    val noseBase: PointF?,
    val mouthLeft: PointF?,
    val mouthRight: PointF?,
    val mouthBottom: PointF?,
    val headEulerAngleY: Float,
    val headEulerAngleZ: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val rotationDegrees: Int,
    val timestampMs: Long,
    /** Dense contour mesh for AI FaceSwap — only populated when [FaceTracker] was asked to run in contour mode; null while it's running its cheaper sticker-anchoring mode. */
    val mesh: FaceMesh? = null
)
