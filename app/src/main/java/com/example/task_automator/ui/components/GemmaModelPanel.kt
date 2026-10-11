package com.example.task_automator.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.task_automator.ChatModelState
import com.example.task_automator.helpers.ModelInstallState

@Composable
fun GemmaModelPanel(
    installation: ModelInstallState,
    runtime: ChatModelState,
    onImport: () -> Unit,
    onDownload: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onRetryLoading: () -> Unit
) {
    var showDownloadDialog by remember { mutableStateOf(false) }
    var wifiOnly by remember { mutableStateOf(true) }
    val installed = installation.installedPath != null

    SolvixCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text("Gemma 4 E2B", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                runtime.loading -> "Loading your offline assistant…"
                runtime.ready && runtime.supportsImages -> "Ready for offline text chat and image analysis."
                runtime.ready -> "Offline text chat is ready. Image analysis is unavailable on this device's GPU."
                else -> installation.label
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (!installed && !installation.busy) {
            Text("One model for chat and images · 2.59 GB", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showDownloadDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Download")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text("Import")
                }
            }
        }
        if (installation.busy || runtime.loading) {
            Spacer(Modifier.height(8.dp))
            if (installation.progress != null) {
                LinearProgressIndicator(progress = { installation.progress }, modifier = Modifier.fillMaxWidth())
                Text("${(installation.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (installation.canCancel) TextButton(onClick = onCancel) { Text("Cancel") }
        }
        (installation.error ?: runtime.error)?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (installed && !runtime.loading) TextButton(onClick = onRetryLoading) { Text("Retry loading") }
        }
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download Gemma 4?") },
            text = {
                Column {
                    Text("Download 2.59 GB from Hugging Face once. After setup, chat and image analysis work offline. You need at least 2.7 GB of free storage.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use Wi-Fi only", modifier = Modifier.weight(1f))
                        Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    onDownload(wifiOnly)
                }) { Text("Start download") }
            },
            dismissButton = { TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") } }
        )
    }
}
