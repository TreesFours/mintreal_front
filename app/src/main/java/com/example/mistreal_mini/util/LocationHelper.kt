package com.example.mistreal_mini.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = Geocoder(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            // Priority High Accuracy ensures it doesn't just return cached stale location
            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).await()
        } catch (e: Exception) {
            null
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
