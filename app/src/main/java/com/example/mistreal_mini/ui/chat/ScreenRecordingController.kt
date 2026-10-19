package com.example.mistreal_mini.ui.chat

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

/**
 * UI-state mirror only — ScreenRecordService owns the actual recording
 * logic (see service/recording/ScreenRecordService.kt); extracted out of
 * ChatViewModel since it was 4 self-contained boolean flips with no other
 * dependency.
 */
class ScreenRecordingController {
    private val _isScreenRecording = mutableStateOf(false)
    val isScreenRecording: State<Boolean> = _isScreenRecording

    private val _isRecordingPaused = mutableStateOf(false)
    val isRecordingPaused: State<Boolean> = _isRecordingPaused

    fun start() {
        _isScreenRecording.value = true
        // Service handles logic
    }

    fun pause() {
        _isRecordingPaused.value = true
    }

    fun resume() {
        _isRecordingPaused.value = false
    }

    fun stop() {
        _isScreenRecording.value = false
        _isRecordingPaused.value = false
    }
}
