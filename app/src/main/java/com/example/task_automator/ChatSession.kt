package com.example.task_automator

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatSessionState(
    val messages: List<String> = emptyList(),
    val draft: String = "",
    val image: Bitmap? = null,
    val sending: Boolean = false,
    val loadingImage: Boolean = false,
    val error: String? = null
) {
    val busy: Boolean get() = sending || loadingImage
}

/** Owned by the chat ViewModel so rotation cannot discard an in-flight reply. */
class ChatSession(
    private val scope: CoroutineScope,
    private val respond: suspend (String, Bitmap?) -> String
) {
    private val mutableState = MutableStateFlow(ChatSessionState())
    val state = mutableState.asStateFlow()

    fun updateDraft(text: String) { mutableState.value = mutableState.value.copy(draft = text) }
    fun setImage(image: Bitmap?) { mutableState.value = mutableState.value.copy(image = image) }
    fun setLoadingImage(loading: Boolean) { mutableState.value = mutableState.value.copy(loadingImage = loading) }
    fun reportError(message: String?) { mutableState.value = mutableState.value.copy(error = message) }
    fun addMessage(message: String) {
        mutableState.value = mutableState.value.copy(messages = mutableState.value.messages + message)
    }

    fun send() {
        val request = mutableState.value
        if (request.busy || (request.draft.isBlank() && request.image == null)) return
        val prompt = request.draft
        val image = request.image
        mutableState.value = request.copy(sending = true, error = null)
        scope.launch {
            try {
                val response = respond(prompt, image)
                addMessage("You: ${prompt.ifBlank { "Attached image" }}")
                addMessage("Bot: $response")
                mutableState.value = mutableState.value.copy(draft = "", image = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Preserve the draft and attachment so a failed request can be retried.
                reportError(error.message ?: "Unable to generate a response. Please retry.")
            } finally {
                mutableState.value = mutableState.value.copy(sending = false)
            }
        }
    }
}
