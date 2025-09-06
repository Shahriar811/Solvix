package com.example.task_automator

import android.hardware.Sensor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.task_automator.data.SensorState
import com.example.task_automator.data.SensorTypes
import com.example.task_automator.helpers.SensorManagerHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(onBackClicked: () -> Unit) {
    val context = LocalContext.current
    val sensorHelper = remember { SensorManagerHelper(context) }

    // Collect state flows
    val sensorState by sensorHelper.sensorState.collectAsState()
    val sensorReadings by sensorHelper.sensorReadings.collectAsState()
    val availableSensors by sensorHelper.availableSensors.collectAsState()

    var selectedSensors by remember { mutableStateOf(setOf<Int>()) }
    var showSensorDetails by remember { mutableStateOf(false) }

    // Clean up on dispose
    DisposableEffect(Unit) {
        onDispose {
            sensorHelper.cleanup()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClicked) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Sensor Monitor",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (sensorState is SensorState.Running) Icons.Default.Sensors else Icons.Default.SensorsOff,
                    contentDescription = "Sensor Status",
                    tint = if (sensorState is SensorState.Running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Card
        StatusCard(sensorState = sensorState)

        Spacer(modifier = Modifier.height(16.dp))

        // Control Buttons
        ControlButtons(
            sensorState = sensorState,
            selectedSensors = selectedSensors,
            onStartSensors = {
                if (selectedSensors.isEmpty()) {
                    sensorHelper.startSensors()
                } else {
                    sensorHelper.startSensors(selectedSensors.toList())
                }
            },
            onStopSensors = { sensorHelper.stopSensors() },
            onToggleDetails = { showSensorDetails = !showSensorDetails }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sensor Selection
        if (showSensorDetails) {
            SensorSelectionCard(
                availableSensors = availableSensors,
                selectedSensors = selectedSensors,
                onSensorToggle = { sensorType ->
                    selectedSensors = if (selectedSensors.contains(sensorType)) {
                        selectedSensors - sensorType
                    } else {
                        selectedSensors + sensorType
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Sensor Readings
        SensorReadingsCard(
            sensorReadings = sensorReadings,
            availableSensors = availableSensors,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusCard(sensorState: SensorState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (sensorState) {
                is SensorState.Running -> MaterialTheme.colorScheme.primaryContainer
                is SensorState.Error -> MaterialTheme.colorScheme.errorContainer
                is SensorState.Starting -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            when (sensorState) {
                is SensorState.Stopped -> {
                    Text("Sensors are stopped", style = MaterialTheme.typography.bodyMedium)
                }
                is SensorState.Starting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Starting sensors...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is SensorState.Running -> {
                    Text("Sensors are active", style = MaterialTheme.typography.bodyMedium)
                }
                is SensorState.Error -> {
                    Text(
                        text = "Error: ${sensorState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlButtons(
    sensorState: SensorState,
    selectedSensors: Set<Int>,
    onStartSensors: () -> Unit,
    onStopSensors: () -> Unit,
    onToggleDetails: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onStartSensors,
            enabled = sensorState !is SensorState.Running && sensorState !is SensorState.Starting,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Sensors, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (selectedSensors.isEmpty()) "Start All" else "Start Selected")
        }

        Button(
            onClick = onStopSensors,
            enabled = sensorState is SensorState.Running,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.SensorsOff, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Stop")
        }

        OutlinedButton(
            onClick = onToggleDetails,
            modifier = Modifier.weight(1f)
        ) {
            Text("Details")
        }
    }
}

@Composable
private fun SensorSelectionCard(
    availableSensors: List<com.example.task_automator.data.SensorInfo>,
    selectedSensors: Set<Int>,
    onSensorToggle: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Available Sensors",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(availableSensors) { sensorInfo ->
                    SensorSelectionItem(
                        sensorInfo = sensorInfo,
                        isSelected = selectedSensors.contains(sensorInfo.type),
                        onToggle = { onSensorToggle(sensorInfo.type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorSelectionItem(
    sensorInfo: com.example.task_automator.data.SensorInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (sensorInfo.isAvailable) {
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            } else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sensorInfo.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!sensorInfo.isAvailable) {
                    Text(
                        text = "Not Available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else if (sensorInfo.vendor.isNotEmpty()) {
                    Text(
                        text = sensorInfo.vendor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (sensorInfo.isAvailable) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}

@Composable
private fun SensorReadingsCard(
    sensorReadings: Map<Int, com.example.task_automator.data.SensorReading>,
    availableSensors: List<com.example.task_automator.data.SensorInfo>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Live Sensor Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (sensorReadings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sensor data available\nStart sensors to see live readings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sensorReadings.toList()) { (sensorType, reading) ->
                        SensorReadingItem(
                            sensorType = sensorType,
                            reading = reading,
                            sensorInfo = availableSensors.find { it.type == sensorType }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorReadingItem(
    sensorType: Int,
    reading: com.example.task_automator.data.SensorReading,
    sensorInfo: com.example.task_automator.data.SensorInfo?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = SensorTypes.getSensorName(sensorType),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reading.formatForDisplay(SensorTypes.getSensorName(sensorType)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            sensorInfo?.let { info ->
                Text(
                    text = "Range: ±${info.maxRange} • Resolution: ${info.resolution}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}