package com.example.mistreal_mini.util.webrtc

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import org.json.JSONObject
import org.webrtc.DataChannel
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

// Conservative, widely-safe RTCDataChannel message size — the spec allows
// larger, but this is the commonly-cited safe max across platforms. Not
// verified on a real device, same standing limitation as every WebRTC
// piece in this app (no device/second device available in this sandbox).
private const val CHUNK_SIZE = 16 * 1024

/**
 * A symmetric wrapper around one already-open DataChannel — usable
 * identically by WatchHostService (per connected viewer) and
 * WatchViewerViewModel, since the channel itself is bidirectional once
 * negotiated (confirmed: the host creates it before its SDP offer, so the
 * SCTP association exists from connection start regardless of which side
 * calls send() first).
 *
 * Protocol: a JSON text header ({fileId, fileName, mimeType, totalBytes}),
 * then binary chunks of CHUNK_SIZE, then a JSON text {done: true} marker.
 * Multiple files queue and send strictly sequentially on a dedicated
 * background thread — one file's full header->chunks->marker cycle
 * completes before the next starts. Deliberately not multiplexed/
 * interleaved; real added complexity for no clear benefit here.
 */
class FileTransferChannel(
    private val channel: DataChannel,
    private val context: Context,
    private val onIncomingProgress: (fileName: String, received: Long, total: Long) -> Unit = { _, _, _ -> },
    private val onFileReceived: (fileName: String, mimeType: String, file: File) -> Unit = { _, _, _ -> }
) {
    private var receivingFileName: String? = null
    private var receivingMimeType: String? = null
    private var receivingTotalBytes: Long = 0
    private var receivingFile: File? = null
    private var receivingStream: FileOutputStream? = null
    private var receivedBytes: Long = 0

    private val sendQueue = LinkedBlockingQueue<() -> Unit>()
    @Volatile private var senderStarted = false

    init {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    handleControlMessage(String(bytes, StandardCharsets.UTF_8))
                } else {
                    handleChunk(buffer.data)
                }
            }
        })
    }

    private fun handleControlMessage(text: String) {
        try {
            val json = JSONObject(text)
            if (json.has("done")) {
                receivingStream?.flush()
                receivingStream?.close()
                val file = receivingFile
                val name = receivingFileName
                val mime = receivingMimeType ?: "application/octet-stream"
                if (file != null && name != null) onFileReceived(name, mime, file)
                receivingStream = null
                receivingFile = null
                receivingFileName = null
                receivedBytes = 0
            } else {
                receivingFileName = json.getString("fileName")
                receivingMimeType = json.optString("mimeType", "application/octet-stream")
                receivingTotalBytes = json.optLong("totalBytes", -1L)
                receivedBytes = 0
                val dir = File(context.cacheDir, "watchhub_transfers").apply { mkdirs() }
                val target = File(dir, "${System.currentTimeMillis()}_${sanitizeFileName(receivingFileName!!)}")
                receivingFile = target
                receivingStream = FileOutputStream(target)
            }
        } catch (e: Exception) {
            // Malformed control message — drop whatever transfer was in
            // progress rather than crash the surrounding PeerConnection.
            receivingStream?.close()
            receivingStream = null
            receivingFile = null
        }
    }

    private fun handleChunk(data: ByteBuffer) {
        val stream = receivingStream ?: return
        val bytes = ByteArray(data.remaining())
        data.get(bytes)
        stream.write(bytes)
        receivedBytes += bytes.size
        onIncomingProgress(receivingFileName ?: "", receivedBytes, receivingTotalBytes)
    }

    fun sendFile(uri: Uri, onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> }, onComplete: () -> Unit = {}) {
        sendQueue.put {
            try {
                val contentResolver = context.contentResolver
                val fileName = queryFileName(uri) ?: "file_${System.currentTimeMillis()}"
                val mimeType = contentResolver.getType(uri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', ""))
                    ?: "application/octet-stream"
                val totalBytes = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

                val header = JSONObject().apply {
                    put("fileId", UUID.randomUUID().toString())
                    put("fileName", fileName)
                    put("mimeType", mimeType)
                    put("totalBytes", totalBytes)
                }
                channel.send(DataChannel.Buffer(ByteBuffer.wrap(header.toString().toByteArray(StandardCharsets.UTF_8)), false))

                var sent = 0L
                contentResolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(CHUNK_SIZE)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        val chunk = if (read == buf.size) buf else buf.copyOf(read)
                        channel.send(DataChannel.Buffer(ByteBuffer.wrap(chunk), true))
                        sent += read
                        onProgress(sent, totalBytes)
                    }
                }

                val doneMsg = JSONObject().apply { put("done", true) }
                channel.send(DataChannel.Buffer(ByteBuffer.wrap(doneMsg.toString().toByteArray(StandardCharsets.UTF_8)), false))
                onComplete()
            } catch (e: Exception) {
                // Best-effort — nothing downstream depends on send-error
                // recovery yet; a failed send just stops where it was.
            }
        }
        startSenderIfNeeded()
    }

    private fun startSenderIfNeeded() {
        if (senderStarted) return
        senderStarted = true
        thread(name = "FileTransferChannel-sender", isDaemon = true) {
            while (true) {
                sendQueue.take().invoke()
            }
        }
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }

    private fun sanitizeFileName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
