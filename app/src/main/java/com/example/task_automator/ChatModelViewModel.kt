package com.example.task_automator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.task_automator.helpers.GemmaModelRepository
import com.example.task_automator.helpers.LlmInferenceHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatModelState(
    val loading: Boolean = false,
    val ready: Boolean = false,
    val supportsImages: Boolean = false,
    val error: String? = null
)

class ChatModelViewModel(application: Application) : AndroidViewModel(application) {
    private val inference = LlmInferenceHelper()
    private val mutableState = MutableStateFlow(ChatModelState())
    val state = mutableState.asStateFlow()
    val session = ChatSession(viewModelScope, inference::generateResponse)

    init {
        GemmaModelRepository.initialize(application)
        viewModelScope.launch {
            GemmaModelRepository.state.collect { installation ->
                if (installation.installedPath != null && !mutableState.value.ready &&
                    !mutableState.value.loading && mutableState.value.error == null) {
                    loadModel(installation.installedPath)
                }
            }
        }
    }

    fun retryLoading() {
        val path = GemmaModelRepository.state.value.installedPath ?: return
        if (mutableState.value.loading || mutableState.value.ready) return
        mutableState.value = ChatModelState(loading = true)
        viewModelScope.launch { loadModel(path) }
    }

    private suspend fun loadModel(path: String) {
        mutableState.value = ChatModelState(loading = true)
        try {
            inference.initialize(getApplication(), path)
            mutableState.value = ChatModelState(ready = true, supportsImages = inference.supportsImages)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableState.value = ChatModelState(
                error = "Gemma 4 could not load on this device: ${error.message ?: "unknown error"}. Close other apps and retry."
            )
        } catch (error: UnsatisfiedLinkError) {
            mutableState.value = ChatModelState(error = "This device does not support the AI runtime. Use a supported 64-bit Android device.")
        }
    }

    override fun onCleared() {
        // The native call may still be finishing. Cleanup waits for it without blocking the UI.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { inference.cleanup() }
        super.onCleared()
    }
}
