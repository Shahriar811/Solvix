package com.example.task_automator

import android.hardware.Sensor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.task_automator.data.SensorInfo
import com.example.task_automator.data.SensorReading
import com.example.task_automator.data.SensorState
import com.example.task_automator.data.SensorTypes
import com.example.task_automator.helpers.SensorManagerHelper
import com.example.task_automator.ui.components.PulseDot
import com.example.task_automator.ui.components.SolvixCard
import com.example.task_automator.ui.components.StatusBadge
import com.example.task_automator.ui.theme.*

@Composable
fun SensorScreen(onBackClicked: () -> Unit) {
    val context = LocalContext.current
    val sensorHelper = remember { SensorManagerHelper(context) }

    val sensorState by sensorHelper.sensorState.collectAsState()
    val sensorReadings by sensorHelper.sensorReadings.collectAsState()
    val availableSensors by sensorHelper.availableSensors.collectAsState()

    var selectedSensors by remember { mutableStateOf(setOf<Int>()) }
    var showSensorFilters by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sensorHelper) {
        var pausedSensors = emptyList<Int>()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    pausedSensors = sensorHelper.activeSensorTypes
                    sensorHelper.stopSensors()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (pausedSensors.isNotEmpty()) sensorHelper.startSensors(pausedSensors)
                    pausedSensors = emptyList()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorHelper.cleanup()
        }
    }

    val isRunning = sensorState is SensorState.Running

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(onClick = onBackClicked) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sensor Telemetry",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            PulseDot(
                                color = if (isRunning) EmeraldSuccess else MaterialTheme.colorScheme.outline,
                                isActive = isRunning
                            )
                        }
                        Text(
                            text = "Hardware Monitoring & Diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusBadge(
                    text = if (isRunning) "Streaming" else "Standby",
                    color = if (isRunning) EmeraldSuccess else MaterialTheme.colorScheme.outline,
                    isActive = isRunning
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Telemetry Overview Stats Ribbon
            SolvixCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverviewStatItem(
                        label = "State",
                        value = when (sensorState) {
                            is SensorState.Running -> "Active"
                            is SensorState.Starting -> "Starting"
                            is SensorState.Error -> "Error"
                            else -> "Idle"
                        },
                        valueColor = when (sensorState) {
                            is SensorState.Running -> EmeraldSuccess
                            is SensorState.Starting -> CyberCyan
                            is SensorState.Error -> RoseError
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    VerticalDivider(
                        modifier = Modifier.height(30.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    OverviewStatItem(
                        label = "Monitored",
                        value = if (selectedSensors.isEmpty()) "${availableSensors.size} All" else "${selectedSensors.size} Filtered",
                        valueColor = ElectricIndigo
                    )

                    VerticalDivider(
                        modifier = Modifier.height(30.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    OverviewStatItem(
                        label = "Hardware",
                        value = "${availableSensors.size} Sensors",
                        valueColor = CyberCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Control Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Start Button
                Surface(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            enabled = !isRunning && sensorState !is SensorState.Starting,
                            onClick = {
                                if (selectedSensors.isEmpty()) {
                                    sensorHelper.startSensors()
                                } else {
                                    sensorHelper.startSensors(selectedSensors.toList())
                                }
                            }
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (!isRunning && sensorState !is SensorState.Starting) {
                                    Brush.linearGradient(listOf(ElectricIndigo, VioletAccent))
                                } else {
                                    Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.25f), Color.Gray.copy(alpha = 0.25f)))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedSensors.isEmpty()) "Start All" else "Start (${selectedSensors.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Stop Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            enabled = isRunning,
                            onClick = { sensorHelper.stopSensors() }
                        ),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isRunning) RoseError.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isRunning) RoseError.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SensorsOff,
                            contentDescription = null,
                            tint = if (isRunning) RoseError else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stop",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRunning) RoseError else MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Filter / Toggle Details Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showSensorFilters = !showSensorFilters },
                    shape = RoundedCornerShape(14.dp),
                    color = if (showSensorFilters) ElectricIndigo.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (showSensorFilters) ElectricIndigo.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (showSensorFilters) Icons.Default.Tune else Icons.Default.FilterList,
                            contentDescription = null,
                            tint = if (showSensorFilters) ElectricIndigo else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (showSensorFilters) ElectricIndigo else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Expandable Filter Section
            AnimatedVisibility(
                visible = showSensorFilters,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    SolvixCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select Hardware Sensors",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (selectedSensors.isNotEmpty()) {
                                Text(
                                    text = "Clear Filter",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricIndigo,
                                    modifier = Modifier.clickable { selectedSensors = emptySet() }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableSensors) { sensor ->
                                val isSelected = selectedSensors.contains(sensor.type)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) ElectricIndigo.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) ElectricIndigo else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedSensors = if (isSelected) {
                                            selectedSensors - sensor.type
                                        } else {
                                            selectedSensors + sensor.type
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = ElectricIndigo,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = sensor.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) ElectricIndigo else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Telemetry Feed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${sensorReadings.size} Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sensor Readings List
            if (sensorReadings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Telemetry Stream Inactive",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tap 'Start All' above to begin streaming real-time hardware sensor updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sensorReadings.toList()) { (sensorType, reading) ->
                        ModernSensorReadingCard(
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
private fun OverviewStatItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun ModernSensorReadingCard(
    sensorType: Int,
    reading: SensorReading,
    sensorInfo: SensorInfo?
) {
    val sensorName = SensorTypes.getSensorName(sensorType)

    SolvixCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getSensorIcon(sensorType),
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = sensorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            PulseDot(color = EmeraldSuccess, isActive = true)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Data Display
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                when (sensorName) {
                    "Proximity", "Light", "Pressure" -> {
                        Text(
                            text = reading.formatForDisplay(sensorName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    else -> {
                        // 3-Axis Clean Monospace Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AxisPill(axis = "X", value = reading.x, color = RoseError)
                            AxisPill(axis = "Y", value = reading.y, color = EmeraldSuccess)
                            AxisPill(axis = "Z", value = reading.z, color = ElectricIndigo)
                        }
                    }
                }
            }
        }

        // Hardware Specs Tag
        sensorInfo?.let { info ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Range: ±${info.maxRange} • Res: ${info.resolution} • Power: ${info.power}mA",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AxisPill(
    axis: String,
    value: Float,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = axis,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = String.format(java.util.Locale.getDefault(), "%.2f", value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun getSensorIcon(sensorType: Int): ImageVector {
    return when (sensorType) {
        Sensor.TYPE_ACCELEROMETER -> Icons.Default.Speed
        Sensor.TYPE_GYROSCOPE -> Icons.Default.Sync
        Sensor.TYPE_LIGHT -> Icons.Default.WbSunny
        Sensor.TYPE_PROXIMITY -> Icons.Default.TouchApp
        Sensor.TYPE_PRESSURE -> Icons.Default.Compress
        Sensor.TYPE_MAGNETIC_FIELD -> Icons.Default.Explore
        else -> Icons.Default.Sensors
    }
}
