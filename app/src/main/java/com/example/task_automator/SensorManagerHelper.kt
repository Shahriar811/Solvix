package com.example.task_automator.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.task_automator.data.SensorInfo
import com.example.task_automator.data.SensorReading
import com.example.task_automator.data.SensorState
import com.example.task_automator.data.SensorTypes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Helper class to manage sensor operations
 */
class SensorManagerHelper(private val context: Context) {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // State flows for reactive UI updates
    private val _sensorState = MutableStateFlow<SensorState>(SensorState.Stopped)
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private val _sensorReadings = MutableStateFlow<Map<Int, SensorReading>>(emptyMap())
    val sensorReadings: StateFlow<Map<Int, SensorReading>> = _sensorReadings.asStateFlow()

    private val _availableSensors = MutableStateFlow<List<SensorInfo>>(emptyList())
    val availableSensors: StateFlow<List<SensorInfo>> = _availableSensors.asStateFlow()

    private var isListening = false
    private val activeSensors = mutableSetOf<Int>()

    companion object {
        private const val TAG = "SensorManagerHelper"
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_NORMAL
    }

    /**
     * Sensor event listener
     */
    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            try {
                val reading = when (event.values.size) {
                    1 -> SensorReading(x = event.values[0])
                    2 -> SensorReading(x = event.values[0], y = event.values[1])
                    else -> SensorReading(
                        x = event.values.getOrNull(0) ?: 0f,
                        y = event.values.getOrNull(1) ?: 0f,
                        z = event.values.getOrNull(2) ?: 0f
                    )
                }

                val currentReadings = _sensorReadings.value.toMutableMap()
                currentReadings[event.sensor.type] = reading
                _sensorReadings.value = currentReadings

                Log.d(TAG, "Sensor ${event.sensor.name} updated: ${reading.formatForDisplay(SensorTypes.getSensorName(event.sensor.type))}")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing sensor data", e)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            Log.d(TAG, "Sensor ${sensor?.name} accuracy changed to $accuracy")
        }
    }

    init {
        scanAvailableSensors()
    }

    /**
     * Scan for available sensors on the device
     */
    private fun scanAvailableSensors() {
        try {
            val sensors = SensorTypes.SUPPORTED_SENSORS.keys.mapNotNull { sensorType ->
                val sensor = sensorManager.getDefaultSensor(sensorType)
                if (sensor != null) {
                    SensorInfo(
                        name = SensorTypes.getSensorName(sensorType),
                        type = sensorType,
                        isAvailable = true,
                        vendor = sensor.vendor,
                        version = sensor.version,
                        maxRange = sensor.maximumRange,
                        resolution = sensor.resolution,
                        power = sensor.power
                    )
                } else {
                    SensorInfo(
                        name = SensorTypes.getSensorName(sensorType),
                        type = sensorType,
                        isAvailable = false
                    )
                }
            }
            _availableSensors.value = sensors
            Log.d(TAG, "Found ${sensors.count { it.isAvailable }} available sensors")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning sensors", e)
            _sensorState.value = SensorState.Error("Failed to scan sensors: ${e.message}")
        }
    }

    /**
     * Start listening to specified sensors
     */
    fun startSensors(sensorTypes: List<Int> = SensorTypes.SUPPORTED_SENSORS.keys.toList()): Boolean {
        if (isListening) {
            Log.w(TAG, "Sensors already listening")
            return false
        }

        try {
            _sensorState.value = SensorState.Starting
            val availableSensorTypes = _availableSensors.value
                .filter { it.isAvailable }
                .map { it.type }

            val sensorsToStart = sensorTypes.filter { it in availableSensorTypes }

            if (sensorsToStart.isEmpty()) {
                _sensorState.value = SensorState.Error("No sensors available to start")
                return false
            }

            var successCount = 0
            sensorsToStart.forEach { sensorType ->
                val sensor = sensorManager.getDefaultSensor(sensorType)
                if (sensor != null) {
                    val success = sensorManager.registerListener(
                        sensorEventListener,
                        sensor,
                        SENSOR_DELAY
                    )
                    if (success) {
                        activeSensors.add(sensorType)
                        successCount++
                        Log.d(TAG, "Started sensor: ${SensorTypes.getSensorName(sensorType)}")
                    } else {
                        Log.e(TAG, "Failed to start sensor: ${SensorTypes.getSensorName(sensorType)}")
                    }
                }
            }

            if (successCount > 0) {
                isListening = true
                _sensorState.value = SensorState.Running
                Log.i(TAG, "Started $successCount sensors successfully")
                return true
            } else {
                _sensorState.value = SensorState.Error("Failed to start any sensors")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting sensors", e)
            _sensorState.value = SensorState.Error("Error starting sensors: ${e.message}")
            return false
        }
    }

    /**
     * Stop all sensor listeners
     */
    fun stopSensors() {
        try {
            if (isListening) {
                sensorManager.unregisterListener(sensorEventListener)
                activeSensors.clear()
                isListening = false
                _sensorState.value = SensorState.Stopped

                // Clear readings
                _sensorReadings.value = emptyMap()

                Log.i(TAG, "All sensors stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sensors", e)
            _sensorState.value = SensorState.Error("Error stopping sensors: ${e.message}")
        }
    }

    /**
     * Start specific sensor
     */
    fun startSpecificSensor(sensorType: Int): Boolean {
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            Log.e(TAG, "Sensor type $sensorType not available")
            return false
        }

        return try {
            val success = sensorManager.registerListener(
                sensorEventListener,
                sensor,
                SENSOR_DELAY
            )
            if (success) {
                activeSensors.add(sensorType)
                if (!isListening) {
                    isListening = true
                    _sensorState.value = SensorState.Running
                }
                Log.d(TAG, "Started specific sensor: ${SensorTypes.getSensorName(sensorType)}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error starting specific sensor", e)
            false
        }
    }

    /**
     * Stop specific sensor
     */
    fun stopSpecificSensor(sensorType: Int) {
        try {
            val sensor = sensorManager.getDefaultSensor(sensorType)
            if (sensor != null) {
                sensorManager.unregisterListener(sensorEventListener, sensor)
                activeSensors.remove(sensorType)

                // Remove reading for this sensor
                val currentReadings = _sensorReadings.value.toMutableMap()
                currentReadings.remove(sensorType)
                _sensorReadings.value = currentReadings

                if (activeSensors.isEmpty()) {
                    isListening = false
                    _sensorState.value = SensorState.Stopped
                }

                Log.d(TAG, "Stopped specific sensor: ${SensorTypes.getSensorName(sensorType)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping specific sensor", e)
        }
    }

    /**
     * Get current reading for a specific sensor
     */
    fun getSensorReading(sensorType: Int): SensorReading? {
        return _sensorReadings.value[sensorType]
    }

    /**
     * Check if a specific sensor is available
     */
    fun isSensorAvailable(sensorType: Int): Boolean {
        return _availableSensors.value.any { it.type == sensorType && it.isAvailable }
    }

    /**
     * Get sensor information
     */
    fun getSensorInfo(sensorType: Int): SensorInfo? {
        return _availableSensors.value.find { it.type == sensorType }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopSensors()
        Log.d(TAG, "SensorManagerHelper cleaned up")
    }
}