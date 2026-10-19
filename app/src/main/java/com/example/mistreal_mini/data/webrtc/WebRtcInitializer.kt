package com.example.mistreal_mini.data.webrtc

import android.content.Context
import org.webrtc.PeerConnectionFactory

/**
 * PeerConnectionFactory.initialize() should only run once per process —
 * WatchHostService and the viewer's own PeerConnection setup both need it,
 * so this is the single shared guard both call into instead of each
 * tracking its own (separate) "did I already init" flag.
 */
object WebRtcInitializer {
    @Volatile private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context) {
        if (initialized) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        initialized = true
    }
}
