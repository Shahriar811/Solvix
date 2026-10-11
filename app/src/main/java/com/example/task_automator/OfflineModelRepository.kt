package com.example.task_automator.helpers

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ModelInstallState(
    val installedPath: String? = null,
    val busy: Boolean = false,
    val canCancel: Boolean = false,
    val label: String = "Download or import the offline assistant to start.",
    val progress: Float? = null,
    val error: String? = null
)

/** DownloadManager keeps the large transfer alive across navigation and process restarts. */
object OfflineModelRepository {
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val MODEL_SIZE = 2_588_147_712L
    const val MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    // Pin the file revision so an upstream update cannot invalidate the bundled checksum.
    const val DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1/gemma-4-E2B-it.litertlm"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(ModelInstallState(busy = true, label = "Checking model…"))
    val state = mutableState.asStateFlow()
    private lateinit var appContext: Context
    private var operation: Job? = null
    @Volatile private var cancelling = false
    private val preferences get() = appContext.getSharedPreferences("gemma4_model", Context.MODE_PRIVATE)
    private val downloads get() = appContext.getSystemService(DownloadManager::class.java)
    private val directory get() = (appContext.getExternalFilesDir("models")
        ?: appContext.noBackupFilesDir.resolve("models")).apply { mkdirs() }
    private val modelFile get() = directory.resolve(FILE_NAME)
    private val partialFile get() = directory.resolve("$FILE_NAME.part")

