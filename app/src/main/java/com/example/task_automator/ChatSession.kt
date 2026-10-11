package com.example.task_automator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatSessionState(
    val chats: List<SavedChat> = emptyList(),
    val current: SavedChat = SavedChat(),
    val draft: String = "",
    val imageName: String? = null,
    val sending: Boolean = false,
    val loadingImage: Boolean = false,
    val historyReady: Boolean = false,
    val partialReply: String = "",
    val error: String? = null
) {
    val busy: Boolean get() = sending || loadingImage
}

/** UI-independent chat state, retention and transactional persistence. */
class ChatSession(
    private val scope: CoroutineScope,
    private val respond: suspend (SavedChat, String, String?, (String) -> Unit) -> String,
    private val persist: suspend (List<SavedChat>, String?) -> Unit
) {
    private val mutableState = MutableStateFlow(ChatSessionState())
    val state = mutableState.asStateFlow()
    private var generation: Job? = null
    private var initialized = false

    fun restore(chats: List<SavedChat>) {
        if (initialized) return
        initialized = true
        mutableState.value = state.value.copy(chats = ChatRetention.keep(chats), historyReady = true, error = null)
    }
    fun updateDraft(text: String) { mutableState.value = state.value.copy(draft = text) }
    fun setImage(name: String?) { mutableState.value = state.value.copy(imageName = name) }
    fun setLoadingImage(loading: Boolean) { mutableState.value = state.value.copy(loadingImage = loading) }
    fun reportError(message: String?) { mutableState.value = state.value.copy(error = message) }

    fun newChat() {
        if (state.value.busy || !state.value.historyReady) return
        mutableState.value = state.value.copy(current = SavedChat(), draft = "", imageName = null, partialReply = "", error = null)
    }
    fun open(id: String) {
        if (state.value.busy) return
        val chat = state.value.chats.find { it.id == id } ?: return
        mutableState.value = state.value.copy(current = chat, draft = "", imageName = null, partialReply = "", error = null)
    }
    fun pin(id: String) = editHistory { chats -> chats.map { if (it.id == id) it.copy(pinned = !it.pinned) else it } }
    fun delete(id: String) = editHistory { chats -> chats.filterNot { it.id == id } }

    private fun editHistory(edit: (List<SavedChat>) -> List<SavedChat>) {
        if (state.value.busy || !state.value.historyReady) return
        val previous = state.value
        val next = ChatRetention.keep(edit(previous.chats))
        mutableState.value = previous.copy(loadingImage = true)
        scope.launch {
            try {
                persist(next, previous.imageName)
                val current = next.find { it.id == previous.current.id }
                    ?: if (previous.current.messages.isEmpty()) previous.current else SavedChat()
                mutableState.value = previous.copy(chats = next, current = current, error = null)
            } catch (error: Exception) {
                mutableState.value = previous.copy(error = "Could not save chat history: ${error.message}")
            }
        }
    }

    fun stop() { generation?.cancel() }
    fun send() {
        val request = state.value
        if (!request.historyReady || request.busy || (request.draft.isBlank() && request.imageName == null)) return
        val prompt = request.draft.trim().ifBlank { "Describe this image." }
        mutableState.value = request.copy(sending = true, partialReply = "", error = null)
        generation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var response = ""
            var cancelled = false
            try {
                response = respond(request.current, prompt, request.imageName) { partial ->
                    mutableState.value = state.value.copy(partialReply = partial)
                }
            } catch (error: CancellationException) {
                cancelled = true
                response = state.value.partialReply
            } catch (error: Exception) {
                reportError("Could not answer: ${error.message ?: "Please try again."}")
            }
            scope.launch {
                try {
                    if (response.isNotBlank()) {
                        val updated = request.current.copy(
                            title = if (request.current.messages.isEmpty()) request.draft.trim().ifBlank { "Image chat" }.take(48) else request.current.title,
                            updatedAt = System.currentTimeMillis(),
                            messages = request.current.messages + listOf(
                                ChatMessage(role = ChatRole.USER, text = prompt, imageName = request.imageName),
                                ChatMessage(role = ChatRole.ASSISTANT, text = response)
                            )
                        )
                        val chats = ChatRetention.keep(request.chats.filterNot { it.id == updated.id } + updated)
                        persist(chats, null)
                        mutableState.value = state.value.copy(chats = chats, current = updated, draft = "", imageName = null, error = null)
                    } else if (!cancelled && state.value.error == null) reportError("No answer was returned. Please try again.")
                } catch (error: Exception) {
                    reportError("Could not save this reply: ${error.message}. Your message is ready to retry.")
                } finally {
                    mutableState.value = state.value.copy(sending = false, partialReply = "")
                }
            }
        }
    }
}
