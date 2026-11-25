package com.example.mistreal_mini.ui.liveai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * A focused, foreground-only "on a call with the AI" screen — unlike
 * CallService/CallScreen, this has no backing Service, since there's no
 * expectation of a live-AI session surviving the user leaving the screen
 * the way a real phone call needs to (matches Google's own Gemini Live UI:
 * a dedicated screen, not a backgroundable one).
 */
@Composable
fun LiveAiScreen(onBack: () -> Unit, viewModel: LiveAiViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionState by viewModel.sessionState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var missingPermissions by remember { mutableStateOf(false) }
    var cameraOn by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) viewModel.connect() else missingPermissions = true
    }

    // Lets the user hand the live session an existing image/video/PDF to
    // look at, instead of only ever seeing whatever the live camera points
    // at right now. A video/PDF isn't played/read frame-by-frame or page-by-
    // page into the session — that's a much bigger feature — this sends a
    // handful of representative stills (video: start/middle/end frames,
    // PDF: first few pages rendered to images) through the exact same
    // sendVideoFrame pipeline a live camera frame already uses, so the
    // backend needs zero changes.
    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val frames = withContext(Dispatchers.IO) { extractAttachmentFrames(context, uri) }
            if (frames.isEmpty()) {
                Toast.makeText(context, "Couldn't read that file.", Toast.LENGTH_SHORT).show()
            } else {
                frames.forEach { viewModel.sendVideoFrame(it) }
                Toast.makeText(context, "Sent to the AI.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasCamera && hasMic) viewModel.connect()
        else permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    // Leaving this screen always ends the session — there's deliberately no
    // "keep talking to the AI in the background" mode, see the class doc above.
    DisposableEffect(Unit) {
        onDispose { viewModel.disconnect() }
    }

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                missingPermissions -> CenteredMessage("Camera and microphone access are required for Live AI.", onBack)
                sessionState == "error" -> CenteredMessage(errorMessage ?: "Couldn't start the live session.", onBack)
                sessionState == "connecting" -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Connecting to Gemini...")
                    }
                }
                sessionState == "active" -> {
                    if (cameraOn) {
                        CameraFeed(
                            lifecycleOwner = lifecycleOwner,
                            onFrame = viewModel::sendVideoFrame,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            Text("Camera paused — still listening", color = Color.White)
                        }
                    }
                    LiveAiControls(
                        cameraOn = cameraOn,
                        onToggleCamera = { cameraOn = !cameraOn },
                        onAttach = { attachLauncher.launch(arrayOf("image/*", "video/*", "application/pdf")) },
                        onEnd = { viewModel.disconnect(); onBack() }
                    )
                }
                sessionState == "ended" -> CenteredMessage("Session ended", onBack, buttonLabel = "Back")
                else -> {} // idle — connect() already kicked off above, nothing to show yet.
            }
        }
    }
}

@Composable
private fun BoxScope.CenteredMessage(text: String, onBack: () -> Unit, buttonLabel: String = "Back") {
    Column(
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) { Text(buttonLabel) }
    }
}

@Composable
private fun BoxScope.LiveAiControls(cameraOn: Boolean, onToggleCamera: () -> Unit, onAttach: () -> Unit, onEnd: () -> Unit) {
    Row(
        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(onClick = onToggleCamera) {
            Icon(if (cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff, contentDescription = if (cameraOn) "Pause camera" else "Resume camera")
        }
        FilledIconButton(onClick = onAttach) {
            Icon(Icons.Default.AttachFile, contentDescription = "Attach a photo, video, or PDF for the AI to look at")
        }
        FloatingActionButton(onClick = onEnd, containerColor = MaterialTheme.colorScheme.error) {
            Icon(Icons.Default.CallEnd, contentDescription = "End session")
        }
    }
}

/**
 * Turns a picked image/video/PDF into one or more still frames to feed
 * through the live session's existing one-JPEG-at-a-time pipeline. Deliberately
 * capped (a handful of video frames, first few PDF pages) — this is a vision
 * snapshot for a live conversation, not a document reader or video player.
 */
private fun extractAttachmentFrames(context: android.content.Context, uri: Uri): List<android.graphics.Bitmap> {
    val mimeType = context.contentResolver.getType(uri) ?: ""
    return try {
        when {
            mimeType.startsWith("image/") -> {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }?.let { listOf(it) } ?: emptyList()
            }
            mimeType.startsWith("video/") -> {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val timestampsUs = if (durationMs > 0) {
                    listOf(0.1, 0.5, 0.9).map { fraction -> (durationMs * fraction * 1000).toLong() }
                } else {
                    listOf(0L)
                }
                val frames = timestampsUs.mapNotNull { us -> retriever.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }
                retriever.release()
                frames
            }
            mimeType == "application/pdf" -> {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    val pageCount = minOf(renderer.pageCount, 5)
                    val pages = (0 until pageCount).map { i ->
                        val page = renderer.openPage(i)
                        val bitmap = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bitmap
                    }
                    renderer.close()
                    pages
                } ?: emptyList()
            }
            else -> emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Self-view camera preview + the frame feed into Gemini. CameraX, not
 * WebRTC — CallService's capture pipeline is wired directly into a
 * PeerConnection with no hook to intercept raw frames for anything else,
 * confirmed by reading it; CameraX's ImageAnalysis is the direct route.
 * Frames are throttled to ~1/sec: this sends one full JPEG per call over
 * the Live session, not a video codec stream, so matching video framerate
 * would be pure waste — not a documented limit, just an obviously sane one
 * given the one-JPEG-per-request shape of sendVideoRealtime.
 */
@Composable
private fun CameraFeed(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onFrame: (android.graphics.Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var lastSentAtMs by remember { mutableStateOf(0L) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy ->
                    val now = System.currentTimeMillis()
                    if (now - lastSentAtMs >= 1000L) {
                        lastSentAtMs = now
                        try {
                            onFrame(imageProxy.toBitmap())
                        } catch (e: Exception) {
                            // Best-effort — a dropped frame is invisible to the user.
                        }
                    }
                    imageProxy.close()
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    // Best-effort — audio-only still works if binding the camera fails.
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}
