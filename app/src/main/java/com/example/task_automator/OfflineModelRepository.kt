package com.example.task_automator.helpers

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ModelInstallState(
    val installedPath: String? = null,
    val installedModels: Map<String, String> = emptyMap(),
    val activeModelId: String = ModelCatalog.DEFAULT_ID,
    val hardware: DeviceHardware? = null,
    val initialized: Boolean = false,
    val busy: Boolean = false,
    val canCancel: Boolean = false,
    val operationModelId: String? = null,
    val label: String = "Download or import an offline assistant.",
    val progress: Float? = null,
    val error: String? = null
)

/** Each verified model has its own file. A failed transfer never removes another model. */
object OfflineModelRepository {
    // Preserve the legacy default constants and directory for existing installations.
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val MODEL_SIZE = 2_588_147_712L
    const val MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    val DOWNLOAD_URL get() = ModelCatalog.default.downloadUrl
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(ModelInstallState(busy = true, label = "Checking model library…"))
    val state = mutableState.asStateFlow()
    private lateinit var app: Context
    private var operation: Job? = null
    @Volatile private var cancelling = false
    private val preferences get() = app.getSharedPreferences("gemma4_model", Context.MODE_PRIVATE)
    private val downloads get() = app.getSystemService(DownloadManager::class.java)
    private val directory get() = (app.getExternalFilesDir("models") ?: app.noBackupFilesDir.resolve("models")).apply { mkdirs() }
    private fun file(model: ModelSpec) = directory.resolve(model.fileName)
    private fun partial(model: ModelSpec) = directory.resolve("${model.fileName}.part")

    @Synchronized fun initialize(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        operation = scope.launch {
            val paths = mutableMapOf<String, String>()
            val errors = mutableListOf<String>()
            ModelCatalog.models.forEach { model ->
                val target = file(model)
                if (target.isFile) {
                    try {
                        if (!isVerified(model)) verify(model, target, false)
                        saveVerification(model)
                        paths[model.id] = target.absolutePath
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        // Only corrupt known bundles are removed. Other models remain intact.
                        if (error is IllegalArgumentException) target.delete()
                        errors += "${model.name}: verification failed; import or download it again."
                    }
                }
            }
            val requested = preferences.getString("active_model", ModelCatalog.DEFAULT_ID) ?: ModelCatalog.DEFAULT_ID
            val active = if (requested in paths) requested else ModelCatalog.DEFAULT_ID
            mutableState.update { it.copy(installedModels = paths, activeModelId = active, installedPath = paths[active],
                hardware = HardwareInspector.read(app, directory), initialized = true, error = errors.firstOrNull()) }
            val downloadId = preferences.getLong("download_id", -1)
            if (downloadId >= 0) {
                val id = preferences.getString("download_model", ModelCatalog.DEFAULT_ID) ?: ModelCatalog.DEFAULT_ID
                try {
                    val model = ModelCatalog.find(id)
                    setBusy(model, "Resuming download…", true)
                    monitor(model, downloadId)
                } catch (error: Exception) { fail(error, ModelCatalog.models.find { it.id == id }) }
            } else {
                ModelCatalog.models.forEach { partial(it).delete() }
                finish("Model library ready.", errors.firstOrNull())
            }
        }
    }

    fun refreshHardware() {
        if (!::app.isInitialized) return
        scope.launch { mutableState.update { it.copy(hardware = HardwareInspector.read(app, directory)) } }
    }

