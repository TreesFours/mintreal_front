package com.example.mistreal_mini.data.webrtc

import android.Manifest
import android.os.Build

/**
 * Runtime permissions Nearby Connections needs, by API level — empty below
 * API 31 since BLUETOOTH/BLUETOOTH_ADMIN there are normal (install-time)
 * permissions, not runtime ones. Shared by WatchHostScreen and
 * WatchViewerScreen so both request exactly the same set.
 */
fun nearbyRuntimePermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}.toTypedArray()
