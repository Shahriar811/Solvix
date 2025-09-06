package com.example.task_automator.data

import android.hardware.Sensor

/**
 * Data class to hold sensor readings
 */
data class SensorReading(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun formatForDisplay(sensorName: String): String {
        return when (sensorName) {
            "Proximity" -> "$sensorName: Distance=${x} cm"
            "Light" -> "$sensorName: Illuminance=${x} lux"
            "Pressure" -> "$sensorName: Pressure=${x} hPa"
            else -> "$sensorName: X=${String.format("%.2f", x)}, Y=${String.format("%.2f", y)}, Z=${String.format("%.2f", z)}"
        }
    }
}

/**
 * Data class to hold sensor information
 */
data class SensorInfo(
    val name: String,
    val type: Int,
    val isAvailable: Boolean,
    val vendor: String = "",
    val version: Int = 0,
    val maxRange: Float = 0f,
    val resolution: Float = 0f,
    val power: Float = 0f
)

/**
 * Sealed class for sensor states
 */
sealed class SensorState {
    object Stopped : SensorState()
    object Starting : SensorState()
    object Running : SensorState()
    data class Error(val message: String) : SensorState()
}

/**
 * Utility object for sensor type mappings
 */
object SensorTypes {
    val SUPPORTED_SENSORS = mapOf(
        Sensor.TYPE_ACCELEROMETER to "Accelerometer",
        Sensor.TYPE_GYROSCOPE to "Gyroscope",
        Sensor.TYPE_PROXIMITY to "Proximity",
        Sensor.TYPE_LIGHT to "Light",
        Sensor.TYPE_PRESSURE to "Pressure",
        Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer",
        Sensor.TYPE_ROTATION_VECTOR to "Rotation Vector"
    )

    fun getSensorName(type: Int): String {
        return SUPPORTED_SENSORS[type] ?: "Unknown Sensor"
    }
}