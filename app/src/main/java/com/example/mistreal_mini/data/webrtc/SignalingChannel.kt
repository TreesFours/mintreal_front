package com.example.mistreal_mini.data.webrtc

/**
 * Both WatchRepository (Socket.IO/internet) and NearbyTransport
 * (Bluetooth/Wi-Fi-Direct) implement this with the same shape, so
 * WatchHostService/WatchViewerViewModel can route a reply back over
 * whichever channel a given peer's messages actually arrived on, without
 * needing to know which transport that peer is using.
 */
interface SignalingChannel {
    fun sendOffer(toPeerId: String, sdp: String)
    fun sendAnswer(toPeerId: String, sdp: String)
    fun sendIce(toPeerId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int)
    fun sendViewerRejected(toPeerId: String, reason: String)
}
