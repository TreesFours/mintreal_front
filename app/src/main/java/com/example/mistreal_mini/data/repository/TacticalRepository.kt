package com.example.mistreal_mini.data.repository

import android.location.Location
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.mistreal_mini.ui.dashboard.IntelLogEntry
import com.example.mistreal_mini.ui.dashboard.TacticalCircle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TacticalRepository @Inject constructor() {

    private val _intelLog = mutableStateListOf<IntelLogEntry>()
    val intelLog: List<IntelLogEntry> = _intelLog

    private val _tacticalCircle = mutableStateOf<TacticalCircle?>(null)
    val tacticalCircle: State<TacticalCircle?> = _tacticalCircle

    private val _tacticalPolygon = mutableStateOf<String?>(null)
    val tacticalPolygon: State<String?> = _tacticalPolygon

    private val _aiBlueprints = mutableStateListOf<String>()
    val aiBlueprints: List<String> = _aiBlueprints

    private val _selectedPins = mutableStateListOf<Location>()
    val selectedPins: List<Location> = _selectedPins

    private val _focusPlaceLabel = mutableStateOf<String?>(null)
    val focusPlaceLabel: State<String?> = _focusPlaceLabel

    private val _searchMarker = mutableStateOf<IntelLogEntry?>(null)
    val searchMarker: State<IntelLogEntry?> = _searchMarker

    private val _targetPoints = mutableStateListOf<Pair<Double, Double>>()
    val targetPoints: List<Pair<Double, Double>> = _targetPoints

    fun addTargetPoint(lat: Double, lon: Double) {
        if (_targetPoints.size >= 4) _targetPoints.clear()
        _targetPoints.add(lat to lon)
    }

    fun clearTargetBox() {
        _targetPoints.clear()
    }

    fun addPin(lat: Double, lon: Double, label: String) {
        val entry = IntelLogEntry(label, lat, lon, "PIN", System.currentTimeMillis())
        _intelLog.add(0, entry)
        if (_intelLog.size > 50) _intelLog.removeAt(_intelLog.size - 1)
        
        val loc = Location("manual").apply {
            latitude = lat
            longitude = lon
        }
        _selectedPins.add(loc)
    }

    fun setTacticalCircle(lat: Double, lon: Double, radius: Double) {
        _tacticalCircle.value = TacticalCircle(lat, lon, radius)
    }

    fun clearTacticalCircle() {
        _tacticalCircle.value = null
        _tacticalPolygon.value = null
        _focusPlaceLabel.value = null
        _aiBlueprints.clear()
    }

    fun addAiBlueprint(geoJson: String) {
        _aiBlueprints.add(geoJson)
    }

    fun setTacticalPolygon(pointsJson: String) {
        _tacticalPolygon.value = pointsJson
        _tacticalCircle.value = null
    }

    fun addToIntelLog(entry: IntelLogEntry) {
        _intelLog.add(0, entry)
        if (_intelLog.size > 50) _intelLog.removeAt(_intelLog.size - 1)
    }

    fun removeIntelItem(entry: IntelLogEntry) {
        _intelLog.remove(entry)
    }
    
    fun setSearchMarker(entry: IntelLogEntry?) {
        _searchMarker.value = entry
    }
    
    fun setFocusPlaceLabel(label: String?) {
        _focusPlaceLabel.value = label
    }

    fun clearAmbiguousLocations() {
        // This is UI state in the original, but if we want it here:
        // Actually, AmbiguousLocations depends on Address which is android specific.
        // I'll keep it in the ViewModel if possible, or use a generic list.
    }
}
