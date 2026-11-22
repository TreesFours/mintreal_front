package com.example.mistreal_mini.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.ui.camera.ar.ArCameraViewModel
import com.example.mistreal_mini.ui.camera.ar.ArStickerOverlay
import com.example.mistreal_mini.ui.camera.ar.AR_STICKER_CATALOG
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Live AR filter camera: real CameraX preview, real ML Kit face tracking,
 * sticker filters pinned to the tracked face, a live AI FaceSwap warp (see
 * [ArStickerOverlay] / [ArCameraViewModel.preparedSwapTarget] — ML Kit's
 * dense face contours triangulated once per target and redrawn every frame
 * against whoever's in front of the camera), and real video recording
 * (tap the shutter for a photo, hold it to record — Snapchat/Instagram
 * style, via [awaitEachGesture]/[waitForUpOrCancellation]).
 *
 * Recorded video now gets the sticker/FaceSwap filter baked into the saved
 * file too, via [ArCameraViewModel.bakeRecordedVideo] →
 * [com.example.mistreal_mini.util.ar.VideoFilterBaker] (Media3 Transformer's
 * `OverlayEffect`, not hand-written GL — see that file's doc for the real
 * tradeoffs: face detection is sampled at ~10fps rather than truly every
 * frame, and this is the least-verified piece of the whole AR camera —
 * no device or real recorded clip to confirm the bake actually looks right
 * end to end). Shows a brief "baking filters…" spinner after recording
 * stops, before handing the final file off.
 *
 * Known limitations, unverified on a real device: triangle seams are
 * hard-edged (though now feathered — see [com.example.mistreal_mini.util.ar.FaceSwapCompositor])
 * and there's no live-preview color match yet. Binding `Preview` +
 * `ImageAnalysis` + `ImageCapture` + `VideoCapture` concurrently isn't
 * guaranteed on every device's camera HAL, so the bind below falls back to
 * dropping live face tracking (not video) if the full combination throws.
 */
@Composable
fun ArFilterCameraScreen(
    onBack: () -> Unit,
    onMediaCaptured: (Uri, Boolean) -> Unit, // (uri, isVideo)
    viewModel: ArCameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val faceTrack by viewModel.faceTrack.collectAsState()
    val selectedSticker by viewModel.selectedSticker.collectAsState()
    val swapTarget by viewModel.swapTarget.collectAsState()
    val preparedSwapTarget by viewModel.preparedSwapTarget.collectAsState()
    val isPreparingSwap by viewModel.isPreparingSwap.collectAsState()
    val swapPrepError by viewModel.swapPrepError.collectAsState()
    val verifiedFaces by viewModel.verifiedFaces.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()

    var overlayText by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }
    var showFaceSwapDialog by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Audio is optional for video: denying it just means recordings come out muted
    // instead of blocking video capture entirely.
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasAudioPermission) audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
            onMediaCaptured(uri, isVideo)
        }
    }

    val previewView = remember { PreviewView(context) }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, viewModel::onFrame) }
    }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }

    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var isBakingVideo by remember { mutableStateOf(false) }
    var recordingElapsedSec by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isRecordingVideo) {
        recordingElapsedSec = 0
        while (isRecordingVideo) {
            delay(1000)
            recordingElapsedSec++
        }
    }

    LaunchedEffect(hasCameraPermission, isFrontCamera) {
        if (!hasCameraPermission) return@LaunchedEffect
        val cameraProvider = awaitCameraProvider(context) ?: return@LaunchedEffect
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis, imageCapture, videoCapture)
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind AR camera with live face tracking + video; retrying without face tracking")
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to bind AR camera even without face tracking")
            }
        }
    }

    fun startVideoRecording() {
        if (isRecordingVideo || isCapturing) return
        val videoFile = File(context.cacheDir, "ar_video_${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()
        var pending = videoCapture.output.prepareRecording(context, outputOptions)
        if (hasAudioPermission) pending = pending.withAudioEnabled()
        isRecordingVideo = true
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                isRecordingVideo = false
                activeRecording = null
                if (!event.hasError()) {
                    val rawUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
                    isBakingVideo = true
                    coroutineScope.launch {
                        // bakeRecordedVideo falls back to rawUri unchanged on any
                        // failure — a bad bake must never lose the real recording.
                        val finalUri = viewModel.bakeRecordedVideo(rawUri)
                        isBakingVideo = false
                        onMediaCaptured(finalUri, true)
                    }
                } else {
                    Timber.e("AR video recording finalized with error code ${event.error}")
                }
            }
        }
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
    }

    fun capturePhoto() {
        if (isCapturing) return
        isCapturing = true
        val file = File(context.cacheDir, "ar_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        val frozenTrack = faceTrack
        val frozenSticker = selectedSticker
        val frozenIsFrontCamera = isFrontCamera
        imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                try {
                    val raw = BitmapFactory.decodeFile(file.absolutePath)
                    if (raw != null) {
                        val baked = viewModel.bakeCapturedPhoto(raw, frozenTrack, frozenSticker, frozenIsFrontCamera)
                        FileOutputStream(file).use { out -> baked.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out) }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to bake sticker/FaceSwap onto captured AR photo")
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                isCapturing = false
                onMediaCaptured(uri, false)
            }

            override fun onError(exception: ImageCaptureException) {
                Timber.e(exception, "AR photo capture failed")
                isCapturing = false
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            ArStickerOverlay(
                faceTrack = faceTrack,
                sticker = selectedSticker,
                isFrontCamera = isFrontCamera,
                preparedSwapTarget = preparedSwapTarget,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera access is required for AR filters.", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant Camera Access") }
            }
        }

        if (swapTarget != null) {
            GlassPanel(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, tint, label) = when {
                        isPreparingSwap -> Triple(Icons.Default.AutoAwesome, Color.Yellow, "PREPARING FACESWAP: ${swapTarget!!.first.label.uppercase()}…")
                        swapPrepError != null -> Triple(Icons.Default.AutoAwesome, Color.Red, "FACESWAP FAILED — ${swapPrepError}")
                        preparedSwapTarget != null -> Triple(Icons.Default.VerifiedUser, Color.Green, "FACESWAP LIVE: ${swapTarget!!.first.label.uppercase()}")
                        else -> Triple(Icons.Default.VerifiedUser, Color.Gray, "FACESWAP TARGET: ${swapTarget!!.first.label.uppercase()}")
                    }
                    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (overlayText.isNotBlank()) {
            Text(
                text = overlayText,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Top Action Bar (Close, Flip Camera, Text, Gallery, FaceSwap)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassPanel(modifier = Modifier.size(40.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, "Exit", tint = Color.White)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassPanel(modifier = Modifier.size(40.dp)) {
                    IconButton(onClick = { viewModel.toggleCameraFacing() }) {
                        Icon(Icons.Default.FlipCameraAndroid, "Flip Camera", tint = Color.White)
                    }
                }
                GlassPanel(modifier = Modifier.size(40.dp)) {
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
                    }
                }
                GlassPanel(modifier = Modifier.size(40.dp)) {
                    IconButton(onClick = { showTextInput = !showTextInput }) {
                        Text("Aa", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
                GlassPanel(modifier = Modifier.size(40.dp)) {
                    IconButton(onClick = { showFaceSwapDialog = true }) {
                        Icon(Icons.Default.AutoAwesome, "AI FaceSwap", tint = if (swapTarget != null) Color.Green else Color.Yellow)
                    }
                }
            }
        }

        // Face Swap Picker Dialog
        if (showFaceSwapDialog) {
            AlertDialog(
                onDismissRequest = { showFaceSwapDialog = false },
                title = { Text("AI FaceSwap Identity") },
                text = {
                    Column {
                        Text(
                            "Select a verified identity to target for AI FaceSwap. The swap renders live once a clear face is found in their reference photo — lighting and skin tone won't perfectly match yet, this is a first pass.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (verifiedFaces.isEmpty()) {
                            Text(
                                "No verified face identities registered yet. Capture a selfie in Settings to register your face.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            verifiedFaces.forEach { pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectSwapTarget(pair)
                                            showFaceSwapDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (swapTarget?.first?.id == pair.first.id) Icons.Default.CheckCircle else Icons.Default.Face,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(pair.first.label, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFaceSwapDialog = false }) { Text("Close") }
                },
                dismissButton = {
                    if (swapTarget != null) {
                        TextButton(onClick = { viewModel.selectSwapTarget(null); showFaceSwapDialog = false }) { Text("Clear Selection") }
                    }
                }
            )
        }

        // Text Input Box Overlay
        if (showTextInput) {
            OutlinedTextField(
                value = overlayText,
                onValueChange = { overlayText = it },
                placeholder = { Text("Add caption...", color = Color.Gray) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        }

        // Bottom Filter Carousel & Shutter Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlassPanel(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    selectedSticker.name.uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                items(AR_STICKER_CATALOG) { sticker ->
                    val isSel = sticker.id == selectedSticker.id
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isSel) Color.Cyan.copy(alpha = 0.3f) else Color.DarkGray.copy(alpha = 0.5f))
                            .border(2.dp, if (isSel) Color.Cyan else Color.Transparent, CircleShape)
                            .clickable { viewModel.selectSticker(sticker) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(sticker.emoji, fontSize = 24.sp)
                    }
                }
            }

            if (isRecordingVideo) {
                Text(
                    text = "REC %d:%02d".format(recordingElapsedSec / 60, recordingElapsedSec % 60),
                    color = Color.Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            } else if (isBakingVideo) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Baking filters into your video…", color = Color.White, fontSize = 11.sp)
                }
            }

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(4.dp, if (isRecordingVideo) Color.Red else Color.White, CircleShape)
                    .pointerInput(hasCameraPermission, isCapturing, isBakingVideo) {
                        if (!hasCameraPermission || isBakingVideo) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            val releasedBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation()
                            }
                            if (releasedBeforeLongPress != null) {
                                if (!isCapturing) capturePhoto()
                            } else {
                                startVideoRecording()
                                waitForUpOrCancellation()
                                stopVideoRecording()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isRecordingVideo) 34.dp else 60.dp)
                        .clip(if (isRecordingVideo) RoundedCornerShape(8.dp) else CircleShape)
                        .background(
                            when {
                                isRecordingVideo -> Color.Red
                                isCapturing -> Color.Gray
                                else -> Color.White
                            }
                        )
                )
            }
        }
    }
}

/**
 * Frosted-glass chrome: a translucent dark base (so it reads against a
 * bright camera feed), a soft gradient border, and a slow diagonal light
 * sweep looping behind the content. Real backdrop blur only kicks in on
 * API 31+ ([Modifier.blur] is a no-op below that); everything else degrades
 * gracefully to the plain tinted-glass look.
 */
@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    content: @Composable () -> Unit
) {
    val sheen by rememberInfiniteTransition(label = "glass-sheen").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(animation = tween(3200, easing = LinearEasing)),
        label = "sheen-offset"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(1.dp) else Modifier)
            .background(Color.Black.copy(alpha = 0.38f))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.02f))))
            .drawWithContent {
                drawContent()
                val sweepWidth = size.width * 0.7f
                val x = size.width * sheen
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                        start = Offset(x - sweepWidth, 0f),
                        end = Offset(x, size.height)
                    )
                )
            }
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f))), shape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private suspend fun awaitCameraProvider(context: android.content.Context): ProcessCameraProvider? =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                if (cont.isActive) cont.resume(future.get())
            } catch (e: Exception) {
                Timber.e(e, "ProcessCameraProvider unavailable for AR camera")
                if (cont.isActive) cont.resume(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }
