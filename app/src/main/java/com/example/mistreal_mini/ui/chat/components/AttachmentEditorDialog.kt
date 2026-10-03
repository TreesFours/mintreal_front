package com.example.mistreal_mini.ui.chat.components

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
    onAiEditVideo: (Uri, String, (Boolean) -> Unit) -> Unit = { _, _, cb -> cb(false) },
    isAiEditingImage: Boolean = false,
    onAiEditImage: (Uri, String, (Boolean) -> Unit) -> Unit = { _, _, cb -> cb(false) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(EditorMode.VIEW) }
    var showAiEditPrompt by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

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
                        EditorToolButton(Icons.Default.AutoFixHigh, "AI Edit") { showAiEditPrompt = true }
                        EditorToolButton(Icons.Default.Check, "Done", tint = Color.Green) { onDismiss() }
                    }
                }
            }
        }
    }

    if (showAiEditPrompt) {
        var instruction by remember { mutableStateOf("") }
        val isBusyEditing = if (isVideo) isAiEditingVideo else isAiEditingImage
        // Fuse the per-segment/region notes (if any were given) together with the
        // overall instruction into one consolidated request, rather than editing
        // each piece separately and trying to stitch independent results back
        // together — the capable provider handles cross-segment consistency
        // internally when given the full picture in one call.
        val segmentFusionBlock = segmentNotes.entries.sortedBy { it.key }
            .joinToString("\n") { (i, note) -> "${if (isVideo) "Segment" else "Region"} ${i + 1}: $note" }
        AlertDialog(
            onDismissRequest = { if (!isBusyEditing) showAiEditPrompt = false },
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
                    OutlinedTextField(
                        value = instruction,
                        onValueChange = { instruction = it },
                        placeholder = { Text("e.g. change the background to a beach") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusyEditing
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fused = if (segmentFusionBlock.isNotBlank()) {
                            "$instruction\n\n[${if (isVideo) "Segment" else "Region"}-specific instructions]\n$segmentFusionBlock"
                        } else instruction
                        val onComplete: (Boolean) -> Unit = { success ->
                            if (success) {
                                showAiEditPrompt = false
                                onDismiss()
                            }
                        }
                        if (isVideo) onAiEditVideo(uri, fused, onComplete) else onAiEditImage(uri, fused, onComplete)
                    },
                    enabled = !isBusyEditing && instruction.isNotBlank()
                ) {
                    if (isBusyEditing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("Edit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiEditPrompt = false }, enabled = !isBusyEditing) { Text("Cancel") }
            }
        )
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
        AlertDialog(
            onDismissRequest = { activeSegment = null },
            title = { Text("${if (isVideo) "Segment" else "Region"} ${segmentIndex + 1} instructions") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("e.g. make the background darker here") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val updated = segmentNotes.toMutableMap()
                    if (noteText.isBlank()) updated.remove(segmentIndex) else updated[segmentIndex] = noteText
                    onNotesChanged(updated)
                    activeSegment = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { activeSegment = null }) { Text("Cancel") }
            }
        )
    }
}
