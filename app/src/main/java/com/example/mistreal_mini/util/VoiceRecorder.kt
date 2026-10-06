package com.example.mistreal_mini.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentFile: File? = null

    // MPEG_4 container + AAC encoder is actually an M4A file, not MP3 — the previous
    // ".mp3" name was a lie about the bytes on disk. MediaPlayer sniffs real content
    // over extension so this alone wasn't silencing playback, but it's genuinely wrong
    // and breaks any tooling/backend that trusts the extension for its mime type.
    fun startRecording(): File? {
        val file = File(context.cacheDir, "voice_record_${System.currentTimeMillis()}.m4a")
        currentFile = file

        return try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            file
        } catch (e: Exception) {
            Timber.e(e, "VoiceRecorder: failed to start recording")
            mediaRecorder?.release()
            mediaRecorder = null
            null
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Timber.e(e, "VoiceRecorder: failed to stop recording cleanly")
        }
        mediaRecorder = null
    }

    fun playRecording(file: File, onComplete: () -> Unit) {
        // Release any prior player instead of leaking it under a reassigned reference.
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { onComplete() }
                setOnErrorListener { _, what, extra ->
                    Timber.e("VoiceRecorder: playback error what=$what extra=$extra")
                    onComplete()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Timber.e(e, "VoiceRecorder: failed to play recording")
            mediaPlayer = null
            onComplete()
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Timber.e(e, "VoiceRecorder: failed to stop playback cleanly")
        }
        mediaPlayer = null
    }

    fun deleteRecording(file: File) {
        if (file.exists()) file.delete()
    }
}
