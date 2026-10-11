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
import com.example.task_automator.helpers.LlmInferenceHelper
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatModelState(val loading: Boolean = false, val ready: Boolean = false, val supportsImages: Boolean = false, val error: String? = null)

class ChatModelViewModel(application: Application) : AndroidViewModel(application) {
    private val inference = LlmInferenceHelper()
    val history = ChatHistoryStore(application.noBackupFilesDir.resolve("chats"))
    private val mutableState = MutableStateFlow(ChatModelState())
    val state = mutableState.asStateFlow()
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
        OfflineModelRepository.initialize(application)
        viewModelScope.launch {
            OfflineModelRepository.state.collect { installation ->
                if (installation.installedPath != null && !state.value.ready && !state.value.loading && state.value.error == null) loadModel(installation.installedPath)
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
    fun retryLoading() {
        val path = OfflineModelRepository.state.value.installedPath ?: return
        if (state.value.loading || state.value.ready) return
        mutableState.value = ChatModelState(loading = true)
        viewModelScope.launch { loadModel(path) }
    }
    private suspend fun loadModel(path: String) {
        mutableState.value = ChatModelState(loading = true)
        try {
            inference.initialize(getApplication(), path)
            mutableState.value = ChatModelState(ready = true, supportsImages = inference.supportsImages)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { mutableState.value = ChatModelState(error = "The offline assistant could not load. Close other apps and try again.") }
        catch (error: UnsatisfiedLinkError) { mutableState.value = ChatModelState(error = "The offline assistant needs a supported 64-bit device.") }
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
