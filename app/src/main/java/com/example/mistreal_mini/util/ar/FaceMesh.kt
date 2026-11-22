package com.example.mistreal_mini.util.ar

import android.graphics.PointF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour

/**
 * A face's dense landmark set, in the order [MESH_CONTOURS] walks ML Kit's
 * contours. [allPoints]' order/length is what lets a triangulation computed
 * once on one face (the AI FaceSwap target) be reused every live frame
 * against a completely different face (whoever's in front of the camera) —
 * both lists are always built by the exact same walk, so index `i` means
 * the same anatomical point in either face.
 */
data class FaceMesh(val allPoints: List<PointF>, val faceOvalPoints: List<PointF>)

private val MESH_CONTOURS = listOf(
    FaceContour.FACE,
    FaceContour.LEFT_EYEBROW_TOP,
    FaceContour.LEFT_EYEBROW_BOTTOM,
    FaceContour.RIGHT_EYEBROW_TOP,
    FaceContour.RIGHT_EYEBROW_BOTTOM,
    FaceContour.LEFT_EYE,
    FaceContour.RIGHT_EYE,
    FaceContour.NOSE_BRIDGE,
    FaceContour.NOSE_BOTTOM,
    FaceContour.UPPER_LIP_TOP,
    FaceContour.UPPER_LIP_BOTTOM,
    FaceContour.LOWER_LIP_TOP,
    FaceContour.LOWER_LIP_BOTTOM
)

/**
 * Returns null (rather than a partially-filled mesh) if any one contour is
 * missing — e.g. extreme head angle or partial occlusion — since a partial
 * list would desync the index correspondence every other part of the swap
 * pipeline depends on.
 */
fun faceMeshPoints(face: Face): FaceMesh? {
    val all = mutableListOf<PointF>()
    var ovalPoints: List<PointF>? = null
    for (type in MESH_CONTOURS) {
        val contourPoints = face.getContour(type)?.points
        if (contourPoints.isNullOrEmpty()) return null
        if (type == FaceContour.FACE) ovalPoints = contourPoints
        all.addAll(contourPoints)
    }
    return FaceMesh(all, ovalPoints ?: return null)
}