    @Synchronized
    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        operation = scope.launch {
            try {
                if (modelFile.isFile) {
                    // Skip rehashing 2.59 GB only if the previously verified file is unchanged.
                    if (modelFile.length() != MODEL_SIZE ||
                        modelFile.length() != preferences.getLong("verified_size", -1) ||
                        modelFile.lastModified() != preferences.getLong("verified_modified", -1)) {
                        verify(modelFile)
                        saveVerification()
                    }
                    publishInstalled()
                } else {
                    val downloadId = preferences.getLong("download_id", -1)
                    if (downloadId >= 0) monitorDownload(downloadId)
                    else {
                        partialFile.delete() // Interrupted imports are never treated as installed.
                        mutableState.value = ModelInstallState()
                    }
                }
            } catch (error: Exception) {
                handleFailure(error)
            }
        }
    }

    @Synchronized
    fun download(wifiOnly: Boolean = true) {
        if (cancelling || mutableState.value.busy || mutableState.value.installedPath != null) return
        mutableState.value = ModelInstallState(busy = true, canCancel = true, label = "Starting download…")
        operation = scope.launch {
            try {
                checkSpace()
                partialFile.delete()
                val request = DownloadManager.Request(Uri.parse(DOWNLOAD_URL))
                    .setTitle("Solvix offline assistant")
                    .setDescription("Downloading the offline AI model (2.59 GB)")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(!wifiOnly)
                    .setAllowedNetworkTypes(if (wifiOnly) DownloadManager.Request.NETWORK_WIFI
                        else DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                    .setAllowedOverRoaming(false)
                    .setDestinationUri(Uri.fromFile(partialFile))
                val id = downloads.enqueue(request)
                preferences.edit().putLong("download_id", id).commit()
                monitorDownload(id)
            } catch (error: Exception) {
                handleFailure(error)
            }
        }
    }

    @Synchronized
    fun importModel(uri: Uri) {
        if (cancelling || mutableState.value.busy || mutableState.value.installedPath != null) return
        mutableState.value = ModelInstallState(busy = true, canCancel = true, label = "Importing model…", progress = 0f)
        operation = scope.launch {
            try {
                val resolver = appContext.contentResolver
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                        val name = if (nameColumn >= 0 && !cursor.isNull(nameColumn)) cursor.getString(nameColumn) else null
                        require(name?.endsWith(".litertlm", ignoreCase = true) != false) {
                            "Select the standard offline .litertlm model (2.59 GB)."
                        }
                        val declaredSize = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else -1L
                        if (declaredSize >= 0) require(declaredSize == MODEL_SIZE) {
                            "Select the standard 2.59 GB mobile model, rather than a processor-specific or web version."
                        }
                    }
                }
                checkSpace()
                resolver.openInputStream(uri)?.use { input ->
                    partialFile.outputStream().buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            require(copied <= MODEL_SIZE) { "Select the standard offline model (2.59 GB)." }
                            output.write(buffer, 0, count)
                            if (!cancelling) mutableState.value = mutableState.value.copy(progress = copied.toFloat() / MODEL_SIZE)
                        }
                    }
                } ?: throw IOException("Cannot open the selected model. Please select it again.")
                installPartial()
            } catch (error: Exception) {
                handleFailure(error)
            }
        }
    }

    @Synchronized
    fun cancel() {
        if (cancelling || !mutableState.value.canCancel) return
        cancelling = true
        val previousOperation = operation
        mutableState.value = mutableState.value.copy(busy = true, canCancel = false, label = "Cancelling…")
        operation = scope.launch {
            try {
                previousOperation?.cancelAndJoin()
                clearDownload()
                partialFile.delete()
                if (modelFile.isFile) publishInstalled()
                else mutableState.value = ModelInstallState(label = "Cancelled. You can download or import again.")
            } catch (error: Exception) {
                mutableState.value = ModelInstallState(error = "Could not cancel the download: ${error.message}")
            } finally {
                cancelling = false
            }
        }
    }

    private suspend fun monitorDownload(id: Long) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val cursor = downloads.query(DownloadManager.Query().setFilterById(id))
                ?: throw IOException("Download is unavailable. Please retry.")
            cursor.use {
                if (!it.moveToFirst()) throw IOException("Download was removed. Please retry.")
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val progress = (bytes.toFloat() / MODEL_SIZE).coerceIn(0f, 1f)
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        installPartial()
                        return
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        throw IOException(if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE)
                            "Not enough storage. Free at least 2.7 GB and retry."
                        else "Download failed ($reason). Check your connection and retry, or import the file.")
                    }
                    else -> if (!cancelling) mutableState.value = ModelInstallState(
                        busy = true, canCancel = true, progress = progress,
                        label = when (status) {
                            DownloadManager.STATUS_PAUSED -> "Waiting for Wi-Fi or connection — download will resume…"
                            DownloadManager.STATUS_PENDING -> "Waiting for download to start…"
                            else -> "Downloading from Hugging Face…"
                        }
                    )
                }
            }
            delay(1000)
        }
    }

    private suspend fun installPartial() {
        verify(partialFile)
        currentCoroutineContext().ensureActive()
        check(partialFile.renameTo(modelFile)) { "Cannot save the model. Check available storage and retry." }
        saveVerification()
        // Keep the successfully installed file; only forget DownloadManager's saved ID.
        preferences.edit().remove("download_id").commit()
        publishInstalled()
    }

    private suspend fun verify(file: File) {
        currentCoroutineContext().ensureActive()
        if (!cancelling) mutableState.value = ModelInstallState(busy = true, canCancel = file == partialFile, label = "Verifying model…", progress = 0f)
        val coroutineContext = currentCoroutineContext()
        ModelIntegrity.verify(file, MODEL_SIZE, MODEL_SHA256) { bytes ->
            coroutineContext.ensureActive()
            if (!cancelling) mutableState.value = mutableState.value.copy(progress = bytes.toFloat() / MODEL_SIZE)
        }
    }

    private fun saveVerification() {
        preferences.edit().putLong("verified_size", modelFile.length())
            .putLong("verified_modified", modelFile.lastModified()).commit()
    }

    private fun publishInstalled() {
        mutableState.value = ModelInstallState(installedPath = modelFile.absolutePath, label = "The offline assistant is installed.")
    }

    private fun checkSpace() {
        require(StatFs(directory.absolutePath).availableBytes >= MODEL_SIZE + 100_000_000L) {
            "Not enough storage. Free at least 2.7 GB for the model. Imports also keep your original file."
        }
    }

    private fun clearDownload() {
        val id = preferences.getLong("download_id", -1)
        if (id >= 0) downloads.remove(id)
        preferences.edit().remove("download_id").commit()
    }

    private suspend fun handleFailure(error: Exception) {
        if (error is CancellationException) throw error
        // A cancelled stream can throw IOException; do not publish idle or delete files
        // while cancel() is still joining this operation.
        currentCoroutineContext().ensureActive()
        clearDownload()
        partialFile.delete()
        // A modified or incomplete installed file must not prevent a clean retry.
        modelFile.delete()
        mutableState.value = ModelInstallState(error = error.message ?: "Unable to install the model. Please retry.")
    }
}
