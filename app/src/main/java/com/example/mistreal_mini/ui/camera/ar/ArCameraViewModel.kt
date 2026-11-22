package com.example.mistreal_mini.ui.camera.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PointF
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.local.entity.VerifiedFaceEntity
import com.example.mistreal_mini.data.repository.VerifiedFaceRepository
import com.example.mistreal_mini.util.ar.DelaunayTriangulator
import com.example.mistreal_mini.util.ar.FaceSwapCompositor
import com.example.mistreal_mini.util.ar.FaceTrackResult
import com.example.mistreal_mini.util.ar.FaceTracker
import com.example.mistreal_mini.util.ar.StickerBitmapRenderer
import com.example.mistreal_mini.util.ar.VideoFilterBaker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * A verified identity's face, precomputed once when it's selected as an AI
 * FaceSwap target: its own bitmap, the ordered mesh points
 * [com.example.mistreal_mini.util.ar.faceMeshPoints] produced for it, and a
 * Delaunay triangulation of those points computed once here and reused
 * every live frame against whoever's actually in front of the camera (same
 * point count/order, different positions) — see
 * [com.example.mistreal_mini.util.ar.FaceSwapCompositor].
 */
data class PreparedSwapTarget(
    val bitmap: Bitmap,
    val points: List<PointF>,
    val ovalPoints: List<PointF>,
    val triangles: List<IntArray>
)

/**
 * Owns live face-tracking state + filter/swap-target selection for
 * [com.example.mistreal_mini.ui.camera.ArFilterCameraScreen]. Phase 2: once
 * a swap target is selected, its reference photo is detected+triangulated
 * once ([selectSwapTarget]), [preparedSwapTarget] goes non-null, and
 * [onFrame] switches the live tracker into contour mode so
 * [FaceSwapCompositor] has what it needs to render the warp every frame.
 */
@HiltViewModel
class ArCameraViewModel @Inject constructor(
    private val faceTracker: FaceTracker,
    verifiedFaceRepository: VerifiedFaceRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val verifiedFaces: StateFlow<List<Pair<VerifiedFaceEntity, Uri>>> =
        verifiedFaceRepository.allFaceUris.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _faceTrack = MutableStateFlow<FaceTrackResult?>(null)
    val faceTrack: StateFlow<FaceTrackResult?> = _faceTrack

    private val _selectedSticker = MutableStateFlow(AR_STICKER_CATALOG.first())
    val selectedSticker: StateFlow<ArSticker> = _selectedSticker

    private val _swapTarget = MutableStateFlow<Pair<VerifiedFaceEntity, Uri>?>(null)
    val swapTarget: StateFlow<Pair<VerifiedFaceEntity, Uri>?> = _swapTarget

    private val _preparedSwapTarget = MutableStateFlow<PreparedSwapTarget?>(null)
    val preparedSwapTarget: StateFlow<PreparedSwapTarget?> = _preparedSwapTarget

    private val _isPreparingSwap = MutableStateFlow(false)
    val isPreparingSwap: StateFlow<Boolean> = _isPreparingSwap

    private val _swapPrepError = MutableStateFlow<String?>(null)
    val swapPrepError: StateFlow<String?> = _swapPrepError

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera

    /** Called from the [androidx.camera.core.ImageAnalysis.Analyzer] on its own executor thread. */
    fun onFrame(imageProxy: ImageProxy) {
        val useContours = _preparedSwapTarget.value != null
        faceTracker.analyze(imageProxy, useContours) { result -> _faceTrack.value = result }
    }

    fun selectSticker(sticker: ArSticker) {
        _selectedSticker.value = sticker
    }

    fun selectSwapTarget(target: Pair<VerifiedFaceEntity, Uri>?) {
        _swapTarget.value = target
        _preparedSwapTarget.value = null
        _swapPrepError.value = null
        if (target == null) return

        _isPreparingSwap.value = true
        viewModelScope.launch {
            try {
                val prepared = prepareSwapTarget(target.second)
                _preparedSwapTarget.value = prepared
                if (prepared == null) {
                    _swapPrepError.value = "Couldn't find a clear face in that identity's reference photo."
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to prepare AI FaceSwap target")
                _swapPrepError.value = "Failed to prepare that identity for FaceSwap."
            } finally {
                _isPreparingSwap.value = false
            }
        }
    }

    private suspend fun prepareSwapTarget(uri: Uri): PreparedSwapTarget? {
        val bitmap = appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val mesh = faceTracker.detectStill(bitmap)?.mesh ?: return null
        val triangles = DelaunayTriangulator.triangulate(mesh.allPoints)
        return PreparedSwapTarget(bitmap, mesh.allPoints, mesh.faceOvalPoints, triangles)
    }

    /**
     * Bakes both the AI FaceSwap (if a target is prepared) and the sticker
     * filter onto a freshly captured still, so the saved photo matches the
     * live preview. Runs a fresh still-detection pass on [source] itself
     * (rather than reusing the live [faceTrack], which is in the lower-res
     * ImageAnalysis frame's coordinate space) since a captured JPEG is
     * full-resolution and detecting directly on it avoids an extra
     * coordinate-mapping error surface. Safe to call off the main thread —
     * this is invoked from [androidx.camera.core.ImageCapture]'s plain
     * (non-suspending) save callback.
     */
    fun bakeCapturedPhoto(source: Bitmap, faceTrack: FaceTrackResult?, sticker: ArSticker, isFrontCamera: Boolean): Bitmap {
        val prepared = _preparedSwapTarget.value
        val swapped = if (prepared == null) {
            source
        } else {
            val stillMesh = faceTracker.detectStillBlocking(source)?.mesh
            if (stillMesh == null) {
                source
            } else {
                val composite = source.copy(Bitmap.Config.ARGB_8888, true)
                val destColor = FaceSwapCompositor.averageColor(source, stillMesh.faceOvalPoints)
                val srcColor = FaceSwapCompositor.averageColor(prepared.bitmap, prepared.ovalPoints)
                FaceSwapCompositor.drawSwap(
                    canvas = Canvas(composite),
                    sourceBitmap = prepared.bitmap,
                    sourcePoints = prepared.points,
                    destPoints = stillMesh.allPoints,
                    triangles = prepared.triangles,
                    destOvalPoints = stillMesh.faceOvalPoints,
                    colorGain = FaceSwapCompositor.colorGainFrom(destColor, srcColor)
                )
                composite
            }
        }
        return StickerBitmapRenderer.bake(swapped, faceTrack, sticker, isFrontCamera)
    }

    /**
     * Bakes the current sticker/AI FaceSwap filter into a just-recorded
     * video file, via [VideoFilterBaker]. Returns [rawUri] unchanged if
     * nothing is selected (nothing to bake) or if baking fails — a failed
     * bake should never lose the user's actual recording.
     */
    suspend fun bakeRecordedVideo(rawUri: Uri): Uri {
        val baked = try {
            VideoFilterBaker.bake(
                context = appContext,
                sourceUri = rawUri,
                faceTracker = faceTracker,
                sticker = _selectedSticker.value,
                isFrontCamera = _isFrontCamera.value,
                preparedSwapTarget = _preparedSwapTarget.value
            )
        } catch (e: Exception) {
            Timber.e(e, "Video filter bake failed for %s", rawUri)
            null
        }
        return baked ?: rawUri
    }

    fun toggleCameraFacing() {
        _isFrontCamera.value = !_isFrontCamera.value
        _faceTrack.value = null // stale track from the old facing would misplace stickers for a frame
    }
}
