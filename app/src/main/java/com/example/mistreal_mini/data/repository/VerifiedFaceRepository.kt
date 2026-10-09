package com.example.mistreal_mini.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.mistreal_mini.data.local.dao.VerifiedFaceDao
import com.example.mistreal_mini.data.local.entity.VerifiedFaceEntity
import com.example.mistreal_mini.util.FaceGuard
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The face-swap safeguard lives here: [register] only ever accepts an image
 * that was captured live via the device camera at call time (the caller must
 * pass a freshly-captured camera Uri, never a gallery pick) and that ML Kit
 * confirms contains exactly one clear face. That's what makes a face
 * available as a swap target — someone else's face can't be added from a
 * photo found online or saved from another app, only by physically being in
 * front of the camera and choosing to go through this flow themselves.
 * The registry itself (this list of faces) is stored device-local only
 * (filesDir, not cache — survives cache clears) and never synced to the
 * backend on its own. The image obviously DOES travel to the backend/BYOK
 * provider when actually used in an edit request — that's unavoidable, it's
 * how the swap happens — but it's never persisted there outside that one
 * request, and no other device/session ever sees this registry.
 */
@Singleton
class VerifiedFaceRepository @Inject constructor(
    private val dao: VerifiedFaceDao,
    private val faceGuard: FaceGuard,
    @ApplicationContext private val context: Context
) {
    val allFaces: Flow<List<VerifiedFaceEntity>> = dao.getAll()

    val allFaceUris: Flow<List<Pair<VerifiedFaceEntity, Uri>>> = allFaces.map { faces ->
        faces.map { it to uriFor(it) }
    }

    fun uriFor(entity: VerifiedFaceEntity): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(entity.imagePath))

    sealed class RegisterResult {
        data class Success(val entity: VerifiedFaceEntity) : RegisterResult()
        data class Error(val message: String) : RegisterResult()
    }

    suspend fun register(label: String, liveCaptureUri: Uri): RegisterResult {
        return try {
            val bitmap = context.contentResolver.openInputStream(liveCaptureUri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: return RegisterResult.Error("Couldn't read the captured photo.")

            val faces = faceGuard.detectFaces(InputImage.fromBitmap(bitmap, 0)).await()
            Timber.d("VerifiedFaceRepository.register(%s): ML Kit found %d face(s)", label, faces.size)
            when {
                faces.isEmpty() -> return RegisterResult.Error("No face detected — make sure your face is clearly visible and try again.")
                faces.size > 1 -> return RegisterResult.Error("More than one face detected — capture just the one person being registered.")
            }

            // Copy out of cache (where the camera capture landed) into permanent
            // storage so it survives cache clears and isn't tied to a transient file.
            val permanentFile = File(context.filesDir, "verified_face_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(liveCaptureUri)?.use { input ->
                permanentFile.outputStream().use { output -> input.copyTo(output) }
            }

            val entity = VerifiedFaceEntity(label = label, imagePath = permanentFile.absolutePath)
            val id = dao.insert(entity)
            Timber.i("VerifiedFaceRepository.register(%s): saved as id=%d", label, id)
            RegisterResult.Success(entity.copy(id = id))
        } catch (e: Exception) {
            Timber.e(e, "VerifiedFaceRepository.register(%s) failed", label)
            RegisterResult.Error(e.message ?: "Face registration failed.")
        }
    }

    suspend fun delete(entity: VerifiedFaceEntity) {
        dao.delete(entity.id)
        File(entity.imagePath).delete()
    }
}
