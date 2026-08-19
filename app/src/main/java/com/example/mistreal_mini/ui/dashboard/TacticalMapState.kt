package com.example.mistreal_mini.ui.dashboard

import android.location.Address
import com.example.mistreal_mini.data.model.DiscoveryResult

/**
 * 🛡️ PROFESSIONAL REFORM: Step 1 - Unified Map State
 * Consolidates all map variables into a single immutable object.
 * This prevents "State Fragmentation" and makes debugging 10x faster.
 */
data class TacticalMapState(
    val focusLocationName: String = "Unknown Area",
    val searchMarker: IntelLogEntry? = null,
    val tacticalPins: List<IntelLogEntry> = emptyList(),
    val tacticalCircle: TacticalCircle? = null,
    val discoveryResults: List<DiscoveryResult> = emptyList(),
    val intelLog: List<IntelLogEntry> = emptyList(),
    val ambiguousLocations: List<Address> = emptyList(),
    val isMapLoading: Boolean = false,
    val isLocationEnabled: Boolean = true,
    val isCalibrationWizardVisible: Boolean = false,
    val calibrationProgress: Float = 0f,
    val compassAccuracy: Int = 0, // Unreliable
    val bearing: Float = 0f,
    val orientation: String = "N"
)
