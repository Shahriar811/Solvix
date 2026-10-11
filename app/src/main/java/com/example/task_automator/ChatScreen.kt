package com.example.task_automator

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.task_automator.helpers.GemmaModelRepository
import com.example.task_automator.services.NotificationListener
import com.example.task_automator.ui.components.PulseDot
import com.example.task_automator.ui.components.SolvixCard
import com.example.task_automator.ui.components.StatusBadge
import com.example.task_automator.ui.components.GemmaModelPanel
import com.example.task_automator.ui.components.SolvixLogo
import com.example.task_automator.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(userName: String, onBackClicked: () -> Unit) {
    val context = LocalContext.current
    val modelViewModel: ChatModelViewModel = viewModel()
    val modelState by modelViewModel.state.collectAsState()
    val installation by GemmaModelRepository.state.collectAsState()
    val session = modelViewModel.session
    val chatState by session.state.collectAsState()
    val message = chatState.draft
    val messages = chatState.messages
    val errorMessage = chatState.error
    val selectedImage = chatState.image
    val isLoading = chatState.busy
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationAccess by remember { mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { GemmaModelRepository.importModel(it) }
    }

    // Notification Broadcast Receiver
    val notificationReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == NotificationListener.ACTION_NOTIFICATION_RECEIVED) {
                    val app = intent.getStringExtra(NotificationListener.EXTRA_NOTIFICATION_APP) ?: "App"
                    val text = intent.getStringExtra(NotificationListener.EXTRA_NOTIFICATION_TEXT) ?: ""
                    session.addMessage("Notification from $app: $text")
                }
            }
        }
    }

    DisposableEffect(context, notificationReceiver) {
        val filter = IntentFilter(NotificationListener.ACTION_NOTIFICATION_RECEIVED)
        LocalBroadcastManager.getInstance(context).registerReceiver(notificationReceiver, filter)
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(notificationReceiver)
        }
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                session.setLoadingImage(true)
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, it)
                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                                val ratio = (1024f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
                                decoder.setTargetSize(
                                    (info.size.width * ratio).toInt().coerceAtLeast(1),
                                    (info.size.height * ratio).toInt().coerceAtLeast(1)
                                )
                            }
                        } else {
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            context.contentResolver.openInputStream(it)?.use { stream ->
                                BitmapFactory.decodeStream(stream, null, bounds)
                            }
                            val options = BitmapFactory.Options()
                            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1024) {
                                options.inSampleSize *= 2
                            }
                            context.contentResolver.openInputStream(it)?.use { stream ->
                                BitmapFactory.decodeStream(stream, null, options)
                            } ?: error("Cannot read the selected image.")
                        }
                    }
                    session.setImage(bitmap)
                    session.reportError(null)
                    session.addMessage("Image selected from gallery")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    session.reportError("Failed to load image: ${error.message}")
                } finally {
                    session.setLoadingImage(false)
                }
            }
        }
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            session.setImage(it)
            session.reportError(null)
            session.addMessage("Image captured from camera")
        }
    }

    fun captureImage() {
        try {
            cameraLauncher.launch(null)
        } catch (error: ActivityNotFoundException) {
            session.reportError("No camera app is available. Select an image from the gallery instead.")
        } catch (error: SecurityException) {
            session.reportError("Camera permission is required. Enable it in Android app settings.")
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) captureImage()
        else session.reportError("Camera permission was denied. You can still select a gallery image.")
    }

    LaunchedEffect(modelState.ready) {
        if (modelState.ready && messages.isEmpty()) {
            session.addMessage("Welcome, $userName! Gemma 4 is ready for offline chat${if (modelState.supportsImages) " and image analysis" else ""}.")
        }
    }

    // Auto-scroll when new message is added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

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

                    SolvixLogo(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Solvix Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            PulseDot(color = EmeraldSuccess, isActive = modelState.ready)
                        }
                        Text(
                            text = "Gemma 4 On-Device AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                StatusBadge(
                    text = if (modelState.ready) "Offline" else "Setup",
                    color = ElectricIndigo,
                    isActive = modelState.ready
                )
            }

            GemmaModelPanel(
                installation = installation,
                runtime = modelState,
                onImport = { modelPicker.launch(arrayOf("*/*")) },
                onDownload = { GemmaModelRepository.download(it) },
                onCancel = { GemmaModelRepository.cancel() },
                onRetryLoading = { modelViewModel.retryLoading() }
            )

            if (!notificationAccess) {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    } catch (error: ActivityNotFoundException) {
                        session.reportError("Notification access settings are unavailable on this device.")
                    }
                }) { Text("Enable notification access (optional)") }
            }

            // Error Banner
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                errorMessage?.let {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Chat Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Typing Indicator at bottom of chat (top of reversed list)
                if (isLoading) {
                    item {
                        ThinkingBubble()
                    }
                }

                items(messages.reversed()) { msg ->
                    ChatMessageItem(message = msg)
                }
            }

            // Image Preview Floating Chip
            AnimatedVisibility(
                visible = selectedImage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                selectedImage?.let { bitmap ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Selected Attachment",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Attached Image",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Ready for multimodal analysis",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { session.setImage(null) },
                                enabled = !isLoading,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Modern Bottom Input Dock
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Camera Action Button
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) captureImage()
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        enabled = modelState.ready && modelState.supportsImages && !isLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = if (isLoading) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Gallery Action Button
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        enabled = modelState.ready && modelState.supportsImages && !isLoading,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Gallery",
                            tint = if (isLoading) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Message Text Input
                    TextField(
                        value = message,
                        onValueChange = session::updateDraft,
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Ask anything...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        singleLine = false,
                        maxLines = 4,
                        enabled = modelState.ready && !isLoading
                    )

                    // Send Action Button
                    val canSend = modelState.ready && (message.isNotBlank() || selectedImage != null) && !isLoading
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (canSend) Brush.linearGradient(listOf(ElectricIndigo, VioletAccent))
                                else Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                            )
                            .clickable(enabled = canSend) {
                                session.send()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern chat message bubble component.
 */
@Composable
private fun ChatMessageItem(message: String) {
    val isUser = message.startsWith("You:")
    val isNotification = message.startsWith("Notification from")
    val isImageEvent = message.startsWith("Image selected") || message.startsWith("Image captured")

    val cleanText = when {
        isUser -> message.removePrefix("You:").trim()
        message.startsWith("Bot:") -> message.removePrefix("Bot:").trim()
        else -> message
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = when {
            isUser -> Alignment.CenterEnd
            isNotification || isImageEvent -> Alignment.Center
            else -> Alignment.CenterStart
        }
    ) {
        when {
            isUser -> {
                // User Bubble with Modern Gradient
                Surface(
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                    color = Color.Transparent,
                    modifier = Modifier.widthIn(max = 290.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricIndigo, VioletAccent)
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = cleanText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            isNotification -> {
                // Notification Smart Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AmberWarning.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.35f)),
                    modifier = Modifier.widthIn(max = 330.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            isImageEvent -> {
                // Image Event Pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            else -> {
                // AI Bot Bubble
                Row(
                    modifier = Modifier.widthIn(max = 310.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(ElectricIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricIndigo,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = cleanText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated thinking dots for when Gemma LLM is generating.
 */
@Composable
private fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingAnim")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 36.dp, top = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gemma is thinking",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ElectricIndigo.copy(alpha = dot1Alpha))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ElectricIndigo.copy(alpha = dot2Alpha))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ElectricIndigo.copy(alpha = dot3Alpha))
                )
            }
        }
    }
}
