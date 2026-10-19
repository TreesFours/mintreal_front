package com.example.mistreal_mini.data.webrtc

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.EglRenderer
import timber.log.Timber
import java.io.File

/**
 * Records a live incoming WebRTC VideoSink into a growing local MP4 — this
 * is what makes "pause the live match, resume later" possible: WebRTC
 * itself has no pause/seek concept (it's a live peer stream, not a file),
 * so the viewer keeps writing everything it receives to disk and plays
 * back from that file instead of rendering the live track directly.
 * ExoPlayer pointed at this same (still-growing) file is what gives real
 * pause/resume/seek-within-what's-buffered-so-far for free.
 *
 * Pipeline: incoming VideoFrame -> EglRenderer (GL) -> MediaCodec's input
 * Surface (hardware H.264 encode) -> drain loop -> MediaMuxer -> this file.
 * This is the standard Android "encode what I'm rendering" pattern —
 * EglRenderer is the exact same class SurfaceViewRenderer uses internally
 * to draw a VideoSink's frames, just pointed at an encoder's input Surface
 * instead of a visible one.
 */
class WatchRecorder(
    private val eglBaseContext: EglBase.Context,
    private val outputFile: File,
    private val width: Int,
    private val height: Int
) : VideoSink {

    private val eglRenderer = EglRenderer("WatchRecorder-")
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    @Volatile private var muxerStarted = false
    @Volatile private var isRunning = false
    private var drainThread: Thread? = null

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface: Surface = codec.createInputSurface()
        codec.start()
        mediaCodec = codec

        mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        eglRenderer.init(eglBaseContext, EglBase.CONFIG_RECORDABLE, GlRectDrawer())
        eglRenderer.createEglSurface(inputSurface)

        isRunning = true
        drainThread = Thread({ drainLoop() }, "WatchRecorder-Drain").apply { start() }
    }

    override fun onFrame(frame: VideoFrame) {
        if (isRunning) eglRenderer.onFrame(frame)
    }

    private fun drainLoop() {
        val codec = mediaCodec ?: return
        val muxer = mediaMuxer ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputIndex = try {
                codec.dequeueOutputBuffer(bufferInfo, 10_000)
            } catch (e: Exception) {
                Timber.e(e, "WatchRecorder: dequeueOutputBuffer failed")
                break
            }

            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                videoTrackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
                continue
            }

            if (outputIndex < 0) {
                // No output ready right now (INFO_TRY_AGAIN_LATER etc). Only
                // safe to give up once stop() has actually been requested —
                // otherwise this just means the host hasn't sent a new frame
                // in the last 10ms, which is normal and not a reason to stop.
                if (!isRunning) break
                continue
            }

            val outputBuffer = codec.getOutputBuffer(outputIndex)
            if (outputBuffer != null && muxerStarted && bufferInfo.size > 0) {
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                muxer.writeSampleData(videoTrackIndex, outputBuffer, bufferInfo)
            }
            codec.releaseOutputBuffer(outputIndex, false)

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
    }

    /** Signals end-of-stream and blocks briefly for the drain loop to flush. */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        try {
            mediaCodec?.signalEndOfInputStream()
        } catch (e: Exception) {
            Timber.e(e, "WatchRecorder: signalEndOfInputStream failed")
        }
        try {
            drainThread?.join(2000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        eglRenderer.release()
        try { mediaCodec?.stop() } catch (e: Exception) {}
        try { mediaCodec?.release() } catch (e: Exception) {}
        try { if (muxerStarted) mediaMuxer?.stop() } catch (e: Exception) {
            Timber.e(e, "WatchRecorder: muxer.stop failed")
        }
        try { mediaMuxer?.release() } catch (e: Exception) {}
    }
}
