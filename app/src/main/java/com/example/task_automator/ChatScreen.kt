package com.example.task_automator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.task_automator.helpers.LlmInferenceHelper
import com.example.task_automator.services.NotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(userName: String, onBackClicked: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Notification Broadcast Receiver
    val notificationReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == NotificationListener.ACTION_NOTIFICATION_RECEIVED) {
                    val app = intent.getStringExtra(NotificationListener.EXTRA_NOTIFICATION_APP)
                    val text = intent.getStringExtra(NotificationListener.EXTRA_NOTIFICATION_TEXT)
                    messages.add("Notification from $app: $text")
                }
            }
        }
    }

    // Register and unregister the receiver
    LaunchedEffect(Unit) {
        val filter = IntentFilter(NotificationListener.ACTION_NOTIFICATION_RECEIVED)
        LocalBroadcastManager.getInstance(context).registerReceiver(notificationReceiver, filter)
    }
    DisposableEffect(Unit) {
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(notificationReceiver)
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                selectedImage = bitmap
                messages.add("Image selected from gallery")
            } catch (e: Exception) {
                errorMessage = "Failed to load image: ${e.message}"
                messages.add("Error: $errorMessage")
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            selectedImage = it
            messages.add("Image captured from camera")
        } ?: run {
            errorMessage = "Failed to capture image"
            messages.add("Error: $errorMessage")
        }
    }

    // Initialize multimodal model
    LaunchedEffect(Unit) {
        isLoading = true
        if (!LlmInferenceHelper.initMultiModalModel(context)) {
            errorMessage = "Failed to initialize multimodal model. Check Logcat."
            messages.add("Error: $errorMessage")
        } else {
            messages.add("Welcome to the chat, $userName! Upload an image, type a message, or wait for notifications.")
        }
        isLoading = false
    }

    // Clean up resources
    DisposableEffect(Unit) {
        onDispose {
            LlmInferenceHelper.cleanup()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Welcome, $userName!",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Display selected image
        selectedImage?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected Image",
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp)
            )
            Button(
                onClick = { selectedImage = null },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Clear Image")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Chat message list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                val isUserMessage = msg.startsWith("You:") || msg.startsWith("Image selected") || msg.startsWith("Image captured")
                val isNotification = msg.startsWith("Notification from")
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .fillMaxWidth(),
                    contentAlignment = when {
                        isUserMessage -> Alignment.CenterEnd
                        isNotification -> Alignment.Center
                        else -> Alignment.CenterStart
                    }
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = when {
                            isUserMessage -> MaterialTheme.colorScheme.primaryContainer
                            isNotification -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isUserMessage -> MaterialTheme.colorScheme.onPrimaryContainer
                                isNotification -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        // Input field and buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message") },
                    enabled = !isLoading
                )
                Button(
                    onClick = {
                        if (message.isNotBlank() || selectedImage != null) {
                            val userMessage = message
                            if (userMessage.isNotBlank()) {
                                messages.add("You: $userMessage")
                            }
                            message = ""
                            isLoading = true

                            coroutineScope.launch {
                                val response = withContext(Dispatchers.Default) {
                                    if (selectedImage != null) {
                                        LlmInferenceHelper.generateMultiModalResponse(userMessage, selectedImage!!)
                                    } else {
                                        LlmInferenceHelper.generateResponse(userMessage)
                                    }
                                }
                                messages.add("Bot: $response")
                                selectedImage = null // Clear image after sending
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("Send")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pick Image")
                }
                Button(
                    onClick = { cameraLauncher.launch(null) },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Camera")
                }
            }
        }
    }
}