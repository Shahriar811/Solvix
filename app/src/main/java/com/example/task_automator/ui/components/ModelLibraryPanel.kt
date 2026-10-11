package com.example.task_automator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.task_automator.ChatModelState
import com.example.task_automator.helpers.*
import java.util.Locale

@Composable
fun ModelLibraryPanel(
    installation: ModelInstallState, runtime: ChatModelState, interactionBusy: Boolean,
    onImport: (String) -> Unit, onDownload: (String, Boolean) -> Unit,
    onUse: (String) -> Unit, onRemove: (String) -> Unit, onCancel: () -> Unit,
    onRefresh: () -> Unit, onSource: (String) -> Unit
) {
    var expandedId by rememberSaveable { mutableStateOf(ModelCatalog.DEFAULT_ID) }
    var installId by rememberSaveable { mutableStateOf<String?>(null) }
    var importMode by rememberSaveable { mutableStateOf(false) }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    var removeId by rememberSaveable { mutableStateOf<String?>(null) }
    var useId by rememberSaveable { mutableStateOf<String?>(null) }
    val blocked = installation.busy || interactionBusy || runtime.loading || !installation.initialized
    val hardware = installation.hardware
    Text("Models", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(8.dp))
    hardware?.let { phone ->
        Text(phone.name, style = MaterialTheme.typography.bodyMedium)
        Text("${phone.soc} · ${phone.cpuCores} CPU cores · Android API ${phone.apiLevel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("RAM ${gib(phone.totalRamBytes)} GiB · available ${gib(phone.availableRamBytes)} GiB · storage ${gb(phone.freeStorageBytes)} GB free", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${if (phone.process64Bit) "64-bit" else "32-bit"} · ${phone.abis.firstOrNull().orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } ?: Text("Checking phone hardware…", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = onRefresh, enabled = !installation.busy) { Text("Recheck phone") }
    Text("Hardware checks are estimates. Loading confirms runtime support; available memory and GPU drivers can still affect usability.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    ModelCatalog.models.forEach { model ->
        val installed = model.id in installation.installedModels
        val active = runtime.ready && runtime.modelId == model.id
        val compatibility = hardware?.let { ModelCompatibility.assess(model, it, installed) }
        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth().clickable { expandedId = if (expandedId == model.id) "" else model.id }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model.name + if (model.id == ModelCatalog.DEFAULT_ID) " · Default" else "", style = MaterialTheme.typography.titleSmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text("${model.sizeLabel} · ${if (model.supportsImages) "Text + photos" else "Text only"}" + when { active -> " · Active"; installed -> " · Installed"; else -> "" }, style = MaterialTheme.typography.bodySmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (expandedId == model.id) Icons.Default.ExpandLess else Icons.Default.ExpandMore, "Model details")
                }
                if (expandedId == model.id) {
                    Spacer(Modifier.height(8.dp))
                    Text(model.description, style = MaterialTheme.typography.bodySmall)
                    Text("RAM: at least ${model.minTotalRamGiB} GiB total; estimated runtime ${model.estimatedRuntimeRamGiB} GiB. Context ${model.contextTokens} tokens.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    compatibility?.let { check ->
                        Text(check.label, style = MaterialTheme.typography.bodySmall, color = if (check.allowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        check.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        check.cautions.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (installed) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRefresh(); useId = model.id }, enabled = !blocked && !active && compatibility?.allowed == true) { Text(if (active) "Active" else "Use model") }
                            if (!active) TextButton(onClick = { removeId = model.id }, enabled = !blocked) { Text("Remove") }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRefresh(); installId = model.id; importMode = false }, enabled = !blocked && compatibility?.allowed == true) { Text("Download") }
                            OutlinedButton(onClick = { onRefresh(); installId = model.id; importMode = true }, enabled = !blocked && compatibility?.allowed == true) { Text("Import") }
                        }
                    }
                    TextButton(onClick = { onSource(model.sourceUrl) }) { Text("Hugging Face · source and license") }
                }
                if (installation.operationModelId == model.id && installation.busy) {
                    Text(installation.label, style = MaterialTheme.typography.bodySmall)
                    val progress = installation.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    } else LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (installation.canCancel) TextButton(onClick = onCancel) { Text("Cancel") }
                }
                if (runtime.loading && runtime.targetModelId == model.id) {
                    Text("Loading model…", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (active && model.supportsImages && !runtime.supportsImages) Text("Text is ready; this phone's GPU could not load image support.", style = MaterialTheme.typography.bodySmall)
                if (runtime.failedModelId == model.id) runtime.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    (installation.error ?: runtime.error)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (!runtime.ready && !blocked && installation.activeModelId in installation.installedModels) TextButton(onClick = { onUse(installation.activeModelId) }) { Text("Retry loading") }
    installId?.let { id ->
        val model = ModelCatalog.find(id)
        val check = hardware?.let { ModelCompatibility.assess(model, it) }
        AlertDialog(onDismissRequest = { installId = null }, title = { Text(if (importMode) "Import ${model.name}?" else "Download ${model.name}?") },
            text = { Column {
                Text(check?.label ?: "Checking hardware…", style = MaterialTheme.typography.titleSmall)
                Text("File: ${model.sizeLabel}. Allow ${gb(model.requiredStorage)} GB of free storage including an estimated runtime cache.")
                if (importMode) Text("Choose ${model.fileName}. Imports keep your original copy; the complete size and checksum will be verified.")
                else Text("Download from Hugging Face. No account is needed for this bundle. Check the model source and license before installing.")
                check?.reasons?.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                check?.cautions?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (!importMode) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi only", Modifier.weight(1f)); Switch(wifiOnly, { wifiOnly = it })
                }
            } },
            confirmButton = { TextButton(enabled = check?.allowed == true && !blocked, onClick = {
                installId = null
                if (importMode) onImport(id) else onDownload(id, wifiOnly)
            }) { Text(if (importMode) "Choose file" else "Start download") } },
            dismissButton = { TextButton(onClick = { installId = null }) { Text("Cancel") } })
    }
    useId?.let { id ->
        val model = ModelCatalog.find(id)
        val check = hardware?.let { ModelCompatibility.assess(model, it, true) }
        AlertDialog(onDismissRequest = { useId = null }, title = { Text("Use ${model.name}?") }, text = { Column {
            Text("The current model will be unloaded first. If loading fails, Solvix will try to restore it.")
            if (!model.supportsImages) Text("This model is text-only. If the current chat contains photos, a new chat opens; the saved conversation stays in history.")
            check?.reasons?.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            check?.cautions?.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        } }, confirmButton = { TextButton(enabled = !blocked && check?.allowed == true, onClick = { useId = null; onUse(id) }) { Text("Switch model") } },
            dismissButton = { TextButton(onClick = { useId = null }) { Text("Cancel") } })
    }
    removeId?.let { id ->
        val model = ModelCatalog.find(id)
        AlertDialog(onDismissRequest = { removeId = null }, title = { Text("Remove ${model.name}?") }, text = { Text("Remove the installed model file from this device. Your chats remain saved. You can import or download the model again.") },
            confirmButton = { TextButton(onClick = { removeId = null; onRemove(id) }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removeId = null }) { Text("Cancel") } })
    }
}

private fun gb(value: Long) = String.format(Locale.US, "%.2f", value / 1_000_000_000.0)
private fun gib(value: Long) = String.format(Locale.US, "%.1f", value.toDouble() / GIB)
