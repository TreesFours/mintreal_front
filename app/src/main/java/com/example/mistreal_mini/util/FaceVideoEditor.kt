package com.example.mistreal_mini.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Video frame extraction + face detection for the "pick which face to
 * swap" flow — extracted out of ChatViewModel, which had this inlined.
 * Pure operation: no chat/trend state, just context + FaceGuard.
 */
@Singleton
class FaceVideoEditor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val faceGuard: FaceGuard
) {
    /**
     * Grabs the video's midpoint frame and runs ML Kit face detection on it,
     * so the UI can show tappable boxes over each detected face — "swap THIS
     * one" instead of a single generic "the main person." Returns the frame
     * plus each face's bounding box as a 0f..1f fraction of the frame (so the
     * UI doesn't need to know the frame's pixel dimensions to draw/hit-test
     * the boxes).
     */
    suspend fun detectFacesInVideo(videoUri: Uri): Pair<Bitmap, List<RectF>>? {
        return try {
            val retriever = MediaMetadataRetriever()
            val frame = try {
                retriever.setDataSource(context, videoUri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.getFrameAtTime((durationMs * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC) // midpoint, ms->us
            } finally {
                retriever.release()
            }
            if (frame == null) {
                Timber.w("detectFacesInVideo: couldn't extract a frame from %s", videoUri)
                return null
            }

            val faces = faceGuard.detectFaces(InputImage.fromBitmap(frame, 0)).await()
            Timber.d("detectFacesInVideo: found %d face(s) in %s", faces.size, videoUri)
            val boxes = faces.map { face ->
                val box = face.boundingBox
                RectF(
                    (box.left.toFloat() / frame.width).coerceIn(0f, 1f),
                    (box.top.toFloat() / frame.height).coerceIn(0f, 1f),
                    (box.right.toFloat() / frame.width).coerceIn(0f, 1f),
                    (box.bottom.toFloat() / frame.height).coerceIn(0f, 1f)
                )
            }
            frame to boxes
        } catch (e: Exception) {
            Timber.e(e, "detectFacesInVideo failed for %s", videoUri)
            null
        }
    }

    /** Highlights the tapped face's box on the frame, for use as the face-swap target reference image. */
    suspend fun markFaceTarget(frame: Bitmap, box: RectF): Uri? =
        MediaEditorUtil.rasterizeFaceTargetMarker(context, frame, box)
}
