package com.example.mistreal_mini.util

import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Headless SOS photo capture — no Preview/PreviewView anywhere, just the
 * ImageCapture use case bound to a throwaway [LifecycleOwner] this class
 * drives itself, since there's no Activity/Fragment UI to bind to at the
 * moment an SOS fires. Gated by the existing CAMERA runtime permission
 * already requested app-wide at startup (MainActivity.checkAndRequestPermissions)
 * — this never prompts, it just no-ops if that permission was ever denied,
 * same fail-open posture as the rest of the SOS capture pipeline (a missing
 * photo must never block the alert itself from firing).
 */
@Singleton
class SosCameraCapture @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val executor: Executor = Executors.newSingleThreadExecutor()

    /** Never throws — returns null on any failure (permission denied, no camera, timeout, capture error) so a photo problem never blocks the SOS alert itself. */
    suspend fun capturePhoto(): File? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            withTimeoutOrNull(8_000L) { captureInternal() }
        } catch (e: Exception) {
            Timber.e(e, "SOS photo capture failed")
            null
        }
    }

    private suspend fun captureInternal(): File? {
        val cameraProvider = awaitCameraProvider() ?: return null
        val lifecycleOwner = ManualLifecycleOwner()
        lifecycleOwner.start()

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        return try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, imageCapture)
            val file = File(context.cacheDir, "sos_photo_${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

            val saved = suspendCancellableCoroutine<Boolean> { cont ->
                imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (cont.isActive) cont.resume(true)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Timber.e(exception, "SOS photo capture error")
                        if (cont.isActive) cont.resume(false)
                    }
                })
            }
            if (saved && file.exists() && file.length() > 0) file else null
        } catch (e: Exception) {
            Timber.e(e, "SOS photo capture bind/takePicture failed")
            null
        } finally {
            cameraProvider.unbindAll()
            lifecycleOwner.stop()
        }
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider? = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                if (cont.isActive) cont.resume(future.get())
            } catch (e: Exception) {
                Timber.e(e, "ProcessCameraProvider unavailable")
                if (cont.isActive) cont.resume(null)
            }
        }, executor)
    }
}

/** Minimal driveable [LifecycleOwner] for binding CameraX outside any Activity/Fragment. */
private class ManualLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    fun start() {
        registry.currentState = Lifecycle.State.STARTED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
