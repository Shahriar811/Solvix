package com.example.task_automator

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.task_automator.helpers.OfflineModelRepository
import com.example.task_automator.helpers.ModelCatalog
import com.example.task_automator.helpers.ModelCompatibility
import com.example.task_automator.helpers.LlmInferenceHelper
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ChatModelViewModel(application: Application) : AndroidViewModel(application) {
    private val inference = LlmInferenceHelper()
    val history = ChatHistoryStore(application.noBackupFilesDir.resolve("chats"))
    private val runtime = ModelRuntimeController(viewModelScope,
        path = { OfflineModelRepository.state.value.installedModels[it] },
        load = { model, path ->
            inference.initialize(application, path, model)
            inference.supportsImages
        },
        commit = OfflineModelRepository::confirmActive
    )
    val state = runtime.state
    val session = ChatSession(viewModelScope,
        respond = { chat, prompt, imageName, emit ->
            inference.streamResponse(chat.id, chat.messages, prompt, imageName, history::imageFile, emit)
        },
        persist = { chats, draftImage -> withContext(Dispatchers.IO) {
            history.save(chats)
            history.removeUnusedImages(chats, draftImage)
        } }
    )
    private var loadingHistory = false

    init {
        loadHistory()
        viewModelScope.launch {
            var previousModelId: String? = null
            state.collect { current ->
                if (current.ready && current.modelId != previousModelId) {
                    if (!current.supportsImages && (session.state.value.imageName != null || session.state.value.current.messages.any { it.imageName != null })) session.newChat()
                    previousModelId = current.modelId
                }
            }
        }
        OfflineModelRepository.initialize(application)
        viewModelScope.launch {
            OfflineModelRepository.state.collect { installation ->
                if (installation.initialized && installation.installedPath != null && !state.value.ready && !state.value.loading && state.value.error == null) runtime.use(installation.activeModelId)
            }
        }
    }
    fun loadHistory() {
        if (loadingHistory || session.state.value.historyReady) return
        loadingHistory = true
        viewModelScope.launch {
            try {
                val chats = withContext(Dispatchers.IO) {
                    history.load().also { chats ->
                        history.save(chats)
                        history.removeUnusedImages(chats)
                    }
                }
                session.restore(chats)
            } catch (error: Exception) { session.reportError("Could not read saved chats. Your history has been preserved. Retry loading history.") }
            finally { loadingHistory = false }
        }
    }
    fun importModel(uri: Uri, id: String) {
        viewModelScope.launch {
            if (!OfflineModelRepository.state.value.initialized) OfflineModelRepository.state.first { it.initialized }
            if (OfflineModelRepository.state.value.busy) session.reportError("Another model installation is in progress. Wait or cancel it first.")
            else OfflineModelRepository.importModel(uri, id)
        }
    }
    fun removeModel(id: String) {
        if (session.state.value.busy || state.value.loading) return
        OfflineModelRepository.remove(id, state.value.modelId.takeIf { state.value.ready })
    }
    fun retryLoading() { useModel(OfflineModelRepository.state.value.activeModelId) }
    fun useModel(id: String) {
        if (session.state.value.busy || state.value.loading || OfflineModelRepository.state.value.busy) return
        val hardware = OfflineModelRepository.state.value.hardware ?: return
        val check = ModelCompatibility.assess(ModelCatalog.find(id), hardware, installed = true)
        if (!check.allowed) { session.reportError(check.reasons.joinToString(" ")); return }
        runtime.use(id)
    }
    fun attach(uri: Uri) = loadAttachment {
        val resolver = getApplication<Application>().contentResolver
        if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val ratio = (1024f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1), (info.size.height * ratio).toInt().coerceAtLeast(1))
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val options = BitmapFactory.Options()
            while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Cannot read image")
        }
    }
    fun attach(bitmap: Bitmap) = loadAttachment { bitmap }
    private fun loadAttachment(decode: () -> Bitmap) {
        if (session.state.value.busy || !session.state.value.historyReady) return
        session.setLoadingImage(true)
        viewModelScope.launch {
            try {
                val name = withContext(Dispatchers.IO) {
                    val bitmap = decode()
                    val bytes = ByteArrayOutputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                        output.toByteArray()
                    }
                    history.putImage(bytes)
                }
                session.setImage(name)
                session.reportError(null)
            } catch (error: Exception) { session.reportError("Could not attach this image. Please select it again.") }
            finally { session.setLoadingImage(false) }
        }
    }
    override fun onCleared() {
        super.onCleared()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { inference.cleanup() }
    }
}
