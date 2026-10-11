package com.example.task_automator

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.task_automator.helpers.OfflineModelRepository
import com.example.task_automator.ui.components.ModelSetupPanel
import com.example.task_automator.ui.components.AboutSection
import com.example.task_automator.ui.components.DEVELOPER_GITHUB_URL
import com.example.task_automator.ui.theme.AppThemeMode
import com.example.task_automator.ui.theme.ThemeManager
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val vm: ChatModelViewModel = viewModel()
    val chat by vm.session.state.collectAsState()
    val runtime by vm.state.collectAsState()
    val installation by OfflineModelRepository.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var settings by rememberSaveable { mutableStateOf(false) }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    var licenseText by remember { mutableStateOf("") }
    LaunchedEffect(showLicense) {
        if (showLicense && licenseText.isEmpty()) {
            licenseText = withContext(Dispatchers.IO) {
                context.assets.open("LICENSE").bufferedReader().use { it.readText() }
            }
        }
    }
    var attachments by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SavedChat?>(null) }
    val listState = rememberLazyListState()
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { OfflineModelRepository.importModel(it) } }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::attach) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap -> bitmap?.let(vm::attach) }
    fun capture() {
        try { camera.launch(null) }
        catch (_: ActivityNotFoundException) { vm.session.reportError("No camera app is available. Choose a photo instead.") }
        catch (_: SecurityException) { vm.session.reportError("Enable camera permission in Android app settings.") }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) capture() else vm.session.reportError("Camera access was denied. You can still choose a photo.")
    }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    LaunchedEffect(chat.current.id) {
        if (chat.current.messages.isNotEmpty()) listState.scrollToItem(chat.current.messages.lastIndex)
    }
    LaunchedEffect(chat.current.messages.size, chat.partialReply, chat.sending) {
        val total = listState.layoutInfo.totalItemsCount
        val bottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (total > 0 && bottom >= total - 2) listState.scrollToItem(total - 1)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !chat.busy,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(310.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
                    Text("Chats", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
                    TextButton(onClick = { vm.session.newChat(); scope.launch { drawer.close() } }, enabled = !chat.busy && chat.historyReady) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New chat")
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (chat.chats.isEmpty()) item { Text("Your conversations will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp, 24.dp)) }
                        val pinned = chat.chats.filter { it.pinned }
                        val recent = chat.chats.filterNot { it.pinned }
                        if (pinned.isNotEmpty()) item { HistoryLabel("Pinned") }
                        items(pinned, key = { it.id }) { saved ->
                            HistoryRow(saved, saved.id == chat.current.id, !chat.busy,
                                { vm.session.open(saved.id); scope.launch { drawer.close() } },
                                { vm.session.pin(saved.id) }, { deleting = saved })
                        }
                        if (recent.isNotEmpty()) item { HistoryLabel("Recent") }
                        items(recent, key = { it.id }) { saved ->
                            HistoryRow(saved, saved.id == chat.current.id, !chat.busy,
                                { vm.session.open(saved.id); scope.launch { drawer.close() } },
                                { vm.session.pin(saved.id) }, { deleting = saved })
                        }
                    }
                    Text("Last 10 recent chats are kept. Pinned chats stay until you delete them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp, 12.dp))
                }
            }
        }
    ) {
        ChatLayout(
            header = {
                Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { drawer.open() } }, enabled = !chat.busy) { Icon(Icons.Default.Menu, "Chat history") }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(if (chat.current.messages.isEmpty()) "Solvix" else chat.current.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = vm.session::newChat, enabled = !chat.busy && chat.historyReady) { Icon(Icons.Default.Edit, "New chat") }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Default.MoreHoriz, "Settings") }
                }
            },
            composer = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    chat.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                        if (!chat.historyReady) TextButton(onClick = vm::loadHistory) { Text("Retry loading history") }
                    }
                    chat.imageName?.takeUnless { chat.sending }?.let { name ->
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(vm.history.imageFile(name), "Attached photo", modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.width(8.dp)); Text("Photo attached", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.session.setImage(null) }, enabled = !chat.busy) { Icon(Icons.Default.Close, "Remove photo") }
                        }
                    }
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.Bottom) {
                            Box {
                                IconButton(onClick = { attachments = true }, enabled = runtime.ready && runtime.supportsImages && chat.historyReady && !chat.busy) { Icon(Icons.Default.Add, "Attach photo") }
                                DropdownMenu(expanded = attachments, onDismissRequest = { attachments = false }) {
                                    DropdownMenuItem(text = { Text("Choose photo") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = {
                                        attachments = false
                                        try { imagePicker.launch("image/*") } catch (_: ActivityNotFoundException) { vm.session.reportError("No photo picker is available.") }
                                    })
                                    DropdownMenuItem(text = { Text("Take photo") }, leadingIcon = { Icon(Icons.Default.CameraAlt, null) }, onClick = {
                                        attachments = false
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) capture()
                                        else cameraPermission.launch(Manifest.permission.CAMERA)
                                    })
                                }
                            }
                            TextField(
                                value = if (chat.sending) "" else chat.draft, onValueChange = vm.session::updateDraft,
                                placeholder = { Text("Message Solvix") }, modifier = Modifier.weight(1f), maxLines = 5,
                                enabled = runtime.ready && chat.historyReady && !chat.busy,
                                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                            )
                            FilledIconButton(
                                onClick = { if (chat.sending) vm.session.stop() else vm.session.send() },
                                enabled = chat.sending || (runtime.ready && chat.historyReady && !chat.busy && (chat.draft.isNotBlank() || chat.imageName != null)),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                if (chat.sending) Icon(Icons.Default.Stop, "Stop response")
                                else Icon(Icons.Default.ArrowUpward, "Send message")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            },
            messages = {
                Column(Modifier.fillMaxSize()) {
                    if (!runtime.ready && chat.current.messages.isNotEmpty()) {
                        TextButton(onClick = { settings = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(if (runtime.loading || installation.busy) "Preparing offline assistant…" else "Set up offline assistant")
                        }
                    }
                    if (chat.current.messages.isEmpty() && !chat.sending) {
                        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (runtime.ready) "How can I help?" else "Your private assistant", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(12.dp))
                            Text(when {
                                runtime.loading -> "Preparing your assistant…"
                                installation.busy -> installation.label
                                runtime.ready -> if (runtime.supportsImages) "Ask a question or share a photo." else "Ask a question."
                                else -> "Set up once. Chat offline whenever you need."
                            }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            if (!runtime.ready) {
                                Spacer(Modifier.height(20.dp))
                                if (runtime.loading || installation.busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                else OutlinedButton(onClick = { settings = true }) { Text("Set up assistant") }
                            }
                        }
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            items(chat.current.messages, key = { it.id }) { message -> ChatMessageView(message, message.imageName?.let(vm.history::imageFile)) }
                            if (chat.sending) {
                                item(key = "pending-user") { ChatMessageView(ChatMessage(role = ChatRole.USER, text = chat.draft.trim().ifBlank { "Describe this image." }), chat.imageName?.let(vm.history::imageFile)) }
                                item(key = "reply") {
                                    if (chat.partialReply.isBlank()) Text("Thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else AssistantText(chat.partialReply)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
    if (settings) {
        ModalBottomSheet(onDismissRequest = { settings = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(20.dp))
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                val mode by ThemeManager.themeMode.collectAsState()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AppThemeMode.SYSTEM to "System", AppThemeMode.LIGHT to "Light", AppThemeMode.DARK to "Dark").forEach { (value, label) ->
                        FilterChip(selected = mode == value, onClick = { ThemeManager.setThemeMode(value) }, label = { Text(label) })
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 20.dp))
                ModelSetupPanel(installation, runtime,
                    onImport = { try { modelPicker.launch(arrayOf("*/*")) } catch (_: ActivityNotFoundException) { vm.session.reportError("No file picker is available.") } },
                    onDownload = { OfflineModelRepository.download(it) }, onCancel = OfflineModelRepository::cancel, onRetryLoading = vm::retryLoading)
                HorizontalDivider(Modifier.padding(vertical = 20.dp))
                AboutSection(onOpenGitHub = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DEVELOPER_GITHUB_URL)))
                    } catch (_: ActivityNotFoundException) {
                        vm.session.reportError("No browser is available to open the GitHub profile.")
                    }
                }, onOpenLicense = { showLicense = true })
            }
        }
    }
    if (showLicense) {
        AlertDialog(onDismissRequest = { showLicense = false }, title = { Text("MIT License") },
            text = {
                SelectionContainer {
                    Text(licenseText.ifEmpty { "Loading license…" }, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Close") } })
    }
    deleting?.let { selected ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete chat?") },
            text = { Text("“${selected.title}” and its photos will be removed from this device. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.session.delete(selected.id); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable private fun HistoryLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp, 16.dp))
}
@Composable
internal fun HistoryRow(chat: SavedChat, selected: Boolean, enabled: Boolean, onOpen: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
        .clickable(enabled = enabled, onClick = onOpen).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(chat.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onPin, enabled = enabled) { Icon(Icons.Default.PushPin, if (chat.pinned) "Unpin chat" else "Pin chat", tint = if (chat.pinned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Default.DeleteOutline, "Delete chat", modifier = Modifier.size(18.dp)) }
    }
}
@Composable private fun ChatMessageView(message: ChatMessage, image: File?) {
    if (message.role == ChatRole.USER) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                Column(Modifier.padding(14.dp)) {
                    image?.let { AsyncImage(it, "Shared photo", modifier = Modifier.sizeIn(maxWidth = 260.dp, maxHeight = 200.dp).clip(RoundedCornerShape(12.dp))) }
                    if (message.text.isNotBlank()) SelectionContainer { Text(message.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)) }
                }
            }
        }
    } else AssistantText(message.text)
}
@Composable private fun AssistantText(text: String) {
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth()) {
        SelectionContainer { ReplyContent(text) }
        IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ContentCopy, "Copy answer", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}