    @Synchronized fun download(wifiOnly: Boolean = true, modelId: String = ModelCatalog.DEFAULT_ID) {
        val model = begin(modelId, "Starting download…") ?: return
        operation = scope.launch {
            try {
                preflight(model)
                partial(model).delete()
                val request = DownloadManager.Request(Uri.parse(model.downloadUrl))
                    .setTitle("Solvix · ${model.name}")
                    .setDescription("Offline model · ${model.sizeLabel}")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(!wifiOnly)
                    .setAllowedNetworkTypes(if (wifiOnly) DownloadManager.Request.NETWORK_WIFI else DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                    .setAllowedOverRoaming(false).setDestinationUri(Uri.fromFile(partial(model)))
                val id = downloads.enqueue(request)
                preferences.edit().putLong("download_id", id).putString("download_model", model.id).commit()
                monitor(model, id)
            } catch (error: Exception) { fail(error, model) }
        }
    }

    @Synchronized fun importModel(uri: Uri, modelId: String = ModelCatalog.DEFAULT_ID) {
        val model = begin(modelId, "Checking import…") ?: return
        operation = scope.launch {
            try {
                preflight(model)
                val resolver = app.contentResolver
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
                        require(name?.endsWith(".litertlm", true) != false) { "Import the selected model's .litertlm mobile bundle; GGUF and .task files are not supported." }
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                        require(size < 0 || size == model.sizeBytes) { "This file does not match ${model.name} (${model.sizeLabel}). Select ${model.fileName}." }
                    }
                }
                setBusy(model, "Importing ${model.name}…", true, 0f)
                resolver.openInputStream(uri)?.use { input ->
                    partial(model).outputStream().buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            require(copied <= model.sizeBytes) { "The file is larger than the selected model. Choose ${model.fileName}." }
                            output.write(buffer, 0, count)
                            if (!cancelling) mutableState.update { it.copy(progress = copied.toFloat() / model.sizeBytes) }
                        }
                    }
                } ?: throw IOException("Cannot read the selected file. Please select it again.")
                install(model)
            } catch (error: Exception) { fail(error, model) }
        }
    }

    private fun begin(id: String, label: String): ModelSpec? {
        if (cancelling || state.value.busy || !state.value.initialized || id in state.value.installedModels) return null
        return ModelCatalog.find(id).also { setBusy(it, label, true) }
    }
    private fun preflight(model: ModelSpec) {
        val hardware = HardwareInspector.read(app, directory)
        mutableState.update { it.copy(hardware = hardware) }
        val result = ModelCompatibility.assess(model, hardware)
        require(result.allowed) { result.reasons.joinToString(" ") }
    }
    private fun setBusy(model: ModelSpec, label: String, canCancel: Boolean, progress: Float? = null) {
        if (!cancelling) mutableState.update { it.copy(busy = true, operationModelId = model.id, label = label,
            canCancel = canCancel, progress = progress, error = null) }
    }

    @Synchronized fun cancel() {
        if (cancelling || !state.value.canCancel) return
        val model = state.value.operationModelId?.let(ModelCatalog::find) ?: return
        cancelling = true
        val previous = operation
        mutableState.update { it.copy(canCancel = false, label = "Cancelling…") }
        operation = scope.launch {
            try {
                previous?.cancelAndJoin()
                clearDownload()
                partial(model).delete()
                // Installation may have finished just as cancel was requested.
                if (file(model).isFile && isVerified(model)) addInstalled(model)
                finish("Cancelled. Installed models are preserved.")
            } catch (error: Exception) { finish("Model library ready.", "Could not cancel: ${error.message}") }
            finally { cancelling = false }
        }
    }

    private suspend fun monitor(model: ModelSpec, id: Long) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val cursor = downloads.query(DownloadManager.Query().setFilterById(id)) ?: throw IOException("Download is unavailable. Retry or import the file.")
            cursor.use {
                if (!it.moveToFirst()) throw IOException("Download was removed. Please retry.")
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> { install(model); return }
                    DownloadManager.STATUS_FAILED -> throw IOException("Download failed. Check storage and connection, then retry or import.")
                    else -> setBusy(model, when (status) {
                        DownloadManager.STATUS_PAUSED -> "Waiting for Wi-Fi or connection…"
                        DownloadManager.STATUS_PENDING -> "Waiting to download ${model.name}…"
                        else -> "Downloading ${model.name}…"
                    }, true, (bytes.toFloat() / model.sizeBytes).coerceIn(0f, 1f))
                }
            }
            delay(1000)
        }
    }

    private suspend fun install(model: ModelSpec) {
        verify(model, partial(model), true)
        currentCoroutineContext().ensureActive()
        check(partial(model).renameTo(file(model))) { "Cannot save this model. Check storage and retry." }
        saveVerification(model)
        addInstalled(model)
        preferences.edit().remove("download_id").remove("download_model").commit()
        finish("${model.name} installed. Choose Use model to switch.")
    }
    private suspend fun verify(model: ModelSpec, target: File, canCancel: Boolean) {
        currentCoroutineContext().ensureActive()
        setBusy(model, "Verifying ${model.name}…", canCancel, 0f)
        val context = currentCoroutineContext()
        ModelIntegrity.verify(target, model.sizeBytes, model.sha256) { checked ->
            context.ensureActive()
            if (!cancelling) mutableState.update { it.copy(progress = checked.toFloat() / model.sizeBytes) }
        }
    }
    private fun saveVerification(model: ModelSpec) {
        preferences.edit().putLong("${model.id}_size", file(model).length())
            .putLong("${model.id}_modified", file(model).lastModified()).putString("${model.id}_sha256", model.sha256).commit()
    }
    private fun isVerified(model: ModelSpec): Boolean {
        val target = file(model)
        val cached = target.length() == preferences.getLong("${model.id}_size", -1) &&
            target.lastModified() == preferences.getLong("${model.id}_modified", -1) && preferences.getString("${model.id}_sha256", null) == model.sha256
        val legacy = model.id == ModelCatalog.DEFAULT_ID && model.sha256 == MODEL_SHA256 &&
            target.length() == preferences.getLong("verified_size", -1) && target.lastModified() == preferences.getLong("verified_modified", -1)
        return target.isFile && target.length() == model.sizeBytes && (cached || legacy)
    }
    private fun addInstalled(model: ModelSpec) {
        mutableState.update {
            val paths = it.installedModels + (model.id to file(model).absolutePath)
            it.copy(installedModels = paths, installedPath = paths[it.activeModelId])
        }
    }
    fun confirmActive(modelId: String) {
        require(modelId in state.value.installedModels)
        preferences.edit().putString("active_model", modelId).apply()
        mutableState.update { it.copy(activeModelId = modelId, installedPath = it.installedModels[modelId]) }
        refreshHardware()
    }
    @Synchronized fun remove(modelId: String, loadedModelId: String? = state.value.activeModelId) {
        if (state.value.busy || modelId == loadedModelId || modelId !in state.value.installedModels) return
        val model = ModelCatalog.find(modelId)
        setBusy(model, "Removing model…", false)
        operation = scope.launch {
            try {
                check(file(model).delete()) { "Could not remove this model." }
                val cacheRoot = app.cacheDir.resolve("models-v018").canonicalFile
                val cache = cacheRoot.resolve(model.id).canonicalFile
                check(cache.parentFile == cacheRoot) { "Invalid model cache path." }
                cache.deleteRecursively()
                mutableState.update {
                    val paths = it.installedModels - modelId
                    it.copy(installedModels = paths, installedPath = paths[it.activeModelId])
                }
                preferences.edit().remove("${model.id}_size").remove("${model.id}_modified").remove("${model.id}_sha256").apply()
                finish("${model.name} removed. Chat history is unchanged.")
            } catch (error: Exception) { finish("Model library ready.", error.message) }
        }
    }
    private fun finish(label: String, error: String? = null) {
        mutableState.update { it.copy(busy = false, canCancel = false, operationModelId = null, progress = null,
            hardware = HardwareInspector.read(app, directory), label = label, error = error) }
    }
    private fun clearDownload() {
        val id = preferences.getLong("download_id", -1)
        if (id >= 0) downloads.remove(id)
        preferences.edit().remove("download_id").remove("download_model").commit()
    }
    private suspend fun fail(error: Exception, model: ModelSpec?) {
        if (error is CancellationException) throw error
        currentCoroutineContext().ensureActive()
        clearDownload()
        model?.let { partial(it).delete() }
        finish("Model library ready.", error.message ?: "Could not install this model. Please retry.")
    }
}
