package com.example.task_automator.data

/**
 * Data class to hold location information
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val provider: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Format location for display
     */
    fun formatForDisplay(): String {
        return "Lat: ${String.format(java.util.Locale.getDefault(), "%.6f", latitude)}, Lng: ${String.format(java.util.Locale.getDefault(), "%.6f", longitude)}"
    }

    /**
     * Get coordinates as a pair
     */
    fun getCoordinates(): Pair<Double, Double> {
        return Pair(latitude, longitude)
    }

    /**
     * Calculate distance to another location (in meters)
     */
    fun distanceTo(other: LocationData): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            this.latitude, this.longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0]
    }
}

/**
 * Sealed class for location permission states
 */
sealed class LocationPermissionState {
    object NotRequested : LocationPermissionState()
    object Granted : LocationPermissionState()
    object Denied : LocationPermissionState()
    object PermanentlyDenied : LocationPermissionState()
}

/**
 * Data class for location-based instructions
 */
data class LocationInstruction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val instruction: String,
    val targetLocation: LocationData? = null,
    val radiusMeters: Float = 100f,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Data class for selected applications
 */
data class SelectedApplication(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val packageName: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        val SUPPORTED_APPS = mapOf(
            "WhatsApp" to "com.whatsapp",
            "Telegram" to "org.telegram.messenger"
        )

        fun fromAppName(appName: String): SelectedApplication? {
            val packageName = SUPPORTED_APPS[appName]
            return if (packageName != null) {
                SelectedApplication(
                    name = appName,
                    packageName = packageName
                )
            } else null
        }
    }
}