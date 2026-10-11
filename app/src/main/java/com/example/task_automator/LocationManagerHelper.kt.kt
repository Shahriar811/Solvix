package com.example.task_automator.helpers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.task_automator.data.LocationData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper class to manage location operations
 */
class LocationManagerHelper(private val context: Context) {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // State flows for reactive UI updates
    private val _locationData = MutableStateFlow<LocationData?>(null)
    val locationData: StateFlow<LocationData?> = _locationData.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(false)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    private val _permissionStatus = MutableStateFlow("Unknown")
    val permissionStatus: StateFlow<String> = _permissionStatus.asStateFlow()

    private var isListening = false

    companion object {
        private const val TAG = "LocationManagerHelper"
        private const val MIN_TIME_BETWEEN_UPDATES = 5000L // 5 seconds
        private const val MIN_DISTANCE_CHANGE_FOR_UPDATES = 10f // 10 meters
    }

    /**
     * Location listener for receiving location updates
     */
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val locationData = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                provider = location.provider ?: "Unknown",
                timestamp = location.time
            )

            _locationData.value = locationData
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d(TAG, "Location provider $provider status changed to $status")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Location provider $provider enabled")
            checkLocationSettings()
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "Location provider $provider disabled")
            checkLocationSettings()
        }
    }

    init {
        checkPermissions()
        checkLocationSettings()
    }

    /**
     * Check location permissions
     */
    private fun checkPermissions() {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        _permissionStatus.value = when {
            fineLocationPermission == PackageManager.PERMISSION_GRANTED -> "Granted"
            coarseLocationPermission == PackageManager.PERMISSION_GRANTED -> "Coarse Granted"
            else -> "Denied"
        }
    }

    /**
     * Check if location services are enabled
     */
    private fun checkLocationSettings() {
        _isLocationEnabled.value = LocationManagerCompat.isLocationEnabled(locationManager)
    }

    /**
     * Request location permission
     */
    fun requestLocationPermission() {
        // This function should be implemented in the Activity/Fragment
        // as it requires ActivityResultLauncher
        Log.d(TAG, "Requesting location permission from Activity/Fragment")
    }

    /**
     * Enable location services
     */
    fun enableLocationServices() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening location settings", e)
        }
    }

    /**
     * Start location updates
     */
    fun startLocationUpdates() {
        if (isListening) return

        try {
            if (hasLocationPermission()) {
                getCurrentLocation()?.let { _locationData.value = it }
                val providers = getAvailableProviders()
                if (providers.isNotEmpty()) {
                    for (provider in providers) {
                        locationManager.requestLocationUpdates(
                            provider,
                            MIN_TIME_BETWEEN_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES,
                            locationListener
                        )
                        isListening = true
                    }
                    isListening = true
                    Log.d(TAG, "Started location updates")
                } else {
                    Log.e(TAG, "No location providers available")
                }
            } else {
                Log.e(TAG, "Location permission not granted")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting location updates", e)
            stopLocationUpdates()
            checkPermissions()
        }
    }

    /**
     * Stop location updates
     */
    fun stopLocationUpdates() {
        if (!isListening) return

        try {
            locationManager.removeUpdates(locationListener)
            isListening = false
            Log.d(TAG, "Stopped location updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping location updates", e)
        }
    }

    /**
     * Get current location
     */
    fun getCurrentLocation(): LocationData? {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        try {
            val providers = getAvailableProviders()
            var bestLocation: Location? = null
            var bestTime = 0L

            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null && (bestLocation == null || location.time > bestTime)) {
                    bestLocation = location
                    bestTime = location.time
                }
            }

            return bestLocation?.let { location ->
                LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    provider = location.provider ?: "Unknown",
                    timestamp = location.time
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last known location", e)
            return null
        }
    }

    /**
     * Check if app has location permission
     */
    fun hasLocationPermission(): Boolean {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        return fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
                coarseLocationPermission == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if fine location permission is granted
     */
    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if coarse location permission is granted
     */
    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get available location providers
     */
    fun getAvailableProviders(): List<String> {
        return try {
            locationManager.getProviders(true).filter {
                it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available providers", e)
            emptyList()
        }
    }

    /**
     * Calculate distance between two locations
     */
    fun calculateDistance(location1: LocationData, location2: LocationData): Float {
        return location1.distanceTo(location2)
    }

    /**
     * Check if location is within specified radius of target
     */
    fun isLocationWithinRadius(
        currentLocation: LocationData,
        targetLocation: LocationData,
        radiusMeters: Float
    ): Boolean {
        val distance = calculateDistance(currentLocation, targetLocation)
        return distance <= radiusMeters
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopLocationUpdates()
        Log.d(TAG, "LocationManagerHelper cleaned up")
    }

    /**
     * Refresh permission and location status
     */
    fun refreshStatus() {
        checkPermissions()
        checkLocationSettings()
    }
}
