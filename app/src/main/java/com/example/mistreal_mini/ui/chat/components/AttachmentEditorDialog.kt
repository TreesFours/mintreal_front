package com.example.mistreal_mini.ui.chat.components

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.mistreal_mini.util.MediaEditorUtil
import com.example.mistreal_mini.ui.util.VideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EditorMode { VIEW, CROP, TRIM, SEGMENT }

private const val SEGMENT_COUNT = 6

// Pencil-markup colors on a region ("this hand has 5 fingers, fix here") —
// strokes are stored as region-local 0f..1f fractional points, same
// convention as crop/region-extraction math elsewhere in this file.
private val ANNOTATION_COLORS = listOf(
    android.graphics.Color.RED, android.graphics.Color.YELLOW,
    android.graphics.Color.CYAN, android.graphics.Color.GREEN, android.graphics.Color.WHITE
)

/**
 * Full-size attachment preview — replaces the old small 300dp AlertDialog preview.
 * Offers crop (images) and trim + 6-way segmentation (video) directly from the
 * preview, so the user can give the AI segment-specific instructions instead of
 * only a single whole-media prompt.
 */
@Composable
fun AttachmentEditorDialog(
    uri: Uri,
    isVideo: Boolean,
    segmentNotes: Map<Int, String>,
    onDismiss: () -> Unit,
    onReplaceAttachment: (Uri) -> Unit,
    onDiscardAttachment: () -> Unit,
    onSegmentNotesChanged: (Map<Int, String>) -> Unit,
    isAiEditingVideo: Boolean = false,
    // First Uri? = optional face-swap reference image (picked from Verified
    // Faces); second Uri? = optional marked frame pointing at which face to target.
    onAiEditVideo: (Uri, Uri?, Uri?, String, (Boolean) -> Unit) -> Unit = { _, _, _, _, cb -> cb(false) },
    isAiEditingImage: Boolean = false,
    // Extra reference images (e.g. a rasterized pencil-annotation overlay, or
    // a face-swap target) alongside the main uri, so "fix this specific spot"
    // markup actually reaches the model instead of just a text description.
    // The Boolean flags whether any of those extras is a face-swap reference
    // (vs. just an annotation overlay), purely for the resulting message's
    // "AI FACE-EDITED" badge.
    onAiEditImage: (Uri, List<Uri>, Boolean, String, (Boolean) -> Unit) -> Unit = { _, _, _, _, cb -> cb(false) },
    // Region-local (0f..1f) stroke points per region index — only meaningful
    // for images, not video (per-scene annotation is separate, later work).
    segmentDrawings: Map<Int, List<List<Pair<Float, Float>>>> = emptyMap(),
    onSegmentDrawingsChanged: (Map<Int, List<List<Pair<Float, Float>>>>) -> Unit = {},
    // (label, imageUri) pairs — faces registered via live camera capture only
    // (see VerifiedFaceRepository); the only identities face-swap can target.
    verifiedFaces: List<Pair<com.example.mistreal_mini.data.local.entity.VerifiedFaceEntity, Uri>> = emptyList(),
    // Synthesizes [text] as narration and muxes it onto the video, handing
    // back the new combined-video Uri (or null on failure).
    isSynthesizingVoiceOver: Boolean = false,
    onAddVoiceOver: (Uri, String, (Uri?) -> Unit) -> Unit = { _, _, cb -> cb(null) },
    // Multi-face video targeting: detect faces on a representative frame, let
    // the user tap which one to swap instead of a single generic "main person."
    onDetectFacesInVideo: suspend (Uri) -> Pair<Bitmap, List<RectF>>? = { null },
    onMarkFaceTarget: suspend (Bitmap, RectF) -> Uri? = { _, _ -> null }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(EditorMode.VIEW) }
    var showAiEditPrompt by remember { mutableStateOf(false) }
    var showVoiceOverPrompt by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var isRasterizing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                    Text("Edit Attachment", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        onDiscardAttachment()
                        onDismiss()
                    }) {
                        Text("DISCARD", color = Color.Red)
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        mode == EditorMode.CROP && !isVideo -> CropEditor(
                            uri = uri,
                            isBusy = isBusy,
                            onCancel = { mode = EditorMode.VIEW },
                            onConfirm = { rect ->
                                isBusy = true
                                scope.launch {
                                    val newUri = MediaEditorUtil.cropImage(context, uri, rect)
                                    isBusy = false
                                    if (newUri != null) {
                                        onReplaceAttachment(newUri)
                                        onDismiss()
                                    }
                                }
                            }
                        )
                        mode == EditorMode.TRIM && isVideo -> TrimEditor(
                            uri = uri,
                            isBusy = isBusy,
                            onCancel = { mode = EditorMode.VIEW },
                            onConfirm = { startMs, endMs ->
                                isBusy = true
                                scope.launch {
                                    val newUri = MediaEditorUtil.trimVideo(context, uri, startMs, endMs)
                                    isBusy = false
                                    if (newUri != null) {
                                        onReplaceAttachment(newUri)
                                        onDismiss()
                                    }
                                }
                            }
                        )
                        mode == EditorMode.SEGMENT -> SegmentEditor(
                            uri = uri,
                            isVideo = isVideo,
                            segmentNotes = segmentNotes,
                            onNotesChanged = onSegmentNotesChanged,
                            segmentDrawings = segmentDrawings,
                            onDrawingsChanged = onSegmentDrawingsChanged,
                            onDone = { mode = EditorMode.VIEW }
                        )
                        else -> {
                            if (isVideo) {
                                VideoPlayer(videoUrl = uri.toString(), modifier = Modifier.fillMaxSize())
                            } else {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    if (isBusy) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }

                if (mode == EditorMode.VIEW) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                    ) {
                        if (!isVideo) {
                            EditorToolButton(Icons.Default.Crop, "Crop") { mode = EditorMode.CROP }
                        } else {
                            EditorToolButton(Icons.Default.ContentCut, "Trim") { mode = EditorMode.TRIM }
                        }
                        EditorToolButton(Icons.Default.ViewColumn, "Split into 6") { mode = EditorMode.SEGMENT }
                        if (isVideo) {
                            EditorToolButton(Icons.Default.Mic, "Voice-Over") { showVoiceOverPrompt = true }
                        }
                        EditorToolButton(Icons.Default.AutoFixHigh, "AI Edit") { showAiEditPrompt = true }
                        EditorToolButton(Icons.Default.Check, "Done", tint = Color.Green) { onDismiss() }
                    }
                }
            }
        }
    }

    if (showVoiceOverPrompt) {
        var narration by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!isSynthesizingVoiceOver) showVoiceOverPrompt = false },
            title = { Text("Add Voice-Over") },
            text = {
                Column {
                    Text(
                        "Replaces this video's audio with narration read aloud from your text.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = narration,
                        onValueChange = { narration = it },
                        placeholder = { Text("What should the voice-over say?") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSynthesizingVoiceOver
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddVoiceOver(uri, narration) { newUri ->
                            if (newUri != null) {
                                onReplaceAttachment(newUri)
                                showVoiceOverPrompt = false
                            }
                        }
                    },
                    enabled = !isSynthesizingVoiceOver && narration.isNotBlank()
                ) {
                    if (isSynthesizingVoiceOver) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceOverPrompt = false }, enabled = !isSynthesizingVoiceOver) { Text("Cancel") }
            }
        )
    }

    if (showAiEditPrompt) {
        var instruction by remember { mutableStateOf("") }
        var pickedFace by remember { mutableStateOf<Pair<com.example.mistreal_mini.data.local.entity.VerifiedFaceEntity, Uri>?>(null) }
        var showFacePicker by remember { mutableStateOf(false) }
        var isDetectingFaces by remember { mutableStateOf(false) }
        var faceTargetFrame by remember { mutableStateOf<Bitmap?>(null) }
        var faceTargetBoxes by remember { mutableStateOf<List<RectF>>(emptyList()) }
        var selectedFaceBoxIndex by remember { mutableStateOf<Int?>(null) }
        var faceTargetUri by remember { mutableStateOf<Uri?>(null) }
        var showFaceTargetPicker by remember { mutableStateOf(false) }
        val isBusyEditing = if (isVideo) isAiEditingVideo else isAiEditingImage
        // Fuse the per-segment/region notes (if any were given) together with the
        // overall instruction into one consolidated request, rather than editing
        // each piece separately and trying to stitch independent results back
        // together — the capable provider handles cross-segment consistency
        // internally when given the full picture in one call.
        val segmentFusionBlock = segmentNotes.entries.sortedBy { it.key }
            .joinToString("\n") { (i, note) -> "${if (isVideo) "Segment" else "Region"} ${i + 1}: $note" }
        val hasDrawings = !isVideo && segmentDrawings.values.any { it.isNotEmpty() }
        AlertDialog(
            onDismissRequest = { if (!isBusyEditing && !isRasterizing) showAiEditPrompt = false },
            title = { Text("AI Edit") },
            text = {
                Column {
                    if (isVideo) {
                        Text(
                            "Needs your own video editing provider configured in Settings (Runway or custom) — Gemini/Veo don't support editing an existing video.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (segmentFusionBlock.isNotBlank()) {
                        Text(
                            "Will include your ${if (isVideo) "segment" else "region"} notes below, fused with this overall instruction.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (hasDrawings) {
                        Text(
                            "Will also attach your pencil markup as a reference image.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { instruction = it },
                        placeholder = { Text("e.g. change the background to a beach") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusyEditing && !isRasterizing
                    )

                    if (verifiedFaces.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("FACE SWAP (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { showFacePicker = true }, modifier = Modifier.fillMaxWidth(), enabled = !isBusyEditing) {
                            Text(pickedFace?.first?.label ?: "Pick a verified face…")
                        }
                        if (pickedFace != null) {
                            TextButton(onClick = { pickedFace = null }) { Text("Clear") }
                        }

                        if (isVideo && pickedFace != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    isDetectingFaces = true
                                    scope.launch {
                                        val result = onDetectFacesInVideo(uri)
                                        isDetectingFaces = false
                                        if (result != null && result.second.isNotEmpty()) {
                                            faceTargetFrame = result.first
                                            faceTargetBoxes = result.second
                                            selectedFaceBoxIndex = null
                                            showFaceTargetPicker = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isBusyEditing && !isDetectingFaces
                            ) {
                                if (isDetectingFaces) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Text(if (faceTargetUri != null) "Change which face to target" else "Multiple people? Pick which face to swap")
                            }
                            if (faceTargetUri != null) {
                                Text(
                                    "Targeting one specific face in the video.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val faceSwapNote = pickedFace?.let {
                            if (faceTargetUri != null) {
                                "\n\nReplace only the face highlighted in the attached marked-up frame with the face shown in the other attached reference image — leave every other person/face in the video unchanged."
                            } else {
                                "\n\nReplace the face of the main person with the face shown in the attached reference image, keeping pose, lighting, and everything else unchanged."
                            }
                        } ?: ""
                        val fused = (if (segmentFusionBlock.isNotBlank()) {
                            "$instruction\n\n[${if (isVideo) "Segment" else "Region"}-specific instructions]\n$segmentFusionBlock" +
                                (if (hasDrawings) "\n\nA marked-up reference image is attached — regions with visible pencil marks show exactly where to apply the above." else "")
                        } else instruction) + faceSwapNote
                        val onComplete: (Boolean) -> Unit = { success ->
                            if (success) {
                                showAiEditPrompt = false
                                onDismiss()
                            }
                        }
                        if (isVideo) {
                            onAiEditVideo(uri, pickedFace?.second, faceTargetUri, fused, onComplete)
                        } else if (hasDrawings || pickedFace != null) {
                            isRasterizing = true
                            scope.launch {
                                // Region-local strokes -> full-image fractional coordinates
                                // (2 cols x 3 rows, same grid extractImageRegionThumbnail uses).
                                val fullImageStrokes = segmentDrawings.entries.flatMap { (index, strokes) ->
                                    val col = index % 2
                                    val row = index / 2
                                    strokes.map { stroke ->
                                        stroke.map { (x, y) -> Pair((col + x) / 2f, (row + y) / 3f) }
                                    }
                                }
                                val overlayUri = if (fullImageStrokes.isNotEmpty()) {
                                    MediaEditorUtil.rasterizeAnnotation(context, uri, fullImageStrokes, android.graphics.Color.RED)
                                } else null
                                isRasterizing = false
                                val extras = listOfNotNull(overlayUri, pickedFace?.second)
                                onAiEditImage(uri, extras, pickedFace != null, fused, onComplete)
                            }
                        } else {
                            onAiEditImage(uri, emptyList(), false, fused, onComplete)
                        }
                    },
                    enabled = !isBusyEditing && !isRasterizing && instruction.isNotBlank()
                ) {
                    if (isBusyEditing || isRasterizing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiEditPrompt = false }, enabled = !isBusyEditing && !isRasterizing) { Text("Cancel") }
            }
        )

        if (showFacePicker) {
            AlertDialog(
                onDismissRequest = { showFacePicker = false },
                title = { Text("Pick a verified face") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn {
                        itemsIndexed(verifiedFaces) { _, pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { pickedFace = pair; showFacePicker = false }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                coil.compose.AsyncImage(
                                    model = pair.second,
                                    contentDescription = pair.first.label,
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(pair.first.label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFacePicker = false }) { Text("Close") }
                }
            )
        }

        if (showFaceTargetPicker && faceTargetFrame != null) {
            AlertDialog(
                onDismissRequest = { showFaceTargetPicker = false },
                title = { Text("Tap the face to swap") },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(faceTargetFrame!!.width.toFloat() / faceTargetFrame!!.height.toFloat())
                    ) {
                        Image(
                            faceTargetFrame!!.asImageBitmap(),
                            contentDescription = "Video frame",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(faceTargetBoxes) {
                                    detectTapGestures { tap ->
                                        val fx = tap.x / size.width
                                        val fy = tap.y / size.height
                                        selectedFaceBoxIndex = faceTargetBoxes.indexOfFirst { box ->
                                            fx in box.left..box.right && fy in box.top..box.bottom
                                        }.takeIf { it >= 0 }
                                    }
                                }
                        ) {
                            faceTargetBoxes.forEachIndexed { index, box ->
                                drawRect(
                                    color = if (index == selectedFaceBoxIndex) Color.Green else Color.Yellow,
                                    topLeft = Offset(box.left * size.width, box.top * size.height),
                                    size = Size(box.width() * size.width, box.height() * size.height),
                                    style = Stroke(width = if (index == selectedFaceBoxIndex) 4f else 2f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val index = selectedFaceBoxIndex ?: return@Button
                            scope.launch {
                                faceTargetUri = onMarkFaceTarget(faceTargetFrame!!, faceTargetBoxes[index])
                                showFaceTargetPicker = false
                            }
                        },
                        enabled = selectedFaceBoxIndex != null
                    ) { Text("Use this face") }
                },
                dismissButton = {
                    TextButton(onClick = { showFaceTargetPicker = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun EditorToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = tint) }
        Text(label, color = tint, fontSize = 11.sp)
    }
}

// ---------------------------------------------------------------------------
// Crop
// ---------------------------------------------------------------------------

@Composable
private fun CropEditor(
    uri: Uri,
    isBusy: Boolean,
    onCancel: () -> Unit,
    onConfirm: (RectF) -> Unit
) {
    val density = LocalDensity.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Crop rect in fractions (0f..1f) of the displayed image box.
    var rect by remember { mutableStateOf(RectF(0.1f, 0.1f, 0.9f, 0.9f)) }
    val minSize = 0.08f

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp)
                .onSizeChanged { boxSize = it }
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Crop target",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            if (boxSize.width > 0 && boxSize.height > 0) {
                val w = boxSize.width.toFloat()
                val h = boxSize.height.toFloat()

                // Scrim with a punched-out hole over the crop rect.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRect(color = Color.Black.copy(alpha = 0.6f))
                        drawRect(
                            color = Color.Transparent,
                            topLeft = Offset(rect.left * w, rect.top * h),
                            size = Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h),
                            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                        )
                    }
                }
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(rect.left * w, rect.top * h),
                        size = Size((rect.right - rect.left) * w, (rect.bottom - rect.top) * h),
                        style = Stroke(width = with(density) { 2.dp.toPx() })
                    )
                }

                // Whole-rect move handle (drag anywhere inside the rect).
                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset((rect.left * w).toInt(), (rect.top * h).toInt()) }
                        .size(
                            with(density) { ((rect.right - rect.left) * w).toDp() },
                            with(density) { ((rect.bottom - rect.top) * h).toDp() }
                        )
                        .pointerInput(boxSize) {
                            detectDragGestures { _, dragAmount ->
                                val dx = dragAmount.x / w
                                val dy = dragAmount.y / h
                                val rectWidth = rect.right - rect.left
                                val rectHeight = rect.bottom - rect.top
                                val newLeft = (rect.left + dx).coerceIn(0f, 1f - rectWidth)
                                val newTop = (rect.top + dy).coerceIn(0f, 1f - rectHeight)
                                rect = RectF(newLeft, newTop, newLeft + rectWidth, newTop + rectHeight)
                            }
                        }
                )

                CropHandle(fx = rect.left, fy = rect.top, w = w, h = h, density = density) { dx, dy ->
                    rect = RectF(
                        (rect.left + dx / w).coerceIn(0f, rect.right - minSize),
                        (rect.top + dy / h).coerceIn(0f, rect.bottom - minSize),
                        rect.right, rect.bottom
                    )
                }
                CropHandle(fx = rect.right, fy = rect.top, w = w, h = h, density = density) { dx, dy ->
                    rect = RectF(
                        rect.left,
                        (rect.top + dy / h).coerceIn(0f, rect.bottom - minSize),
                        (rect.right + dx / w).coerceIn(rect.left + minSize, 1f),
                        rect.bottom
                    )
                }
                CropHandle(fx = rect.left, fy = rect.bottom, w = w, h = h, density = density) { dx, dy ->
                    rect = RectF(
                        (rect.left + dx / w).coerceIn(0f, rect.right - minSize),
                        rect.top,
                        rect.right,
                        (rect.bottom + dy / h).coerceIn(rect.top + minSize, 1f)
                    )
                }
                CropHandle(fx = rect.right, fy = rect.bottom, w = w, h = h, density = density) { dx, dy ->
                    rect = RectF(
                        rect.left, rect.top,
                        (rect.right + dx / w).coerceIn(rect.left + minSize, 1f),
                        (rect.bottom + dy / h).coerceIn(rect.top + minSize, 1f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
        ) {
            EditorToolButton(Icons.Default.Close, "Cancel", tint = Color.Gray, onClick = onCancel)
            EditorToolButton(Icons.Default.Check, "Apply Crop", tint = Color.Green) {
                if (!isBusy) onConfirm(rect)
            }
        }
    }
}

@Composable
private fun CropHandle(
    fx: Float,
    fy: Float,
    w: Float,
    h: Float,
    density: androidx.compose.ui.unit.Density,
    onDrag: (Float, Float) -> Unit
) {
    val handleSizeDp = 28.dp
    val handlePx = with(density) { handleSizeDp.toPx() }
    Box(
        modifier = Modifier
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    (fx * w - handlePx / 2).toInt(),
                    (fy * h - handlePx / 2).toInt()
                )
            }
            .size(handleSizeDp)
            .clip(CircleShape)
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount -> onDrag(dragAmount.x, dragAmount.y) }
            }
    )
}

// ---------------------------------------------------------------------------
// Trim
// ---------------------------------------------------------------------------

@Composable
private fun TrimEditor(
    uri: Uri,
    isBusy: Boolean,
    onCancel: () -> Unit,
    onConfirm: (startMs: Long, endMs: Long) -> Unit
) {
    val context = LocalContext.current
    var durationMs by remember { mutableStateOf(0L) }
    var range by remember { mutableStateOf(0f..1f) }

    LaunchedEffect(uri) {
        durationMs = withContext(Dispatchers.IO) { MediaEditorUtil.getVideoDurationMs(context, uri) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            VideoPlayer(videoUrl = uri.toString(), modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.padding(16.dp)) {
            if (durationMs > 0) {
                Text(
                    "${formatMs((range.start * durationMs).toLong())} – ${formatMs((range.endInclusive * durationMs).toLong())} / ${formatMs(durationMs)}",
                    color = Color.White
                )
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..1f
                )
            } else {
                Text("Reading video length…", color = Color.Gray)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                EditorToolButton(Icons.Default.Close, "Cancel", tint = Color.Gray, onClick = onCancel)
                EditorToolButton(Icons.Default.Check, "Apply Trim", tint = Color.Green) {
                    if (!isBusy && durationMs > 0) {
                        onConfirm((range.start * durationMs).toLong(), (range.endInclusive * durationMs).toLong())
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ---------------------------------------------------------------------------
// Segment (6-way split for precision, segment-by-segment instructions)
// ---------------------------------------------------------------------------

@Composable
private fun SegmentEditor(
    uri: Uri,
    isVideo: Boolean,
    segmentNotes: Map<Int, String>,
    onNotesChanged: (Map<Int, String>) -> Unit,
    segmentDrawings: Map<Int, List<List<Pair<Float, Float>>>> = emptyMap(),
    onDrawingsChanged: (Map<Int, List<List<Pair<Float, Float>>>>) -> Unit = {},
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var durationMs by remember { mutableStateOf(0L) }
    val thumbnails = remember { mutableStateListOf<Bitmap?>(*arrayOfNulls(SEGMENT_COUNT)) }
    var activeSegment by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uri) {
        if (isVideo) {
            durationMs = withContext(Dispatchers.IO) { MediaEditorUtil.getVideoDurationMs(context, uri) }
            for (i in 0 until SEGMENT_COUNT) {
                thumbnails[i] = MediaEditorUtil.extractSegmentThumbnail(context, uri, i, SEGMENT_COUNT, durationMs)
            }
        } else {
            for (i in 0 until SEGMENT_COUNT) {
                thumbnails[i] = MediaEditorUtil.extractImageRegionThumbnail(context, uri, i)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (isVideo) {
                VideoPlayer(videoUrl = uri.toString(), modifier = Modifier.fillMaxSize())
            } else {
                AsyncImage(model = uri, contentDescription = "Preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        Text(
            if (isVideo) "Tap a segment to give the AI a specific instruction for just that part of the video."
            else "Tap a region to give the AI a specific instruction for just that part of the picture.",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(thumbnails) { index, bitmap ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray)
                        .clickable { activeSegment = index }
                ) {
                    bitmap?.let {
                        Image(it.asImageBitmap(), contentDescription = "Segment ${index + 1}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Text(
                        "${index + 1}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                    )
                    if (!segmentNotes[index].isNullOrBlank()) {
                        Icon(
                            Icons.Default.Edit,
                            "Has note",
                            tint = Color.Green,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp)
                        )
                    }
                    if (!segmentDrawings[index].isNullOrEmpty()) {
                        Icon(
                            Icons.Default.Create,
                            "Has markup",
                            tint = Color.Red,
                            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp)
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            EditorToolButton(Icons.Default.Check, "Done", tint = Color.Green, onClick = onDone)
        }
    }

    val segmentIndex = activeSegment
    if (segmentIndex != null) {
        var noteText by remember(segmentIndex) { mutableStateOf(segmentNotes[segmentIndex] ?: "") }
        // Each stroke is a list of (x,y) points, 0f..1f fractions of this
        // region's own thumbnail — converted to full-image coordinates only
        // at send-time (see the AI Edit confirm handler above), since a
        // region can be re-opened and re-drawn independently of the others.
        val strokes = remember(segmentIndex) { mutableStateListOf<List<Pair<Float, Float>>>().apply { addAll(segmentDrawings[segmentIndex] ?: emptyList()) } }
        var currentStroke by remember(segmentIndex) { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
        var drawColor by remember { mutableStateOf(ANNOTATION_COLORS[0]) }
        val thumbnail = thumbnails.getOrNull(segmentIndex)

        AlertDialog(
            onDismissRequest = { activeSegment = null },
            title = { Text("${if (isVideo) "Segment" else "Region"} ${segmentIndex + 1} instructions") },
            text = {
                Column {
                    if (!isVideo && thumbnail != null) {
                        Text(
                            "Draw directly on the image to point out exactly where (optional).",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                        ) {
                            Image(
                                thumbnail.asImageBitmap(),
                                contentDescription = "Region ${segmentIndex + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(segmentIndex) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentStroke = listOf(offset.x / size.width to offset.y / size.height)
                                            },
                                            onDrag = { change, _ ->
                                                currentStroke = currentStroke + (change.position.x / size.width to change.position.y / size.height)
                                            },
                                            onDragEnd = {
                                                if (currentStroke.size > 1) strokes.add(currentStroke)
                                                currentStroke = emptyList()
                                            }
                                        )
                                    }
                            ) {
                                val allStrokes = if (currentStroke.size > 1) strokes + listOf(currentStroke) else strokes
                                allStrokes.forEach { stroke ->
                                    for (i in 0 until stroke.size - 1) {
                                        drawLine(
                                            color = Color(drawColor),
                                            start = Offset(stroke[i].first * size.width, stroke[i].second * size.height),
                                            end = Offset(stroke[i + 1].first * size.width, stroke[i + 1].second * size.height),
                                            strokeWidth = 6f,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }
                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ANNOTATION_COLORS.forEach { colorInt ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (drawColor == colorInt) Color.Gray else Color.Transparent)
                                        .padding(3.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .clickable { drawColor = colorInt }
                                )
                            }
                            if (strokes.isNotEmpty()) {
                                TextButton(onClick = { strokes.clear() }) { Text("CLEAR") }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("e.g. make the background darker here") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedNotes = segmentNotes.toMutableMap()
                    if (noteText.isBlank()) updatedNotes.remove(segmentIndex) else updatedNotes[segmentIndex] = noteText
                    onNotesChanged(updatedNotes)

                    val updatedDrawings = segmentDrawings.toMutableMap()
                    if (strokes.isEmpty()) updatedDrawings.remove(segmentIndex) else updatedDrawings[segmentIndex] = strokes.toList()
                    onDrawingsChanged(updatedDrawings)

                    activeSegment = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { activeSegment = null }) { Text("Cancel") }
            }
        )
    }
}
