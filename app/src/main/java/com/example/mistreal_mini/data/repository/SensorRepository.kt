package com.example.mistreal_mini.data.repository

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorRepository @Inject constructor(
    private val sensorManager: SensorManager
) {

    private val smoothingFactor = 0.15f
    private var lastBearing = 0f

    fun getOrientationFlow(): Flow<OrientationData> = callbackFlow {
        val rotationAngles = FloatArray(3)
        val accelerometerReading = FloatArray(3)
        val magnetometerReading = FloatArray(3)
        val rotationMatrixInternal = FloatArray(9)
        val adjustedRotationMatrix = FloatArray(9)
        
        var usingRotationVectorPrimary = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return

                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        usingRotationVectorPrimary = true
                        SensorManager.getRotationMatrixFromVector(rotationMatrixInternal, event.values)
                        processRotationMatrix(rotationMatrixInternal)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
                        if (!usingRotationVectorPrimary) {
                            tryComputeFallback()
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                        if (!usingRotationVectorPrimary) {
                            tryComputeFallback()
                        }
                    }
                }
            }

            private fun tryComputeFallback() {
                if (SensorManager.getRotationMatrix(rotationMatrixInternal, null, accelerometerReading, magnetometerReading)) {
                    processRotationMatrix(rotationMatrixInternal)
                }
            }

            private fun processRotationMatrix(matrix: FloatArray) {
                SensorManager.remapCoordinateSystem(
                    matrix,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    adjustedRotationMatrix
                )
                SensorManager.getOrientation(adjustedRotationMatrix, rotationAngles)
                val azimuthDegrees = Math.toDegrees(rotationAngles[0].toDouble()).toFloat()
                val normalizedBearing = (azimuthDegrees + 360) % 360
                
                val smoothed = computeSmoothedBearing(normalizedBearing)
                trySend(OrientationData(
                    bearing = smoothed,
                    orientation = getOrientationLabel(smoothed)
                ))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Accuracy updates can be handled here if needed
            }
        }

        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector != null) {
            sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_UI)
            // Register magnetometer for accuracy signals if needed elsewhere, 
            // but here we focus on orientation.
        } else {
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel != null && mag != null) {
                sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
            }
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun computeSmoothedBearing(degree: Float): Float {
        var diff = degree - lastBearing
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        
        val smoothed = (lastBearing + smoothingFactor * diff + 360) % 360
        lastBearing = smoothed
        return smoothed
    }

    private fun getOrientationLabel(bearing: Float): String {
        return when {
            bearing >= 337.5 || bearing < 22.5 -> "N"
            bearing >= 22.5 && bearing < 67.5 -> "NE"
            bearing >= 67.5 && bearing < 112.5 -> "E"
            bearing >= 112.5 && bearing < 157.5 -> "SE"
            bearing >= 157.5 && bearing < 202.5 -> "S"
            bearing >= 202.5 && bearing < 247.5 -> "SW"
            bearing >= 247.5 && bearing < 292.5 -> "W"
            bearing >= 292.5 && bearing < 337.5 -> "NW"
            else -> "N"
        }
    }

    fun getAccuracyFlow(): Flow<Int> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {}
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    trySend(accuracy)
                }
            }
        }
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val rv = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        if (mag != null) sensorManager.registerListener(listener, mag, SensorManager.SENSOR_DELAY_UI)
        if (rv != null) sensorManager.registerListener(listener, rv, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}

data class OrientationData(
    val bearing: Float,
    val orientation: String
)
