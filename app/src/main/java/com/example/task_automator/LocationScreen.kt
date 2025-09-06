package com.example.task_automator

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.task_automator.data.LocationData
import com.example.task_automator.helpers.LocationManagerHelper
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.util.Log
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(onBackClicked: () -> Unit) {
    val context = LocalContext.current
    val locationHelper = remember { LocationManagerHelper(context) }
    val coroutineScope = rememberCoroutineScope()

    // State variables
    val locationData by locationHelper.locationData.collectAsState()
    val isLocationEnabled by locationHelper.isLocationEnabled.collectAsState()
    val permissionStatus by locationHelper.permissionStatus.collectAsState()
    var showAddInstruction by remember { mutableStateOf(false) }
    var showAddApplication by remember { mutableStateOf(false) }
    var instructionText by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<String?>(null) }
    var instructions by remember { mutableStateOf(listOf<String>()) }
    var selectedApps by remember { mutableStateOf(listOf<String>()) }
    var selectedDestination by remember { mutableStateOf<LatLng?>(null) }
    var distanceToDestination by remember { mutableStateOf<Float?>(null) }
    var alwaysMyLocation by remember { mutableStateOf(true) } // Default to true for real-time tracking
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    // MapView lifecycle management
    val mapView = remember { MapView(context) }

    // Start location updates and clean up on dispose
    DisposableEffect(Unit) {
        mapView.onCreate(null)
        mapView.getMapAsync { map ->
            googleMap = map
            map.uiSettings.isMyLocationButtonEnabled = true
            map.uiSettings.isZoomControlsEnabled = true
            // Set long-click listener for selecting destination
            map.setOnMapLongClickListener { latLng ->
                selectedDestination = latLng
                map.clear() // Clear previous markers
                // Add marker for destination
                map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )
                // Update distance if current location is available
                locationData?.let { current ->
                    val dest = LocationData(latLng.latitude, latLng.longitude, 0f, "Manual")
                    distanceToDestination = current.distanceTo(dest)
                }
            }
            // Update map with current location
            locationData?.let { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Current Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                if (alwaysMyLocation) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        }
        if (locationHelper.hasLocationPermission()) {
            locationHelper.startLocationUpdates()
        }
        onDispose {
            mapView.onDestroy()
            locationHelper.stopLocationUpdates()
            locationHelper.cleanup()
        }
    }

    // Update map when location changes
    LaunchedEffect(locationData) {
        googleMap?.let { map ->
            locationData?.let { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                map.clear() // Clear existing markers
                // Add current location marker
                map.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Current Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                // Add destination marker if set
                selectedDestination?.let { dest ->
                    map.addMarker(
                        MarkerOptions()
                            .position(dest)
                            .title("Destination")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                    val destLoc = LocationData(dest.latitude, dest.longitude, 0f, "Manual")
                    distanceToDestination = loc.distanceTo(destLoc)
                }
                if (alwaysMyLocation) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
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
                    text = "Location Services",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (isLocationEnabled) Icons.Default.LocationOn else Icons.Default.LocationOff,
                    contentDescription = "Location Status",
                    tint = if (isLocationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Map View
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            AndroidView(factory = { mapView }) { view ->
                view.getMapAsync { map ->
                    googleMap = map
                    map.uiSettings.isMyLocationButtonEnabled = true
                    map.uiSettings.isZoomControlsEnabled = true
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { alwaysMyLocation = !alwaysMyLocation },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (alwaysMyLocation) "Disable My Location" else "Enable My Location")
            }
            Button(
                onClick = {
                    selectedDestination = null
                    distanceToDestination = null
                    googleMap?.clear()
                    locationData?.let { loc ->
                        googleMap?.addMarker(
                            MarkerOptions()
                                .position(LatLng(loc.latitude, loc.longitude))
                                .title("Current Location")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        )
                        if (alwaysMyLocation) {
                            googleMap?.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(loc.latitude, loc.longitude),
                                    15f
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear Destination")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Location Status and Distance
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                LocationInfoRow("Location Enabled", if (isLocationEnabled) "Yes" else "No")
                Spacer(modifier = Modifier.height(4.dp))
                LocationInfoRow("Permission Status", permissionStatus)
                Spacer(modifier = Modifier.height(4.dp))
                if (locationData != null) {
                    LocationInfoRow("Latitude", locationData!!.latitude.toString())
                    Spacer(modifier = Modifier.height(4.dp))
                    LocationInfoRow("Longitude", locationData!!.longitude.toString())
                    Spacer(modifier = Modifier.height(4.dp))
                    LocationInfoRow("Accuracy", "${locationData!!.accuracy} meters")
                    Spacer(modifier = Modifier.height(4.dp))
                    LocationInfoRow("Provider", locationData!!.provider)
                    Spacer(modifier = Modifier.height(4.dp))
                    LocationInfoRow(
                        "Last Updated",
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(locationData!!.timestamp))
                    )
                }
                if (distanceToDestination != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LocationInfoRow("Distance to Destination", "${String.format("%.2f", distanceToDestination)} meters")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Refresh and Permission Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { locationHelper.refreshStatus() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Refresh")
            }
            if (!isLocationEnabled || permissionStatus != "Granted") {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (!locationHelper.isLocationEnabled.value) {
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            } else if (!locationHelper.hasLocationPermission()) {
                                // Permission request handled in MainActivity
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (!isLocationEnabled) "Enable Location" else "Request Permission")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Instructions and Applications (unchanged)
        if (showAddInstruction) {
            AlertDialog(
                onDismissRequest = { showAddInstruction = false },
                title = { Text("Add Location Instruction") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = instructionText,
                            onValueChange = { instructionText = it },
                            label = { Text("Instruction") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (instructionText.isNotBlank()) {
                                instructions = instructions + instructionText
                                instructionText = ""
                                showAddInstruction = false
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddInstruction = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddApplication) {
            AlertDialog(
                onDismissRequest = { showAddApplication = false },
                title = { Text("Select Application") },
                text = {
                    Column {
                        com.example.task_automator.data.SelectedApplication.SUPPORTED_APPS.keys.forEach { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedApp == app,
                                    onClick = { selectedApp = app }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(app)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedApp?.let {
                                selectedApps = selectedApps + it
                                showAddApplication = false
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddApplication = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Instructions & Apps",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showAddInstruction = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Instruction")
                        }
                        IconButton(onClick = { showAddApplication = true }) {
                            Icon(Icons.Default.Apps, contentDescription = "Add Application")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (instructions.isEmpty() && selectedApps.isEmpty()) {
                    Text(
                        text = "No instructions or applications added",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 150.dp)
                    ) {
                        items(instructions) { instruction ->
                            Text(
                                text = "Instruction: $instruction",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        items(selectedApps) { app ->
                            Text(
                                text = "App: $app",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}