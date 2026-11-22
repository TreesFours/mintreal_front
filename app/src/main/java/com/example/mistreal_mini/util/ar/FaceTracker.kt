package com.example.mistreal_mini.util.ar

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live-frame face tracking for the AR filter camera — a separate detector
 * instance from [com.example.mistreal_mini.util.FaceGuard], which is tuned
 * for one-shot biometric-gate accuracy (PERFORMANCE_MODE_ACCURATE), not the
 * per-frame latency a live camera preview needs.
 *
 * Runs two ML Kit clients: [fastDetector] (sparse named landmarks only,
 * PERFORMANCE_MODE_FAST — Phase 1's sticker anchoring) and [meshDetector]
 * (landmarks + full contours, ML Kit's most expensive mode — Phase 2's AI
 * FaceSwap triangulated warp, which needs the ~130 contour points for a
 * mesh). Callers only pay the expensive mode's cost once a swap target is
 * actually prepared and rendering; otherwise everything still runs through
 * [fastDetector] exactly as Phase 1 did.
 */
@Singleton
class FaceTracker @Inject constructor() {
    private val fastDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
    )

    private val meshDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
    )

    /**
     * Must close [imageProxy] itself once ML Kit is done with it (success or
     * failure) — the underlying media Image has to stay valid for the
     * duration of detection, unlike a plain bitmap grab. [onResult] is
     * invoked on ML Kit's own callback thread, not the analyzer's executor.
     * [useContours] selects [meshDetector] over [fastDetector] — only pass
     * true while an AI FaceSwap target is actually prepared and live.
     */
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun analyze(imageProxy: ImageProxy, useContours: Boolean, onResult: (FaceTrackResult?) -> Unit) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            onResult(null)
            return
        }
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        // Raw buffer dimensions, deliberately NOT InputImage.width/height — those
        // are ambiguous about whether rotation was already folded in across ML
        // Kit versions. Keeping rotationDegrees separate lets the overlay's
        // coordinate mapping (ArStickerOverlay) apply it explicitly and
        // unambiguously once that math is validated on a real device.
        val rawWidth = mediaImage.width
        val rawHeight = mediaImage.height
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val detector = if (useContours) meshDetector else fastDetector
        detector.process(inputImage)
            .addOnSuccessListener { faces -> onResult(faces.firstOrNull()?.let { toTrackResult(it, rawWidth, rawHeight, rotationDegrees) }) }
            .addOnFailureListener { e ->
                Timber.w(e, "Live face detection failed")
                onResult(null)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** One-shot mesh detection on an already-upright still bitmap — used to precompute an AI FaceSwap target's own face mesh when it's selected. */
    suspend fun detectStill(bitmap: Bitmap): FaceTrackResult? = try {
        val faces = meshDetector.process(InputImage.fromBitmap(bitmap, 0)).await()
        faces.firstOrNull()?.let { toTrackResult(it, bitmap.width, bitmap.height, 0) }
    } catch (e: Exception) {
        Timber.w(e, "Still face detection failed for FaceSwap target")
        null
    }

    /** Blocking variant of [detectStill] for call sites that are already off the main thread and can't suspend — namely [androidx.camera.core.ImageCapture]'s plain callback interface, used when baking a swap into a just-captured photo. */
    fun detectStillBlocking(bitmap: Bitmap): FaceTrackResult? = try {
        val face = Tasks.await(meshDetector.process(InputImage.fromBitmap(bitmap, 0))).firstOrNull()
        face?.let { toTrackResult(it, bitmap.width, bitmap.height, 0) }
    } catch (e: Exception) {
        Timber.w(e, "Still face detection failed for FaceSwap bake")
        null
    }

    private fun toTrackResult(face: Face, imageWidth: Int, imageHeight: Int, rotationDegrees: Int): FaceTrackResult {
        fun point(type: Int): android.graphics.PointF? = face.getLandmark(type)?.position
        return FaceTrackResult(
            boundingBox = face.boundingBox,
            leftEye = point(FaceLandmark.LEFT_EYE),
            rightEye = point(FaceLandmark.RIGHT_EYE),
            noseBase = point(FaceLandmark.NOSE_BASE),
            mouthLeft = point(FaceLandmark.MOUTH_LEFT),
            mouthRight = point(FaceLandmark.MOUTH_RIGHT),
            mouthBottom = point(FaceLandmark.MOUTH_BOTTOM),
            headEulerAngleY = face.headEulerAngleY,
            headEulerAngleZ = face.headEulerAngleZ,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rotationDegrees = rotationDegrees,
            timestampMs = System.currentTimeMillis(),
            mesh = faceMeshPoints(face)
        )
    }
}
