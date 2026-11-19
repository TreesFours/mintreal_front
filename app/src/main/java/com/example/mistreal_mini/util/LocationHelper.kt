package com.example.mistreal_mini.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = Geocoder(context)

    /**
     * Callers (the map's "my location" button in particular) were calling
     * getCurrentLocation() with no way to tell "permission was never
     * granted" apart from "GPS just couldn't get a fix" apart from "no
     * error at all, just slow" — all three looked identical: nothing
     * happens, no feedback. This lets a caller check first and show the
     * right message instead of silently doing nothing.
     */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            val fresh = fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
            fresh ?: fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            try {
                fusedLocationClient.lastLocation.await()
            } catch (e2: Exception) {
                Timber.w(e, "LocationHelper: getCurrentLocation failed")
                null
            }
        }
    }

    // 🏙️ Reverse Geocoding: Convert Coordinates to City Name
    suspend fun getCityName(latitude: Double, longitude: Double): String? {
        return try {
            val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1) ?: emptyList()
            if (addresses.isNotEmpty()) {
                val address = addresses[0]
                // Return city, admin area, or country
                address.locality ?: address.adminArea ?: address.countryName
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "LocationHelper: getCityName failed")
            null
        }
    }

    // Reverse geocode to a full street address line (not just city) — for
    // business registration's "Physical Address" field, which getCityName
    // alone isn't detailed enough to fill in.
    suspend fun getFullAddress(latitude: Double, longitude: Double): String? {
        return try {
            val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1) ?: emptyList()
            val address = addresses.firstOrNull() ?: return null
            address.getAddressLine(0) ?: listOfNotNull(address.locality, address.adminArea, address.countryName)
                .joinToString(", ").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.w(e, "LocationHelper: getFullAddress failed")
            null
        }
    }

    // 🏙️ Forward Geocoding: Convert City Name to Coordinates
    suspend fun getCoordinates(cityName: String): android.location.Location? {
        return try {
            val addresses = geocoder.getFromLocationName(cityName, 1) ?: emptyList()
            if (addresses.isNotEmpty()) {
                val address = addresses[0]
                val location = android.location.Location("geocoder")
                location.latitude = address.latitude
                location.longitude = address.longitude
                location
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "LocationHelper: getCoordinates failed")
            null
        }
    }

    // Get orientation (N, NE, E, SE, S, SW, W, NW)
    fun getOrientation(latitude: Double, longitude: Double): String {
        // Simplified: return based on coordinates relative to equator/prime meridian
        val isNorth = latitude > 0
        val isEast = longitude > 0
        return when {
            isNorth && isEast -> "NE"
            isNorth && !isEast -> "NW"
            !isNorth && isEast -> "SE"
            else -> "SW"
        }
    }
}
