package com.example.mistreal_mini.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a [VoicePreset] to an already-recorded voice note. [VoiceRecorder]
 * only exposes whole-clip [android.media.MediaRecorder] output (AAC/.m4a,
 * confirmed no raw PCM access during capture), so this can't be a live
 * buffer transform the way [VoiceFxProcessor] was originally written for —
 * it's a decode-to-PCM / process / re-encode pass run on the finished file
 * before it's attached to an outgoing message. Standard synchronous
 * MediaCodec decode+encode loop; unverified against a real device/real
 * recorded file in this environment.
 */
@Singleton
class VoiceFxRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Returns a new file's [Uri] with [preset] applied, or [sourceUri] unchanged if [preset] is [VoicePreset.NONE]. Null only on a real decode/encode failure. [customPitch] only matters for [VoicePreset.CUSTOM]. */
    fun applyPreset(sourceUri: Uri, preset: VoicePreset, customPitch: Float = 1.0f): Uri? {
        if (preset == VoicePreset.NONE) return sourceUri
        return try {
            val pcm = decodeToPcm(sourceUri) ?: return null
            val processed = VoiceFxProcessor.processPcmBuffer(pcm.samples, pcm.samples.size, preset, pcm.sampleRate, customPitch)
            val outFile = File(context.cacheDir, "voicefx_${System.currentTimeMillis()}.m4a")
            encodeFromPcm(processed, pcm.sampleRate, pcm.channelCount, outFile)
            Uri.fromFile(outFile)
        } catch (e: Exception) {
            Timber.e(e, "VoiceFx render failed for %s / %s", sourceUri, preset)
            null
        }
    }

    private data class DecodedPcm(val samples: ShortArray, val sampleRate: Int, val channelCount: Int)

    private fun decodeToPcm(uri: Uri): DecodedPcm? {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            return null
        }
        extractor.selectTrack(trackIndex)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mime = format.getString(MediaFormat.KEY_MIME)!!

        val pcmOut = ByteArrayOutputStream()

        // TextToSpeech.synthesizeToFile() always writes a .wav of already-raw
        // PCM (confirmed Android platform behavior, regardless of the
        // filename extension passed to it) — there's no decoder for
        // "audio/raw" since there's nothing to decode, so MediaCodec would
        // just throw here. Human voice notes (MediaRecorder's AAC .m4a)
        // still need the real decode loop below.
        if (mime == MediaFormat.MIMETYPE_AUDIO_RAW) {
            val buffer = ByteBuffer.allocate(64 * 1024)
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val chunk = ByteArray(sampleSize)
                buffer.position(0)
                buffer.get(chunk)
                pcmOut.write(chunk)
                extractor.advance()
            }
            extractor.release()
            val bytes = pcmOut.toByteArray()
            val shorts = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return DecodedPcm(shorts, sampleRate, channelCount)
        }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        val timeoutUs = 10_000L

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                if (bufferInfo.size > 0) {
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(chunk)
                    pcmOut.write(chunk)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop()
        codec.release()
        extractor.release()

        val bytes = pcmOut.toByteArray()
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return DecodedPcm(shorts, sampleRate, channelCount)
    }

    private fun encodeFromPcm(samples: ShortArray, sampleRate: Int, channelCount: Int, outFile: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val pcmBytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            .apply { asShortBuffer().put(samples) }
            .array()

        var offset = 0
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        var presentationTimeUs = 0L
        val bytesPerFrame = 2 * channelCount
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val remaining = pcmBytes.size - offset
                    val chunkSize = minOf(inputBuffer.capacity(), remaining)
                    if (chunkSize <= 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        inputBuffer.clear()
                        inputBuffer.put(pcmBytes, offset, chunkSize)
                        codec.queueInputBuffer(inputIndex, 0, chunkSize, presentationTimeUs, 0)
                        offset += chunkSize
                        presentationTimeUs = (offset / bytesPerFrame).toLong() * 1_000_000L / sampleRate
                    }
                }
            }
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                muxerTrackIndex = muxer.addTrack(codec.outputFormat)
                muxer.start()
                muxerStarted = true
            } else if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                if (bufferInfo.size > 0 && muxerStarted) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }

        codec.stop()
        codec.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
    }
}
